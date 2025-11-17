package com.hellwaves.hellwavesmod.equipment;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.io.InputStreamReader;
import java.util.Random;

public class EquipmentConfig {

    private static final Random rand = new Random();
    private static JsonObject config;

    public static void load() {
        try (InputStreamReader reader = new InputStreamReader(
                EquipmentConfig.class.getResourceAsStream("/assets/hellwavesmod/config/wave_equipment.json"))) {
            config = new Gson().fromJson(reader, JsonObject.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Weighted random item
    public static String getRandomItem(String waveKey, String slot) {
        if (config == null) return "minecraft:air";

        JsonArray array;
        if (slot.equals("mainhand")) {
            array = config.getAsJsonObject(waveKey).getAsJsonArray("mainhand");
        } else {
            array = config.getAsJsonObject(waveKey).getAsJsonObject("armor").getAsJsonArray(slot);
        }

        // Calculate total weight
        int totalWeight = 0;
        for (JsonElement e : array) totalWeight += e.getAsJsonObject().get("weight").getAsInt();

        int r = rand.nextInt(totalWeight);
        for (JsonElement e : array) {
            JsonObject obj = e.getAsJsonObject();
            r -= obj.get("weight").getAsInt();
            if (r < 0) return obj.get("item").getAsString();
        }
        return array.get(0).getAsJsonObject().get("item").getAsString();
    }
}
