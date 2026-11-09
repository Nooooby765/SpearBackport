package com.spearbackport.network;

import com.spearbackport.SpearBackportMod;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * One tiny client-to-server packet: 1.12.2 would otherwise only attack an
 * entity the vanilla three-block picker found.
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
