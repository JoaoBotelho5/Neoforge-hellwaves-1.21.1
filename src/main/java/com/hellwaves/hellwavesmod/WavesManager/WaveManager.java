package com.hellwaves.hellwavesmod.WavesManager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hellwaves.hellwavesmod.regivents.HWDeferredRegister;
import com.hellwaves.hellwavesmod.Waves.Wave;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class WaveManager {

    public static final List<Wave> WAVES = new ArrayList<>();
    public static JsonObject equipmentConfig;

    static {
        List<EntityType<? extends net.minecraft.world.entity.Mob>> enemies1 = new ArrayList<>();
        for (int i = 0; i < 5; i++) enemies1.add(EntityType.ZOMBIE);
        for (int i = 0; i < 5; i++) enemies1.add(EntityType.SKELETON);
        for (int i = 0; i < 5; i++) enemies1.add(EntityType.SPIDER);
        WAVES.add(new Wave(enemies1));

        List<EntityType<? extends net.minecraft.world.entity.Mob>> enemies2 = new ArrayList<>();
        for (int i = 0; i < 10; i++) enemies2.add(EntityType.ZOMBIE);
        for (int i = 0; i < 10; i++) enemies2.add(EntityType.SKELETON);
        for (int i = 0; i < 3; i++) enemies2.add(HWDeferredRegister.WARPED_MINER.get()); // WARPED MINERS
        for (int i = 0; i < 2; i++) enemies2.add(EntityType.WITHER_SKELETON);
        WAVES.add(new Wave(enemies2));

        // Wave 3 - Undead Lords will be added dynamically based on player count
        List<EntityType<? extends net.minecraft.world.entity.Mob>> enemies3 = new ArrayList<>();
        for (int i = 0; i < 10; i++) enemies3.add(EntityType.STRAY);
        for (int i = 0; i < 6; i++) enemies3.add(EntityType.SPIDER);
        for (int i = 0; i < 5; i++) enemies3.add(HWDeferredRegister.WARPED_MINER.get()); // WARPED MINERS
        for (int i = 0; i < 4; i++) enemies3.add(EntityType.WITHER_SKELETON);
        // NOTE: Undead Lords NOT added here - they're added in activateWave()
        WAVES.add(new Wave(enemies3));

        try (InputStreamReader reader = new InputStreamReader(
                WaveManager.class.getResourceAsStream("/assets/hellwavesmod/config/wave_equipment.json"))) {
            equipmentConfig = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            e.printStackTrace();
            equipmentConfig = new JsonObject();
        }
    }



    public static List<Mob> activateWave(Level world, BlockPos pos, Player player, int waveNumber) {
        // Wave 3 needs dynamic boss scaling
        if (waveNumber == 3) {
            // Count online players
            int playerCount = world.getServer().getPlayerList().getPlayerCount();

            // Scale Undead Lords: 1-3 based on player count (capped at 3)
            int undeadLordCount = Math.min(3, Math.max(1, playerCount));

            // Create a copy of the base wave 3 enemies
            List<EntityType<? extends Mob>> enemies3Dynamic = new ArrayList<>();
            for (int i = 0; i < 10; i++) enemies3Dynamic.add(EntityType.STRAY);
            for (int i = 0; i < 6; i++) enemies3Dynamic.add(EntityType.SPIDER);
            for (int i = 0; i < 5; i++) enemies3Dynamic.add(HWDeferredRegister.WARPED_MINER.get());
            for (int i = 0; i < 4; i++) enemies3Dynamic.add(EntityType.WITHER_SKELETON);

            // Add scaled Undead Lords
            for (int i = 0; i < undeadLordCount; i++) {
                enemies3Dynamic.add(HWDeferredRegister.UNDEAD_LORD.get());
            }

            // Create temporary wave with scaled bosses
            Wave dynamicWave = new Wave(enemies3Dynamic);
            return dynamicWave.spawn(world, pos, waveNumber);
        } else {
            // Normal waves
            Wave wave = WAVES.get(waveNumber - 1);
            return wave.spawn(world, pos, waveNumber);
        }
    }
}