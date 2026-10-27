package com.spearbackport.init;

import com.spearbackport.SpearBackportMod;
import com.spearbackport.item.SpearMaterial;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * The seven spear sound events, named exactly like the official
 * {@code assets/minecraft/sounds.json} entries: {@code item.spear.attack},
 * {@code item.spear.hit}, {@code item.spear.use}, {@code item.spear.lunge} and
 * the three {@code item.spear_wood.*} twins the wooden tier uses.
 *
 * <p>1.21.11 ships dedicated samples for all of them, so this mod generates its
 * own set (see {@code tools/make_spear_sounds.py} and
 * {@code assets/spearbackport/sounds.json}). The variant counts also match:
 * {@code use} has one sample, {@code attack}, {@code hit} and {@code lunge} have
 * three randomised ones.
 *
 * <p>One deliberate difference: the official set splits Lunge into three
 * separate events ({@code item.spear.lunge_1..3}, one sample each), which the
 * game chooses between. 1.12.2's single-event multi-variant form gives the same
 * audible variation from one event, so this mod keeps a single
 * {@code item.spear.lunge}.
 */
@Mod.EventBusSubscriber(modid = SpearBackportMod.MOD_ID)
public final class ModSounds {

    public static SoundEvent SPEAR_ATTACK;
    public static SoundEvent SPEAR_ATTACK_WOOD;
    public static SoundEvent SPEAR_HIT;
    public static SoundEvent SPEAR_HIT_WOOD;
    public static SoundEvent SPEAR_USE;
    public static SoundEvent SPEAR_USE_WOOD;
    public static SoundEvent SPEAR_LUNGE;

    private ModSounds() {}

    @SubscribeEvent
    public static void registerSounds(RegistryEvent.Register<SoundEvent> event) {
        SPEAR_ATTACK = register(event, "item.spear.attack");
        SPEAR_ATTACK_WOOD = register(event, "item.spear_wood.attack");
        SPEAR_HIT = register(event, "item.spear.hit");
        SPEAR_HIT_WOOD = register(event, "item.spear_wood.hit");
        SPEAR_USE = register(event, "item.spear.use");
        SPEAR_USE_WOOD = register(event, "item.spear_wood.use");
        SPEAR_LUNGE = register(event, "item.spear.lunge");
    }

    /** The thrust whoosh of a jab, played even on a whiff. */
    public static SoundEvent attack(SpearMaterial tier) {
        return tier == SpearMaterial.WOODEN ? SPEAR_ATTACK_WOOD : SPEAR_ATTACK;
    }

    /** The impact of a jab or charge contact. */
    public static SoundEvent hit(SpearMaterial tier) {
        return tier == SpearMaterial.WOODEN ? SPEAR_HIT_WOOD : SPEAR_HIT;
    }

    /** Played once when the charge starts, like {@code KineticWeapon.makeSound}. */
    public static SoundEvent use(SpearMaterial tier) {
        return tier == SpearMaterial.WOODEN ? SPEAR_USE_WOOD : SPEAR_USE;
    }

    /** The rush of air from the Lunge enchantment. */
    public static SoundEvent lunge() {
        return SPEAR_LUNGE;
    }

    private static SoundEvent register(RegistryEvent.Register<SoundEvent> event, String name) {
        ResourceLocation location = new ResourceLocation(SpearBackportMod.MOD_ID, name);
        SoundEvent sound = new SoundEvent(location);
        sound.setRegistryName(location);
        event.getRegistry().register(sound);
        return sound;
    }
}
