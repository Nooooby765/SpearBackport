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
 * Entry point. Items and enchantments register themselves through
 * {@code @Mod.EventBusSubscriber} classes; this class wires the network channel
 * and adds the crafting recipes.
 */
@Mod(modid = SpearBackportMod.MOD_ID, name = SpearBackportMod.MOD_NAME, version = SpearBackportMod.VERSION,
        acceptedMinecraftVersions = "[1.12.2]")
public class SpearBackportMod {

    public static final String MOD_ID = "spearbackport";
    public static final String MOD_NAME = "Spear Backport";
    public static final String VERSION = "1.0.0";

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        SpearNetwork.init();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        registerRecipes();
    }

    /**
     * Diagonal recipe, exactly like 1.21.11:
     * <pre>
     *   M
     *  S
     * S
     * </pre>
     * Ore dictionary names are used so modded planks/ingots/gems work too.
     */
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
