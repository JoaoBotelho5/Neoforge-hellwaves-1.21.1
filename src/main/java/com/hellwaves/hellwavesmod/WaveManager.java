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

public class WaveManager {

    public static final List<Wave> WAVES = new ArrayList<>();
    public static JsonObject equipmentConfig;

    static {
        List<EntityType<? extends net.minecraft.world.entity.Mob>> enemies1 = new ArrayList<>();
        for (int i = 0; i < 5; i++) enemies1.add(EntityType.ZOMBIE);
        for (int i = 0; i < 5; i++) enemies1.add(EntityType.SKELETON);
        WAVES.add(new Wave(enemies1));

        List<EntityType<? extends net.minecraft.world.entity.Mob>> enemies2 = new ArrayList<>();
        for (int i = 0; i < 10; i++) enemies2.add(EntityType.ZOMBIE);
        for (int i = 0; i < 10; i++) enemies2.add(EntityType.SKELETON);
        for (int i = 0; i < 3; i++) enemies2.add(HWDeferredRegister.WARPED_MINER.get()); // WARPED MINERS
        for (int i = 0; i < 2; i++) enemies2.add(EntityType.WITHER_SKELETON);
        WAVES.add(new Wave(enemies2));

        List<EntityType<? extends net.minecraft.world.entity.Mob>> enemies3 = new ArrayList<>();
        for (int i = 0; i < 10; i++) enemies3.add(EntityType.STRAY);
        for (int i = 0; i < 6; i++) enemies3.add(EntityType.SPIDER);
        for (int i = 0; i < 5; i++) enemies3.add(HWDeferredRegister.WARPED_MINER.get()); // WARPED MINERS
        for (int i = 0; i < 4; i++) enemies3.add(EntityType.WITHER_SKELETON);
        enemies3.add(HWDeferredRegister.UNDEAD_LORD.get()); // BOSS MOB - LAST SPAWN
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
        Wave wave = WAVES.get(waveNumber - 1);
        return wave.spawn(world, pos, waveNumber);
    }
}
