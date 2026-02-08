package com.hellwaves.hellwavesmod.HWMobs;

import com.hellwaves.hellwavesmod.inventory.GuardianInventory;
import com.hellwaves.hellwavesmod.inventory.GuardianInventoryMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

public class SkeletonGuardian extends AbstractSkeleton implements RangedAttackMob, IGuardian {
    // Estados do mob
    public enum GuardState {
        STAY,       // Fica parado no lugar
        FOLLOW,     // Segue o jogador
        WANDER_AREA // Vaga pela área
    }

    private BlockPos stayPosition = null;
    private GuardState currentState = GuardState.STAY;
    private Player followingPlayer = null;
    private int stateChangeCooldown = 0;
    private GuardianInventory guardianInventory;

    // Leveling System
    private int guardianLevel = 1;
    private static final int MAX_LEVEL = 5;
    private int arrowsFired = 0;
    // Regeneração natural
    private int regenTimer = 0;
    private boolean wasInCombat = false;
    private int combatRegenTickCounter = 0;
    private int idleRegenTickCounter = 0;


    // Flag to prevent equipment clearing when restoring from cage
    private boolean restoringFromCage = false;

    public SkeletonGuardian(EntityType<? extends AbstractSkeleton> entityType, Level level) {
        super(entityType, level);
        this.setCustomName(Component.literal("§7Skeleton Guardian§r"));
        this.setCustomNameVisible(true);
        this.xpReward = 0;
    }

    @Override
    public void setRestoringFromCage(boolean restoring) {
        this.restoringFromCage = restoring;
    }

    @Override
    public boolean isRestoringFromCage() {
        return this.restoringFromCage;
    }

    @Override
    public GuardianInventory getGuardianInventory() {
        if (guardianInventory == null) {
            guardianInventory = new GuardianInventory(this);
        }
        return guardianInventory;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        if (this.isRestoringFromCage()) {
            // NÃO equipa arco nem nada
            return;
        }

        super.populateDefaultEquipmentSlots(random, difficulty);
    }


    // ===== LEVELING SYSTEM METHODS =====

    @Override
    public int getGuardianLevel() {
        return guardianLevel;
    }

    @Override
    public void setGuardianLevel(int level) {
        if (level >= 1 && level <= MAX_LEVEL) {
            int oldLevel = this.guardianLevel;
            this.guardianLevel = level;

            if (!this.level().isClientSide()) {
                applyLevelBonuses(oldLevel, level);

                // Visual feedback
                this.level().playSound(null, this.blockPosition(),
                        SoundEvents.PLAYER_LEVELUP, this.getSoundSource(), 1.0F, 1.0F);

                // Particle effect
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(
                            net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                            this.getX(), this.getY() + 1.0D, this.getZ(),
                            20, 0.5D, 0.5D, 0.5D, 0.1D
                    );
                }
            }
        }
    }

    public boolean canUpgrade() {
        return guardianLevel < MAX_LEVEL;
    }

    public ItemStack getUpgradeCost() {
        return switch (guardianLevel) {
            case 1 -> new ItemStack(Items.GOLD_BLOCK, 1);      // Different from Zombie's BOOK
            case 2 -> new ItemStack(Items.EMERALD_BLOCK, 1);   // Different from Zombie's DIAMOND_BLOCK
            case 3 -> new ItemStack(Items.ENDER_EYE, 1); // Different from Zombie's NETHERITE_SCRAP
            case 4 -> new ItemStack(Items.NAUTILUS_SHELL, 1);     // Same as Zombie
            default -> ItemStack.EMPTY;
        };
    }

    public boolean tryUpgrade(Player player) {
        if (!canUpgrade()) {
            return false;
        }

        ItemStack cost = getUpgradeCost();
        if (cost.isEmpty()) {
            return false;
        }

        if (!player.getAbilities().instabuild && !hasRequiredItems(player, cost)) {
            player.displayClientMessage(
                    Component.literal("§cYou need " + cost.getCount() + "x " +
                            cost.getHoverName().getString() + " to upgrade!"), true);
            return false;
        }

        if (!player.getAbilities().instabuild) {
            consumeItems(player, cost);
        }

        setGuardianLevel(guardianLevel + 1);

        player.displayClientMessage(
                Component.literal("§aSkeleton Guardian upgraded to Level " + guardianLevel + "!"), false);

        return true;
    }

    private boolean hasRequiredItems(Player player, ItemStack required) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameComponents(stack, required)) {
                count += stack.getCount();
                if (count >= required.getCount()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void consumeItems(Player player, ItemStack required) {
        int remaining = required.getCount();
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameComponents(stack, required)) {
                int toRemove = Math.min(remaining, stack.getCount());
                stack.shrink(toRemove);
                remaining -= toRemove;
            }
        }
    }

    private void applyLevelBonuses(int oldLevel, int newLevel) {
        // Level 2: Resistance I permanent
        if (newLevel == 2) {
            this.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 999999, 0, false, false));
        }


        // Level 3: +5 max health
        if (newLevel == 3) {
            var maxHealthAttr = this.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealthAttr != null) {
                double currentMax = maxHealthAttr.getBaseValue();
                maxHealthAttr.setBaseValue(currentMax + 5.0D);
                this.setHealth(this.getHealth() + 5.0F);
            }
        }

        // Level 4: every 5 arrows, fires a faster one. handled by safebowgoal
        if (newLevel == 4) {
            return;
        }

        // Level 5: Is handled by performrangedattack, aplying offhand effects.
        if (newLevel == 5) {
            return;
        }
    }

    private float getRegenAmount() {
        return guardianLevel >= 3 ? 1.5F : 1.0F;
    }

    // ===== STATE MANAGEMENT METHODS =====

    public GuardState getCurrentState() {
        return currentState;
    }

    public void cycleState(Player player) {
        if (stateChangeCooldown > 0) return;

        currentState = switch (currentState) {
            case STAY -> GuardState.FOLLOW;
            case FOLLOW -> GuardState.WANDER_AREA;
            case WANDER_AREA -> GuardState.STAY;
        };

        if (currentState == GuardState.FOLLOW) {
            followingPlayer = player;
        }

        if (currentState == GuardState.STAY) {
            stayPosition = this.blockPosition(); // <-- CORRECT PLACE
        }

        this.getNavigation().stop();

        String stateName = switch (currentState) {
            case STAY -> "§cStay";
            case FOLLOW -> "§aFollow";
            case WANDER_AREA -> "§eWander";
        };

        player.displayClientMessage(
                Component.literal("Guardian mode: " + stateName),
                true
        );

        stateChangeCooldown = 20;
    }


    // ===== AI REGISTRATION =====

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new GuardianStayGoal(1.0D));
        this.goalSelector.addGoal(1, new GuardianFollowGoal(1.0D));
        this.goalSelector.addGoal(1, new GuardianWanderGoal(0.8D));
        this.goalSelector.addGoal(2, new SafeRangedBowAttackGoal<>(this, 1.0D, 15.0F));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2D, false));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new GuardianAttackHostilesGoal());

    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D) // Normal
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    // ===== AI GOAL CLASSES =====

    private class GuardianStayGoal extends Goal {
        private final double speed; // <-- speed configurável

        public GuardianStayGoal(double speed) {
            this.speed = speed;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return currentState == GuardState.STAY && SkeletonGuardian.this.getTarget() == null;
        }

        @Override
        public void start() {
            SkeletonGuardian.this.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (stayPosition == null) return;

            double distSq = SkeletonGuardian.this.distanceToSqr(
                    stayPosition.getX() + 0.5,
                    stayPosition.getY(),
                    stayPosition.getZ() + 0.5
            );

            if (distSq > 0.5D) {
                SkeletonGuardian.this.getNavigation().moveTo(
                        stayPosition.getX() + 0.5,
                        stayPosition.getY(),
                        stayPosition.getZ() + 0.5,
                        speed // <-- usa speed do construtor
                );
            } else {
                SkeletonGuardian.this.getNavigation().stop();
            }
        }
    }


    private class GuardianFollowGoal extends Goal {
        private static final double FOLLOW_DISTANCE = 3.0D;
        private static final double TOO_FAR_DISTANCE = 10.0D;

        private final double speed; // <-- velocidade configurável

        public GuardianFollowGoal(double speed) {
            this.speed = speed;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return currentState == GuardState.FOLLOW &&
                    followingPlayer != null &&
                    followingPlayer.isAlive() &&
                    SkeletonGuardian.this.getTarget() == null;
        }

        @Override
        public boolean canContinueToUse() {
            return currentState == GuardState.FOLLOW &&
                    followingPlayer != null &&
                    followingPlayer.isAlive() &&
                    SkeletonGuardian.this.getTarget() == null;
        }

        @Override
        public void start() {
            SkeletonGuardian.this.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (followingPlayer == null) return;

            double distSq = SkeletonGuardian.this.distanceToSqr(followingPlayer);

            if (distSq > (TOO_FAR_DISTANCE * TOO_FAR_DISTANCE)) {
                SkeletonGuardian.this.teleportTo(
                        followingPlayer.getX(),
                        followingPlayer.getY(),
                        followingPlayer.getZ()
                );
            } else if (distSq > (FOLLOW_DISTANCE * FOLLOW_DISTANCE)) {
                // <-- usa speed parametrizado aqui
                SkeletonGuardian.this.getNavigation().moveTo(followingPlayer, speed);
            } else {
                SkeletonGuardian.this.getNavigation().stop();
            }
        }

        @Override
        public void stop() {
            SkeletonGuardian.this.getNavigation().stop();
        }
    }


    private class GuardianWanderGoal extends Goal {
        private static final double WANDER_RADIUS = 10.0D;
        private final double speed; // <-- velocidade configurável

        public GuardianWanderGoal(double speed) {
            this.speed = speed;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return currentState == GuardState.WANDER_AREA &&
                    SkeletonGuardian.this.getTarget() == null &&
                    SkeletonGuardian.this.getRandom().nextInt(120) == 0;
        }

        @Override
        public void start() {
            double randomX = SkeletonGuardian.this.getX() + (SkeletonGuardian.this.getRandom().nextDouble() * 2 - 1) * WANDER_RADIUS;
            double randomY = SkeletonGuardian.this.getY();
            double randomZ = SkeletonGuardian.this.getZ() + (SkeletonGuardian.this.getRandom().nextDouble() * 2 - 1) * WANDER_RADIUS;

            // <-- usa speed parametrizado aqui
            SkeletonGuardian.this.getNavigation().moveTo(randomX, randomY, randomZ, speed);
        }
    }


    private class GuardianHurtByTargetGoal extends HurtByTargetGoal {
        public GuardianHurtByTargetGoal() {
            super(SkeletonGuardian.this);
        }

        @Override
        public boolean canUse() {
            LivingEntity attacker = SkeletonGuardian.this.getLastHurtByMob();
            if (attacker == null) return false;
            if (attacker instanceof SkeletonGuardian) return false;

            return super.canUse();
        }

        @Override
        protected void alertOther(Mob mob, LivingEntity target) {
            if (target != null && isHostileMob(target)) {
                super.alertOther(mob, target);
            }
        }
    }

    private class GuardianAttackHostilesGoal extends NearestAttackableTargetGoal<LivingEntity> {
        private static final double TARGET_RANGE = 10.0D;

        public GuardianAttackHostilesGoal() {
            super(SkeletonGuardian.this, LivingEntity.class, 10, true, false,
                    entity -> {
                        if (!(entity instanceof Mob mob)) return false;
                        if (entity instanceof SkeletonGuardian) return false;
                        if (entity instanceof ZombieGuardian) return false;
                        if (entity instanceof Creeper) return false;
                        if (!isHostileMob(mob)) return false;
                        if (!entity.isAlive()) return false;

                        double distSq = SkeletonGuardian.this.distanceToSqr(entity);
                        return distSq <= (TARGET_RANGE * TARGET_RANGE);
                    });
        }

        @Override
        public boolean canUse() {
            LivingEntity currentTarget = SkeletonGuardian.this.getTarget();
            if (currentTarget == null || !currentTarget.isAlive()) {
                return super.canUse();
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity currentTarget = SkeletonGuardian.this.getTarget();

            if (currentTarget == null || !currentTarget.isAlive()) {
                return false;
            }

            double distSq = SkeletonGuardian.this.distanceToSqr(currentTarget);
            if (distSq > (TARGET_RANGE * TARGET_RANGE)) {
                return false;
            }

            return super.canContinueToUse();
        }
        @Override
        public void tick() {
            LivingEntity target = SkeletonGuardian.this.getTarget();

            // Remove target morto
            if (target == null || !target.isAlive()) {
                SkeletonGuardian.this.setTarget(null);
                SkeletonGuardian.this.setLastHurtByMob(null);
                return;
            }

            super.tick();
        }

    }

    private boolean isHostileMob(LivingEntity target) {
        if (target instanceof Player ||
                target instanceof IronGolem ||
                target instanceof SnowGolem ||
                target instanceof Wolf ||
                target instanceof Villager ||
                target instanceof Creeper ||
                target instanceof SkeletonGuardian ||
                target instanceof ZombieGuardian) {
            return false;
        }

        return target instanceof Monster ||
                target instanceof Enemy ||
                target instanceof AbstractPiglin;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (target == null) return false;
        if (target instanceof SkeletonGuardian || target instanceof ZombieGuardian || target instanceof Creeper) return false;

        boolean isDefending = this.getLastHurtByMob() == target;

        if (target instanceof Player ||
                target instanceof IronGolem ||
                target instanceof SnowGolem ||
                target instanceof Wolf ||
                target instanceof Villager) {
            return isDefending;
        }

        return isHostileMob(target);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);

        // Limpa qualquer target morto imediatamente
        LivingEntity target = this.getTarget();
        if (target != null && !target.isAlive()) {
            this.setTarget(null);
        }

        LivingEntity last = this.getLastHurtByMob();
        if (last != null && !last.isAlive()) {
            this.setLastHurtByMob(null);
        }

        return result;
    }

    // ===== TICK & ABILITIES =====

    @Override
    public void aiStep() {
        super.aiStep();

        if (stateChangeCooldown > 0) {
            stateChangeCooldown--;
        }

        // Clear dead target
        LivingEntity target = this.getTarget();
        if (target != null && !target.isAlive()) {
            this.setTarget(null);
        }

        // Clear dead lastHurtByMob
        LivingEntity last = this.getLastHurtByMob();
        if (last != null && !last.isAlive()) {
            this.setLastHurtByMob(null);
        }

        // ---- COMBAT TRANSITION LOGIC ----
        boolean inCombat = this.getTarget() != null;

        if (wasInCombat && !inCombat) {
            // combat just ended
            if (currentState == GuardState.STAY && stayPosition != null) {
                this.getNavigation().moveTo(
                        stayPosition.getX() + 0.5,
                        stayPosition.getY(),
                        stayPosition.getZ() + 0.5,
                        1.0D
                );
            }
        }

        wasInCombat = inCombat;
        // --------------------------------

        handleRegeneration();
        findNearbyHostileTarget(); // optional
    }

    @Override
    protected boolean isSunBurnTick() {
        return false;
    }

    private void findNearbyHostileTarget() {
        double range = 10.0D;
        AABB searchBox = this.getBoundingBox().inflate(range);
        List<LivingEntity> nearbyMobs = this.level().getEntitiesOfClass(
                LivingEntity.class,
                searchBox,
                entity -> entity instanceof Mob mob &&
                        isHostileMob(mob) &&
                        !(entity instanceof SkeletonGuardian) &&
                        entity.isAlive() &&
                        this.distanceToSqr(entity) <= (range * range)
        );

        if (!nearbyMobs.isEmpty()) {
            LivingEntity closest = null;
            double closestDist = Double.MAX_VALUE;

            for (LivingEntity mob : nearbyMobs) {
                double dist = this.distanceToSqr(mob);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = mob;
                }
            }

            if (closest != null) {
                this.setTarget(closest);
            }
        }
    }

    private void handleRegeneration() {
        boolean inCombat = this.getTarget() != null;

        if (inCombat != wasInCombat) {
            wasInCombat = inCombat;
            if (!inCombat) {
                idleRegenTickCounter = 0;
            } else {
                combatRegenTickCounter = 0;
            }
        }

        if (inCombat) {
            combatRegenTickCounter++;
            // Em combate: 1 HP a cada 10s antes do lvl 3, 2 HP a cada 5s no lvl 3+
            int combatIntervalTicks = (guardianLevel >= 3) ? 100 : 200; // 20 ticks por segundo
            float combatHeal = (guardianLevel >= 3) ? 2.0F : 1.0F;

            if (combatRegenTickCounter >= combatIntervalTicks) {
                healSafely(combatHeal);
                combatRegenTickCounter = 0;
            }
        } else {
            idleRegenTickCounter++;
            // Fora de combate: 1 HP a cada 5s antes do lvl 3, 2 HP a cada 5s no lvl 3+
            int idleIntervalTicks = (guardianLevel >= 3) ? 100 : 100;
            float idleHeal = (guardianLevel >= 3) ? 2.0F : 1.0F;

            if (idleRegenTickCounter >= idleIntervalTicks) {
                healSafely(idleHeal);
                idleRegenTickCounter = 0;
            }
        }
    }


    private void healSafely(float amount) {
        this.setHealth(Math.min(this.getHealth() + amount, this.getMaxHealth()));
    }



    // ===== INTERACTION =====

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);

        if (!this.level().isClientSide()) {
            // Check if player is holding an Empty Soul Cage
            if (itemInHand.getItem() instanceof com.hellwaves.hellwavesmod.Items.EmptySoulCageItem) {
                return com.hellwaves.hellwavesmod.Items.EmptySoulCageItem.captureGuardian(itemInHand, player, this);
            }

            if (player.isShiftKeyDown()) {
                // SHIFT + RIGHT-CLICK: Open inventory
                player.openMenu(new net.minecraft.world.MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return Component.literal("Guardian Inventory");
                    }

                    @Override
                    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory playerInventory, Player player) {
                        return new GuardianInventoryMenu(containerId, playerInventory, getGuardianInventory());
                    }
                }, buf -> buf.writeInt(SkeletonGuardian.this.getId()));

                return InteractionResult.SUCCESS;
            } else {
                // NORMAL RIGHT-CLICK: Cycle state
                cycleState(player);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide());
    }

    // ===== BOW HANDLING =====

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        if (target == null || !target.isAlive()) return;

        ItemStack bowStack = this.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW));
        if (bowStack.isEmpty()) return;

        ItemStack arrowStack;

        if (guardianLevel >= 5) {
            // Nível 5: usar flecha da offhand se houver
            ItemStack offhand = this.getItemBySlot(EquipmentSlot.OFFHAND);
            if (offhand.getItem() instanceof ArrowItem) {
                arrowStack = offhand;
            } else {
                arrowStack = new ItemStack(Items.ARROW); // fallback: flecha normal
            }
        } else {
            // Níveis 1-4: sempre flecha normal sem efeitos
            arrowStack = new ItemStack(Items.ARROW);
        }

        // Cria a flecha propriamente dita usando o método oficial
        AbstractArrow arrow = ((ArrowItem) arrowStack.getItem()).createArrow(this.level(), arrowStack, this, bowStack);

        // Disparo
        double dx = target.getX() - this.getX();
        double dy = target.getY(0.3333D) - arrow.getY();
        double dz = target.getZ() - this.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        arrow.shoot(dx, dy + distance * 0.2D, dz, 1.6F, (float)(14 - this.level().getDifficulty().getId() * 4));

        this.playSound(SoundEvents.SKELETON_SHOOT,
                1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));

        this.level().addFreshEntity(arrow);

        // Level 4: fast 5th arrow
        if (guardianLevel >= 4) arrowsFired++;
    }





    // ===== SOUNDS =====

    @Override
    protected SoundEvent getAmbientSound() {
        return null; // Silent ambient sounds
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SKELETON_DEATH;
    }

    @Override
    protected SoundEvent getStepSound() {
        return null; // Silent steps
    }

    // ===== NBT PERSISTENCE =====

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("GuardState", currentState.ordinal());
        if (stayPosition != null) {
            compound.putLong("StayPos", stayPosition.asLong());
        }
        compound.putInt("StateChangeCooldown", stateChangeCooldown);
        compound.putInt("RegenTimer", regenTimer);
        compound.putInt("GuardianLevel", guardianLevel);
        compound.putBoolean("WasInCombat", wasInCombat);
        if (followingPlayer != null) {
            compound.putUUID("FollowingPlayer", followingPlayer.getUUID());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("GuardState")) {
            currentState = GuardState.values()[compound.getInt("GuardState")];
        }

        if (compound.contains("StayPos")) {
            stayPosition = BlockPos.of(compound.getLong("StayPos"));
        }

        if (compound.contains("StateChangeCooldown")) {
            stateChangeCooldown = compound.getInt("StateChangeCooldown");
        }
        if (compound.contains("RegenTimer")) {
            regenTimer = compound.getInt("RegenTimer");
        }
        if (compound.contains("GuardianLevel")) {
            int savedLevel = compound.getInt("GuardianLevel");
            this.guardianLevel = savedLevel;
            // Reapply effects for level 4 and 5
            if (savedLevel >= 2) {
                this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 999999, 0, false, false));
            }
            if (savedLevel >= 4) {
                return;
            }
            if (savedLevel >= 5) {
                return;
            }
        }
        if (compound.contains("WasInCombat")) {
            wasInCombat = compound.getBoolean("WasInCombat");
        }
        if (compound.contains("FollowingPlayer")) {
            if (this.level() instanceof ServerLevel serverLevel) {
                Entity entity = serverLevel.getEntity(compound.getUUID("FollowingPlayer"));
                if (entity instanceof Player p) {
                    this.followingPlayer = p;
                }
            }
        }
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        spawnGroupData = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);

        // Only clear equipment when first spawned, NOT when restoring from cage
        if (!restoringFromCage) {
            this.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            this.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            this.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            this.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }

        return spawnGroupData;
    }

    @Override
    public void onEquipItem(EquipmentSlot slot, ItemStack oldItem, ItemStack newItem) {
        // Completely bypass AbstractSkeleton weapon logic
        // DO NOT call super
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);

        // Drop all equipped items
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = this.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                this.spawnAtLocation(stack);
                this.setItemSlot(slot, ItemStack.EMPTY);
            }
        }

        // Drop inventory items
        GuardianInventory inventory = getGuardianInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                this.spawnAtLocation(stack);
            }
        }
    }

    public class SafeRangedBowAttackGoal<T extends Monster & RangedAttackMob> extends Goal {

        private final T mob;
        private int attackCooldown = 0;
        private int pullingTicks = 0;
        private final double speedModifier;
        private final float attackRadius;

        private static final int NORMAL_DRAW_TIME = 20;
        private static final int FAST_DRAW_TIME = 5;

        private int strafeTime = 0;
        private boolean strafeLeft = true;

        public SafeRangedBowAttackGoal(T mob, double speedModifier, float attackRadius) {
            this.mob = mob;
            this.speedModifier = speedModifier;
            this.attackRadius = attackRadius;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = mob.getTarget();
            return target != null && target.isAlive() && mob.getMainHandItem().getItem() instanceof BowItem;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = mob.getTarget();
            return target != null && target.isAlive() && mob.getMainHandItem().getItem() instanceof BowItem;
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null || !target.isAlive()) {
                // Stop using bow if target is gone or dead
                if (mob.isUsingItem()) {
                    pullingTicks = 0;
                    mob.stopUsingItem();
                    mob.setAggressive(false);
                }
                return;
            }

            double distanceSq = mob.distanceToSqr(target);
            double attackRadiusSq = attackRadius * attackRadius;

            // Look at target (head + body)
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

            // Initialize strafing vars
            if (!mob.getPersistentData().contains("strafeDir")) mob.getPersistentData().putInt("strafeDir", 1);
            if (!mob.getPersistentData().contains("strafeTime")) mob.getPersistentData().putInt("strafeTime", 0);

            int strafeDir = mob.getPersistentData().getInt("strafeDir");
            int strafeTime = mob.getPersistentData().getInt("strafeTime") + 1;

            if (strafeTime >= 20) {
                if (mob.getRandom().nextFloat() < 0.3F) strafeDir = -strafeDir;
                strafeTime = 0;
            }

            mob.getPersistentData().putInt("strafeDir", strafeDir);
            mob.getPersistentData().putInt("strafeTime", strafeTime);

            // Move toward/away to stay inside attack range
            if (distanceSq > attackRadiusSq) {
                mob.getNavigation().moveTo(target, speedModifier * 0.5);
            } else if (distanceSq < attackRadiusSq * 0.75) {
                Vec3 away = new Vec3(mob.getX() - target.getX(), 0, mob.getZ() - target.getZ()).normalize().scale(0.1);
                mob.setDeltaMovement(away.x, mob.getDeltaMovement().y, away.z);
                mob.getNavigation().stop();
            } else {
                mob.getNavigation().stop();
            }

            // Lateral strafing
            if (distanceSq <= attackRadiusSq) {
                Vec3 toTarget = new Vec3(target.getX() - mob.getX(), 0, target.getZ() - mob.getZ()).normalize();
                Vec3 strafeVec = new Vec3(-toTarget.z, 0, toTarget.x).scale(0.05 * strafeDir);
                double newDistSq = mob.distanceToSqr(target.getX() + strafeVec.x, mob.getY(), target.getZ() + strafeVec.z);
                if (newDistSq <= attackRadiusSq) {
                    mob.setDeltaMovement(mob.getDeltaMovement().add(strafeVec));
                }
            }

            // Attack cooldown
            if (attackCooldown > 0) {
                attackCooldown--;
                return;
            }

            // Start using bow if not already
            if (!mob.isUsingItem()) {
                mob.startUsingItem(InteractionHand.MAIN_HAND);
                mob.setAggressive(true);
                pullingTicks = 0;
            }

            pullingTicks++;

            // === 5th-arrow FAST LOGIC ===
            int drawTime = NORMAL_DRAW_TIME;
            if (mob instanceof SkeletonGuardian guardian &&
                    guardian.getGuardianLevel() >= 4 &&
                    guardian.arrowsFired % 5 == 4) {
                drawTime = FAST_DRAW_TIME;
            }

            // Only shoot if target is still alive
            if (pullingTicks >= drawTime && target.isAlive()) {
                mob.performRangedAttack(target, 1.6F);

                if (mob instanceof SkeletonGuardian guardian) {
                    guardian.arrowsFired++;
                }

                pullingTicks = 0;
                mob.stopUsingItem();
                mob.setAggressive(false);
                attackCooldown = 0;
            }
        }

        @Override
        public void stop() {
            pullingTicks = 0;
            mob.stopUsingItem();
            mob.setAggressive(false);
        }
    }

}