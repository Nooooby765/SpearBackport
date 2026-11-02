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
 *
 * <p>This has to be an always-subscribed hook: vanilla checks
 * {@code isHandActive()} unconditionally at {@code EntityPlayerSP} line 992, so
 * any tick in which that bit can be true must be corrected in the same tick and
 * between lines 989 and 992. No item lifecycle callback lands in that window.
 * The idle cost is one virtual call resolving to the {@code handActive} boolean
 * field ({@code EntityPlayerSP} overrides {@code isHandActive()} with a field
 * read, it is not a data manager lookup), and nothing is allocated.
 */
@Mod.EventBusSubscriber(modid = SpearBackportMod.MOD_ID, value = Side.CLIENT)
public final class ClientInputHandler {

    private static final float INVERSE_SLOWDOWN = 5.0F;

    private ClientInputHandler() {}

    @SubscribeEvent
    public static void onInputUpdate(InputUpdateEvent event) {
        EntityPlayer player = event.getEntityPlayer();
        // Cheapest predicate first: the idle tick stops at the handActive field.
        if (!player.isHandActive() || player.isRiding()) {
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
