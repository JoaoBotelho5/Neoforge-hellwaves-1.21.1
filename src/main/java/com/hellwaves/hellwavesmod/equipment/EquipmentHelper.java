package com.hellwaves.hellwavesmod.equipment;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Random;

public class EquipmentHelper {

    private static final Random random = new Random();

    public static void applyGear(Mob mob, JsonObject waveConfig) {
        // Mainhand
        JsonArray mainhand = waveConfig.getAsJsonArray("mainhand");
        if (mainhand != null) {
            String mainItemId = getRandomItem(mainhand);
            ResourceLocation rl = ResourceLocation.tryParse(mainItemId);
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item != null) {
                mob.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
            }
        }

        // Armor
        JsonObject armorObj = waveConfig.getAsJsonObject("armor");
        if (armorObj != null) {
            setArmorPiece(mob, armorObj, "head", EquipmentSlot.HEAD);
            setArmorPiece(mob, armorObj, "chest", EquipmentSlot.CHEST);
            setArmorPiece(mob, armorObj, "legs", EquipmentSlot.LEGS);
            setArmorPiece(mob, armorObj, "feet", EquipmentSlot.FEET);
        }
    }

    private static void setArmorPiece(Mob mob, JsonObject armorObj, String slotName, EquipmentSlot slot) {
        JsonArray slotArray = armorObj.getAsJsonArray(slotName);
        if (slotArray != null && slotArray.size() > 0) {
            String itemId = getRandomItem(slotArray);
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item != null) {
                mob.setItemSlot(slot, new ItemStack(item));
            }
        }
    }

    private static String getRandomItem(JsonArray items) {
        int totalWeight = 0;
        for (int i = 0; i < items.size(); i++) {
            totalWeight += items.get(i).getAsJsonObject().get("weight").getAsInt();
        }

        int r = random.nextInt(totalWeight);
        for (int i = 0; i < items.size(); i++) {
            JsonObject obj = items.get(i).getAsJsonObject();
            r -= obj.get("weight").getAsInt();
            if (r < 0) return obj.get("item").getAsString();
        }
        return items.get(0).getAsJsonObject().get("item").getAsString();
    }
}
