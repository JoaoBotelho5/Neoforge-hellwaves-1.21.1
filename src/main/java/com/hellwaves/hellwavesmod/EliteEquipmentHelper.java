package com.hellwaves.hellwavesmod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class EliteEquipmentHelper {

    public static void applyGear(Mob mob, JsonObject waveConfig) {
        // Apply armor and weapons
        applyItem(mob, waveConfig, "mainhand", net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        applyItem(mob, waveConfig, "offhand", net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        applyItem(mob, waveConfig, "helmet", net.minecraft.world.entity.EquipmentSlot.HEAD);
        applyItem(mob, waveConfig, "chestplate", net.minecraft.world.entity.EquipmentSlot.CHEST);
        applyItem(mob, waveConfig, "leggings", net.minecraft.world.entity.EquipmentSlot.LEGS);
        applyItem(mob, waveConfig, "boots", net.minecraft.world.entity.EquipmentSlot.FEET);

        // Apply potion effects
        if (waveConfig.has("effects")) {
            applyEffects(mob, waveConfig.getAsJsonArray("effects"));
        }

        // Enchantments will be handled separately if needed
    }

    private static void applyItem(Mob mob, JsonObject config, String slot, net.minecraft.world.entity.EquipmentSlot equipmentSlot) {
        if (config.has(slot)) {
            String itemId = config.get(slot).getAsString();
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            if (item != null) {
                ItemStack stack = new ItemStack(item);

                // Apply pre-enchanted items if specified in a simpler way
                if (config.has("pre_enchanted") && config.get("pre_enchanted").getAsBoolean()) {
                    // For now, we'll skip complex enchantment system
                    // You can add simple enchantments later
                }

                mob.setItemSlot(equipmentSlot, stack);
            }
        }
    }

    private static void applyEffects(Mob mob, JsonArray effectsArray) {
        for (JsonElement element : effectsArray) {
            JsonObject effectObj = element.getAsJsonObject();
            String effectId = effectObj.get("effect").getAsString();
            int duration = effectObj.get("duration").getAsInt();
            int amplifier = effectObj.get("amplifier").getAsInt();

            MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.parse(effectId));
            if (effect != null) {
                mob.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect), duration, amplifier, false, false));
            }
        }
    }

    private static net.minecraft.world.entity.EquipmentSlot getSlotFromString(String slot) {
        return switch (slot) {
            case "mainhand" -> net.minecraft.world.entity.EquipmentSlot.MAINHAND;
            case "offhand" -> net.minecraft.world.entity.EquipmentSlot.OFFHAND;
            case "helmet" -> net.minecraft.world.entity.EquipmentSlot.HEAD;
            case "chestplate" -> net.minecraft.world.entity.EquipmentSlot.CHEST;
            case "leggings" -> net.minecraft.world.entity.EquipmentSlot.LEGS;
            case "boots" -> net.minecraft.world.entity.EquipmentSlot.FEET;
            default -> net.minecraft.world.entity.EquipmentSlot.MAINHAND;
        };
    }
}