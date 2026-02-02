package com.hellwaves.hellwavesmod.HWMobs;

import net.minecraft.core.BlockPos;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import javax.annotation.Nullable;

public class WarpedMiner extends ZombifiedPiglin {

    private BlockPos targetPos;
    private final WarpedMinerBreakGoal breakGoal;

    private int hitCount = 0;
    private LivingEntity combatTarget;

    private BlockPos lastPos;
    private int stuckTicks;

    public WarpedMiner(EntityType<? extends ZombifiedPiglin> type, Level level) {
        super(type, level);
        this.breakGoal = new WarpedMinerBreakGoal(this);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    public void setTargetBlock(BlockPos pos) {
        this.targetPos = pos;
    }

    @Override
    public void tick() {
        super.tick();

        if (targetPos == null) return;

        // Combat handling (3-hit rule)
        if (combatTarget != null) {
            if (!combatTarget.isAlive()) {
                combatTarget = null;
                hitCount = 0;
            }
            return;
        }

        // Navigation
        if (!this.getNavigation().isInProgress()) {
            this.getNavigation().moveTo(
                    targetPos.getX() + 0.5,
                    targetPos.getY(),
                    targetPos.getZ() + 0.5,
                    1.0
            );
        }

        // Stuck detection
        BlockPos now = this.blockPosition();
        if (now.equals(lastPos)) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
        lastPos = now;

        if (stuckTicks < 30) return;

        // Predictive breaking
        if (!breakGoal.isBreaking()) {
            attemptPredictiveBreak();
        }

        breakGoal.tick();
    }

    /**
     * FIXED: uses TARGET direction, not facing direction
     */
    private void attemptPredictiveBreak() {
        BlockPos current = this.blockPosition();

        int dx = Integer.compare(targetPos.getX(), current.getX());
        int dz = Integer.compare(targetPos.getZ(), current.getZ());
        int dy = Integer.compare(targetPos.getY(), current.getY());

        BlockPos front;

        // Horizontal priority (THIS FIXES SIDEWAYS MINING)
        if (dx != 0) {
            front = current.offset(dx, 0, 0);
        } else if (dz != 0) {
            front = current.offset(0, 0, dz);
        } else if (dy != 0) {
            front = current.offset(0, dy, 0);
        } else {
            return;
        }

        BlockPos[] candidates = {
                front,
                front.above(),
                front.below()
        };

        for (BlockPos p : candidates) {
            if (!this.level().getBlockState(p).isAir()) {
                breakGoal.startBreaking(p);
                return;
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof LivingEntity attacker) {
            hitCount++;
            if (hitCount >= 3) {
                combatTarget = attacker;
                this.setTarget(attacker);
            }
        }
        return super.hurt(source, amount);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            DifficultyInstance diff,
            MobSpawnType type,
            @Nullable SpawnGroupData data
    ) {
        return super.finalizeSpawn(level, diff, type, data);
    }
}
