package com.hellwaves.hellwavesmod.HWMobs;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.Set;

public class WarpedMinerBreakGoal extends Goal {

    private static final Set<Block> PROTECTED_BLOCKS = Set.of(
            Blocks.BEDROCK,
            Blocks.END_PORTAL,
            Blocks.END_PORTAL_FRAME,
            Blocks.NETHER_PORTAL,
            Blocks.SPAWNER,
            Blocks.COMMAND_BLOCK,
            Blocks.REPEATING_COMMAND_BLOCK,
            Blocks.CHAIN_COMMAND_BLOCK,
            Blocks.STRUCTURE_BLOCK,
            Blocks.JIGSAW,
            Blocks.BARRIER
    );

    private final Mob mob;
    private BlockPos breakingPos;
    private int progress;
    private int requiredTime;

    public WarpedMinerBreakGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return false; // activated manually by miner logic
    }

    public boolean isBreaking() {
        return breakingPos != null;
    }

    public void startBreaking(BlockPos pos) {
        if (GlobalMiningReservation.isReserved(pos)) return;

        BlockState state = mob.level().getBlockState(pos);
        if (state.isAir()) return;
        if (PROTECTED_BLOCKS.contains(state.getBlock())) return;

        if (!GlobalMiningReservation.reserve(pos)) return;

        breakingPos = pos;
        progress = 0;
        requiredTime = Math.max(20, (int)(state.getDestroySpeed(mob.level(), pos) * 30));
    }

    @Override
    public void tick() {
        if (breakingPos == null) return;

        Level level = mob.level();
        BlockState state = level.getBlockState(breakingPos);

        if (state.isAir()) {
            stopBreaking();
            return;
        }

        progress++;

        if (progress >= requiredTime) {
            level.destroyBlock(breakingPos, true, mob);
            stopBreaking();
        }
    }

    public void stopBreaking() {
        if (breakingPos != null) {
            GlobalMiningReservation.release(breakingPos);
        }
        breakingPos = null;
        progress = 0;
        requiredTime = 0;
    }
}
