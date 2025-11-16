package com.hellwaves.hellwavesmod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class EliteWaveManager {
    public static final List<EliteWave> WAVES = new ArrayList<>();
    public static JsonObject equipmentConfig;

    static {
        // Wave 1: Basic hostile mobs
        List<EntityType<? extends Mob>> enemies1 = new ArrayList<>();
        for (int i = 0; i < 8; i++) enemies1.add(EntityType.ZOMBIE);
        for (int i = 0; i < 8; i++) enemies1.add(EntityType.SKELETON);
        for (int i = 0; i < 4; i++) enemies1.add(EntityType.SPIDER);
        WAVES.add(new EliteWave(enemies1));

        // Wave 2: Nether mobs
        List<EntityType<? extends Mob>> enemies2 = new ArrayList<>();
        for (int i = 0; i < 12; i++) enemies2.add(EntityType.PIGLIN);
        for (int i = 0; i < 8; i++) enemies2.add(EntityType.WITHER_SKELETON);
        for (int i = 0; i < 4; i++) enemies2.add(EntityType.BLAZE);
        WAVES.add(new EliteWave(enemies2));

        // Wave 3: End mobs
        List<EntityType<? extends Mob>> enemies3 = new ArrayList<>();
        for (int i = 0; i < 4; i++) enemies3.add(EntityType.ENDERMAN);
        for (int i = 0; i < 8; i++) enemies3.add(EntityType.WITHER_SKELETON);
        for (int i = 0; i < 2; i++) enemies3.add(EntityType.PIGLIN_BRUTE);
        WAVES.add(new EliteWave(enemies3));

        // Wave 4: Boss-like mobs
        List<EntityType<? extends Mob>> enemies4 = new ArrayList<>();
        for (int i = 0; i < 4; i++) enemies4.add(EntityType.RAVAGER);
        for (int i = 0; i < 8; i++) enemies4.add(EntityType.PILLAGER);
        for (int i = 0; i < 6; i++) enemies4.add(EntityType.VINDICATOR);
        for (int i = 0; i < 2; i++) enemies4.add(EntityType.EVOKER);
        WAVES.add(new EliteWave(enemies4));

        // Wave 5: Ultimate challenge
        List<EntityType<? extends Mob>> enemies5 = new ArrayList<>();
        for (int i = 0; i < 4; i++) enemies5.add(EntityType.WITHER_SKELETON);
        for (int i = 0; i < 3; i++) enemies5.add(EntityType.BLAZE);
        for (int i = 0; i < 2; i++) enemies5.add(EntityType.RAVAGER);
        for (int i = 0; i < 1; i++) enemies5.add(EntityType.WARDEN); // Big finale!
        WAVES.add(new EliteWave(enemies5));

        try (InputStreamReader reader = new InputStreamReader(
                EliteWaveManager.class.getResourceAsStream("/assets/hellwavesmod/config/elite_wave_equipment.json"))) {
            equipmentConfig = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            e.printStackTrace();
            equipmentConfig = new JsonObject();
        }
    }

    public static List<Mob> activateWave(Level world, BlockPos pos, Player player, int waveNumber) {
        EliteWave wave = WAVES.get(waveNumber - 1);
        return wave.spawn(world, pos, waveNumber);
    }
}