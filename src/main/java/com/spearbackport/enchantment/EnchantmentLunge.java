package com.spearbackport.enchantment;

import com.spearbackport.SpearBackportMod;
import com.spearbackport.item.SpearItem;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/**
 * Lunge: a spear-exclusive enchantment that launches the wielder forward on a
 * successful jab. Level I/II/III consume 1/2/3 hunger points and one extra
 * durability, matching 1.21.11.
 *
 * <p>Costs and weight follow the 1.21.11 enchantment definition:
 * weight 5, minimum cost {@code 5 + 8 * (level - 1)}, maximum cost 20 higher.
 * It is legal on enchanted books and compatible with Mending.
 */
public class EnchantmentLunge extends Enchantment {

    public EnchantmentLunge() {
        super(Rarity.UNCOMMON, EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
        setName("lunge");
        setRegistryName(new ResourceLocation(SpearBackportMod.MOD_ID, "lunge"));
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinEnchantability(int level) {
        return 5 + 8 * (Math.max(1, level) - 1);
    }

    @Override
    public int getMaxEnchantability(int level) {
        return getMinEnchantability(level) + 20;
    }

    /**
     * Spears are the intended target. Books must also pass so Lunge can be
     * traded, fished and applied with an anvil like any other enchantment.
     */
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return stack.getItem() instanceof SpearItem || stack.getItem() == Items.ENCHANTED_BOOK;
    }

    @Override
    public boolean canApply(ItemStack stack) {
        return stack.getItem() instanceof SpearItem || stack.getItem() == Items.ENCHANTED_BOOK;
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }
}
