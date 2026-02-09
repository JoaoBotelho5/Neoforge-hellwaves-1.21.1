package com.hellwaves.hellwavesmod.HWMobs;

import net.minecraft.core.BlockPos;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public class WarpedMiner extends ZombifiedPiglin {

    private BlockPos targetPos;
    private final WarpedMinerBreakGoal breakGoal;

    private int hitCount = 0;
    private LivingEntity combatTarget;

    private BlockPos lastPos;
    private int stuckTicks;

    // Track recently broken blocks to avoid re-placing them
    private BlockPos lastBrokenBlock = null;
    private int ticksSinceBreak = 0;

    // Block placement tracking
    private int placementCooldown = 0;
    private static final int PLACEMENT_DELAY = 8;

    // Stair building tracking
    private int blocksPlacedAtCurrentHeight = 0;
    private int lastYLevel = Integer.MIN_VALUE; // Track Y level to reset counter
    private static final int BLOCKS_BEFORE_STEP_UP = 3; // Place 3 blocks horizontally, then step up

    // Track spawn position to avoid going back to it
    private BlockPos spawnPos;

    public WarpedMiner(EntityType<? extends ZombifiedPiglin> type, Level level) {
        super(type, level);
        this.breakGoal = new WarpedMinerBreakGoal(this);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return ZombifiedPiglin.createAttributes()
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D);
    }
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    public void setTargetBlock(BlockPos pos) {
        this.targetPos = pos;
        System.out.println("WarpedMiner targetPos set to: " + pos);
    }

    @Override
    public void tick() {
        super.tick();

        // Record spawn position on first tick
        if (spawnPos == null) {
            spawnPos = this.blockPosition();
        }

        if (targetPos == null) {
            System.out.println("WarpedMiner has NULL targetPos! Not moving.");
            return;
        }

        // SAFETY: If targetPos is same as spawn, something went wrong
        if (targetPos.equals(spawnPos)) {
            System.out.println("WARNING: targetPos equals spawnPos! This mob will go nowhere.");
            return;
        }

        // Decrease placement cooldown
        if (placementCooldown > 0) {
            placementCooldown--;
        }

        // Track time since last break
        if (ticksSinceBreak < 100) {
            ticksSinceBreak++;
        } else {
            lastBrokenBlock = null;
        }

        // Combat handling (3-hit rule)
        if (combatTarget != null) {
            if (!combatTarget.isAlive()) {
                combatTarget = null;
                hitCount = 0;
            } else {
                // Navigate to and attack the combat target
                this.getNavigation().moveTo(combatTarget, 1.0);
            }
            return;
        }

        // Make the miner "shift walk" to prevent falling off blocks it places
        this.setShiftKeyDown(true);

        BlockPos now = BlockPos.containing(this.position());

        // Reset counter if Y level changed (mob stepped up)
        if (now.getY() != lastYLevel) {
            blocksPlacedAtCurrentHeight = 0;
            lastYLevel = now.getY();
        }

        // Fill any gaps below the mob to prevent falling
        BlockPos belowMob = now.below();
        BlockState belowState = this.level().getBlockState(belowMob);
        if (!belowState.isSolid() && placementCooldown == 0) {
            this.level().setBlock(belowMob, Blocks.COBBLESTONE.defaultBlockState(), 3);
            placementCooldown = PLACEMENT_DELAY;
        }

        // Check if reached destination (must be at correct X, Z, AND Y)
        int horizontalDist = Math.abs(targetPos.getX() - now.getX()) + Math.abs(targetPos.getZ() - now.getZ());
        int verticalDist = Math.abs(targetPos.getY() - now.getY());

        if (horizontalDist <= 1 && verticalDist <= 1) {
            this.getNavigation().stop();
            return;
        }

        // Stuck detection
        if (now.equals(lastPos)) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
        lastPos = now;

        // Determine if we're climbing or going horizontal
        int heightDiff = targetPos.getY() - now.getY();
        boolean needsToClimb = heightDiff > 0;

        // If currently breaking, continue breaking
        if (breakGoal.isBreaking()) {
            breakGoal.tick();
            return;
        }

        // If stuck, try breaking obstacles
        if (stuckTicks >= 20) {
            if (attemptBreakObstacle(now, needsToClimb)) {
                breakGoal.tick();
                return;
            }
        }

        // Main logic: climb or bridge
        if (placementCooldown == 0) {
            if (needsToClimb) {
                // BUILD STAIRS
                if (attemptBuildStairs(now)) {
                    return;
                }
            } else {
                // HORIZONTAL BRIDGE
                if (attemptHorizontalBridge(now)) {
                    return;
                }
            }
        }

        // ALWAYS navigate toward target - don't check if in progress
        this.getNavigation().moveTo(
                targetPos.getX() + 0.5,
                targetPos.getY(),
                targetPos.getZ() + 0.5,
                0.8
        );
    }

    /**
     * Build stairs going straight toward the target
     * Every 3 horizontal blocks, place a step-up block
     */
    private boolean attemptBuildStairs(BlockPos current) {
        // Direction toward target
        int dx = Integer.compare(targetPos.getX(), current.getX());
        int dz = Integer.compare(targetPos.getZ(), current.getZ());

        if (dx == 0 && dz == 0) {
            return false;
        }

        // Next position toward target
        BlockPos nextPos = current.offset(dx, 0, dz);
        BlockPos belowNext = nextPos.below();

        BlockState nextState = this.level().getBlockState(nextPos);
        BlockState belowState = this.level().getBlockState(belowNext);

        // Don't place if recently broken
        if (lastBrokenBlock != null && belowNext.equals(lastBrokenBlock) && ticksSinceBreak < 40) {
            return false;
        }

        // Check if we should step up (every 3 blocks)
        boolean shouldStepUp = (blocksPlacedAtCurrentHeight >= BLOCKS_BEFORE_STEP_UP);

        if (shouldStepUp) {
            // Place a block at mob's current level to step onto
            BlockPos stepBlock = current.offset(dx, 0, dz); // Same Y as current position
            BlockState stepState = this.level().getBlockState(stepBlock);

            if (stepState.isAir() || !stepState.getFluidState().isEmpty()) {
                this.level().setBlock(stepBlock, Blocks.COBBLESTONE.defaultBlockState(), 3);
                placementCooldown = PLACEMENT_DELAY;
                stuckTicks = 0;
                blocksPlacedAtCurrentHeight = 0; // Reset counter after stepping up
                return true;
            } else if (stepState.isSolid()) {
                // Step already exists, just reset counter
                blocksPlacedAtCurrentHeight = 0;
                return false;
            }
        } else {
            // Place normal floor block below next position
            if (!belowState.isSolid()) {
                this.level().setBlock(belowNext, Blocks.COBBLESTONE.defaultBlockState(), 3);
                placementCooldown = PLACEMENT_DELAY;
                stuckTicks = 0;
                blocksPlacedAtCurrentHeight++;
                return true;
            } else if (belowState.isSolid() && nextState.isAir()) {
                // Floor already exists, just increment counter
                blocksPlacedAtCurrentHeight++;
                return false;
            }
        }

        return false;
    }

    /**
     * Creates a horizontal bridge toward the target
     */
    private boolean attemptHorizontalBridge(BlockPos current) {
        BlockPos target = this.targetPos;

        int dx = Integer.compare(target.getX(), current.getX());
        int dz = Integer.compare(target.getZ(), current.getZ());

        if (dx == 0 && dz == 0) {
            return false;
        }

        BlockPos nextHorizontal = current.offset(dx, 0, dz);
        BlockPos belowNext = nextHorizontal.below();
        BlockState belowState = this.level().getBlockState(belowNext);

        if (belowState.isAir() || !belowState.getFluidState().isEmpty()) {
            this.level().setBlock(belowNext, Blocks.COBBLESTONE.defaultBlockState(), 3);
            placementCooldown = PLACEMENT_DELAY;
            stuckTicks = 0;
            return true;
        }

        return false;
    }

    /**
     * Attempts to break obstacles in the path
     */
    private boolean attemptBreakObstacle(BlockPos current, boolean climbing) {
        BlockPos targetBreak = null;

        int dx = Integer.compare(targetPos.getX(), current.getX());
        int dz = Integer.compare(targetPos.getZ(), current.getZ());

        BlockPos forward = current.offset(dx, 0, dz);

        BlockPos[] candidates = {
                forward,
                forward.above(),
                forward.above().above()
        };

        for (BlockPos p : candidates) {
            BlockState state = this.level().getBlockState(p);
            if (!state.isAir() && state.getDestroySpeed(this.level(), p) >= 0) {
                targetBreak = p;
                break;
            }
        }

        if (targetBreak != null) {
            breakGoal.startBreaking(targetBreak);
            lastBrokenBlock = targetBreak;
            ticksSinceBreak = 0;
            return true;
        }

        return false;
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
        data = super.finalizeSpawn(level, diff, type, data);

        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));

        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        this.setDropChance(EquipmentSlot.HEAD, 0.0F);

        this.setCustomName(Component.literal("§c⚠ Warped Miner ⚠"));
        this.setCustomNameVisible(true);

        return data;
    }
}