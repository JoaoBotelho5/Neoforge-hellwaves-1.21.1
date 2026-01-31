package com.hellwaves.hellwavesmod.HWMobs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.*;

public class WarpedMiner extends ZombifiedPiglin {
    // Lista de blocos que podem ser quebrados (até a dureza do deepslate)
    private static final Set<Block> UNBREAKABLE_BLOCKS = Set.of(
            Blocks.BEDROCK,
            Blocks.END_PORTAL_FRAME,
            Blocks.END_PORTAL,
            Blocks.NETHER_PORTAL,
            Blocks.BARRIER,
            Blocks.COMMAND_BLOCK,
            Blocks.CHAIN_COMMAND_BLOCK,
            Blocks.REPEATING_COMMAND_BLOCK,
            Blocks.STRUCTURE_BLOCK,
            Blocks.JIGSAW,
            Blocks.STRUCTURE_VOID
    );

    // Sistema de quebra progressiva
    private static final Map<Block, Integer> BREAK_TIMES = Map.of(
            Blocks.OBSIDIAN, 300,
            Blocks.CRYING_OBSIDIAN, 300,
            Blocks.RESPAWN_ANCHOR, 250,
            Blocks.ANCIENT_DEBRIS, 800,
            Blocks.STONE, 100,
            Blocks.DEEPSLATE, 120,
            Blocks.IRON_ORE, 60,
            Blocks.DIAMOND_ORE, 70,
            Blocks.COBBLESTONE, 100
    );

    private static final int MIN_BREAK_TIME = 30;
    private static final int BREAK_INTERVAL = 20;
    private static final int MAX_BREAK_DISTANCE = 3;

    private int breakTimer = 0;
    private boolean hasSpawnedGear = false;
    private BlockPos targetBlockPos;

    // Sistema de quebra progressiva
    private BlockPos currentBreakingPos = null;
    private int breakingProgress = 0;
    private int lastBreakStage = -1;
    private int breakStartTime = 0;

    // Sistema de combate
    private int combatCooldown = 0;
    private boolean inCombat = false;
    private LivingEntity combatTarget = null;

    public WarpedMiner(EntityType<? extends ZombifiedPiglin> entityType, Level level) {
        super(entityType, level);
        this.setCustomName(Component.literal("§aWarped Miner§r"));
        this.setCustomNameVisible(true);
        this.xpReward = 25;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0D, true)); // Apenas para se defender
        this.goalSelector.addGoal(2, new MoveToTargetBlockGoal());
        this.goalSelector.addGoal(3, new BreakBlocksInPathGoal());
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        // APENAS DEFESA - não ataca proativamente
        this.targetSelector.addGoal(0, new HurtByTargetGoal(this).setAlertOthers());
        // REMOVIDO: NearestAttackableTargetGoal - não ataca jogadores ou outros mobs proativamente
    }

    public static AttributeSupplier.Builder createAttributes() {
        return ZombifiedPiglin.createAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 25.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.5D);
    }

    @Override
    public void tick() {
        super.tick();

        if (!hasSpawnedGear && !this.level().isClientSide()) {
            applySpawnGear();
            hasSpawnedGear = true;
        }

        if (!this.level().isClientSide() && this.isAlive()) {
            updateCombatState();

            // Só quebra blocos se não estiver em combate
            if (!inCombat) {
                breakTimer++;

                // Verificar se precisa cancelar a quebra atual
                if (currentBreakingPos != null) {
                    if (!shouldContinueBreaking(currentBreakingPos)) {
                        System.out.println("Cancelling break at " + currentBreakingPos + " - no longer valid");
                        resetBreaking();
                    } else {
                        // CONTINUAR quebra progressiva
                        continueBreakingBlock();
                    }
                }

                // Verificar novos blocos para quebrar apenas se não estiver quebrando nada
                if (breakTimer >= BREAK_INTERVAL && currentBreakingPos == null) {
                    breakBlocksInPath();
                    breakTimer = 0;
                }
            } else {
                // Em combate, cancela qualquer quebra em andamento
                if (currentBreakingPos != null) {
                    resetBreaking();
                }

                // Se o alvo de combate não existe mais ou está muito longe, sair do modo combate
                LivingEntity currentTarget = this.getTarget();
                if (currentTarget == null || !currentTarget.isAlive() ||
                        this.distanceToSqr(currentTarget) > 100.0D) { // 10 blocos de distância
                    this.setTarget(null);
                    this.inCombat = false;
                    this.combatCooldown = 0;
                    System.out.println("Warped Miner exiting combat mode - target lost");
                }
            }
        }
    }

    private void updateCombatState() {
        if (combatCooldown > 0) {
            combatCooldown--;
        }

        LivingEntity currentTarget = this.getTarget();
        if (currentTarget != null && currentTarget.isAlive()) {
            inCombat = true;
            combatTarget = currentTarget;
            combatCooldown = 100;
        } else if (combatCooldown <= 0) {
            inCombat = false;
            combatTarget = null;
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide()) {
            Entity attacker = source.getEntity();
            if (attacker instanceof LivingEntity) {
                // Só defende se for atacado diretamente
                this.setTarget((LivingEntity) attacker);
                this.inCombat = true;
                this.combatCooldown = 100;

                if (currentBreakingPos != null) {
                    resetBreaking();
                }

                System.out.println("Warped Miner attacked by " + attacker.getName().getString() + ", defending itself");
            }
        }

        return super.hurt(source, amount);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        // Só permite definir target se for em defesa própria
        if (target != null) {
            // Verifica se foi atacado por este alvo recentemente
            boolean wasHurtByTarget = this.getLastHurtByMob() == target;

            if (wasHurtByTarget) {
                super.setTarget(target);
                this.inCombat = true;
                this.combatTarget = target;
                this.combatCooldown = 100;
                System.out.println("Warped Miner setting defensive target: " + target.getName().getString());
            } else {
                // Ignora targets que não são em defesa própria
                System.out.println("Warped Miner ignoring non-defensive target: " + target.getName().getString());
            }
        } else {
            super.setTarget(null);
        }
    }

    // Override para prevenir targeting proativo
    @Override
    public boolean canAttack(LivingEntity target) {
        // Só pode atacar se estiver se defendendo
        return this.inCombat && this.getTarget() == target;
    }


    private boolean shouldContinueBreaking(BlockPos breakingPos) {
        if (inCombat) {
            return false;
        }

        BlockPos currentPos = this.blockPosition();

        if (currentPos.distSqr(breakingPos) > MAX_BREAK_DISTANCE * MAX_BREAK_DISTANCE) {
            return false;
        }

        if (!canBreakBlock(breakingPos)) {
            return false;
        }

        if (!isBlockInImmediatePath(breakingPos)) {
            return false;
        }

        return true;
    }

    private void applySpawnGear() {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));

        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        this.setDropChance(EquipmentSlot.HEAD, 0.0F);
    }

    public void setTargetBlock(BlockPos target) {
        this.targetBlockPos = target;
        System.out.println("Warped Miner target set to: " + target);
    }

    private void breakBlocksInPath() {
        if (inCombat) {
            return;
        }

        if (this.targetBlockPos == null) return;
        if (this.getTarget() != null) return;
        if (currentBreakingPos != null) return;

        BlockPos currentPos = this.blockPosition();

        List<BlockPos> blocksToBreak = findImmediateBlocksToBreak(currentPos);

        if (!blocksToBreak.isEmpty()) {
            BlockPos targetPos = blocksToBreak.get(0);
            System.out.println("Starting to break immediate block at: " + targetPos);
            startBreakingBlock(targetPos);
        }
    }

    private List<BlockPos> findImmediateBlocksToBreak(BlockPos currentPos) {
        List<BlockPos> blocks = new ArrayList<>();

        if (this.targetBlockPos == null) return blocks;

        Direction moveDirection = getMovementDirection();
        if (moveDirection != null) {
            BlockPos directlyInFront = currentPos.relative(moveDirection);
            if (isImmediateObstacle(directlyInFront)) {
                blocks.add(directlyInFront);
                return blocks;
            }
        }

        if (moveDirection != null) {
            BlockPos aboveFront = currentPos.relative(moveDirection).above();
            if (isImmediateObstacle(aboveFront)) {
                blocks.add(aboveFront);
                return blocks;
            }
        }

        if (this.targetBlockPos.getY() > currentPos.getY()) {
            BlockPos directlyAbove = currentPos.above();
            if (isImmediateObstacle(directlyAbove)) {
                blocks.add(directlyAbove);
                return blocks;
            }
        }

        return blocks;
    }

    private boolean isImmediateObstacle(BlockPos pos) {
        if (inCombat) {
            return false;
        }

        BlockPos currentPos = this.blockPosition();

        if (pos.getY() < currentPos.getY()) {
            return false;
        }

        double distSqr = currentPos.distSqr(pos);

        // CRITICAL FIX: Se está a 3 blocos ou menos (9 squared), permite minerar
        // independentemente de estar mob-blocked
        if (distSqr <= MAX_BREAK_DISTANCE * MAX_BREAK_DISTANCE) {
            // Verifica se é um bloco que pode e deve ser quebrado
            if (!canBreakBlock(pos)) {
                return false;
            }

            // Se está na direção do movimento E dentro do alcance, pode minerar
            if (isBlockingImmediateMovement(pos)) {
                return true;
            }

            // Se não está na direção imediata mas ainda está dentro do alcance,
            // verifica se está entre o mineiro e o alvo
            if (this.targetBlockPos != null && isBlockBetweenMinerAndTarget(pos)) {
                return true;
            }
        }

        return false;
    }

    private boolean isBlockingImmediateMovement(BlockPos pos) {
        BlockPos currentPos = this.blockPosition();

        Direction moveDirection = getMovementDirection();
        if (moveDirection != null && pos.equals(currentPos.relative(moveDirection))) {
            return true;
        }

        if (moveDirection != null && pos.equals(currentPos.relative(moveDirection).above())) {
            return true;
        }

        if (this.targetBlockPos.getY() > currentPos.getY() && pos.equals(currentPos.above())) {
            return true;
        }

        return false;
    }

    private Direction getMovementDirection() {
        if (this.targetBlockPos == null) return null;

        BlockPos currentPos = this.blockPosition();
        BlockPos delta = this.targetBlockPos.subtract(currentPos);

        if (Math.abs(delta.getY()) > Math.max(Math.abs(delta.getX()), Math.abs(delta.getZ()))) {
            return delta.getY() > 0 ? Direction.UP : Direction.DOWN;
        }

        if (Math.abs(delta.getX()) > Math.abs(delta.getZ())) {
            return delta.getX() > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return delta.getZ() > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    private boolean isBlockInImmediatePath(BlockPos pos) {
        return isImmediateObstacle(pos);
    }

    private boolean isBlockBetweenMinerAndTarget(BlockPos blockPos) {
        if (this.targetBlockPos == null) {
            return false;
        }

        BlockPos currentPos = this.blockPosition();

        // Verifica se o bloco está aproximadamente na direção do alvo
        // usando produto escalar dos vetores
        double dx1 = blockPos.getX() - currentPos.getX();
        double dy1 = blockPos.getY() - currentPos.getY();
        double dz1 = blockPos.getZ() - currentPos.getZ();

        double dx2 = this.targetBlockPos.getX() - currentPos.getX();
        double dy2 = this.targetBlockPos.getY() - currentPos.getY();
        double dz2 = this.targetBlockPos.getZ() - currentPos.getZ();

        // Produto escalar - se positivo, está na mesma direção geral
        double dotProduct = dx1 * dx2 + dy1 * dy2 + dz1 * dz2;

        return dotProduct > 0;
    }

    private boolean canBreakBlock(BlockPos pos) {
        if (this.level().isEmptyBlock(pos)) {
            return false;
        }

        BlockState blockState = this.level().getBlockState(pos);
        Block block = blockState.getBlock();

        if (UNBREAKABLE_BLOCKS.contains(block)) {
            return false;
        }

        if (blockState.getDestroySpeed(this.level(), pos) < 0) {
            return false;
        }

        if (blockState.getDestroySpeed(this.level(), pos) > 50.0D) {
            return false;
        }

        return blockState.is(BlockTags.MINEABLE_WITH_PICKAXE) ||
                blockState.is(BlockTags.MINEABLE_WITH_AXE) ||
                blockState.is(BlockTags.MINEABLE_WITH_SHOVEL) ||
                blockState.is(BlockTags.MINEABLE_WITH_HOE) ||
                blockState.is(BlockTags.LEAVES) ||
                blockState.is(BlockTags.WOOL) ||
                blockState.is(BlockTags.ICE) ||
                isHardBlock(block);
    }

    private void startBreakingBlock(BlockPos pos) {
        this.currentBreakingPos = pos;
        this.breakingProgress = 0;
        this.lastBreakStage = -1;
        this.breakStartTime = this.tickCount;

        BlockState blockState = this.level().getBlockState(pos);
        int breakTime = getBreakTime(blockState.getBlock());
        System.out.println("Starting to break " + blockState.getBlock().getName().getString() +
                " at " + pos + " - Time required: " + breakTime + " ticks");
    }

    private void continueBreakingBlock() {
        if (currentBreakingPos == null) return;

        BlockState blockState = this.level().getBlockState(currentBreakingPos);
        Block block = blockState.getBlock();

        if (!shouldContinueBreaking(currentBreakingPos)) {
            resetBreaking();
            return;
        }

        breakingProgress++;
        int totalBreakTime = getBreakTime(block);

        if (breakingProgress % 20 == 0) {
            System.out.println("Breaking " + block.getName().getString() +
                    " - Progress: " + breakingProgress + "/" + totalBreakTime +
                    " (" + (breakingProgress * 100 / totalBreakTime) + "%)");
        }

        int currentStage = (int) ((breakingProgress / (float) totalBreakTime) * 10);

        if (currentStage != lastBreakStage) {
            showBreakingParticles(currentStage);
            lastBreakStage = currentStage;

            if (currentStage > 0 && currentStage < 10) {
                this.level().playSound(null, currentBreakingPos, blockState.getSoundType().getHitSound(),
                        this.getSoundSource(), 0.6F, 0.8F + this.random.nextFloat() * 0.4F);
            }
        }

        if (breakingProgress >= totalBreakTime) {
            finishBreakingBlock();
        }
    }

    private void showBreakingParticles(int stage) {
        if (currentBreakingPos == null) return;

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.CRIT,
                    currentBreakingPos.getX() + 0.5,
                    currentBreakingPos.getY() + 0.5,
                    currentBreakingPos.getZ() + 0.5,
                    2 + stage,
                    0.2, 0.2, 0.2,
                    0.05
            );

            if (stage >= 5) {
                serverLevel.sendParticles(
                        ParticleTypes.SMOKE,
                        currentBreakingPos.getX() + 0.5,
                        currentBreakingPos.getY() + 0.5,
                        currentBreakingPos.getZ() + 0.5,
                        stage - 2,
                        0.15, 0.15, 0.15,
                        0.02
                );
            }
        }
    }

    private void finishBreakingBlock() {
        if (currentBreakingPos != null) {
            int timeTaken = this.tickCount - breakStartTime;
            System.out.println("FINISHED breaking block at " + currentBreakingPos +
                    " - Time taken: " + timeTaken + " ticks");
            breakBlock(currentBreakingPos);
            resetBreaking();
        }
    }

    private void resetBreaking() {
        this.currentBreakingPos = null;
        this.breakingProgress = 0;
        this.lastBreakStage = -1;
        this.breakStartTime = 0;
    }

    private int getBreakTime(Block block) {
        int time = BREAK_TIMES.getOrDefault(block, 40);
        return Math.max(time, MIN_BREAK_TIME);
    }

    private boolean isHardBlock(Block block) {
        return BREAK_TIMES.containsKey(block);
    }

    private void breakBlock(BlockPos pos) {
        if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
            BlockState blockState = this.level().getBlockState(pos);
            Block block = blockState.getBlock();

            this.level().playSound(null, pos, blockState.getSoundType().getBreakSound(),
                    this.getSoundSource(), 1.0F, 1.0F);

            serverLevel.sendParticles(
                    ParticleTypes.CLOUD,
                    pos.getX() + 0.5,
                    pos.getY() + 0.5,
                    pos.getZ() + 0.5,
                    8,
                    0.3, 0.3, 0.3,
                    0.1
            );

            ItemStack itemStack = new ItemStack(block.asItem());
            if (!itemStack.isEmpty()) {
                serverLevel.sendParticles(
                        new ItemParticleOption(ParticleTypes.ITEM, itemStack),
                        pos.getX() + 0.5,
                        pos.getY() + 0.5,
                        pos.getZ() + 0.5,
                        6,
                        0.2, 0.2, 0.2,
                        0.05
                );
            }

            Block.getDrops(blockState, serverLevel, pos, null, this, ItemStack.EMPTY)
                    .forEach(dropStack -> {
                        Block.popResource(this.level(), pos, dropStack);
                    });

            this.level().destroyBlock(pos, false, this);
        }
    }

    // Goals
    private class MoveToTargetBlockGoal extends Goal {
        private int stuckTimer = 0;
        private BlockPos lastPos = null;

        public MoveToTargetBlockGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return WarpedMiner.this.targetBlockPos != null &&
                    WarpedMiner.this.getTarget() == null;
        }

        @Override
        public boolean canContinueToUse() {
            return WarpedMiner.this.targetBlockPos != null &&
                    WarpedMiner.this.getTarget() == null &&
                    WarpedMiner.this.distanceToSqr(
                            WarpedMiner.this.targetBlockPos.getX() + 0.5,
                            WarpedMiner.this.targetBlockPos.getY(),
                            WarpedMiner.this.targetBlockPos.getZ() + 0.5
                    ) > 1.0D;
        }

        @Override
        public void start() {
            this.stuckTimer = 0;
            this.lastPos = WarpedMiner.this.blockPosition();
        }

        @Override
        public void tick() {
            if (WarpedMiner.this.targetBlockPos != null) {
                BlockPos currentPos = WarpedMiner.this.blockPosition();

                if (lastPos != null && currentPos.distSqr(lastPos) < 0.1) {
                    stuckTimer++;
                } else {
                    stuckTimer = 0;
                }
                lastPos = currentPos;

                if (stuckTimer > 40) {
                    stuckTimer = 0;
                }

                WarpedMiner.this.getNavigation().moveTo(
                        WarpedMiner.this.targetBlockPos.getX() + 0.5,
                        WarpedMiner.this.targetBlockPos.getY(),
                        WarpedMiner.this.targetBlockPos.getZ() + 0.5,
                        1.0D
                );
            }
        }
    }

    private class BreakBlocksInPathGoal extends Goal {
        @Override
        public boolean canUse() {
            return WarpedMiner.this.targetBlockPos != null &&
                    WarpedMiner.this.getTarget() == null;
        }
    }

    // Sound overrides
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIFIED_PIGLIN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.ZOMBIFIED_PIGLIN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIFIED_PIGLIN_DEATH;
    }


    // NBT Data Persistence
    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("BreakTimer", breakTimer);
        compound.putBoolean("HasSpawnedGear", hasSpawnedGear);
        compound.putInt("BreakingProgress", breakingProgress);
        compound.putInt("LastBreakStage", lastBreakStage);
        compound.putBoolean("InCombat", inCombat);
        compound.putInt("CombatCooldown", combatCooldown);
        compound.putInt("BreakStartTime", breakStartTime);
        if (currentBreakingPos != null) {
            compound.putInt("BreakingX", currentBreakingPos.getX());
            compound.putInt("BreakingY", currentBreakingPos.getY());
            compound.putInt("BreakingZ", currentBreakingPos.getZ());
        }
        if (targetBlockPos != null) {
            compound.putInt("TargetX", targetBlockPos.getX());
            compound.putInt("TargetY", targetBlockPos.getY());
            compound.putInt("TargetZ", targetBlockPos.getZ());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("BreakTimer")) {
            breakTimer = compound.getInt("BreakTimer");
        }
        if (compound.contains("HasSpawnedGear")) {
            hasSpawnedGear = compound.getBoolean("HasSpawnedGear");
        }
        if (compound.contains("BreakingProgress")) {
            breakingProgress = compound.getInt("BreakingProgress");
        }
        if (compound.contains("LastBreakStage")) {
            lastBreakStage = compound.getInt("LastBreakStage");
        }
        if (compound.contains("InCombat")) {
            inCombat = compound.getBoolean("InCombat");
        }
        if (compound.contains("CombatCooldown")) {
            combatCooldown = compound.getInt("CombatCooldown");
        }
        if (compound.contains("BreakStartTime")) {
            breakStartTime = compound.getInt("BreakStartTime");
        }
        if (compound.contains("BreakingX") && compound.contains("BreakingY") && compound.contains("BreakingZ")) {
            currentBreakingPos = new BlockPos(
                    compound.getInt("BreakingX"),
                    compound.getInt("BreakingY"),
                    compound.getInt("BreakingZ")
            );
        }
        if (compound.contains("TargetX") && compound.contains("TargetY") && compound.contains("TargetZ")) {
            targetBlockPos = new BlockPos(
                    compound.getInt("TargetX"),
                    compound.getInt("TargetY"),
                    compound.getInt("TargetZ")
            );
        }
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        spawnGroupData = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        applySpawnGear();
        hasSpawnedGear = true;
        return spawnGroupData;
    }
}