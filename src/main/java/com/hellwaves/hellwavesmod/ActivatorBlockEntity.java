package com.hellwaves.hellwavesmod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
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

    public void checkCompletion(ServerLevel world) {
        // If all waves are done and no active mobs remain
        if (nextWave > MAX_WAVES && activeMobs.isEmpty()) {
            if (!world.isClientSide) {
                ItemStack[] drops = {
                        new ItemStack(Items.DIAMOND_BLOCK, 1),
                        new ItemStack(Items.EMERALD_BLOCK, 1),
                        new ItemStack(Items.ANCIENT_DEBRIS, 1)
                };

                for (ItemStack stack : drops) {
                    world.addFreshEntity(new ItemEntity(world, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack));
                }


                // Remove the block
                world.removeBlock(worldPosition, false);
            }
        }
    }

    public void tick(ServerLevel world) {
        // Remove dead mobs
        activeMobs.removeIf(mob -> mob.level() != world || mob.isRemoved());

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

        checkCompletion(world);
    }


}
