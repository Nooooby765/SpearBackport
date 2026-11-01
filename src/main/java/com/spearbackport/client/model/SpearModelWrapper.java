package com.spearbackport.client.model;

import com.spearbackport.client.SpearRenderContext;
import com.spearbackport.client.animation.SpearPose;
import com.spearbackport.client.animation.SpearUseParams;
import com.spearbackport.item.SpearItem;
import com.spearbackport.item.SpearMaterial;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumHandSide;

import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

import javax.vecmath.Matrix4f;
import java.util.List;

/**
 * Selects between the 16x16 inventory icon and the long 32x32 in-hand model,
 * mirroring the 26.3 {@code display_context} item model: GUI, ground and fixed
 * use the icon; first person, third person and head use the long model.
 *
 * <p>It also adds the procedural in-hand animation. 1.21.11 drives the spear
 * from {@code SpearAnimations}, which is plain math over the held time and the
 * swing progress rather than keyframes; 1.12.2 has no comparable hook, but
 * {@code handlePerspective} is asked for the transform of the model in exactly
 * the place where 1.21.11 applies its pose, so the same calls can be replayed
 * directly on top of the display matrix.
 *
 * <p>Cost: the display pairs are resolved once and cached per transform type.
 * Every transform that is not a hand transform returns a cached pair directly,
 * and so does every frame in which the spear is neither charging nor jabbing.
 * While it is animating, the pose matrix, the charge parameters and the returned
 * pair are all reused in place (one set per transform type), so an animating
 * frame allocates nothing either. Reusing them is safe because Forge's
 * {@code handleCameraTransforms} copies the matrix into GL and reads the model
 * before it returns — the returned pair never outlives the call.
 */
public final class SpearModelWrapper implements IBakedModel {

    /** The transform types that can carry the animation. */
    private static final boolean[] ANIMATED = new boolean[TransformType.values().length];

    static {
        ANIMATED[TransformType.FIRST_PERSON_LEFT_HAND.ordinal()] = true;
        ANIMATED[TransformType.FIRST_PERSON_RIGHT_HAND.ordinal()] = true;
        ANIMATED[TransformType.THIRD_PERSON_LEFT_HAND.ordinal()] = true;
        ANIMATED[TransformType.THIRD_PERSON_RIGHT_HAND.ordinal()] = true;
    }

    private final IBakedModel inventory;
    private final IBakedModel hand;
    private final SpearItem item;
    private final SpearMaterial material;

    @SuppressWarnings("unchecked")
    private final Pair<? extends IBakedModel, Matrix4f>[] cache =
            new Pair[TransformType.values().length];

    /** One reusable pose per animated transform type; null for the others. */
    private final Matrix4f[] poseScratch = new Matrix4f[TransformType.values().length];
    /** One reusable result pair per animated transform type; null for the others. */
    @SuppressWarnings("unchecked")
    private final MutablePair<IBakedModel, Matrix4f>[] animatedPairs =
            new MutablePair[TransformType.values().length];
    /** Reusable charge parameters, refilled on every animating frame. */
    private final SpearUseParams useParams = new SpearUseParams();

    public SpearModelWrapper(IBakedModel inventory, IBakedModel hand, SpearItem item) {
        this.inventory = inventory;
        this.hand = hand;
        this.item = item;
        this.material = item.getTier();
        for (int i = 0; i < ANIMATED.length; i++) {
            if (ANIMATED[i]) {
                poseScratch[i] = SpearPose.newPose();
                animatedPairs[i] = new MutablePair<>();
            }
        }
    }

    @Override
    public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
        return inventory.getQuads(state, side, rand);
    }

    @Override
    public boolean isAmbientOcclusion() {
        return inventory.isAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return inventory.isGui3d();
    }

    @Override
    public boolean isBuiltInRenderer() {
        return inventory.isBuiltInRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return inventory.getParticleTexture();
    }

    @Override
    public ItemOverrideList getOverrides() {
        return inventory.getOverrides();
    }

    @Override
    public Pair<? extends IBakedModel, Matrix4f> handlePerspective(TransformType type) {
        int index = type.ordinal();
        Pair<? extends IBakedModel, Matrix4f> base = cache[index];
        if (base == null) {
            base = (usesHandModel(type) ? hand : inventory).handlePerspective(type);
            cache[index] = base;
        }
        if (!ANIMATED[index]) {
            return base;
        }

        Matrix4f animation = pose(type);
        if (animation == null) {
            return base;
        }

        // The display transform is applied first; the animation happens in the
        // hand frame in front of it, exactly like 1.21.11's PoseStack order.
        Matrix4f display = base.getRight();
        if (display != null) {
            animation.mul(display);
        }
        MutablePair<IBakedModel, Matrix4f> pair = animatedPairs[index];
        pair.setLeft(base.getLeft());
        pair.setRight(animation);
        return pair;
    }

    /**
     * Returns the animation matrix for the given hand transform, or {@code null}
     * when this hand is not holding this spear or nothing is playing.
     */
    private Matrix4f pose(TransformType type) {
        boolean firstPerson = type == TransformType.FIRST_PERSON_LEFT_HAND
                || type == TransformType.FIRST_PERSON_RIGHT_HAND;

        EntityPlayer player;
        float partialTicks;
        if (firstPerson) {
            // First person only ever renders the local player's own hand.
            Minecraft minecraft = Minecraft.getMinecraft();
            player = minecraft.player;
            partialTicks = minecraft.getRenderPartialTicks();
        } else {
            player = SpearRenderContext.player();
            partialTicks = SpearRenderContext.partialTicks();
        }
        if (player == null) {
            return null;
        }

        // Idle fast path: a spear is held most of the time and animates almost
        // never, so bail before any hand or item lookup. The last frame of a
        // swing still reports progress, so the retract is not cut short.
        //
        // isHandActive() is a plain field read on the local player (EntityPlayerSP
        // overrides it) and a DataManager lookup plus an unbox on any other
        // player drawn in third person, so it is sampled once here and reused by
        // the charge branch below instead of being queried twice per frame.
        boolean active = player.isHandActive();
        float swing = player.getSwingProgress(partialTicks);
        if (!active && swing <= 0.0F) {
            return null;
        }

        boolean rightSide = type == TransformType.FIRST_PERSON_RIGHT_HAND
                || type == TransformType.THIRD_PERSON_RIGHT_HAND;
        EnumHandSide side = rightSide ? EnumHandSide.RIGHT : EnumHandSide.LEFT;
        EnumHand hand = player.getPrimaryHand() == side ? EnumHand.MAIN_HAND : EnumHand.OFF_HAND;
        ItemStack stack = player.getHeldItem(hand);
        if (stack.getItem() != item) {
            return null;
        }
        int invert = rightSide ? 1 : -1;

        // 1.21.11 checks for item use before it looks at the swing animation, so
        // holding right click always wins over a simultaneous jab. The third
        // person item layer is the exception: it stacks the stab and the charge
        // pose, while the first person renderer only ever applies one of them.
        //
        // The isHandActive() result is reused from the idle fast path; nothing
        // between the two reads can change the hand state, so the predicate is
        // exactly the same but costs one DataManager lookup instead of two.
        // getActiveItemStack() is a plain field, so its identity check stays.
        if (active && player.getActiveHand() == hand
                && player.getActiveItemStack().getItem() == item) {
            float timeHeld = stack.getMaxItemUseDuration() - (player.getItemInUseCount() - partialTicks + 1.0F);
            SpearUseParams params = useParams.fill(material, timeHeld);
            if (firstPerson) {
                // Before the rise starts and once the spear has settled back the
                // pose is the identity: keep the cached display transform.
                if (params.isNeutral()) {
                    return null;
                }
                Matrix4f pose = beginPose(type);
                SpearPose.applyFirstPersonUse(pose, params, invert);
                return pose;
            }

            boolean neutral = params.isNeutral();
            if (neutral && swing <= 0.0F) {
                return null;
            }
            Matrix4f pose = beginPose(type);
            SpearPose.applyThirdPersonAttack(pose, swing, SpearPose.FORWARD_MOVEMENT);
            if (!neutral) {
                SpearPose.applyThirdPersonUse(pose, params, invert, swing, SpearPose.FORWARD_MOVEMENT);
            }
            return pose;
        }

        if (swing <= 0.0F || player.swingingHand != hand) {
            return null;
        }

        Matrix4f pose = beginPose(type);
        if (firstPerson) {
            SpearPose.applyFirstPersonAttack(pose, swing, invert);
        } else {
            SpearPose.applyThirdPersonAttack(pose, swing, SpearPose.FORWARD_MOVEMENT);
        }
        return pose;
    }

    /**
     * Resets and returns the reusable pose for this transform type. Every pose
     * starts at the identity, exactly like the per-frame {@code Matrix4f} it
     * replaces, so the numbers fed to the matrix primitives are unchanged.
     */
    private Matrix4f beginPose(TransformType type) {
        Matrix4f pose = poseScratch[type.ordinal()];
        pose.setIdentity();
        return pose;
    }

    private static boolean usesHandModel(TransformType type) {
        switch (type) {
            case FIRST_PERSON_LEFT_HAND:
            case FIRST_PERSON_RIGHT_HAND:
            case THIRD_PERSON_LEFT_HAND:
            case THIRD_PERSON_RIGHT_HAND:
            case HEAD:
                return true;
            default:
                return false;
        }
    }
}
