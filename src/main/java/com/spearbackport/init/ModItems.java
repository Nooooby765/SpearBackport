package com.spearbackport.init;

import com.spearbackport.SpearBackportMod;
import com.spearbackport.item.SpearItem;
import com.spearbackport.item.SpearMaterial;

import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registers the five spears into direct static fields instead of
 * {@code @ObjectHolder} reflection, so they are readable right after registration.
 */
@Mod.EventBusSubscriber(modid = SpearBackportMod.MOD_ID)
public final class ModItems {

    public static SpearItem WOODEN_SPEAR;
    public static SpearItem STONE_SPEAR;
    public static SpearItem GOLDEN_SPEAR;
    public static SpearItem IRON_SPEAR;
    public static SpearItem DIAMOND_SPEAR;

    private static final List<SpearItem> SPEARS = new ArrayList<SpearItem>(5);

    private ModItems() {}

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        for (SpearMaterial tier : SpearMaterial.values()) {
            SpearItem spear = new SpearItem(tier);
            event.getRegistry().register(spear);
            SPEARS.add(spear);
            switch (tier) {
                case WOODEN:  WOODEN_SPEAR  = spear; break;
                case STONE:   STONE_SPEAR   = spear; break;
                case GOLDEN:  GOLDEN_SPEAR  = spear; break;
                case IRON:    IRON_SPEAR    = spear; break;
                case DIAMOND: DIAMOND_SPEAR = spear; break;
            }
        }
    }

    public static List<SpearItem> all() {
        return Collections.unmodifiableList(SPEARS);
    }
}
