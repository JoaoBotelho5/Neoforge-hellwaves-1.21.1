package com.hellwaves.hellwavesmod.HWMobs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
            Blocks.OBSIDIAN, 900, // 45 segundos
            Blocks.CRYING_OBSIDIAN, 900, // 45 segundos
            Blocks.RESPAWN_ANCHOR, 600, // 30 segundos
            Blocks.ANCIENT_DEBRIS, 1200 // 60 segundos
    );

    private static final int BREAK_INTERVAL = 20; // 1 segundo (20 ticks * 1)

    private int breakTimer = 0;
    private boolean hasSpawnedGear = false;
    private BlockPos targetBlockPos; // Bloco alvo que ele deve alcançar

    // Sistema de quebra progressiva
    private BlockPos currentBreakingPos = null;
    private int breakingProgress = 0;

    public WarpedMiner(EntityType<? extends ZombifiedPiglin> entityType, Level level) {
        super(entityType, level);
        this.setCustomName(Component.literal("§aWarped Miner§r"));
        this.setCustomNameVisible(true);
        this.xpReward = 25;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MoveToTargetBlockGoal()); // Alta prioridade
        this.goalSelector.addGoal(2, new BreakBlocksInPathGoal());
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D)); // Prioridade mais baixa
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        // Só ataca se for atacado primeiro
        this.targetSelector.addGoal(0, new HurtByTargetGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return ZombifiedPiglin.createAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 25.0D);
    }

    @Override
    public void tick() {
        super.tick();

        if (!hasSpawnedGear && !this.level().isClientSide()) {
            applySpawnGear();
            hasSpawnedGear = true;
        }

        if (!this.level().isClientSide() && this.isAlive()) {
            breakTimer++;

            // Só quebrar blocos se não estiver se movendo bem em direção ao alvo
            if (breakTimer >= BREAK_INTERVAL) {
                if (shouldBreakBlocks()) {
                    breakBlocksInPath();
                }
                breakTimer = 0;
            }

            // Atualizar quebra progressiva a cada tick
            if (currentBreakingPos != null) {
                continueBreakingBlock();
            }
        }
    }

    // Novo método para determinar se deve quebrar blocos
    private boolean shouldBreakBlocks() {
        if (this.targetBlockPos == null) return false;

        BlockPos currentPos = this.blockPosition();

        // Verificar se está se movendo em direção ao alvo
        double distanceToTarget = this.distanceToSqr(
                this.targetBlockPos.getX() + 0.5,
                this.targetBlockPos.getY(),
                this.targetBlockPos.getZ() + 0.5
        );

        // Se está muito longe ou não está se movendo, quebrar blocos
        if (distanceToTarget > 100.0) { // 10 blocos de distância
            return true;
        }

        // Verificar se há blocos bloqueando diretamente na frente
        BlockPos forward = currentPos.relative(this.getDirection());
        if (!this.level().isEmptyBlock(forward) && isBlockingPath(forward)) {
            return true;
        }

        return false;
    }

    private void applySpawnGear() {
        // Equipar picareta de netherite
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));

        // No drop chance for equipment
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        this.setDropChance(EquipmentSlot.HEAD, 0.0F);
    }

    // Método para definir o bloco alvo (será chamado pelo sistema de waves)
    public void setTargetBlock(BlockPos target) {
        this.targetBlockPos = target;
    }

    // Método auxiliar para obter direção para o alvo
    private Direction getDirectionToTarget() {
        if (this.targetBlockPos == null) return null;

        BlockPos currentPos = this.blockPosition();
        BlockPos delta = this.targetBlockPos.subtract(currentPos);

        // Obter a direção principal baseada no maior delta absoluto
        int absX = Math.abs(delta.getX());
        int absZ = Math.abs(delta.getZ());

        if (absX > absZ) {
            return delta.getX() > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return delta.getZ() > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    private void breakBlocksInPath() {
        if (this.targetBlockPos == null) return;
        if (this.getTarget() != null) return;

        // Se já está quebrando um bloco, continua o progresso
        if (currentBreakingPos != null) {
            continueBreakingBlock();
            return;
        }

        BlockPos currentPos = this.blockPosition();
        Set<BlockPos> blocksToCheck = new HashSet<>();

        // Estratégia mais focada - apenas blocos que realmente bloqueiam
        BlockPos forward = currentPos.relative(this.getDirection());

        // Apenas blocos que realmente bloqueiam o movimento
        blocksToCheck.add(forward); // Bloco diretamente na frente
        blocksToCheck.add(forward.above()); // Bloco acima na frente (para saltar)

        // Blocos na direção do alvo apenas se diferente da direção atual
        Direction toTargetDirection = getDirectionToTarget();
        if (toTargetDirection != null && toTargetDirection != this.getDirection()) {
            BlockPos targetForward = currentPos.relative(toTargetDirection);
            blocksToCheck.add(targetForward);
        }

        // Bloco acima do mob apenas se estiver dentro dele
        if (!this.level().isEmptyBlock(currentPos.above())) {
            blocksToCheck.add(currentPos.above());
        }

        // DEBUG: Mostrar blocos sendo verificados
        if (this.tickCount % 100 == 0) {
            System.out.println("Warped Miner checking " + blocksToCheck.size() + " blocks for breaking at " + currentPos);
        }

        // Lógica de prioridade
        List<BlockPos> easyBlocks = new ArrayList<>();
        List<BlockPos> hardBlocks = new ArrayList<>();

        for (BlockPos pos : blocksToCheck) {
            if (canBreakBlock(pos) && isBlockingPath(pos)) {
                BlockState blockState = this.level().getBlockState(pos);
                if (isHardBlock(blockState.getBlock())) {
                    hardBlocks.add(pos);
                } else {
                    easyBlocks.add(pos);
                }
            }
        }

        // Primeiro tenta quebrar blocos fáceis
        for (BlockPos pos : easyBlocks) {
            System.out.println("Breaking easy block at: " + pos);
            breakBlock(pos);
            return;
        }

        // Se só há blocos duros, começa a quebrar um
        if (!hardBlocks.isEmpty()) {
            BlockPos targetPos = hardBlocks.get(0);
            System.out.println("Starting to break hard block at: " + targetPos);
            startBreakingBlock(targetPos);
        }
    }

    private boolean canBreakBlock(BlockPos pos) {
        if (this.level().isEmptyBlock(pos)) {
            return false;
        }

        BlockState blockState = this.level().getBlockState(pos);
        Block block = blockState.getBlock();

        // Verificar se o bloco não está na lista de blocos inquebráveis
        if (UNBREAKABLE_BLOCKS.contains(block)) {
            return false;
        }

        // Verificar se o bloco não é bedrock ou outros blocos indestrutíveis
        if (blockState.getDestroySpeed(this.level(), pos) < 0) {
            return false;
        }

        // Verificar dureza máxima (aumentada para permitir obsidiana)
        if (blockState.getDestroySpeed(this.level(), pos) > 50.0D) {
            return false;
        }

        // Verificar tags comuns de blocos quebráveis
        return blockState.is(BlockTags.MINEABLE_WITH_PICKAXE) ||
                blockState.is(BlockTags.MINEABLE_WITH_AXE) ||
                blockState.is(BlockTags.MINEABLE_WITH_SHOVEL) ||
                blockState.is(BlockTags.MINEABLE_WITH_HOE) ||
                blockState.is(BlockTags.LEAVES) ||
                blockState.is(BlockTags.WOOL) ||
                blockState.is(BlockTags.ICE) ||
                isHardBlock(block);
    }

    private boolean isBlockingPath(BlockPos pos) {
        if (this.targetBlockPos == null) return false;

        BlockPos currentPos = this.blockPosition();

        // NUNCA quebrar blocos abaixo do mob (chão)
        if (pos.getY() < currentPos.getY()) {
            return false;
        }

        // Blocos diretamente na frente
        BlockPos forward = currentPos.relative(this.getDirection());
        if (pos.equals(forward)) {
            return true; // Bloco diretamente na frente - SEMPRE quebrar
        }

        // Bloco acima do que está na frente (para pular/escadas)
        if (pos.equals(forward.above())) {
            // Só quebrar se estiver bloqueando o movimento vertical
            return !this.level().isEmptyBlock(forward) ||
                    !this.level().isEmptyBlock(currentPos.above());
        }

        // Blocos na direção do alvo
        Direction toTargetDirection = getDirectionToTarget();
        if (toTargetDirection != null) {
            BlockPos targetForward = currentPos.relative(toTargetDirection);
            if (pos.equals(targetForward)) {
                return true; // Bloco na direção do alvo
            }
        }

        // Bloco acima do mob (se estiver dentro de um bloco)
        if (pos.equals(currentPos.above())) {
            return !this.level().isEmptyBlock(currentPos.above());
        }

        // Verificar se está no caminho direto apenas para blocos na frente
        return isInDirectPathToTarget(pos) &&
                pos.getY() >= currentPos.getY() &&
                pos.getY() <= currentPos.getY() + 1;
    }

    private boolean isInDirectPathToTarget(BlockPos pos) {
        if (this.targetBlockPos == null) return false;

        BlockPos currentPos = this.blockPosition();

        // Só considerar blocos que estão no mesmo nível Y ou acima
        if (pos.getY() < currentPos.getY()) {
            return false;
        }

        // Criar uma linha 2D (ignorando Y) do mob para o alvo
        double mobX = currentPos.getX() + 0.5;
        double mobZ = currentPos.getZ() + 0.5;
        double targetX = this.targetBlockPos.getX() + 0.5;
        double targetZ = this.targetBlockPos.getZ() + 0.5;

        double blockX = pos.getX() + 0.5;
        double blockZ = pos.getZ() + 0.5;

        // Calcular distância do bloco até a linha entre mob e alvo
        double distanceToLine = pointToLineDistance(mobX, mobZ, targetX, targetZ, blockX, blockZ);

        // Só quebrar blocos que estão muito próximos da linha (máximo 1.5 blocos de distância)
        return distanceToLine < 1.5;
    }

    private double pointToLineDistance(double lineX1, double lineZ1, double lineX2, double lineZ2, double pointX, double pointZ) {
        double A = pointX - lineX1;
        double B = pointZ - lineZ1;
        double C = lineX2 - lineX1;
        double D = lineZ2 - lineZ1;

        double dot = A * C + B * D;
        double len_sq = C * C + D * D;
        double param = (len_sq != 0) ? dot / len_sq : -1;

        double xx, zz;

        if (param < 0) {
            xx = lineX1;
            zz = lineZ1;
        } else if (param > 1) {
            xx = lineX2;
            zz = lineZ2;
        } else {
            xx = lineX1 + param * C;
            zz = lineZ1 + param * D;
        }

        double dx = pointX - xx;
        double dz = pointZ - zz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    // Métodos do sistema de quebra progressiva
    private void startBreakingBlock(BlockPos pos) {
        this.currentBreakingPos = pos;
        this.breakingProgress = 0;

        BlockState blockState = this.level().getBlockState(pos);
        System.out.println("Starting to break " + blockState.getBlock().getName().getString() + " at " + pos);

        // Efeito visual inicial
        this.level().playSound(null, pos, blockState.getSoundType().getHitSound(),
                this.getSoundSource(), 1.0F, 1.0F);
    }

    private void continueBreakingBlock() {
        if (currentBreakingPos == null) return;

        BlockState blockState = this.level().getBlockState(currentBreakingPos);
        Block block = blockState.getBlock();

        // Verificar se o bloco ainda existe
        if (!canBreakBlock(currentBreakingPos) || !isBlockingPath(currentBreakingPos)) {
            resetBreaking();
            return;
        }

        breakingProgress++;

        // Efeitos visuais durante a quebra
        if (breakingProgress % 20 == 0) { // A cada segundo
            // Som de quebra
            this.level().playSound(null, currentBreakingPos, blockState.getSoundType().getHitSound(),
                    this.getSoundSource(), 0.8F, 0.9F + this.random.nextFloat() * 0.2F);

            // Partículas de quebra (apenas no lado do servidor)
            if (this.level() instanceof ServerLevel serverLevel) {
                // Usando partículas simples que funcionam com certeza
                serverLevel.sendParticles(
                        ParticleTypes.CRIT,
                        currentBreakingPos.getX() + 0.5,
                        currentBreakingPos.getY() + 0.5,
                        currentBreakingPos.getZ() + 0.5,
                        8, // quantidade
                        0.3, 0.3, 0.3, // spread
                        0.1 // velocidade extra
                );
            }

            System.out.println("Breaking progress: " + breakingProgress + "/" + getBreakTime(block));
        }

        // Verificar se terminou de quebrar
        if (breakingProgress >= getBreakTime(block)) {
            finishBreakingBlock();
        }
    }

    private void finishBreakingBlock() {
        if (currentBreakingPos != null) {
            System.out.println("Finished breaking block at " + currentBreakingPos);
            breakBlock(currentBreakingPos);
            resetBreaking();
        }
    }

    private void resetBreaking() {
        this.currentBreakingPos = null;
        this.breakingProgress = 0;
    }

    private int getBreakTime(Block block) {
        return BREAK_TIMES.getOrDefault(block, 40); // Default 2 segundos para blocos normais
    }

    private boolean isHardBlock(Block block) {
        return BREAK_TIMES.containsKey(block);
    }

    private void breakBlock(BlockPos pos) {
        if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
            BlockState blockState = this.level().getBlockState(pos);
            Block block = blockState.getBlock();

            // Efeitos melhorados
            this.level().playSound(null, pos, blockState.getSoundType().getBreakSound(),
                    this.getSoundSource(), 1.0F, 1.0F);

            // Partículas de quebra final
            serverLevel.sendParticles(
                    ParticleTypes.EXPLOSION,
                    pos.getX() + 0.5,
                    pos.getY() + 0.5,
                    pos.getZ() + 0.5,
                    12, // quantidade
                    0.5, 0.5, 0.5, // spread
                    0.2 // velocidade extra
            );

            // Dropar os itens do bloco
            Block.getDrops(blockState, serverLevel, pos, null, this, ItemStack.EMPTY)
                    .forEach(itemStack -> {
                        Block.popResource(this.level(), pos, itemStack);
                    });

            // Quebrar o bloco
            this.level().destroyBlock(pos, false, this);
        }
    }

    // Goal para mover-se em direção ao bloco alvo
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
                    ) > 2.0D; // Reduzir para 1 bloco de distância
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

                // Verificar se está preso
                if (lastPos != null && currentPos.distSqr(lastPos) < 1.0) {
                    stuckTimer++;
                } else {
                    stuckTimer = 0;
                }
                lastPos = currentPos;

                // Se está preso por mais de 3 segundos, tentar quebrar mais agressivamente
                if (stuckTimer > 60) {
                    System.out.println("Warped Miner stuck at " + currentPos + ", forcing block breaking");
                    WarpedMiner.this.breakBlocksInPath();
                    stuckTimer = 0;
                }

                // Sempre tentar mover para o alvo
                WarpedMiner.this.getNavigation().moveTo(
                        WarpedMiner.this.targetBlockPos.getX() + 0.5,
                        WarpedMiner.this.targetBlockPos.getY(),
                        WarpedMiner.this.targetBlockPos.getZ() + 0.5,
                        1.0D
                );

                // Forçar quebra de blocos se não está se movendo bem
                if (!WarpedMiner.this.getNavigation().isDone() && WarpedMiner.this.random.nextFloat() < 0.1F) {
                    WarpedMiner.this.breakBlocksInPath();
                }
            }
        }
    }

    // Goal para quebrar blocos no caminho
    private class BreakBlocksInPathGoal extends Goal {
        @Override
        public boolean canUse() {
            return WarpedMiner.this.targetBlockPos != null &&
                    WarpedMiner.this.getTarget() == null;
        }

        @Override
        public void tick() {
            // A lógica de quebrar blocos é tratada no método tick() principal
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

        // Apply gear immediately on spawn
        applySpawnGear();
        hasSpawnedGear = true;

        return spawnGroupData;
    }
}