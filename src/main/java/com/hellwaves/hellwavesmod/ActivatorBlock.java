package com.hellwaves.hellwavesmod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class ActivatorBlock extends Block {

    public static final int MAX_WAVES = 3;
    private int nextWave = 1;
    private final List<Mob> activeMobs = new ArrayList<>();


    public ActivatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!world.isClientSide()) {
            ((ServerLevel) world).scheduleTick(pos, this, 1);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource rand) {
        // Find mobs within 2 blocks
        Iterator<Mob> it = activeMobs.iterator();
        while (it.hasNext()) {
            Mob mob = it.next();
            if (mob.isRemoved()) {
                it.remove();
            }
        }
        boolean shouldExplode = false;
        for (Mob mob : activeMobs) {
            if (mob.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 4) {
                shouldExplode = true;
                break;
            }
        }
        if (shouldExplode) {
            explode(world, pos);
        } else {
            // Schedule the next tick to keep checking
            world.scheduleTick(pos, this, 1);
        }
    }

    private void explode(Level world, BlockPos centerPos) {
        // The explode method in 1.19+:
        world.explode(
                null, // the entity causing the explosion
                centerPos.getX() + 0.5,
                centerPos.getY() + 0.5,
                centerPos.getZ() + 0.5,
                25.0f, // explosion strength
                Level.ExplosionInteraction.BLOCK
        );

        nextWave = 1;
        activeMobs.clear();
    }
    public void activateWave(Level world, BlockPos pos, net.minecraft.world.entity.player.Player player) {
        if (!world.isClientSide() && nextWave <= MAX_WAVES) {
            List <Mob> spawned = WaveManager.activateWave(world, pos, player, nextWave);
            activeMobs.addAll(spawned);

            player.displayClientMessage(
                    Component.literal("Wave " + nextWave + " has started!"),
                    false
            );

            nextWave++; // increment for the next activation
        }
    }
    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        super.onRemove(state, world, pos, newState, isMoving);

        // Reset the wave count if this block instance is removed
        if (!world.isClientSide() && state.getBlock() != newState.getBlock()) {
            nextWave = 1;
        }
    }
}
