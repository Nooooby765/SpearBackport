package com.spearbackport.client.model;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;

import org.apache.commons.lang3.tuple.Pair;

import javax.vecmath.Matrix4f;
import java.util.List;

/**
 * Selects between the 16x16 inventory icon and the long 32x32 in-hand model,
 * mirroring the 26.3 {@code display_context} item model: GUI, ground and fixed
 * use the icon; first person, third person and head use the long model.
 *
 * <p>The transform pairs are cached per {@link TransformType}, so this adds no
 * per-frame allocation. All other model queries delegate to the icon model.
 */
public final class SpearModelWrapper implements IBakedModel {

    private final IBakedModel inventory;
    private final IBakedModel hand;

    @SuppressWarnings("unchecked")
    private final Pair<? extends IBakedModel, Matrix4f>[] cache =
            new Pair[TransformType.values().length];

    @SuppressWarnings("unchecked")
    public SpearModelWrapper(IBakedModel inventory, IBakedModel hand) {
        this.inventory = inventory;
        this.hand = hand;
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
        Pair<? extends IBakedModel, Matrix4f> pair = cache[index];
        if (pair == null) {
            IBakedModel model = usesHandModel(type) ? hand : inventory;
            pair = model.handlePerspective(type);
            cache[index] = pair;
        }
        return pair;
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
