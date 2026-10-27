package com.spearbackport.init;

import com.spearbackport.SpearBackportMod;
import com.spearbackport.enchantment.EnchantmentLunge;

import net.minecraft.enchantment.Enchantment;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Registers Lunge and keeps a direct reference so combat code never depends on
 * {@code @ObjectHolder} reflection.
 */
@Mod.EventBusSubscriber(modid = SpearBackportMod.MOD_ID)
public final class ModEnchantments {

    public static Enchantment LUNGE;

    private ModEnchantments() {}

    @SubscribeEvent
    public static void registerEnchantments(RegistryEvent.Register<Enchantment> event) {
        LUNGE = new EnchantmentLunge();
        event.getRegistry().register(LUNGE);
    }
}
