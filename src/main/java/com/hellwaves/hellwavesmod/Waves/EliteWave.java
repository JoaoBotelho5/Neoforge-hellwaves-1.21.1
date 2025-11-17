package com.hellwaves.hellwavesmod.Waves;

import com.google.gson.JsonObject;
import com.hellwaves.hellwavesmod.equipment.EliteEquipmentHelper;
import com.hellwaves.hellwavesmod.WavesManager.EliteWaveManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

public class EliteWave {
    private final List<EntityType<? extends Mob>> enemies;

    public EliteWave(List<EntityType<? extends Mob>> enemies) {
        this.enemies = enemies;
    }

    public List<Mob> spawn(Level world, BlockPos pos, int waveNumber) {
        List<Mob> spawned = new ArrayList<>();
        JsonObject waveConfig = EliteWaveManager.equipmentConfig.getAsJsonObject("elite_wave" + waveNumber);

        int numberOfMobs = enemies.size();
        double radius = 25.0; // Larger spawn radius

        for (int i = 0; i < numberOfMobs; i++) {
            EntityType<? extends Mob> type = enemies.get(i);
            Mob mob = type.create(world);
            if (mob != null) {
                double angle = 2 * Math.PI * i / numberOfMobs;
                double x = pos.getX() + radius * Math.cos(angle);
                double z = pos.getZ() + radius * Math.sin(angle);

                int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING, (int)x, (int)z);

                mob.moveTo(x, y, z, 0, 0);
                world.addFreshEntity(mob);

                if (waveConfig != null) {
                    EliteEquipmentHelper.applyGear(mob, waveConfig);
                }

                // Use the same WalkCenterGoal or create an elite version
                mob.goalSelector.addGoal(1, new Wave.WalkCenterGoal(mob, pos, 1.2)); // Faster movement

                // No drops for elite mobs
                mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0f);
                mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.OFFHAND, 0f);
                mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.HEAD, 0f);
                mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.CHEST, 0f);
                mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.LEGS, 0f);
                mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.FEET, 0f);

                spawned.add(mob);
            }
        }

        return spawned;
    }
}