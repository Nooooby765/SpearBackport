package com.spearbackport.client;

import net.minecraft.entity.player.EntityPlayer;

/**
 * Carries the "which player is being rendered right now" information that
 * 1.12.2 does not hand to {@code IBakedModel.handlePerspective}.
 *
 * <p>First person always renders the local player, so no context is needed
 * there. Third person items are drawn by {@code LayerHeldItem} during a player
 * render, are given no entity, and the renderer has no hook between the arm
 * transform and the item transform — so the current player is tracked around
 * {@code RenderPlayerEvent.Pre/Post} instead. That costs four field writes per
 * rendered player and nothing at all per tick.
 *
 * <p>The save/restore is a stack of depth one rather than a plain clear, so a
 * nested player render cannot lose the outer player. If another mod cancels
 * {@code Pre} the restore never runs, which only leaves a stale value behind
 * until the next successful render: {@code SpearModelWrapper} re-validates the
 * hand and the item before using it, so the worst case is one frame without a
 * third person animation.
 */
public final class SpearRenderContext {

    private static EntityPlayer player;
    private static float partialTicks;

    private static EntityPlayer savedPlayer;
    private static float savedPartialTicks;

    private SpearRenderContext() {}

    /** Called before a player model is drawn. */
    public static void begin(EntityPlayer renderedPlayer, float renderedPartialTicks) {
        savedPlayer = player;
        savedPartialTicks = partialTicks;
        player = renderedPlayer;
        partialTicks = renderedPartialTicks;
    }

    /** Called after a player model is drawn; restores the previous frame owner. */
    public static void end() {
        player = savedPlayer;
        partialTicks = savedPartialTicks;
        savedPlayer = null;
    }

    /** The player whose model is currently being drawn, or {@code null}. */
    public static EntityPlayer player() {
        return player;
    }

    public static float partialTicks() {
        return partialTicks;
    }
}
