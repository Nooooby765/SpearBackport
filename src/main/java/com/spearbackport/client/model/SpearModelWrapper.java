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
 * While it is animating, one {@link Matrix4f} (plus one small immutable
 * {@link SpearUseParams} and one {@code Pair}) is allocated per frame.
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

    public SpearModelWrapper(IBakedModel inventory, IBakedModel hand, SpearItem item) {
        this.inventory = inventory;
        this.hand = hand;
        this.item = item;
        this.material = item.getTier();
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
        IBakedModel model = base.getLeft();
        return Pair.of(model, animation);
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

        boolean rightSide = type == TransformType.FIRST_PERSON_RIGHT_HAND
                || type == TransformType.THIRD_PERSON_RIGHT_HAND;
        EnumHandSide side = rightSide ? EnumHandSide.RIGHT : EnumHandSide.LEFT;
        EnumHand hand = player.getPrimaryHand() == side ? EnumHand.MAIN_HAND : EnumHand.OFF_HAND;
        ItemStack stack = player.getHeldItem(hand);
        if (stack.getItem() != item) {
            return null;
        }
        int invert = rightSide ? 1 : -1;
        float swing = player.getSwingProgress(partialTicks);

        // 1.21.11 checks for item use before it looks at the swing animation, so
        // holding right click always wins over a simultaneous jab. The third
        // person item layer is the exception: it stacks the stab and the charge
        // pose, while the first person renderer only ever applies one of them.
        if (player.isHandActive() && player.getActiveHand() == hand
                && player.getActiveItemStack().getItem() == item) {
            float timeHeld = stack.getMaxItemUseDuration() - (player.getItemInUseCount() - partialTicks + 1.0F);
            SpearUseParams params = SpearUseParams.of(material, timeHeld);
            Matrix4f pose = SpearPose.newPose();
            if (firstPerson) {
                SpearPose.applyFirstPersonUse(pose, params, invert);
            } else {
                SpearPose.applyThirdPersonAttack(pose, swing, SpearPose.FORWARD_MOVEMENT);
                SpearPose.applyThirdPersonUse(pose, params, invert, swing, SpearPose.FORWARD_MOVEMENT);
            }
            return pose;
        }

        if (swing <= 0.0F || player.swingingHand != hand) {
            return null;
        }

        Matrix4f pose = SpearPose.newPose();
        if (firstPerson) {
            SpearPose.applyFirstPersonAttack(pose, swing, invert);
        } else {
            SpearPose.applyThirdPersonAttack(pose, swing, SpearPose.FORWARD_MOVEMENT);
        }
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
