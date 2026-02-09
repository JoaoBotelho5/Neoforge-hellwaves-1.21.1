package com.hellwaves.hellwavesmod.Waves;

import com.google.gson.JsonObject;
import com.hellwaves.hellwavesmod.equipment.EliteEquipmentHelper;
import com.hellwaves.hellwavesmod.HWMobs.WarpedMiner;
import com.hellwaves.hellwavesmod.WavesManager.EliteWaveManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.ai.goal.Goal;

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

                int y;

                // Phantom spawn no ar
                if (mob.getType() == EntityType.PHANTOM) {
                    y = world.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z) + 15;
                }
                // Warden spawn correto
                else if (mob.getType() == EntityType.WARDEN) {
                    y = world.getHeight(Heightmap.Types.WORLD_SURFACE, (int) x, (int) z);
                }
                // Outros mobs normais
                else {
                    y = world.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z);
                }

                mob.moveTo(x, y, z, 0.0F, 0.0F);

                // CRITICAL: Prevent despawning when player leaves range
                mob.setPersistenceRequired();

                // Configuração especial para Warden
                if (mob instanceof Warden warden && world instanceof ServerLevel serverLevel) {
                    DifficultyInstance difficulty = serverLevel.getCurrentDifficultyAt(mob.blockPosition());

                    // Corrige spawn invertido / estado interno
                    warden.finalizeSpawn(
                            serverLevel,
                            difficulty,
                            MobSpawnType.EVENT,
                            null
                    );

                    // Remove goals que fazem o Warden se enterrar (dig down / emerge)
                    warden.goalSelector.getAvailableGoals().removeIf(
                            wrapped -> {
                                Goal goal = wrapped.getGoal();
                                String name = goal.getClass().getSimpleName();
                                return name.contains("Dig") || name.contains("Emerge");
                            }
                    );

                    warden.setPersistenceRequired();
                }

                world.addFreshEntity(mob);

                // Aplica equipamento se houver configuração
                if (waveConfig != null) {
                    EliteEquipmentHelper.applyGear(mob, waveConfig);
                }

                // === WARPEd MINER CUSTOM GEAR & NAME FIX ===
                if (mob instanceof WarpedMiner warpedMiner) {
                    warpedMiner.setTargetBlock(pos); // Define o bloco alvo como o ativador

                    // Force custom name + gear AFTER wave equipment is applied
                    warpedMiner.setItemSlot(EquipmentSlot.MAINHAND, Items.IRON_PICKAXE.getDefaultInstance());
                    warpedMiner.setItemSlot(EquipmentSlot.HEAD, Items.NETHERITE_HELMET.getDefaultInstance());
                    warpedMiner.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
                    warpedMiner.setDropChance(EquipmentSlot.HEAD, 0.0F);
                    warpedMiner.setCustomName(Component.literal("§c⚠ Warped Miner ⚠"));
                    warpedMiner.setCustomNameVisible(true);
                }

                // CRITICAL: WarpedMiner já tem seus próprios goals de mining e movimento
                // Não adicionar WalkCenterGoal pois interfere com a mineração
                if (!(mob instanceof WarpedMiner)) {
                    mob.goalSelector.addGoal(1, new Wave.WalkCenterGoal(mob, pos, 1.2)); // Faster movement
                }

                // Desativa drops
                mob.setDropChance(EquipmentSlot.MAINHAND, 0f);
                mob.setDropChance(EquipmentSlot.OFFHAND, 0f);
                mob.setDropChance(EquipmentSlot.HEAD, 0f);
                mob.setDropChance(EquipmentSlot.CHEST, 0f);
                mob.setDropChance(EquipmentSlot.LEGS, 0f);
                mob.setDropChance(EquipmentSlot.FEET, 0f);

                spawned.add(mob);
            }
        }

        return spawned;
    }
}