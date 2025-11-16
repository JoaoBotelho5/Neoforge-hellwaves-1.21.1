package com.hellwaves.hellwavesmod.HWMobs;

import net.minecraft.core.BlockPos;
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

    private static final double MAX_BREAK_HARDNESS = 3.0D; // Deepslate tem 3.0 de dureza
    private static final int BREAK_INTERVAL = 40; // 2 segundos (20 ticks * 2)

    private int breakTimer = 0;
    private boolean hasSpawnedGear = false;
    private BlockPos targetBlockPos; // Bloco alvo que ele deve alcançar

    public WarpedMiner(EntityType<? extends ZombifiedPiglin> entityType, Level level) {
        super(entityType, level);
        this.setCustomName(Component.literal("§aWarped Miner§r"));
        this.setCustomNameVisible(true);
        this.xpReward = 25;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MoveToTargetBlockGoal());
        this.goalSelector.addGoal(2, new BreakBlocksInPathGoal());
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

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

        // Apply gear on first tick if not already applied
        if (!hasSpawnedGear && !this.level().isClientSide()) {
            applySpawnGear();
            hasSpawnedGear = true;
        }

        // Handle block breaking
        if (!this.level().isClientSide() && this.isAlive()) {
            breakTimer++;

            if (breakTimer >= BREAK_INTERVAL) {
                breakBlocksInPath();
                breakTimer = 0;
            }
        }
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

    private void breakBlocksInPath() {
        if (this.targetBlockPos == null) return;
        if (this.getTarget() != null) return; // Não quebrar blocos quando em combate

        BlockPos currentPos = this.blockPosition();
        Set<BlockPos> blocksToCheck = new HashSet<>();

        // Focar APENAS em blocos que estão diretamente na frente
        BlockPos forward = currentPos.relative(this.getDirection());
        blocksToCheck.add(forward);
        blocksToCheck.add(forward.above()); // Para blocos que precisam pular

        // Verificar também o bloco atual se estiver dentro de um bloco (raro)
        blocksToCheck.add(currentPos.above());

        // Ordenar por prioridade: primeiro blocos diretamente na frente, depois outros
        List<BlockPos> prioritizedBlocks = new ArrayList<>(blocksToCheck);

        // Ordenar por proximidade com a linha para o alvo
        prioritizedBlocks.sort((pos1, pos2) -> {
            boolean isForward1 = pos1.equals(forward) || pos1.equals(forward.above());
            boolean isForward2 = pos2.equals(forward) || pos2.equals(forward.above());

            if (isForward1 && !isForward2) return -1;
            if (!isForward1 && isForward2) return 1;

            double dist1 = isInDirectPathToTarget(pos1) ? 0 : 1;
            double dist2 = isInDirectPathToTarget(pos2) ? 0 : 1;
            return Double.compare(dist1, dist2);
        });

        for (BlockPos pos : prioritizedBlocks) {
            if (canBreakBlock(pos) && isBlockingPath(pos)) {
                breakBlock(pos);
                // Quebrar apenas um bloco por vez
                break;
            }
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

        // Verificar dureza máxima
        if (blockState.getDestroySpeed(this.level(), pos) > MAX_BREAK_HARDNESS) {
            return false;
        }

        // Verificar tags comuns de blocos quebráveis
        return blockState.is(BlockTags.MINEABLE_WITH_PICKAXE) ||
                blockState.is(BlockTags.MINEABLE_WITH_AXE) ||
                blockState.is(BlockTags.MINEABLE_WITH_SHOVEL) ||
                blockState.is(BlockTags.MINEABLE_WITH_HOE) ||
                blockState.is(BlockTags.LEAVES) ||
                blockState.is(BlockTags.WOOL) ||
                blockState.is(BlockTags.ICE);
    }

    private boolean isBlockingPath(BlockPos pos) {
        if (this.targetBlockPos == null) return false;

        BlockPos currentPos = this.blockPosition();

        // Verificar se o bloco está diretamente no caminho para o alvo
        if (pos.getY() < currentPos.getY()) {
            return false;
        }

        BlockPos forwardPos = currentPos.relative(this.getDirection());
        if (pos.equals(forwardPos)) {
            return true;
        }

        if (pos.equals(forwardPos.above())) {
            return true;
        }
        if (isInDirectPathToTarget(pos)) {
            return true;
        }

        return false;

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

    private boolean isInLineOfSight(BlockPos start, BlockPos end, BlockPos check) {
        // Verificação simplificada se o bloco está na linha entre start e end
        double distToLine = distanceToLine(start, end, check);
        return distToLine < 1.5D; // Margem de 1.5 blocos
    }

    private double distanceToLine(BlockPos lineStart, BlockPos lineEnd, BlockPos point) {
        // Cálculo simplificado da distância de um ponto até uma linha
        double lineLength = Math.sqrt(lineStart.distSqr(lineEnd));
        if (lineLength == 0) return Math.sqrt(lineStart.distSqr(point));

        double t = Math.max(0, Math.min(1,
                ((point.getX() - lineStart.getX()) * (lineEnd.getX() - lineStart.getX()) +
                        (point.getY() - lineStart.getY()) * (lineEnd.getY() - lineStart.getY()) +
                        (point.getZ() - lineStart.getZ()) * (lineEnd.getZ() - lineStart.getZ())) /
                        (lineLength * lineLength)));

        BlockPos projection = new BlockPos(
                (int)(lineStart.getX() + t * (lineEnd.getX() - lineStart.getX())),
                (int)(lineStart.getY() + t * (lineEnd.getY() - lineStart.getY())),
                (int)(lineStart.getZ() + t * (lineEnd.getZ() - lineStart.getZ()))
        );

        return Math.sqrt(point.distSqr(projection));
    }

    private void breakBlock(BlockPos pos) {
        if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
            BlockState blockState = this.level().getBlockState(pos);
            Block block = blockState.getBlock();

            // Dropar os itens do bloco
            Block.getDrops(blockState, serverLevel, pos, null, this, ItemStack.EMPTY)
                    .forEach(itemStack -> {
                        Block.popResource(this.level(), pos, itemStack);
                    });

            // Quebrar o bloco
            this.level().destroyBlock(pos, false, this);

            // Efeitos de som
            this.level().playSound(null, pos, blockState.getSoundType().getBreakSound(),
                    this.getSoundSource(), 1.0F, 1.0F);
        }
    }

    // Goal para mover-se em direção ao bloco alvo
    private class MoveToTargetBlockGoal extends Goal {
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
                    ) > 4.0D; // Parar quando estiver a 2 blocos de distância
        }

        @Override
        public void tick() {
            if (WarpedMiner.this.targetBlockPos != null) {
                WarpedMiner.this.getNavigation().moveTo(
                        WarpedMiner.this.targetBlockPos.getX() + 0.5,
                        WarpedMiner.this.targetBlockPos.getY(),
                        WarpedMiner.this.targetBlockPos.getZ() + 0.5,
                        1.0D
                );
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