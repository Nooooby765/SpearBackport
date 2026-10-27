package com.spearbackport.network;

import com.spearbackport.SpearBackportMod;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * One tiny client-to-server packet, used because 1.12.2 only sends an attack
 * packet when the vanilla picker found an entity within three blocks. The spear
 * must be able to jab empty air (for Lunge), blocks, and targets up to 4.5
 * blocks away, so the client sends this packet instead of relying on
 * {@code CPacketUseEntity}.
 */
public final class SpearNetwork {

    public static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel(SpearBackportMod.MOD_ID);

    private static boolean initialized;

    private SpearNetwork() {}

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        CHANNEL.registerMessage(MessageSpearJab.Handler.class, MessageSpearJab.class, 0, Side.SERVER);
    }
}
