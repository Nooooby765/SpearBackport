package com.spearbackport.item;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.Item.ToolMaterial;

/**
 * Per-tier statistics from 1.21.11 data; jab damage and attack speed are final
 * tooltip values, and the modifiers in {@link SpearItem} are derived against the
 * 1.12.2 baseline (1.0 damage, 4.0 speed).
 */
public enum SpearMaterial {
    //                     name       material            jab   attack speed      mult   repair                                       activate engaged tired disengage dismount
    WOODEN  ("wooden",  ToolMaterial.WOOD,    1.0F, 20.0F / 13.0F, 0.700F, Item.getItemFromBlock(Blocks.PLANKS),      15, 100, 100, 100, 14.0D),
    GOLDEN  ("golden",  ToolMaterial.GOLD,    1.0F, 20.0F / 19.0F, 0.700F, Items.GOLD_INGOT,                          14,  70, 100, 105, 10.0D),
    STONE   ("stone",   ToolMaterial.STONE,   2.0F,  4.0F /  3.0F, 0.820F, Item.getItemFromBlock(Blocks.COBBLESTONE), 14,  90,  90,  95, 10.0D),
    IRON    ("iron",    ToolMaterial.IRON,    3.0F, 20.0F / 19.0F, 0.950F, Items.IRON_INGOT,                          12,  50,  85,  90,  8.0D),
    DIAMOND ("diamond", ToolMaterial.DIAMOND, 4.0F, 20.0F / 21.0F, 1.075F, Items.DIAMOND,                             10,  60,  70,  70,  7.5D);

    public final String registryName;
    public final ToolMaterial toolMaterial;

    public final float jabDamage;
    /** Attacks per second ({@code 20 / cooldown ticks}). */
    public final float attackSpeed;
    public final float chargeMultiplier;
    public final Item repairItem;

    public final int activationDelay;
    public final int engagedDuration;
    public final int tiredDuration;
    public final int disengagedDuration;
    /** Blocks/second needed to dismount during the engaged stage. */
    public final double dismountSpeed;

    public final int tiredAt;
    public final int disengagedAt;
    public final int expiresAt;

    SpearMaterial(String registryName, ToolMaterial toolMaterial, float jabDamage, float attackSpeed,
                  float chargeMultiplier, Item repairItem, int activationDelay, int engagedDuration,
                  int tiredDuration, int disengagedDuration, double dismountSpeed) {
        this.registryName = registryName;
        this.toolMaterial = toolMaterial;
        this.jabDamage = jabDamage;
        this.attackSpeed = attackSpeed;
        this.chargeMultiplier = chargeMultiplier;
        this.repairItem = repairItem;
        this.activationDelay = activationDelay;
        this.engagedDuration = engagedDuration;
        this.tiredDuration = tiredDuration;
        this.disengagedDuration = disengagedDuration;
        this.dismountSpeed = dismountSpeed;

        this.tiredAt = activationDelay + engagedDuration;
        this.disengagedAt = tiredAt + tiredDuration;
        this.expiresAt = disengagedAt + disengagedDuration;
    }
}
