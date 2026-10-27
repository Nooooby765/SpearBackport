package com.spearbackport.item;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.Item.ToolMaterial;

/**
 * Every per-tier spear statistic, taken from the 1.21.11 Java Edition data.
 *
 * <p>Jab damage and attack speed are the final tooltip values. The attribute
 * modifiers applied by {@link SpearItem} are derived from them against the
 * 1.12.2 player baseline (1.0 attack damage, 4.0 attack speed).
 *
 * <p>Charge-attack timing is expressed in ticks. The three stages are
 * <em>engaged</em> (dismount + knockback + damage), <em>tired</em> (knockback +
 * damage) and <em>disengaged</em> (damage only). 1.21.11 release-candidate
 * values are used for the second and third stages.
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

    /** Final jab attack damage shown in the tooltip. */
    public final float jabDamage;
    /** Final attack speed in attacks per second (20 / cooldown ticks). */
    public final float attackSpeed;
    /** Charge damage multiplier applied to the closing speed in blocks/second. */
    public final float chargeMultiplier;
    /** Item consumed by anvil repair. */
    public final Item repairItem;

    /** Ticks between starting the charge and the first possible hit. */
    public final int activationDelay;
    /** Engaged-stage length in ticks. */
    public final int engagedDuration;
    /** Tired-stage length in ticks. */
    public final int tiredDuration;
    /** Disengaged-stage length in ticks. */
    public final int disengagedDuration;
    /** Attacker speed (blocks/second) needed to dismount a target during the engaged stage. */
    public final double dismountSpeed;

    /** First tick at which the tired stage starts. */
    public final int tiredAt;
    /** First tick at which the disengaged stage starts. */
    public final int disengagedAt;
    /** First tick at which the spear stops dealing damage. */
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
