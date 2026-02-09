package com.hellwaves.hellwavesmod.HWMobs;

import com.hellwaves.hellwavesmod.regivents.HWDeferredRegister;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import javax.annotation.Nullable;

import static com.hellwaves.hellwavesmod.regivents.HWDeferredRegister.GREAT_SWORD;

public class UndeadLord extends Zombie {
    // Custom stats
    private static final double BASE_HEALTH = 100.0D;
    private static final double BASE_DAMAGE = 12.0D;
    private static final double BASE_ARMOR = 12.0D;
    private static final float BASE_SPEED = 0.3F;
    private static final float SCALE = 1.3F; // 1.3x size multiplier

    private boolean hasSpawnedGear = false;

    // Minion spawn tracking
    private static final int MINION_SPAWN_INTERVAL = 300; // 15 seconds (20 ticks * 15)
    private int minionSpawnTimer = 0;
    private int minionsSpawned = 0;

    // Dash ability tracking
    private static final int DASH_COOLDOWN_TICKS = 100; // 5 seconds (20 ticks * 5)
    private static final int DASH_DURATION_TICKS = 15; // 0.75 seconds (1 tick = 1.00s)
    private int dashCooldownTimer = 0;
    private boolean isDashing = false;
    private int dashDurationTimer = 0;

    public UndeadLord(EntityType<? extends Zombie> entityType, Level level) {
        super(entityType, level);
        this.setCustomName(Component.literal("§6Undead Lord§r"));
        this.setCustomNameVisible(true);
        this.xpReward = 1000;
    }

    @Override
    public float getScale() {
        return SCALE;
    }

    // Override para prevenir queima no sol
    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(2, new MoveTowardsTargetGoal(this, 1.0D, 32.0F));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(0, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, BASE_DAMAGE)
                .add(Attributes.ARMOR, BASE_ARMOR)
                .add(Attributes.MOVEMENT_SPEED, BASE_SPEED)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.5D)
                .add(Attributes.FOLLOW_RANGE, 35.0D);
    }

    // Sound overrides
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

    // Override para não ser um zombie normal
    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    public boolean isBaby() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide()) {

            // 1. Auto-target: procura novo alvo se não tiver ou se o atual morreu
            LivingEntity currentTarget = this.getTarget();
            if (currentTarget == null || !currentTarget.isAlive()) {
                Player nearestPlayer = this.level().getNearestPlayer(this, 35.0D);
                if (nearestPlayer != null && nearestPlayer.isAlive()) {
                    this.setTarget(nearestPlayer);
                }
            }

            // 2. Auto-defesa: retaliar quem atacou
            LivingEntity attacker = this.getLastHurtByMob();
            if (attacker != null && attacker.isAlive() && !(attacker instanceof UndeadLord || attacker.getPersistentData().getBoolean("UndeadLordMinion"))) {
                this.setTarget(attacker);
            }

            // 3. Dash e spawn minions (o teu código já existente)
            handleDashAbility();

            if (this.getTarget() != null && this.isAlive()) {
                minionSpawnTimer++;

                if (minionSpawnTimer >= MINION_SPAWN_INTERVAL) {
                    spawnMinions();
                    minionSpawnTimer = 0;
                    minionsSpawned++;
                }
            }
        }
    }


    private void handleDashAbility() {
        if (!this.level().isClientSide()) {
            // Update dash cooldown
            if (dashCooldownTimer > 0) {
                dashCooldownTimer--;
            }

            // Handle active dash
            if (isDashing) {
                dashDurationTimer--;

                if (dashDurationTimer <= 0) {
                    // End dash
                    isDashing = false;
                    // Remove speed effect
                    this.removeEffect(MobEffects.MOVEMENT_SPEED);
                }
            }

            // Try to trigger new dash
            if (this.getTarget() != null && this.isAlive() && !isDashing && dashCooldownTimer <= 0) {
                Player target = this.getTarget() instanceof Player ? (Player) this.getTarget() : null;

                if (target != null && this.distanceTo(target) < 20.0F && this.random.nextFloat() < 0.02F) {
                    // Trigger dash
                    triggerDash();
                }
            }
        }
    }

    private void triggerDash() {
        if (!this.level().isClientSide()) {
            isDashing = true;
            dashDurationTimer = DASH_DURATION_TICKS;
            dashCooldownTimer = DASH_COOLDOWN_TICKS;

            // Apply Speed 2 effect for the dash duration
            this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DASH_DURATION_TICKS, 2, false, false));

            // Dash sound effect
            this.level().playSound(null, this.blockPosition(), SoundEvents.ZOMBIE_AMBIENT, this.getSoundSource(), 1.2F, 1.5F);

            // Visual effect - particles
            // You can add particle effects here if desired

            System.out.println("Undead Lord used Dash!");
        }
    }

    private void applySpawnGear() {
        System.out.println("=== APPLYING UNDEAD LORD GEAR ===");

        // Equipar o machado - VAI APARECER porque Zombie mostra itens!
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(HWDeferredRegister.GREAT_SWORD.get()));
        this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(Items.GOLDEN_HELMET));
        this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(Items.NETHERITE_CHESTPLATE));
        this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new net.minecraft.world.item.ItemStack(Items.GOLDEN_LEGGINGS));
        this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(Items.GOLDEN_BOOTS));

        System.out.println("Main hand item: " + this.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND));
        System.out.println("Has greatsword: " +
                this.getItemBySlot(EquipmentSlot.MAINHAND)
                        .is(HWDeferredRegister.GREAT_SWORD.get()));

        // No drop chance for equipment
        this.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0F);
        this.setDropChance(net.minecraft.world.entity.EquipmentSlot.HEAD, 0.0F);
        this.setDropChance(net.minecraft.world.entity.EquipmentSlot.CHEST, 0.0F);
        this.setDropChance(net.minecraft.world.entity.EquipmentSlot.LEGS, 0.0F);
        this.setDropChance(net.minecraft.world.entity.EquipmentSlot.FEET, 0.0F);
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        this.setDropChance(EquipmentSlot.OFFHAND, 0.0F);


    }

    private void spawnMinions() {
        if (!this.level().isClientSide()) {
            Level level = this.level();

            // Spawn 2 Esqueletos Arqueiros
            for (int i = 0; i < 2; i++) {
                Skeleton archerSkeleton = new Skeleton(EntityType.SKELETON, level);
                archerSkeleton.getPersistentData().putBoolean("UndeadLordMinion", true);

                archerSkeleton.setPos(this.getX() + (this.random.nextDouble() - 0.5) * 3,
                        this.getY(),
                        this.getZ() + (this.random.nextDouble() - 0.5) * 3);
                archerSkeleton.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(Items.BOW));
                archerSkeleton.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(Items.LEATHER_HELMET));
                archerSkeleton.setCustomName(Component.literal("§eSkeleton Archer§r"));

                // No equipment drops
                archerSkeleton.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0F);
                archerSkeleton.setDropChance(net.minecraft.world.entity.EquipmentSlot.HEAD, 0.0F);

                archerSkeleton.targetSelector.getAvailableGoals().removeIf(g -> g.getGoal() instanceof HurtByTargetGoal);



                // Make them target the same player
                if (this.getTarget() != null) {
                    archerSkeleton.setTarget(this.getTarget());
                }

                level.addFreshEntity(archerSkeleton);
            }

            // Spawn 1 Zombie com machado
            Zombie axeZombie = new Zombie(EntityType.ZOMBIE, level);
            axeZombie.getPersistentData().putBoolean("UndeadLordMinion", true);

            axeZombie.setPos(this.getX() + (this.random.nextDouble() - 0.5) * 3,
                    this.getY(),
                    this.getZ() + (this.random.nextDouble() - 0.5) * 3);
            axeZombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(Items.IRON_AXE));
            axeZombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(Items.IRON_HELMET));
            axeZombie.setCustomName(Component.literal("§eZombie Guard§r"));

            axeZombie.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0F);
            axeZombie.setDropChance(net.minecraft.world.entity.EquipmentSlot.HEAD, 0.0F);

            axeZombie.targetSelector.getAvailableGoals().removeIf(g -> g.getGoal() instanceof HurtByTargetGoal);

            // Make them target the same player
            if (this.getTarget() != null) {
                axeZombie.setTarget(this.getTarget());
            }

            level.addFreshEntity(axeZombie);

            // Sound and message (only show message for first few spawns to avoid spam)
            this.level().playSound(null, this.blockPosition(), SoundEvents.ZOMBIE_AMBIENT, this.getSoundSource(), 1.5F, 0.8F);

            if (minionsSpawned <= 3) { // Only show message for first 3 spawns
                this.level().players().forEach(player -> {
                    if (player.distanceTo(this) < 20) {
                        player.displayClientMessage(Component.literal("§6The Undead Lord calls for reinforcements!§r"), true);
                    }
                });
            }
        }
    }

    @Override
    public boolean isAlliedTo(net.minecraft.world.entity.Entity other) {
        // Treat Piglin Lord and all minions as allies
        return other instanceof UndeadLord
                || other.getPersistentData().getBoolean("UndeadLordMinion")
                || this.getPersistentData().getBoolean("UndeadLordMinion");
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);

        if (!this.level().isClientSide()) {

            // DROP DA GREATSWORD
            ItemStack greatsword = new ItemStack(GREAT_SWORD.get());
            this.spawnAtLocation(greatsword);

            // Som + mensagem (o que já tinhas)
            this.level().playSound(
                    null,
                    this.blockPosition(),
                    SoundEvents.ZOMBIE_DEATH,
                    this.getSoundSource(),
                    2.0F,
                    1.0F
            );

            this.level().players().forEach(player ->
                    player.displayClientMessage(
                            Component.literal("§6The Undead Lord has been defeated!§r"),
                            true
                    )
            );
        }
    }

    // NBT Data Persistence
    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("MinionSpawnTimer", minionSpawnTimer);
        compound.putInt("MinionsSpawned", minionsSpawned);
        compound.putBoolean("HasSpawnedGear", hasSpawnedGear);
        compound.putInt("DashCooldownTimer", dashCooldownTimer);
        compound.putBoolean("IsDashing", isDashing);
        compound.putInt("DashDurationTimer", dashDurationTimer);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("MinionSpawnTimer")) {
            minionSpawnTimer = compound.getInt("MinionSpawnTimer");
        }
        if (compound.contains("MinionsSpawned")) {
            minionsSpawned = compound.getInt("MinionsSpawned");
        }
        if (compound.contains("HasSpawnedGear")) {
            hasSpawnedGear = compound.getBoolean("HasSpawnedGear");
        }
        if (compound.contains("DashCooldownTimer")) {
            dashCooldownTimer = compound.getInt("DashCooldownTimer");
        }
        if (compound.contains("IsDashing")) {
            isDashing = compound.getBoolean("IsDashing");
        }
        if (compound.contains("DashDurationTimer")) {
            dashDurationTimer = compound.getInt("DashDurationTimer");
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