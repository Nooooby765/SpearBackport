package com.spearbackport.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.spearbackport.SpearBackportMod;
import com.spearbackport.combat.SpearCombat;
import com.spearbackport.combat.SpearJabState;
import com.spearbackport.init.ModSounds;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
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
 * The spear item; all combat math lives in {@link SpearCombat}. Extending
 * {@link ItemSword} gives sword enchantment compatibility and the standard
 * durability rules.
 */
public class SpearItem extends ItemSword {

    // Do not redeclare these UUIDs: ItemStack.getTooltip() compares them with ==,
    // so the inherited Item fields must be used or the tooltip shows generic lines.

    /** Hold time is effectively "until released"; the charge itself expires first. */
    private static final int USE_DURATION = 72000;

    private static final Multimap<String, AttributeModifier> NO_MODIFIERS = ImmutableMultimap.of();

    private final SpearMaterial tier;
    /** Built once: the tooltip asks for the modifiers on every inventory frame. */
    private final Multimap<String, AttributeModifier> mainHandModifiers;

    public SpearItem(SpearMaterial tier) {
        super(tier.toolMaterial);
        this.tier = tier;
        // Player baseline in 1.12.2: 1.0 attack damage, 4.0 attack speed.
        this.mainHandModifiers = ImmutableMultimap.<String, AttributeModifier>builder()
                .put(SharedMonsterAttributes.ATTACK_DAMAGE.getName(),
                        new AttributeModifier(ATTACK_DAMAGE_MODIFIER, "Weapon modifier",
                                tier.jabDamage - 1.0D, 0))
                .put(SharedMonsterAttributes.ATTACK_SPEED.getName(),
                        new AttributeModifier(ATTACK_SPEED_MODIFIER, "Weapon modifier",
                                tier.attackSpeed - 4.0D, 0))
                .build();
        setRegistryName(SpearBackportMod.MOD_ID, tier.registryName + "_spear");
        setTranslationKey(SpearBackportMod.MOD_ID + "." + tier.registryName + "_spear");
        setCreativeTab(net.minecraft.creativetab.CreativeTabs.COMBAT);
    }

    public SpearMaterial getTier() {
        return tier;
    }

    @Override
    public Multimap<String, AttributeModifier> getAttributeModifiers(EntityEquipmentSlot slot, ItemStack stack) {
        return slot == EntityEquipmentSlot.MAINHAND ? mainHandModifiers : NO_MODIFIERS;
    }

    @Override
    public boolean getIsRepairable(ItemStack toRepair, ItemStack repair) {
        return repair.getItem() == tier.repairItem;
    }

    /**
     * True swallows the swing for a refused click (1.21.11's
     * {@code MINIMUM_ATTACK_CHARGE} gate); {@code isUser()} is false on the
     * server, so a real jab still swings.
     *
     * @see com.spearbackport.combat.SpearJabState
     */
    @Override
    public boolean onEntitySwing(EntityLivingBase entity, ItemStack stack) {
        if (!(entity instanceof EntityPlayer)) {
            return false;
        }
        EntityPlayer player = (EntityPlayer) entity;
        return player.isUser() && !SpearJabState.isApproved(player.ticksExisted);
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return USE_DURATION;
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        // NONE avoids the bow-style draw pose and the 0.2x slowdown, which
        // ClientInputHandler cancels.
        return EnumAction.NONE;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        player.setActiveHand(hand);
        if (!world.isRemote) {
            // KineticWeapon.makeSound: one world sound when the charge starts.
            world.playSound(null, player.posX, player.posY, player.posZ,
                    ModSounds.use(tier), SoundCategory.PLAYERS, 1.0F, 1.0F);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public void onUsingTick(ItemStack stack, EntityLivingBase player, int count) {
        if (player instanceof EntityPlayer) {
            SpearCombat.tickCharge(stack, (EntityPlayer) player, getMaxItemUseDuration(stack) - count);
        }
    }

    /** Weapons, not tools: no block-breaking speed and no mining at all. */
    @Override
    public float getDestroySpeed(ItemStack stack, IBlockState state) {
        return -1.0F;
    }
}
