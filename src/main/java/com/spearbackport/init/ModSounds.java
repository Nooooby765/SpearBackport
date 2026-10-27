package com.spearbackport.init;

import com.spearbackport.SpearBackportMod;
import com.spearbackport.item.SpearMaterial;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * The six spear sound events. 1.21.11 ships dedicated
 * {@code item.spear.{attack,hit,use}} samples plus {@code wood_} variants for the
 * wooden tier; 1.12.2 has no such assets, so this mod uses its own generated set
 * (see {@code tools/make_spear_sounds.py} and {@code assets/spearbackport/sounds.json}).
 *
 * <p>Only the wooden spear gets the wooden samples, mirroring the
 * {@code material == ToolMaterial.WOOD} switch in {@code Item.Properties#spear}.
 */
@Mod.EventBusSubscriber(modid = SpearBackportMod.MOD_ID)
public final class ModSounds {

    public static SoundEvent SPEAR_ATTACK;
    public static SoundEvent SPEAR_ATTACK_WOOD;
    public static SoundEvent SPEAR_HIT;
    public static SoundEvent SPEAR_HIT_WOOD;
    public static SoundEvent SPEAR_USE;
    public static SoundEvent SPEAR_USE_WOOD;

    private ModSounds() {}

    @SubscribeEvent
    public static void registerSounds(RegistryEvent.Register<SoundEvent> event) {
        SPEAR_ATTACK = register(event, "item.spear.attack");
        SPEAR_ATTACK_WOOD = register(event, "item.spear.wood_attack");
        SPEAR_HIT = register(event, "item.spear.hit");
        SPEAR_HIT_WOOD = register(event, "item.spear.wood_hit");
        SPEAR_USE = register(event, "item.spear.use");
        SPEAR_USE_WOOD = register(event, "item.spear.wood_use");
    }

    /** The stab whoosh of a jab. */
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

    private static SoundEvent register(RegistryEvent.Register<SoundEvent> event, String name) {
        ResourceLocation location = new ResourceLocation(SpearBackportMod.MOD_ID, name);
        SoundEvent sound = new SoundEvent(location);
        sound.setRegistryName(location);
        event.getRegistry().register(sound);
        return sound;
    }
}
