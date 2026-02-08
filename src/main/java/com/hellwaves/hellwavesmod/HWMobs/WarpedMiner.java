package com.hellwaves.hellwavesmod.HWMobs;

import net.minecraft.core.BlockPos;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
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

    // Spiral direction (0=North, 1=East, 2=South, 3=West)
    private int spiralDirection = 0;
    private int stepsSinceDirectionChange = 0;
    private static final int STEPS_PER_SPIRAL = 3; // 3 blocks per side of spiral

    // Track if next block should be higher
    private boolean nextBlockGoesUp = false;

    // Track recently broken blocks to avoid re-placing them
    private BlockPos lastBrokenBlock = null;
    private int ticksSinceBreak = 0;

    // Block placement tracking
    private int placementCooldown = 0;
    private static final int PLACEMENT_DELAY = 8;

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

        // Decrease placement cooldown
        if (placementCooldown > 0) {
            placementCooldown--;
        }

        // Track time since last break
        if (ticksSinceBreak < 100) {
            ticksSinceBreak++;
        } else {
            lastBrokenBlock = null; // Clear after 5 seconds
        }

        // Combat handling (3-hit rule)
        if (combatTarget != null) {
            if (!combatTarget.isAlive()) {
                combatTarget = null;
                hitCount = 0;
            }
            return;
        }

        // Make the miner "shift walk" to prevent falling off blocks it places
        this.setShiftKeyDown(true);

        BlockPos now = this.blockPosition();

        // Check if reached destination
        int horizontalDist = Math.abs(targetPos.getX() - now.getX()) + Math.abs(targetPos.getZ() - now.getZ());
        int verticalDist = Math.abs(targetPos.getY() - now.getY());

        if (horizontalDist <= 1 && verticalDist == 0) {
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
                // SPIRAL UPWARD
                if (attemptSpiralStaircase(now)) {
                    return;
                }
            } else {
                // HORIZONTAL BRIDGE
                if (attemptHorizontalBridge(now)) {
                    return;
                }
            }
        }

        // Try to navigate
        if (!this.getNavigation().isInProgress()) {
            if (needsToClimb) {
                // Navigate in spiral direction
                int[] offset = getSpiralOffset();
                BlockPos spiralTarget = now.offset(offset[0], 0, offset[1]);
                this.getNavigation().moveTo(
                        spiralTarget.getX() + 0.5,
                        spiralTarget.getY(),
                        spiralTarget.getZ() + 0.5,
                        1.0
                );
            } else {
                // Navigate toward target horizontally
                this.getNavigation().moveTo(
                        targetPos.getX() + 0.5,
                        targetPos.getY(),
                        targetPos.getZ() + 0.5,
                        1.0
                );
            }
        }
    }

    /**
     * SIMPLE: Place blocks to form stairs - avoid placing on existing stairs or just-broken spots
     */
    private boolean attemptSpiralStaircase(BlockPos current) {
        int[] offset = getSpiralOffset();

        // Position we want to place at (one block forward in spiral direction)
        BlockPos forward = current.offset(offset[0], 0, offset[1]);
        BlockPos forwardBelow = forward.below();

        // Check what exists at forward position
        BlockState forwardState = this.level().getBlockState(forward);
        BlockState forwardBelowState = this.level().getBlockState(forwardBelow);

        // DON'T place if:
        // 1. There's already a solid block at forward (existing stairs)
        // 2. This is the block we just broke
        if (!forwardState.isAir() && forwardState.isSolid()) {
            // Block already exists, just move on
            stepsSinceDirectionChange++;
            if (stepsSinceDirectionChange >= STEPS_PER_SPIRAL) {
                spiralDirection = (spiralDirection + 1) % 4;
                stepsSinceDirectionChange = 0;
            }
            return false;
        }

        // Don't place in recently broken position
        if (lastBrokenBlock != null && forward.equals(lastBrokenBlock) && ticksSinceBreak < 40) {
            return false;
        }

        // DON'T place if there's already a solid floor below (we'd be placing ON TOP of stairs)
        if (forwardBelowState.isSolid() && forwardState.isAir()) {
            // There's a floor here already, skip this position
            stepsSinceDirectionChange++;
            if (stepsSinceDirectionChange >= STEPS_PER_SPIRAL) {
                spiralDirection = (spiralDirection + 1) % 4;
                stepsSinceDirectionChange = 0;
            }
            return false;
        }

        // Only place if we need to fill a gap (no floor below)
        if (!forwardBelowState.isSolid() && (forwardState.isAir() || !forwardState.getFluidState().isEmpty())) {
            this.level().setBlock(forward, Blocks.COBBLESTONE.defaultBlockState(), 3);
            placementCooldown = PLACEMENT_DELAY;
            stuckTicks = 0;
            this.getNavigation().stop();

            // Update spiral direction
            stepsSinceDirectionChange++;
            if (stepsSinceDirectionChange >= STEPS_PER_SPIRAL) {
                spiralDirection = (spiralDirection + 1) % 4;
                stepsSinceDirectionChange = 0;
            }

            return true;
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
            this.getNavigation().stop();
            return true;
        }

        return false;
    }

    /**
     * Attempts to break obstacles in the path
     */
    private boolean attemptBreakObstacle(BlockPos current, boolean climbing) {
        BlockPos targetBreak = null;

        if (climbing) {
            int[] offset = getSpiralOffset();
            BlockPos forward = current.offset(offset[0], 0, offset[1]);

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
        } else {
            int dx = Integer.compare(targetPos.getX(), current.getX());
            int dz = Integer.compare(targetPos.getZ(), current.getZ());

            BlockPos forward = current.offset(dx, 0, dz);

            BlockPos[] candidates = {
                    forward,
                    forward.above(),
                    current.above().above()
            };

            for (BlockPos p : candidates) {
                BlockState state = this.level().getBlockState(p);
                if (!state.isAir() && state.getDestroySpeed(this.level(), p) >= 0) {
                    targetBreak = p;
                    break;
                }
            }
        }

        if (targetBreak != null) {
            breakGoal.startBreaking(targetBreak);
            // Remember this position
            lastBrokenBlock = targetBreak;
            ticksSinceBreak = 0;
            return true;
        }

        return false;
    }

    /**
     * Gets the X and Z offset for the current spiral direction
     */
    private int[] getSpiralOffset() {
        switch (spiralDirection) {
            case 0: return new int[]{0, -1};  // North
            case 1: return new int[]{1, 0};   // East
            case 2: return new int[]{0, 1};   // South
            case 3: return new int[]{-1, 0};  // West
            default: return new int[]{0, -1};
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