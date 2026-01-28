package com.hellwaves.hellwavesmod.HWMobs;

import com.hellwaves.hellwavesmod.inventory.GuardianInventory;
import com.hellwaves.hellwavesmod.inventory.GuardianInventoryMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

public class ZombieGuardian extends Zombie implements RangedAttackMob {
    // Estados do mob
    public enum GuardState {
        STAY,       // Fica parado no lugar
        FOLLOW,     // Segue o jogador
        WANDER_AREA // Vaga pela área
    }

    private GuardState currentState = GuardState.STAY; // Start in STAY mode
    private Player followingPlayer = null;
    private int stateChangeCooldown = 0;
    private GuardianInventory guardianInventory;
    private boolean restoringFromCage = false;

    public void setRestoringFromCage(boolean restoring) {
        this.restoringFromCage = restoring;
    }

    // Leveling System
    private int guardianLevel = 1;
    private static final int MAX_LEVEL = 5;

    // Regeneração natural
    private static final int BASE_REGEN_INTERVAL = 100; // 5 segundos (20 ticks * 5)
    private int regenTimer = 0;
    private int outOfCombatRegenTimer = 0;
    private int inCombatRegenTimer = 0;

    // AOE Ability (Level 5)
    private static final int AOE_COOLDOWN = 200; // 7.5 segundos (20 ticks * 7.5)
    private static final float AOE_RADIUS = 1.5F;
    private static final float AOE_DAMAGE = 3.0F;
    private int aoeCooldownTimer = 0;
    private boolean wasInCombat = false;

    public ZombieGuardian(EntityType<? extends Zombie> entityType, Level level) {
        super(entityType, level);
        this.setCustomName(Component.literal("§2Zombie Guardian§r"));
        this.setCustomNameVisible(true);
        this.xpReward = 50;
    }

    public GuardianInventory getGuardianInventory() {
        if (guardianInventory == null) {
            guardianInventory = new GuardianInventory(this);
        }
        return guardianInventory;
    }

    // ===== LEVELING SYSTEM METHODS =====

    public int getGuardianLevel() {
        return guardianLevel;
    }

    public void setGuardianLevel(int level) {
        if (level >= 1 && level <= MAX_LEVEL) {
            int oldLevel = this.guardianLevel;
            this.guardianLevel = level;

            // Only apply level bonuses if NOT restoring from Soul Cage
            if (!restoringFromCage && !this.level().isClientSide()) {
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
            case 1 -> new ItemStack(Items.BOOK, 5);
            case 2 -> new ItemStack(Items.DIAMOND_BLOCK, 1);
            case 3 -> new ItemStack(Items.NETHERITE_SCRAP, 1);
            case 4 -> new ItemStack(Items.NETHER_STAR, 1);
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
                Component.literal("§aZombie Guardian upgraded to Level " + guardianLevel + "!"), false);

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
        if (newLevel == 3) {
            var maxHealthAttr = this.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealthAttr != null) {
                double currentMax = maxHealthAttr.getBaseValue();
                maxHealthAttr.setBaseValue(currentMax + 10.0D);
                this.setHealth(40.0F);
            }
        }

        if (newLevel == 4) {
            this.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 999999, 0, false, false));
        }
    }

    private float getRegenAmount() {
        return guardianLevel >= 2 ? 2.0F : 1.0F;
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

        this.getNavigation().stop();

        String stateName = switch (currentState) {
            case STAY -> "§cStay";
            case FOLLOW -> "§aFollow";
            case WANDER_AREA -> "§eWander";
        };

        player.displayClientMessage(Component.literal("Guardian mode: " + stateName), true);

        stateChangeCooldown = 20;
    }

    // ===== AI REGISTRATION =====

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new GuardianStayGoal());
        this.goalSelector.addGoal(1, new GuardianFollowGoal());
        this.goalSelector.addGoal(1, new GuardianWanderGoal());
        this.goalSelector.addGoal(2, new GuardianRangedBowAttackGoal<>(this, 1.0D, 20, 15.0F));
        this.goalSelector.addGoal(3, new GuardianMeleeAttackGoal());
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new GuardianHurtByTargetGoal());
        this.targetSelector.addGoal(2, new GuardianAttackHostilesGoal());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.18D) // Slower than default (0.25D -> 0.18D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0D); // Required for proper entity loading
    }

    @Override
    public boolean isSunBurnTick() {
        return false; // Guardian Zombies are immune to sunlight
    }

    // ===== TICK & ABILITIES =====

    @Override
    public void aiStep() {
        super.aiStep();

        if (!this.level().isClientSide()) {
            if (stateChangeCooldown > 0) {
                stateChangeCooldown--;
            }
            if (aoeCooldownTimer > 0) {
                aoeCooldownTimer--;
            }

            handleRegeneration();

            if (guardianLevel >= 5) {
                handleAOEAbility();
            }

            updateWeapon();
        }
    }

    private void handleRegeneration() {
        boolean inCombat = this.getTarget() != null;

        if (inCombat != wasInCombat) {
            wasInCombat = inCombat;
            if (!inCombat) {
                outOfCombatRegenTimer = 0;
            } else {
                inCombatRegenTimer = 0;
            }
        }

        if (inCombat) {
            inCombatRegenTimer++;
            if (inCombatRegenTimer >= BASE_REGEN_INTERVAL * 4) {
                if (this.getHealth() < this.getMaxHealth()) {
                    this.heal(getRegenAmount());
                }
                inCombatRegenTimer = 0;
            }
        } else {
            outOfCombatRegenTimer++;
            if (outOfCombatRegenTimer >= BASE_REGEN_INTERVAL) {
                if (this.getHealth() < this.getMaxHealth()) {
                    this.heal(getRegenAmount());
                }
                outOfCombatRegenTimer = 0;
            }
        }
    }

    private void handleAOEAbility() {
        if (this.getTarget() == null || aoeCooldownTimer > 0) return;

        AABB searchBox = this.getBoundingBox().inflate(AOE_RADIUS);
        List<LivingEntity> nearbyMobs = this.level().getEntitiesOfClass(
                LivingEntity.class,
                searchBox,
                entity -> entity != this &&
                        entity instanceof Mob mob &&
                        isHostileMob(mob) &&
                        !(entity instanceof ZombieGuardian) &&
                        entity.isAlive()
        );

        if (!nearbyMobs.isEmpty()) {
            for (LivingEntity mob : nearbyMobs) {
                mob.hurt(this.damageSources().mobAttack(this), AOE_DAMAGE);

                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(
                            net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                            mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(),
                            3, 0.5D, 0.5D, 0.5D, 0.0D
                    );
                }
            }

            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, this.getSoundSource(), 1.0F, 1.0F);

            aoeCooldownTimer = AOE_COOLDOWN;
        }
    }

    private void updateWeapon() {
        // Weapon handling is done through the AI goals
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
                        return new GuardianInventoryMenu(containerId, playerInventory, ZombieGuardian.this);
                    }
                }, buf -> buf.writeInt(ZombieGuardian.this.getId()));

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

    public boolean isHoldingBow() {
        ItemStack mainHand = this.getItemBySlot(EquipmentSlot.MAINHAND);
        return mainHand.getItem() instanceof BowItem;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        ItemStack arrowItem = new ItemStack(Items.ARROW);
        Arrow arrow = new Arrow(this.level(), this, arrowItem, null);

        double deltaX = target.getX() - this.getX();
        double deltaY = target.getY(0.3333333333333333D) - arrow.getY();
        double deltaZ = target.getZ() - this.getZ();
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        arrow.shoot(deltaX, deltaY + horizontalDistance * 0.20000000298023224D, deltaZ, 1.6F, 14.0F);

        if (guardianLevel >= 2) {
            arrow.setBaseDamage(arrow.getBaseDamage() + (guardianLevel - 1) * 0.5D);
        }

        this.level().addFreshEntity(arrow);
        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    // ===== HOSTILE MOB DETECTION =====

    public boolean isHostileMob(LivingEntity entity) {
        if (entity == null || entity == this) return false;
        if (entity instanceof ZombieGuardian) return false;

        // Include all illagers (Vindicator, Evoker, Pillager, Illusioner, etc.)
        return entity instanceof Monster ||
                entity instanceof Slime ||
                entity instanceof Ghast ||
                entity instanceof AbstractPiglin ||
                entity instanceof AbstractIllager ||  // This covers all illager types
                entity instanceof Vex;  // Vexes summoned by Evokers
    }

    // ===== CUSTOM AI GOALS =====

    private class GuardianRangedBowAttackGoal<T extends Monster & RangedAttackMob> extends RangedBowAttackGoal<T> {
        public GuardianRangedBowAttackGoal(T mob, double speedModifier, int attackInterval, float attackRadius) {
            super(mob, speedModifier, attackInterval, attackRadius);
        }

        @Override
        public boolean canUse() {
            LivingEntity target = ZombieGuardian.this.getTarget();
            return ZombieGuardian.this.isHoldingBow() &&
                    target != null &&
                    isHostileMob(target) &&
                    super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = ZombieGuardian.this.getTarget();
            return ZombieGuardian.this.isHoldingBow() &&
                    target != null &&
                    isHostileMob(target) &&
                    super.canContinueToUse();
        }
    }

    private class GuardianMeleeAttackGoal extends MeleeAttackGoal {
        public GuardianMeleeAttackGoal() {
            super(ZombieGuardian.this, 1.2D, false);
        }

        @Override
        public boolean canUse() {
            LivingEntity target = ZombieGuardian.this.getTarget();
            return !ZombieGuardian.this.isHoldingBow() &&
                    target != null &&
                    isHostileMob(target) &&
                    super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = ZombieGuardian.this.getTarget();
            return !ZombieGuardian.this.isHoldingBow() &&
                    target != null &&
                    isHostileMob(target) &&
                    super.canContinueToUse();
        }
    }

    private class GuardianFollowGoal extends Goal {
        public GuardianFollowGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return currentState == GuardState.FOLLOW &&
                    followingPlayer != null &&
                    followingPlayer.isAlive() &&
                    ZombieGuardian.this.getTarget() == null;
        }

        @Override
        public boolean canContinueToUse() {
            return currentState == GuardState.FOLLOW &&
                    followingPlayer != null &&
                    followingPlayer.isAlive() &&
                    ZombieGuardian.this.getTarget() == null &&
                    ZombieGuardian.this.distanceToSqr(followingPlayer) > 4.0D;
        }

        @Override
        public void tick() {
            if (followingPlayer != null && followingPlayer.isAlive()) {
                ZombieGuardian.this.getNavigation().moveTo(followingPlayer, 1.2D);
                ZombieGuardian.this.getLookControl().setLookAt(followingPlayer, 10.0F, 5.0F);
            }
        }
    }

    private class GuardianStayGoal extends Goal {
        public GuardianStayGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return currentState == GuardState.STAY &&
                    ZombieGuardian.this.getTarget() == null;
        }

        @Override
        public boolean canContinueToUse() {
            return currentState == GuardState.STAY &&
                    ZombieGuardian.this.getTarget() == null;
        }

        @Override
        public void start() {
            ZombieGuardian.this.getNavigation().stop();
        }
    }

    private class GuardianWanderGoal extends WaterAvoidingRandomStrollGoal {
        public GuardianWanderGoal() {
            super(ZombieGuardian.this, 1.0D);
        }

        @Override
        public boolean canUse() {
            return currentState == GuardState.WANDER_AREA &&
                    ZombieGuardian.this.getTarget() == null &&
                    super.canUse();
        }
    }

    private class GuardianHurtByTargetGoal extends HurtByTargetGoal {
        public GuardianHurtByTargetGoal() {
            super(ZombieGuardian.this);
        }

        @Override
        public boolean canUse() {
            LivingEntity attacker = ZombieGuardian.this.getLastHurtByMob();
            return attacker != null && isHostileMob(attacker) && super.canUse();
        }

        @Override
        protected void alertOther(Mob mob, LivingEntity target) {
            if (target != null && isHostileMob(target)) {
                super.alertOther(mob, target);
            }
        }
    }

    private class GuardianAttackHostilesGoal extends NearestAttackableTargetGoal<LivingEntity> {
        private int retargetTimer = 0;
        private static final int RETARGET_INTERVAL = 20; // Check every second (20 ticks)
        private static final double TARGET_RANGE = 10.0D; // 10 block range

        public GuardianAttackHostilesGoal() {
            super(ZombieGuardian.this, LivingEntity.class, 10, true, false,
                    entity -> entity instanceof Mob mob &&
                            isHostileMob(mob) &&
                            !(entity instanceof ZombieGuardian) &&
                            ZombieGuardian.this.distanceToSqr(entity) <= (TARGET_RANGE * TARGET_RANGE));
        }

        @Override
        public boolean canUse() {
            // Always try to find targets if we don't have one
            if (ZombieGuardian.this.getTarget() == null || !ZombieGuardian.this.getTarget().isAlive()) {
                return super.canUse();
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity currentTarget = ZombieGuardian.this.getTarget();

            // Stop if target is dead or too far
            if (currentTarget == null || !currentTarget.isAlive()) {
                return false;
            }

            // Stop if target is out of range
            if (ZombieGuardian.this.distanceToSqr(currentTarget) > (TARGET_RANGE * TARGET_RANGE)) {
                return false;
            }

            return super.canContinueToUse();
        }

        @Override
        public void tick() {
            super.tick();
            retargetTimer++;

            // Periodically search for new targets even while we have one
            if (retargetTimer >= RETARGET_INTERVAL) {
                retargetTimer = 0;

                LivingEntity currentTarget = ZombieGuardian.this.getTarget();

                // If current target is dead or invalid, find a new one immediately
                if (currentTarget == null || !currentTarget.isAlive()) {
                    this.findTarget();
                }
                // If current target is out of range, find closer target
                else if (ZombieGuardian.this.distanceToSqr(currentTarget) > (TARGET_RANGE * TARGET_RANGE)) {
                    this.findTarget();
                }
            }
        }

        @Override
        public void start() {
            super.start();
            retargetTimer = 0;
        }

        @Override
        public void stop() {
            // DON'T clear the target here - let the goal system handle retargeting
            // Clearing it prevents finding new targets
            retargetTimer = 0;
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (target == null) return false;
        if (target instanceof ZombieGuardian) return false;

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
        if (source.getEntity() instanceof ZombieGuardian) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.ZOMBIE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_DEATH;
    }

    @Override
    protected SoundEvent getStepSound() {
        return SoundEvents.ZOMBIE_STEP;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("GuardState", currentState.ordinal());
        compound.putInt("StateChangeCooldown", stateChangeCooldown);
        compound.putInt("RegenTimer", regenTimer);
        compound.putInt("GuardianLevel", guardianLevel);
        compound.putInt("AOECooldownTimer", aoeCooldownTimer);
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
        if (compound.contains("StateChangeCooldown")) {
            stateChangeCooldown = compound.getInt("StateChangeCooldown");
        }
        if (compound.contains("RegenTimer")) {
            regenTimer = compound.getInt("RegenTimer");
        }
        if (compound.contains("GuardianLevel")) {
            int savedLevel = compound.getInt("GuardianLevel");
            this.guardianLevel = savedLevel;
            if (savedLevel >= 4) {
                this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 999999, 0, false, false));
            }
        }
        if (compound.contains("AOECooldownTimer")) {
            aoeCooldownTimer = compound.getInt("AOECooldownTimer");
        }
        if (compound.contains("WasInCombat")) {
            wasInCombat = compound.getBoolean("WasInCombat");
        }
        if (compound.contains("FollowingPlayer")) {
            if (this.level() instanceof ServerLevel serverLevel) {
                Entity entity = serverLevel.getEntity(compound.getUUID("FollowingPlayer"));
                if (entity instanceof Player player) {
                    this.followingPlayer = player;
                }
            }
        }
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        spawnGroupData = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        this.setBaby(false);

        // Only clear equipment if NOT restoring from a Soul Cage
        if (!restoringFromCage) {
            this.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            this.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            this.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            this.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        } else {
            // Reset the flag so it doesn't affect future spawns
            restoringFromCage = false;
        }

        return spawnGroupData;
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
}