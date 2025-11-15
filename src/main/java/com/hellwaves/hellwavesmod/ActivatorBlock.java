package com.hellwaves.hellwavesmod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;


public class ActivatorBlock extends Block {

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
        List<Mob> mobs = world.getEntitiesOfClass(Mob.class, state.getShape(world, pos)
                .bounds().move(pos.getX(), pos.getY(), pos.getZ()).inflate(2));
        if (!mobs.isEmpty()) {
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
    }
}
