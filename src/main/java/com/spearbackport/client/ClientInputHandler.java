package com.spearbackport.client;

import com.spearbackport.SpearBackportMod;
import com.spearbackport.item.SpearItem;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovementInput;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 1.12.2 multiplies movement input by 0.2 whenever {@code isHandActive()} is
 * true, regardless of {@code EnumAction}. A 1.21.11 spear charge must not slow
 * the player down (it is meant to be used while sprinting or riding), so the
 * input is pre-scaled by the inverse here. Vanilla applies its 0.2 factor
 * immediately afterwards, leaving the original input untouched.
 *
 * <p>Riding is left alone because vanilla skips the slowdown while mounted.
 */
@Mod.EventBusSubscriber(modid = SpearBackportMod.MOD_ID, value = Side.CLIENT)
public final class ClientInputHandler {

    private static final float INVERSE_SLOWDOWN = 5.0F;

    private ClientInputHandler() {}

    @SubscribeEvent
    public static void onInputUpdate(InputUpdateEvent event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player.isRiding() || !player.isHandActive()) {
            return;
        }

        ItemStack active = player.getActiveItemStack();
        if (!(active.getItem() instanceof SpearItem)) {
            return;
        }

        MovementInput input = event.getMovementInput();
        if (input.moveForward != 0.0F) {
            input.moveForward *= INVERSE_SLOWDOWN;
        }
        if (input.moveStrafe != 0.0F) {
            input.moveStrafe *= INVERSE_SLOWDOWN;
        }
    }
}
