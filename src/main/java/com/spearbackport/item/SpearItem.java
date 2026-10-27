package com.spearbackport.item;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.spearbackport.SpearBackportMod;
import com.spearbackport.combat.SpearCombat;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;

/**
 * The spear item. All combat math lives in {@link SpearCombat}:
 * <ul>
 *   <li><b>Jab</b> (left click) is handled by a small server-side listener on
 *       {@code AttackEntityEvent}. That gives us the 2..4.5 block reach window,
 *       multi-target piercing, no crits and the forced 100% attack charge
 *       without a mixin.</li>
 *   <li><b>Charge</b> (right hold) is driven by the vanilla item-use lifecycle;
 *       {@link #onUsingTick} forwards to {@link SpearCombat#tickCharge}.</li>
 * </ul>
 *
 * <p>Extending {@link ItemSword} gives us sword enchantment compatibility
 * (Sharpness, Smite, Bane of Arthropods, Knockback, Fire Aspect, Looting,
 * Unbreaking, Mending) and the standard durability/enchantability rules for
 * free.
 */
public class SpearItem extends ItemSword {

    // Do not redeclare ATTACK_DAMAGE_MODIFIER / ATTACK_SPEED_MODIFIER.
    // ItemStack.getTooltip() compares those UUIDs with ==, so the exact static
    // Item fields inherited here must be used or the tooltip shows generic
    // (red/blue) modifier lines instead of the weapon totals.

    /** Hold time is effectively "until released"; the charge itself expires first. */
    private static final int USE_DURATION = 72000;

    private final SpearMaterial tier;

    public SpearItem(SpearMaterial tier) {
        super(tier.toolMaterial);
        this.tier = tier;
        setRegistryName(SpearBackportMod.MOD_ID, tier.registryName + "_spear");
        setTranslationKey(SpearBackportMod.MOD_ID + "." + tier.registryName + "_spear");
        setCreativeTab(net.minecraft.creativetab.CreativeTabs.COMBAT);
    }

    public SpearMaterial getTier() {
        return tier;
    }

    // ------------------------------------------------------------------
    // Attributes
    // ------------------------------------------------------------------

    @Override
    public Multimap<String, AttributeModifier> getAttributeModifiers(EntityEquipmentSlot slot, ItemStack stack) {
        if (slot != EntityEquipmentSlot.MAINHAND) {
            return ImmutableMultimap.of();
        }

        // Player baseline in 1.12.2: 1.0 attack damage, 4.0 attack speed.
        Multimap<String, AttributeModifier> modifiers = HashMultimap.create(2, 1);
        modifiers.put(SharedMonsterAttributes.ATTACK_DAMAGE.getName(),
                new AttributeModifier(ATTACK_DAMAGE_MODIFIER, "Weapon modifier", tier.jabDamage - 1.0D, 0));
        modifiers.put(SharedMonsterAttributes.ATTACK_SPEED.getName(),
                new AttributeModifier(ATTACK_SPEED_MODIFIER, "Weapon modifier", tier.attackSpeed - 4.0D, 0));
        return modifiers;
    }

    @Override
    public boolean getIsRepairable(ItemStack toRepair, ItemStack repair) {
        return repair.getItem() == tier.repairItem;
    }

    // ------------------------------------------------------------------
    // Item use lifecycle (charge attack)
    // ------------------------------------------------------------------

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return USE_DURATION;
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        // NONE keeps the vanilla bow-style draw pose and the 0.2x movement
        // slowdown out of the way; ClientInputHandler cancels that slowdown.
        return EnumAction.NONE;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        player.setActiveHand(hand);
        if (!world.isRemote) {
            world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.55F, 0.9F);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public void onUsingTick(ItemStack stack, EntityLivingBase player, int count) {
        if (player instanceof EntityPlayer) {
            SpearCombat.tickCharge(stack, (EntityPlayer) player, getMaxItemUseDuration(stack) - count);
        }
    }

    // ------------------------------------------------------------------
    // Block breaking
    // ------------------------------------------------------------------

    /**
     * A spear performs a jab instead of mining, so it can never break blocks.
     * Returning -1 makes every block unbreakable with this item; the click
     * itself is also cancelled in {@code SpearCombat} so no block interaction
     * or break progress leaks through.
     */
    @Override
    public float getDestroySpeed(ItemStack stack, IBlockState state) {
        return -1.0F;
    }
}
