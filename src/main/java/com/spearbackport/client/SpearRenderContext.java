package com.spearbackport.client;

import net.minecraft.entity.player.EntityPlayer;

/**
 * The player being drawn, which {@code IBakedModel.handlePerspective} is not
 * given in 1.12.2; a cancelled {@code Pre} can leave a stale player, but the
 * wrapper re-validates hand and item.
 */
public final class SpearRenderContext {

    private static EntityPlayer player;
    private static float partialTicks;

    private static EntityPlayer savedPlayer;
    private static float savedPartialTicks;

    private SpearRenderContext() {}

    public static void begin(EntityPlayer renderedPlayer, float renderedPartialTicks) {
        savedPlayer = player;
        savedPartialTicks = partialTicks;
        player = renderedPlayer;
        partialTicks = renderedPartialTicks;
    }

    public static void end() {
        player = savedPlayer;
        partialTicks = savedPartialTicks;
        savedPlayer = null;
    }

    public static EntityPlayer player() {
        return player;
    }

    public static float partialTicks() {
        return partialTicks;
    }
}
