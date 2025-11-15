package com.hellwaves.hellwavesmod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

public class ActivatorBlockEntity extends BlockEntity {

    public static final int MAX_WAVES = 3;

    public int nextWave = 1; // per-block
    public int tickCountdown = 0; // per-block
    public final List<Mob> activeMobs = new ArrayList<>();

    public ActivatorBlockEntity(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        super(ModBlockEntities.ACTIVATOR_BLOCK_ENTITY.get(), pos, state);
    }

    public void tick(ServerLevel world) {
        // Remove dead mobs
        activeMobs.removeIf(Mob::isRemoved);

        // Check for mobs nearby
        for (Mob mob : activeMobs) {
            if (mob.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 4) {
                world.explode(null,
                        worldPosition.getX() + 0.5,
                        worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5,
                        25f,
                        Level.ExplosionInteraction.BLOCK);

                nextWave = 1;
                activeMobs.clear();
                tickCountdown = 0;
                return;
            }
        }

        // Automatic wave activation
        if (activeMobs.isEmpty() && nextWave <= MAX_WAVES) {
            if (tickCountdown <= 0) {
                activeMobs.addAll(WaveManager.activateWave(world, worldPosition, null, nextWave));
                nextWave++;
                tickCountdown = 1200;
            } else tickCountdown--;
        }
    }
}
