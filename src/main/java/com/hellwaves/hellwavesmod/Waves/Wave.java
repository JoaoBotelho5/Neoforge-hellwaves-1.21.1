package com.hellwaves.hellwavesmod.Waves;

import com.google.gson.JsonObject;
import com.hellwaves.hellwavesmod.Blocks.ActivatorBlock;
import com.hellwaves.hellwavesmod.Blocks.EliteActivatorBlock;
import com.hellwaves.hellwavesmod.equipment.EquipmentHelper;
import com.hellwaves.hellwavesmod.HWMobs.WarpedMiner;
import com.hellwaves.hellwavesmod.WavesManager.WaveManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class Wave {
    private final List<EntityType<? extends Mob>> enemies;


    public Wave(List<EntityType<? extends Mob>> enemies) {
        this.enemies = enemies;
    }

    public List<Mob> spawn(Level world, BlockPos pos, int waveNumber) {
        List<Mob> spawned = new ArrayList<>();
        JsonObject waveConfig = WaveManager.equipmentConfig.getAsJsonObject("wave" + waveNumber);

        int numberofMobs = enemies.size();
        double radius = 20.0;

        for (int i = 0; i < numberofMobs; i++) {
            EntityType<? extends Mob> type = enemies.get(i);
            Mob mob = type.create(world);
            if (mob != null) {
                double angle = 2 * Math.PI * i / numberofMobs;
                double x = pos.getX() + radius * Math.cos(angle);
                double z = pos.getZ() + radius * Math.sin(angle);

                int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING, (int)x, (int)z);

                mob.moveTo(x, y, z, 0, 0);

                // CONFIGURAÇÃO ESPECIAL PARA O WARPED MINER
                if (mob instanceof WarpedMiner warpedMiner) {
                    warpedMiner.setTargetBlock(pos); // Define o bloco alvo como o ativador
                }

                world.addFreshEntity(mob);

                if (waveConfig != null) {
                    EquipmentHelper.applyGear(mob, waveConfig);
                }

                mob.goalSelector.addGoal(1, new WalkCenterGoal(mob, pos, 1.0));

                mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0f);
                mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.OFFHAND, 0f);
                mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.HEAD, 0f);
                mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.CHEST, 0f);
                mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.LEGS, 0f);
                mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.FEET, 0f);

                spawned.add(mob); // add to the list
            }
        }

        return spawned;
    }


    public static class WalkCenterGoal extends Goal {
        private final Mob mob;
        private final BlockPos center;
        private final double speed;

        public WalkCenterGoal(Mob mob, BlockPos center, double speed) {
            this.mob = mob;
            this.center = center;
            this.speed = speed;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {

            if (!(mob.level().getBlockState(center).getBlock() instanceof ActivatorBlock) &&
            !(mob.level().getBlockState(center).getBlock() instanceof EliteActivatorBlock)) {
                return false;
            }

            // Only run when mob has no target
            if (mob.getTarget() != null) return false;

            // Only run when far from center
            return mob.distanceToSqr(
                    center.getX() + 0.5,
                    center.getY() + 0.5,
                    center.getZ() + 0.5
            ) > 4.0; // 2-block radius
        }

        @Override
        public void tick() {
            mob.getNavigation().moveTo(
                    center.getX() + 0.5,
                    center.getY(),
                    center.getZ() + 0.5,
                    speed
            );
        }
    }


}
