package com.hellwaves.hellwavesmod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ActivatorBlock extends Block implements EntityBlock {

    private static final int TICKS_BETWEEN_WAVES = 1200;

    public ActivatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ActivatorBlockEntity(pos, state);
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!world.isClientSide) {
            // Schedule first tick for explosion/mob check
            world.scheduleTick(pos, this, 1);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource rand) {
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof ActivatorBlockEntity blockEntity)) return;

        // Remove dead mobs
        blockEntity.activeMobs.removeIf(Mob::isRemoved);

        // Explode if any mob is near
        for (Mob mob : blockEntity.activeMobs) {
            if (mob.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 4) {
                explode(world, pos, blockEntity);
                return;
            }
        }

        // Automatic wave activation
        if (blockEntity.activeMobs.isEmpty() && blockEntity.nextWave <= ActivatorBlockEntity.MAX_WAVES) {
            if (blockEntity.tickCountdown <= 0) {
                int wave = blockEntity.nextWave;
                blockEntity.activeMobs.addAll(WaveManager.activateWave(world, pos, null, wave));

                world.getServer().getPlayerList().getPlayers().forEach(p ->
                        p.sendSystemMessage(Component.literal("Wave " + wave + " has started!"))
                );

                blockEntity.nextWave++;
                blockEntity.tickCountdown = TICKS_BETWEEN_WAVES;
            } else {
                blockEntity.tickCountdown--;
            }
        }

        // Always schedule next tick
        world.scheduleTick(pos, this, 1);
    }

    private void explode(ServerLevel world, BlockPos pos, ActivatorBlockEntity blockEntity) {
        world.explode(
                null,
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                25.0f,
                Level.ExplosionInteraction.BLOCK
        );

        blockEntity.nextWave = 1;
        blockEntity.activeMobs.clear();
        blockEntity.tickCountdown = 0;
    }

    public void activateWave(Level world, BlockPos pos, Player player) {
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof ActivatorBlockEntity blockEntity)) return;

        if (blockEntity.nextWave <= ActivatorBlockEntity.MAX_WAVES) {
            int wave = blockEntity.nextWave;
            blockEntity.activeMobs.addAll(WaveManager.activateWave(world, pos, player, wave));

            if (player != null) {
                player.displayClientMessage(Component.literal("Wave " + wave + " has started!"), false);
            } else {
                world.getServer().getPlayerList().getPlayers().forEach(p ->
                        p.sendSystemMessage(Component.literal("Wave " + wave + " has started!"))
                );
            }

            blockEntity.nextWave++;
        }
    }
}
