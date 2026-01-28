package com.hellwaves.hellwavesmod.Blocks;

import com.hellwaves.hellwavesmod.WavesManager.EliteWaveManager;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

public class EliteActivatorBlock extends Block implements EntityBlock {

    // Propriedade para controlar o tamanho do portal (1, 2, 3, 4 ou 5)
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, 5);

    private static final int TICKS_BETWEEN_WAVES = 1000;

    public EliteActivatorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LEVEL, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
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
            // Verificar se o bloco ainda existe
            if (serverLevel.getBlockState(pos).isAir() || serverLevel.getBlockState(pos).getBlock() != state.getBlock()) {
                return;
            }

            blockEntity.tick(serverLevel);

            // Atualizar o tamanho do portal baseado na wave atual (5 níveis para 5 waves)
            int currentWave = blockEntity.nextWave - 1;
            int portalLevel = Math.min(5, Math.max(1, currentWave));

            // Verificar novamente se o bloco ainda existe antes de atualizar
            if (serverLevel.getBlockState(pos).getBlock() == state.getBlock() && state.getValue(LEVEL) != portalLevel) {
                serverLevel.setBlock(pos, state.setValue(LEVEL, portalLevel), 3);
            }
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

        for (Mob mob : blockEntity.activeMobs) {
            if (mob.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 4) {
                explode(world, pos, blockEntity);
                return;
            }
        }

        // Verificar se completou todas as waves
        if (blockEntity.activeMobs.isEmpty() && blockEntity.nextWave > EliteActivatorBlockEntity.MAX_WAVES) {
            // Todas as waves completas e sem mobs - remover o bloco
            world.removeBlock(pos, false);
            return;
        }

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
        // Limpar tudo primeiro
        blockEntity.clearMobs();
        blockEntity.nextWave = 1;
        blockEntity.tickCountdown = 0;

        // REMOVER O BLOCO
        world.removeBlock(pos, false);

        // Depois explodir
        world.explode(
                null,
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                35.0f,
                Level.ExplosionInteraction.BLOCK
        );
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