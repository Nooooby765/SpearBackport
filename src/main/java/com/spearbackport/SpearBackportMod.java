package com.spearbackport;

import com.spearbackport.init.ModItems;
import com.spearbackport.network.SpearNetwork;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

/**
 * Entry point: wires the network channel and adds the crafting recipes. Items and
 * enchantments register themselves through {@code @Mod.EventBusSubscriber} classes.
 */
@Mod(modid = SpearBackportMod.MOD_ID, name = SpearBackportMod.MOD_NAME, version = SpearBackportMod.VERSION,
        acceptedMinecraftVersions = "[1.12.2]")
public class SpearBackportMod {

    /** Generated from {@code gradle.properties} by RetroFuturaGradle, so metadata cannot drift. */
    public static final String MOD_ID = Tags.MOD_ID;
    public static final String MOD_NAME = Tags.MOD_NAME;
    public static final String VERSION = Tags.VERSION;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        SpearNetwork.init();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        registerRecipes();
    }

    /** Diagonal pattern as in 1.21.11; ore dictionary names also accept modded planks and ingots. */
    private void registerRecipes() {
        addSpearRecipe(ModItems.WOODEN_SPEAR, "plankWood");
        addSpearRecipe(ModItems.GOLDEN_SPEAR, "ingotGold");
        addSpearRecipe(ModItems.STONE_SPEAR, "cobblestone");
        addSpearRecipe(ModItems.IRON_SPEAR, "ingotIron");
        addSpearRecipe(ModItems.DIAMOND_SPEAR, "gemDiamond");
    }

    private static void addSpearRecipe(Item result, Object head) {
        if (result == null) {
            return;
        }
        GameRegistry.addShapedRecipe(
                new ResourceLocation(MOD_ID, result.getRegistryName().getPath()),
                null,
                new ItemStack(result),
                "  M",
                " S ",
                "S  ",
                'M', head,
                'S', net.minecraft.init.Items.STICK);
    }
}
