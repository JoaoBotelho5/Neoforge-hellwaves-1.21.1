package com.hellwaves.hellwavesmod.HWMobs;

import com.hellwaves.hellwavesmod.inventory.GuardianInventory;
import com.hellwaves.hellwavesmod.inventory.GuardianInventoryMenu;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

public class ZombieGuardian extends Zombie implements RangedAttackMob, IGuardian {
    // Estados do mob
    public enum GuardState {
        STAY,       // Fica parado no lugar
        FOLLOW,     // Segue o jogador
        WANDER_AREA // Vaga pela área
    }
    private BlockPos stayPosition = null;
    private GuardState currentState = GuardState.STAY; // Start in STAY mode
    private Player followingPlayer = null;
    private int stateChangeCooldown = 0;
    private GuardianInventory guardianInventory;

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

    // Sliding control - prevent slide when using bow, allow after kill
    private boolean justKilledMob = false;
    private int slideAllowedTimer = 0;

    // Flag to prevent equipment clearing when restoring from cage
    private boolean restoringFromCage = false;

    public ZombieGuardian(EntityType<? extends Zombie> entityType, Level level) {
        super(entityType, level);
        this.setCustomName(Component.literal("§2Zombie Guardian§r"));
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
            case 1 -> new ItemStack(Items.BOOK, 10);
            case 2 -> new ItemStack(Items.DIAMOND_BLOCK, 1);
            case 3 -> new ItemStack(Items.NETHERITE_SCRAP, 1);
            case 4 -> new ItemStack(Items.WITHER_SKELETON_SKULL, 1);
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
        if (newLevel >= 2) {
            // Level 2: max health increase + regen boost
            var maxHealthAttr = this.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealthAttr != null) {
                maxHealthAttr.setBaseValue(40.0D); // Set max HP to 40
                this.setHealth(40.0F);
            }
            // Regen is handled automatically in getRegenAmount():
            // getRegenAmount() returns 2.0F for level >= 2
        }

        if (newLevel >= 3) {
            // Level 3: Damage Resistance I
            this.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 999999, 0, false, false));
        }

        if (newLevel >= 4) {
            // Level 4: Shield blocking (already handled in hurt())
        }
        if (newLevel >= 5) {
            // Level 5: handled in
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
        if (currentState == ZombieGuardian.GuardState.STAY) {
            stayPosition = this.blockPosition(); // <-- CORRECT PLACE
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
        this.goalSelector.addGoal(1, new GuardianStayGoal(1.1D));
        this.goalSelector.addGoal(1, new GuardianFollowGoal(1.1D));
        this.goalSelector.addGoal(1, new GuardianWanderGoal(1.1D));
        this.goalSelector.addGoal(2, new GuardianRangedBowAttackGoal<>(this, 1.0D, 20, 15.0F));
        this.goalSelector.addGoal(3, new GuardianMeleeAttackGoal());
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new GuardianAttackHostilesGoal());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.20D) // Slower than default (0.25D -> 0.18D)
                .add(Attributes.ATTACK_DAMAGE, 4.00)
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
            if (currentState == ZombieGuardian.GuardState.STAY && stayPosition != null) {
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

        if (guardianLevel >= 5) {
            handleAOEAbility();
        }
    }

    private void findNearbyHostileTarget() {
        // Não sobrescrever o target se já houver player ou guardian alvo
        if (this.getTarget() instanceof Player || this.getTarget() instanceof ZombieGuardian) return;

        double range = 10.0D;
        List<LivingEntity> nearbyMobs = this.level().getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(range),
                entity -> entity instanceof Mob mob &&
                        isHostileMob(mob) &&
                        !(entity instanceof ZombieGuardian) &&
                        !(entity instanceof SkeletonGuardian) &&
                        entity.isAlive() &&
                        this.distanceToSqr(entity) <= (range * range)
        );

        if (!nearbyMobs.isEmpty()) {
            LivingEntity closest = nearbyMobs.stream()
                    .min((a, b) -> Double.compare(this.distanceToSqr(a), this.distanceToSqr(b)))
                    .orElse(null);
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
                        !(entity instanceof SkeletonGuardian) &&
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
                        return new GuardianInventoryMenu(containerId, playerInventory, getGuardianInventory());
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

        this.level().addFreshEntity(arrow);
        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    // ===== HOSTILE MOB DETECTION =====

    public boolean isHostileMob(LivingEntity entity) {
        if (entity == null || entity == this) return false;
        if (entity instanceof ZombieGuardian) return false;
        if (entity instanceof SkeletonGuardian) return false;


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
                    target.isAlive() &&
                    super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = ZombieGuardian.this.getTarget();
            return ZombieGuardian.this.isHoldingBow() &&
                    target != null &&
                    target.isAlive() &&
                    isHostileMob(target) &&
                    super.canContinueToUse();
        }

        @Override
        public void tick() {
            LivingEntity target = ZombieGuardian.this.getTarget();
            if (target == null || !target.isAlive()) {
                ZombieGuardian.this.setTarget(null);
                this.stop();
                return;
            }

            // Prevent sliding when using bow (unless just killed a mob)
            if (!justKilledMob) {
                // Stop navigation when shooting
                if (ZombieGuardian.this.isAggressive()) {
                    ZombieGuardian.this.getNavigation().stop();
                }
            }

            super.tick();
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
        private static final double FOLLOW_DISTANCE = 1.5D;
        private static final double TOO_FAR_DISTANCE = 10.0D;

        private final double speed; // <-- velocidade configurável

        public GuardianFollowGoal(double speed) {
            this.speed = speed;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return currentState == ZombieGuardian.GuardState.FOLLOW &&
                    followingPlayer != null &&
                    followingPlayer.isAlive() &&
                    ZombieGuardian.this.getTarget() == null;
        }

        @Override
        public boolean canContinueToUse() {
            return currentState == ZombieGuardian.GuardState.FOLLOW &&
                    followingPlayer != null &&
                    followingPlayer.isAlive() &&
                    ZombieGuardian.this.getTarget() == null;
        }

        @Override
        public void start() {
            ZombieGuardian.this.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (followingPlayer == null) return;

            double distSq = ZombieGuardian.this.distanceToSqr(followingPlayer);

            if (distSq > (TOO_FAR_DISTANCE * TOO_FAR_DISTANCE)) {
                ZombieGuardian.this.teleportTo(
                        followingPlayer.getX(),
                        followingPlayer.getY(),
                        followingPlayer.getZ()
                );
            } else if (distSq > (FOLLOW_DISTANCE * FOLLOW_DISTANCE)) {
                // <-- usa speed parametrizado aqui
                ZombieGuardian.this.getNavigation().moveTo(followingPlayer, speed);
            } else {
                ZombieGuardian.this.getNavigation().stop();
            }
        }

        @Override
        public void stop() {
            ZombieGuardian.this.getNavigation().stop();
        }
    }

    private class GuardianStayGoal extends Goal {
        private final double speed; // <-- speed configurável

        public GuardianStayGoal(double speed) {
            this.speed = speed;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return currentState == ZombieGuardian.GuardState.STAY && ZombieGuardian.this.getTarget() == null;
        }

        @Override
        public void start() {
            ZombieGuardian.this.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (stayPosition == null) return;

            double distSq = ZombieGuardian.this.distanceToSqr(
                    stayPosition.getX() + 0.5,
                    stayPosition.getY(),
                    stayPosition.getZ() + 0.5
            );

            if (distSq > 0.5D) {
                ZombieGuardian.this.getNavigation().moveTo(
                        stayPosition.getX() + 0.5,
                        stayPosition.getY(),
                        stayPosition.getZ() + 0.5,
                        speed // <-- usa speed do construtor
                );
            } else {
                ZombieGuardian.this.getNavigation().stop();
            }
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
            return currentState == ZombieGuardian.GuardState.WANDER_AREA &&
                    ZombieGuardian.this.getTarget() == null &&
                    ZombieGuardian.this.getRandom().nextInt(120) == 0;
        }

        @Override
        public void start() {
            double randomX = ZombieGuardian.this.getX() + (ZombieGuardian.this.getRandom().nextDouble() * 2 - 1) * WANDER_RADIUS;
            double randomY = ZombieGuardian.this.getY();
            double randomZ = ZombieGuardian.this.getZ() + (ZombieGuardian.this.getRandom().nextDouble() * 2 - 1) * WANDER_RADIUS;

            // <-- usa speed parametrizado aqui
            ZombieGuardian.this.getNavigation().moveTo(randomX, randomY, randomZ, speed);
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
        private static final double TARGET_RANGE = 10.0D;

        public GuardianAttackHostilesGoal() {
            super(ZombieGuardian.this, LivingEntity.class, 10, true, false,
                    entity -> {
                        if (!(entity instanceof Mob mob)) return false;
                        if (entity instanceof ZombieGuardian) return false;
                        if (entity instanceof SkeletonGuardian) return false;
                        if (!isHostileMob(mob)) return false;
                        if (!entity.isAlive()) return false;

                        double distSq = ZombieGuardian.this.distanceToSqr(entity);
                        return distSq <= (TARGET_RANGE * TARGET_RANGE);
                    });
        }

        @Override
        public boolean canUse() {
            // Always try to find a target within range, even while wandering or following
            LivingEntity currentTarget = ZombieGuardian.this.getTarget();

            if (currentTarget == null || !currentTarget.isAlive() || ZombieGuardian.this.distanceToSqr(currentTarget) > TARGET_RANGE * TARGET_RANGE) {
                return super.canUse();
            }

            return true; // Keep using current target
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity currentTarget = ZombieGuardian.this.getTarget();

            if (currentTarget == null || !currentTarget.isAlive()) {
                return false;
            }

            double distSq = ZombieGuardian.this.distanceToSqr(currentTarget);
            if (distSq > TARGET_RANGE * TARGET_RANGE) {
                return false;
            }

            return true; // Continue attacking
        }

        @Override
        public void start() {
            super.start();
            // Optional: remove debug logs for cleaner gameplay
            // System.out.println("[GUARDIAN] Starting attack on: " + ZombieGuardian.this.getTarget());
        }

        @Override
        public void stop() {
            // Don't clear target; goal will naturally re-evaluate next tick
        }
    }


    @Override
    public boolean canAttack(LivingEntity target) {
        if (target == null) return false;
        if (target instanceof ZombieGuardian || target instanceof SkeletonGuardian) return false;

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

        // Level 4: 50% chance to block damage with shield (no durability loss)
        if (guardianLevel >= 4) {
            ItemStack offhandStack = this.getItemBySlot(EquipmentSlot.OFFHAND);
            if (offhandStack.getItem() instanceof ShieldItem) {
                if (this.getRandom().nextFloat() < 0.35F) {
                    // Block the damage - play shield sound
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                            SoundEvents.SHIELD_BLOCK, this.getSoundSource(), 1.0F, 1.0F);
                    return false; // Damage blocked
                }
            }
        }

        boolean result = super.hurt(source, amount);

        // Check if target died from our attack - enable sliding
        LivingEntity target = this.getTarget();
        if (target != null && !target.isAlive()) {
            justKilledMob = true;
            slideAllowedTimer = 20; // 1 seconds
        }

        return result;
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
        if (stayPosition != null) {
            compound.putLong("StayPos", stayPosition.asLong());
        }
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