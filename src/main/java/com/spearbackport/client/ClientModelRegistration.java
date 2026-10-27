package com.spearbackport.client;

import com.spearbackport.SpearBackportMod;
import com.spearbackport.client.model.SpearModelWrapper;
import com.spearbackport.init.ModItems;
import com.spearbackport.item.SpearItem;

import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Registers both models for every spear and installs the display-context
 * wrapper at bake time. Item sprites are stitched by the normal item-model
 * loader; no manual atlas registration is involved.
 */
@Mod.EventBusSubscriber(modid = SpearBackportMod.MOD_ID, value = Side.CLIENT)
public final class ClientModelRegistration {

    private ClientModelRegistration() {}

    @SubscribeEvent
    public static void onModelRegistry(ModelRegistryEvent event) {
        for (SpearItem spear : ModItems.all()) {
            ModelResourceLocation inventory = inventoryModel(spear);
            ModelResourceLocation hand = handModel(spear);
            ModelBakery.registerItemVariants(spear, inventory, hand);
            ModelLoader.setCustomModelResourceLocation(spear, 0, inventory);
        }
    }

    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent event) {
        for (SpearItem spear : ModItems.all()) {
            ModelResourceLocation inventory = inventoryModel(spear);
            IBakedModel inventoryBaked = event.getModelRegistry().getObject(inventory);
            if (inventoryBaked == null || inventoryBaked instanceof SpearModelWrapper) {
                continue;
            }

            IBakedModel handBaked = event.getModelRegistry().getObject(handModel(spear));
            if (handBaked == null) {
                handBaked = inventoryBaked;
            }
            event.getModelRegistry().putObject(inventory,
                    new SpearModelWrapper(inventoryBaked, handBaked, spear));
        }
    }

    private static ModelResourceLocation inventoryModel(SpearItem spear) {
        return new ModelResourceLocation(spear.getRegistryName(), "inventory");
    }

    private static ModelResourceLocation handModel(SpearItem spear) {
        ResourceLocation name = spear.getRegistryName();
        return new ModelResourceLocation(
                new ResourceLocation(name.getNamespace(), name.getPath() + "_in_hand"), "inventory");
    }
}
