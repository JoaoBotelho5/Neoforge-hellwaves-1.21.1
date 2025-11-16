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

public class EliteActivatorBlock extends Block implements EntityBlock {
    private static final int TICKS_BETWEEN_WAVES = 1000; // Faster waves

    public EliteActivatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EliteActivatorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.ELITE_ACTIVATOR_BLOCK_ENTITY.get(), EliteActivatorBlock::tick);
    }

    private static void tick(Level level, BlockPos pos, BlockState state, EliteActivatorBlockEntity blockEntity) {
        if (level instanceof ServerLevel serverLevel) {
            blockEntity.tick(serverLevel);
        }
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!world.isClientSide) {
            world.scheduleTick(pos, this, 1);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource rand) {
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof EliteActivatorBlockEntity blockEntity)) return;

        blockEntity.activeMobs.removeIf(mob -> !mob.isAlive() || mob.isRemoved());

        // Explode if any mob is near
        for (Mob mob : blockEntity.activeMobs) {
            if (mob.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 4) {
                explode(world, pos, blockEntity);
                return;
            }
        }

        // Automatic wave activation
        if (blockEntity.activeMobs.isEmpty() && blockEntity.nextWave <= EliteActivatorBlockEntity.MAX_WAVES) {
            if (blockEntity.tickCountdown <= 0) {
                int wave = blockEntity.nextWave;
                var newMobs = EliteWaveManager.activateWave(world, pos, null, wave);
                for (Mob mob : newMobs) {
                    blockEntity.addMob(mob);
                }

                world.getServer().getPlayerList().getPlayers().forEach(p ->
                        p.sendSystemMessage(Component.literal("§6Elite Wave " + wave + " has started!§r"))
                );

                blockEntity.nextWave++;
                blockEntity.tickCountdown = TICKS_BETWEEN_WAVES;
                blockEntity.setChanged();
            } else {
                blockEntity.tickCountdown--;
                if (blockEntity.tickCountdown % 20 == 0) {
                    blockEntity.setChanged();
                }
            }
        }

        world.scheduleTick(pos, this, 1);
    }

    private void explode(ServerLevel world, BlockPos pos, EliteActivatorBlockEntity blockEntity) {
        world.explode(
                null,
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                35.0f, // Bigger explosion
                Level.ExplosionInteraction.BLOCK
        );

        blockEntity.nextWave = 1;
        blockEntity.clearMobs();
        blockEntity.tickCountdown = 0;
        blockEntity.setChanged();
    }

    public void activateWave(Level world, BlockPos pos, Player player) {
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof EliteActivatorBlockEntity blockEntity)) return;

        if (blockEntity.nextWave <= EliteActivatorBlockEntity.MAX_WAVES) {
            int wave = blockEntity.nextWave;
            var newMobs = EliteWaveManager.activateWave(world, pos, player, wave);
            for (Mob mob : newMobs) {
                blockEntity.addMob(mob);
            }

            if (player != null) {
                player.displayClientMessage(Component.literal("§6Elite Wave " + wave + " has started!§r"), false);
            } else {
                world.getServer().getPlayerList().getPlayers().forEach(p ->
                        p.sendSystemMessage(Component.literal("§6Elite Wave " + wave + " has started!§r"))
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