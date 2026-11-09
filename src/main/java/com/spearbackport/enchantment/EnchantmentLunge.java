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
 * Lunge: spear-only, launches the wielder forward on a jab; 1.21.11 weight 5,
 * cost {@code 5 + 8 * (level - 1)} to +20, legal on books, compatible with Mending.
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

    /** Books must pass too, so Lunge can be traded and applied. */
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
