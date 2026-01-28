package com.hellwaves.hellwavesmod.Blocks;

import com.hellwaves.hellwavesmod.WavesManager.WaveManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;

public class ActivatorBlock extends Block implements EntityBlock {

    // Propriedade para controlar o tamanho do portal (1, 2 ou 3)
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, 3);

    private static final int TICKS_BETWEEN_WAVES = 1200;

    public ActivatorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LEVEL, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ActivatorBlockEntity(pos, state);
    }

    // === RENDERING FIXES ===

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;  // Render as a normal model
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return false;  // Block doesn't let skylight through
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
        return 15;  // Block is fully opaque
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter worldIn, BlockPos pos) {
        return 0.2F;  // Make it cast proper shadows
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentBlockState, Direction direction) {
        return false;  // Never skip rendering any face
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter reader, BlockPos pos, CollisionContext context) {
        return Shapes.block();  // Full block shape for rendering
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        return Shapes.block();  // Full block collision
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;  // Use the shape to calculate light occlusion
    }

    @Override
    protected boolean isOcclusionShapeFullBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return true;  // Tell the game this block fully occludes adjacent faces
    }

    // === END RENDERING FIXES ===

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.ACTIVATOR_BLOCK_ENTITY.get(), ActivatorBlock::tick);
    }

    private static void tick(Level level, BlockPos pos, BlockState state, ActivatorBlockEntity blockEntity) {
        if (level instanceof ServerLevel serverLevel) {
            // Verificar se o bloco ainda existe
            if (serverLevel.getBlockState(pos).isAir() || serverLevel.getBlockState(pos).getBlock() != state.getBlock()) {
                return;
            }

            blockEntity.tick(serverLevel);

            // Atualizar o tamanho do portal baseado na wave atual
            int currentWave = blockEntity.nextWave - 1;
            int portalLevel = 1;

            if (currentWave >= 3) {
                portalLevel = 3; // Wave 3+ = portal grande
            } else if (currentWave >= 2) {
                portalLevel = 2; // Wave 2 = portal médio
            } else {
                portalLevel = 1; // Wave 1 = portal pequeno
            }

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
        if (!(be instanceof ActivatorBlockEntity blockEntity)) return;

        // Verificar se o bloco foi removido
        if (world.getBlockState(pos).isAir() || world.getBlockState(pos).getBlock() != this) {
            return;
        }

        blockEntity.activeMobs.removeIf(mob -> !mob.isAlive() || mob.isRemoved());

        if (!blockEntity.activeMobs.isEmpty()) {
            final double ACTIVATION_RADIUS = 1.5;
            final double DOUBLE_ACTIVATION_RADIUS_SQR = ACTIVATION_RADIUS * ACTIVATION_RADIUS;

            for (Mob mob : blockEntity.activeMobs) {
                double deltaX = mob.getX() - (pos.getX() + 0.5);
                double deltaZ = mob.getZ() - (pos.getZ() + 0.5);
                double horizontalDistanceSqr = deltaX * deltaX + deltaZ * deltaZ;

                if (horizontalDistanceSqr <= DOUBLE_ACTIVATION_RADIUS_SQR) {
                    explode(world, pos, blockEntity);
                    return; // NÃO AGENDAR MAIS TICKS
                }
            }
        }

        // Verificar se completou todas as waves
        if (blockEntity.activeMobs.isEmpty() && blockEntity.nextWave > ActivatorBlockEntity.MAX_WAVES) {
            // Todas as waves completas e sem mobs - remover o bloco
            world.removeBlock(pos, false);
            return;
        }

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

    private void explode(ServerLevel world, BlockPos pos, ActivatorBlockEntity blockEntity) {
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
                25.0f,
                Level.ExplosionInteraction.BLOCK
        );
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