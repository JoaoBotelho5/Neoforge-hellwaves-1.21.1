package com.hellwaves.hellwavesmod.equipment;

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

import java.util.Random;

public class EliteEquipmentHelper {

    private static final Random random = new Random();

    public static void applyGear(Mob mob, JsonObject waveConfig) {
        // Apply mainhand
        if (waveConfig.has("mainhand")) {
            JsonArray mainhand = waveConfig.getAsJsonArray("mainhand");
            if (mainhand != null && mainhand.size() > 0) {
                String itemId = getRandomItem(mainhand);
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));
                if (item != null) {
                    mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(item));
                }
            }
        }

        // Apply offhand
        if (waveConfig.has("offhand")) {
            JsonArray offhand = waveConfig.getAsJsonArray("offhand");
            if (offhand != null && offhand.size() > 0) {
                String itemId = getRandomItem(offhand);
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));
                if (item != null) {
                    mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(item));
                }
            }
        }

        // Apply armor
        JsonObject armorObj = waveConfig.getAsJsonObject("armor");
        if (armorObj != null) {
            setArmorPiece(mob, armorObj, "head", net.minecraft.world.entity.EquipmentSlot.HEAD);
            setArmorPiece(mob, armorObj, "chest", net.minecraft.world.entity.EquipmentSlot.CHEST);
            setArmorPiece(mob, armorObj, "legs", net.minecraft.world.entity.EquipmentSlot.LEGS);
            setArmorPiece(mob, armorObj, "feet", net.minecraft.world.entity.EquipmentSlot.FEET);
        }

        // Apply potion effects
        if (waveConfig.has("effects")) {
            applyEffects(mob, waveConfig.getAsJsonArray("effects"));
        }
    }

    private static void setArmorPiece(Mob mob, JsonObject armorObj, String slotName, net.minecraft.world.entity.EquipmentSlot slot) {
        JsonArray slotArray = armorObj.getAsJsonArray(slotName);
        if (slotArray != null && slotArray.size() > 0) {
            String itemId = getRandomItem(slotArray);
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));
            if (item != null) {
                mob.setItemSlot(slot, new ItemStack(item));
            }
        }
    }

    private static String getRandomItem(JsonArray items) {
        // Calculate total weight
        int totalWeight = 0;
        for (int i = 0; i < items.size(); i++) {
            totalWeight += items.get(i).getAsJsonObject().get("weight").getAsInt();
        }

        // Select random item based on weight
        int r = random.nextInt(totalWeight);
        for (int i = 0; i < items.size(); i++) {
            JsonObject obj = items.get(i).getAsJsonObject();
            r -= obj.get("weight").getAsInt();
            if (r < 0) return obj.get("item").getAsString();
        }

        // Fallback to first item
        return items.get(0).getAsJsonObject().get("item").getAsString();
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
}