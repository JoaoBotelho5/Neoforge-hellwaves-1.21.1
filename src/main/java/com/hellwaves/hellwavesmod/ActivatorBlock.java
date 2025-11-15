package com.hellwaves.hellwavesmod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ActivatorBlock extends Block {

    public static final int MAX_WAVES = 3;
    private int nextWave = 1;
    private boolean waveScheduled = false; // track automatic wave scheduling
    private final List<Mob> activeMobs = new ArrayList<>();
    private static final int TICKS_BETWEEN_WAVES = 1200;
    private int tickCountdown = 0;

    public ActivatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!world.isClientSide && state.getBlock() != oldState.getBlock()) {
            // Schedule the first tick immediately for explosion checks
            world.scheduleTick(pos, this, 1);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource rand) {
        if (world.isClientSide()) return;

        // Remove dead mobs
        Iterator<Mob> it = activeMobs.iterator();
        while (it.hasNext()) {
            Mob mob = it.next();
            if (mob.isRemoved()) it.remove();
        }

        // Check if any mob is within 2 blocks and explode
        for (Mob mob : activeMobs) {
            if (mob.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 4) {
                explode(world, pos);
                return; // stop ticking after explosion
            }
        }

        // If no active mobs, check for automatic wave activation
        if (activeMobs.isEmpty() && nextWave <= MAX_WAVES) {
            if (tickCountdown <= 0) { // tickCountdown is a new field
                int wave = nextWave;
                List<Mob> spawned = WaveManager.activateWave(world, pos, null, wave);
                activeMobs.addAll(spawned);

                world.getServer().getPlayerList().getPlayers().forEach(p ->
                        p.sendSystemMessage(Component.literal("Wave " + wave + " has started!"))
                );

                nextWave++;
                tickCountdown = TICKS_BETWEEN_WAVES; // reset countdown for next wave
            } else {
                tickCountdown--; // count down each tick
            }
        }

        // Always schedule the next tick for explosion checking
        world.scheduleTick(pos, this, 1);
    }

    private void explode(Level world, BlockPos centerPos) {
        world.explode(
                null,
                centerPos.getX() + 0.5,
                centerPos.getY() + 0.5,
                centerPos.getZ() + 0.5,
                25.0f,
                Level.ExplosionInteraction.BLOCK
        );

        nextWave = 1;
        activeMobs.clear();
        waveScheduled = false;
    }

    public void activateWave(Level world, BlockPos pos, Player player) {
        if (!world.isClientSide() && nextWave <= MAX_WAVES) {
            int wave = nextWave;
            List<Mob> spawned = WaveManager.activateWave(world, pos, player, wave);
            activeMobs.addAll(spawned);

            if (player != null) {
                player.displayClientMessage(Component.literal("Wave " + wave + " has started!"), false);
            } else {
                world.getServer().getPlayerList().getPlayers().forEach(p ->
                        p.sendSystemMessage(Component.literal("Wave " + wave + " has started!"))
                );
            }

            nextWave++;
        }
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        super.onRemove(state, world, pos, newState, isMoving);

        if (!world.isClientSide() && state.getBlock() != newState.getBlock()) {
            nextWave = 1;
            activeMobs.clear();
            waveScheduled = false;
        }
    }
}