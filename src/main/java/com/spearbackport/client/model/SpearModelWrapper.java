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
 * Swaps the 16x16 icon for the long in-hand model and replays the 1.21.11 pose
 * where 1.21.11 applies it; the pose matrix, charge parameters and returned pair
 * are reused in place, safe because Forge copies the matrix and reads the model
 * before {@code handleCameraTransforms} returns.
 */
public final class SpearModelWrapper implements IBakedModel {

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
    @SuppressWarnings("unchecked")
    private final MutablePair<IBakedModel, Matrix4f>[] animatedPairs =
            new MutablePair[TransformType.values().length];
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

        // The display transform is applied first; the animation runs in the hand
        // frame in front of it, exactly like 1.21.11's PoseStack order.
        Matrix4f display = base.getRight();
        if (display != null) {
            animation.mul(display);
        }
        MutablePair<IBakedModel, Matrix4f> pair = animatedPairs[index];
        pair.setLeft(base.getLeft());
        pair.setRight(animation);
        return pair;
    }

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

        // Idle fast path: a held spear animates almost never. isHandActive() is a
        // DataManager lookup on other players, so it is sampled once and reused below.
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

        // 1.21.11 checks item use before the swing, so right click wins over a
        // simultaneous jab; third person stacks both poses, first person uses one.
        if (active && player.getActiveHand() == hand
                && player.getActiveItemStack().getItem() == item) {
            float timeHeld = stack.getMaxItemUseDuration() - (player.getItemInUseCount() - partialTicks + 1.0F);
            SpearUseParams params = useParams.fill(material, timeHeld);
            if (firstPerson) {
                // Neutral before the rise and after settling: keep the cached transform.
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
