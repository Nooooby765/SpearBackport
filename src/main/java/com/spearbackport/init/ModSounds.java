package com.spearbackport.init;

import com.spearbackport.SpearBackportMod;
import com.spearbackport.item.SpearMaterial;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Names must match {@code assets/minecraft/sounds.json} ({@code item.spear.*} plus
 * three {@code item.spear_wood.*} twins); 1.12.2 reproduces the three 1.21.11 Lunge
 * sounds as one multi-variant {@code item.spear.lunge}. Samples come from
 * {@code tools/make_spear_sounds.py}.
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

    public static SoundEvent attack(SpearMaterial tier) {
        return tier == SpearMaterial.WOODEN ? SPEAR_ATTACK_WOOD : SPEAR_ATTACK;
    }

    public static SoundEvent hit(SpearMaterial tier) {
        return tier == SpearMaterial.WOODEN ? SPEAR_HIT_WOOD : SPEAR_HIT;
    }

    public static SoundEvent use(SpearMaterial tier) {
        return tier == SpearMaterial.WOODEN ? SPEAR_USE_WOOD : SPEAR_USE;
    }

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
