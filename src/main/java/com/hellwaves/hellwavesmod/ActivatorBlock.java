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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ActivatorBlock extends Block implements EntityBlock {

    private static final int TICKS_BETWEEN_WAVES = 1200;

    public ActivatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ActivatorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.ACTIVATOR_BLOCK_ENTITY.get(), ActivatorBlock::tick);
    }

    private static void tick(Level level, BlockPos pos, BlockState state, ActivatorBlockEntity blockEntity) {
        if (level instanceof ServerLevel serverLevel) {
            blockEntity.tick(serverLevel);
        }
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
        blockEntity.activeMobs.removeIf(mob -> !mob.isAlive() || mob.isRemoved());
        if (!blockEntity.activeMobs.isEmpty()) {
            final double ACTIVATION_RADIUS = 2.0; // VALOR X AND Y
            final double DOUBLE_ACTIVATION_RADIUS_SQR = ACTIVATION_RADIUS * ACTIVATION_RADIUS;

            for (Mob mob : blockEntity.activeMobs) {
                double deltaX = mob.getX() - (pos.getX() + 0.5);
                double deltaZ = mob.getZ() - (pos.getZ() + 0.5);
                double horizontalDistanceSqr = deltaX * deltaX + deltaZ * deltaZ;

                if (horizontalDistanceSqr <= DOUBLE_ACTIVATION_RADIUS_SQR) {
                    explode(world, pos, blockEntity);
                    return;
                }
            }
        }

        // Automatic wave activation
        if (blockEntity.activeMobs.isEmpty() && blockEntity.nextWave <= ActivatorBlockEntity.MAX_WAVES) {
            if (blockEntity.tickCountdown <= 0) {
                int wave = blockEntity.nextWave;
                var newMobs = WaveManager.activateWave(world, pos, null, wave);
                for (Mob mob : newMobs) {
                    blockEntity.addMob(mob);
                }

                world.getServer().getPlayerList().getPlayers().forEach(p ->
                        p.sendSystemMessage(Component.literal("Wave " + wave + " has started!"))
                );

                blockEntity.nextWave++;
                blockEntity.tickCountdown = TICKS_BETWEEN_WAVES;
                blockEntity.setChanged(); // Mark as changed
            } else {
                blockEntity.tickCountdown--;
                // Only mark as changed occasionally to reduce disk I/O
                if (blockEntity.tickCountdown % 20 == 0) {
                    blockEntity.setChanged();
                }
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
        blockEntity.clearMobs(); // Use helper method that calls setChanged()
        blockEntity.tickCountdown = 0;
        blockEntity.setChanged();
    }

    public void activateWave(Level world, BlockPos pos, Player player) {
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof ActivatorBlockEntity blockEntity)) return;

        if (blockEntity.nextWave <= ActivatorBlockEntity.MAX_WAVES) {
            int wave = blockEntity.nextWave;
            var newMobs = WaveManager.activateWave(world, pos, player, wave);
            for (Mob mob : newMobs) {
                blockEntity.addMob(mob);
            }

            if (player != null) {
                player.displayClientMessage(Component.literal("Wave " + wave + " has started!"), false);
            } else {
                world.getServer().getPlayerList().getPlayers().forEach(p ->
                        p.sendSystemMessage(Component.literal("Wave " + wave + " has started!"))
                );
            }

            blockEntity.nextWave++;
            blockEntity.setChanged();
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(BlockEntityType<A> type, BlockEntityType<E> targetType, BlockEntityTicker<? super E> ticker) {
        return targetType == type ? (BlockEntityTicker<A>) ticker : null;
    }
}