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

import javax.annotation.Nullable;
import java.util.EnumSet;

public class ZombieGuardian extends Zombie implements RangedAttackMob {
    // Estados do mob
    public enum GuardState {
        STAY,       // Fica parado no lugar
        FOLLOW,     // Segue o jogador
        WANDER_AREA // Vaga pela área
    }

    private GuardState currentState = GuardState.WANDER_AREA;
    private Player followingPlayer = null;
    private int stateChangeCooldown = 0;
    private GuardianInventory guardianInventory;

    // Regeneração natural
    private static final int REGEN_INTERVAL = 100; // 5 segundos (20 ticks * 5)
    private int regenTimer = 0;

    public ZombieGuardian(EntityType<? extends Zombie> entityType, Level level) {
        super(entityType, level);
        this.setCustomName(Component.literal("§2Zombie Guardian§r"));
        this.setCustomNameVisible(true);
        this.xpReward = 10;
    }

    public GuardianInventory getGuardianInventory() {
        if (guardianInventory == null) {
            guardianInventory = new GuardianInventory(this);
        }
        return guardianInventory;
    }

    @Override
    protected void registerGoals() {
        // Goals de movimento
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new GuardianRangedBowAttackGoal(this, 1.0D, 20, 15.0F));
        this.goalSelector.addGoal(2, new GuardianMeleeAttackGoal());
        this.goalSelector.addGoal(3, new GuardianFollowGoal());
        this.goalSelector.addGoal(4, new GuardianStayGoal());
        this.goalSelector.addGoal(5, new GuardianWanderGoal());
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        // Targeting - Ataque a mobs hostis
        this.targetSelector.addGoal(0, new GuardianHurtByTargetGoal());
        this.targetSelector.addGoal(1, new GuardianAttackHostilesGoal());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 50.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.18D)
                .add(Attributes.FOLLOW_RANGE, 30.0D);
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    public boolean isBaby() {
        return false;
    }

    @Override
    public void setBaby(boolean baby) {
        // Ignora
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return true;
    }

    @Override
    public boolean isPreventingPlayerRest(Player player) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide()) {
            if (stateChangeCooldown > 0) {
                stateChangeCooldown--;
            }

            if (currentState == GuardState.FOLLOW && followingPlayer != null && this.getTarget() == null) {
                if (!followingPlayer.isAlive() || this.distanceToSqr(followingPlayer) > 100.0D) {
                    setState(GuardState.STAY);
                }
            }

            // Atualizar atributos baseado no equipamento
            updateAttributesFromEquipment();

            // Regeneração natural de saúde
            handleNaturalRegen();
        }
    }

    private void handleNaturalRegen() {
        // Incrementar timer
        regenTimer++;

        // A cada 5 segundos (100 ticks), regenerar 1 HP
        if (regenTimer >= REGEN_INTERVAL) {
            regenTimer = 0;

            // Só regenerar se não estiver com vida cheia
            if (this.getHealth() < this.getMaxHealth()) {
                this.heal(1.0F);

                // Efeito visual de partículas de cura (opcional)
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(
                            net.minecraft.core.particles.ParticleTypes.HEART,
                            this.getX(),
                            this.getY() + 1.0D,
                            this.getZ(),
                            1, // quantidade
                            0.2D, 0.2D, 0.2D, // spread
                            0.0D // velocidade
                    );
                }
            }
        }
    }

    private void updateAttributesFromEquipment() {
        ItemStack mainhand = this.getItemBySlot(EquipmentSlot.MAINHAND);

        double baseAttack = 5.0D;
        double totalAttack = baseAttack;

        if (!mainhand.isEmpty()) {
            var mainhandModifiers = mainhand.getAttributeModifiers();
            for (var entry : mainhandModifiers.modifiers()) {
                if (entry.attribute().is(Attributes.ATTACK_DAMAGE)) {
                    totalAttack += entry.modifier().amount();
                }
            }

            totalAttack += calculateEnchantmentDamageBonus(mainhand);
        }

        var attackAttribute = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackAttribute != null && attackAttribute.getBaseValue() != totalAttack) {
            attackAttribute.setBaseValue(totalAttack);
        }
    }

    private double calculateEnchantmentDamageBonus(ItemStack stack) {
        if (stack.isEmpty()) return 0.0D;

        double bonus = 0.0D;

        try {
            var enchantmentRegistry = this.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);

            int sharpnessLevel = stack.getEnchantmentLevel(enchantmentRegistry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS));
            if (sharpnessLevel > 0) {
                bonus += 0.5D + (sharpnessLevel * 0.5D);
            }

            int smiteLevel = stack.getEnchantmentLevel(enchantmentRegistry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SMITE));
            if (smiteLevel > 0) {
                bonus += smiteLevel * 2.5D;
            }

            int baneLevel = stack.getEnchantmentLevel(enchantmentRegistry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.BANE_OF_ARTHROPODS));
            if (baneLevel > 0) {
                bonus += baneLevel * 2.5D;
            }

            int impalingLevel = stack.getEnchantmentLevel(enchantmentRegistry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.IMPALING));
            if (impalingLevel > 0 && (this.isInWater() || this.level().isRainingAt(this.blockPosition()))) {
                bonus += impalingLevel * 2.5D;
            }

        } catch (Exception e) {
            System.err.println("Error calculating enchantment damage bonus: " + e.getMessage());
        }

        return bonus;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!super.doHurtTarget(target)) {
            return false;
        }

        if (target instanceof LivingEntity livingTarget) {
            ItemStack mainhand = this.getItemBySlot(EquipmentSlot.MAINHAND);

            if (!mainhand.isEmpty()) {
                try {
                    var enchantmentRegistry = this.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);

                    int fireAspectLevel = mainhand.getEnchantmentLevel(enchantmentRegistry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FIRE_ASPECT));
                    if (fireAspectLevel > 0) {
                        livingTarget.igniteForSeconds(fireAspectLevel * 4);
                    }

                    int knockbackLevel = mainhand.getEnchantmentLevel(enchantmentRegistry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.KNOCKBACK));
                    if (knockbackLevel > 0) {
                        double knockbackStrength = knockbackLevel * 0.5D;
                        double dx = livingTarget.getX() - this.getX();
                        double dz = livingTarget.getZ() - this.getZ();
                        livingTarget.knockback(knockbackStrength, dx, dz);
                    }

                    if (mainhand.getItem() instanceof SwordItem) {
                        int sweepingLevel = mainhand.getEnchantmentLevel(enchantmentRegistry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SWEEPING_EDGE));
                        if (sweepingLevel > 0) {
                            double sweepDamage = 1.0D + (sweepingLevel * 0.5D);

                            for (LivingEntity nearbyEntity : this.level().getEntitiesOfClass(LivingEntity.class,
                                    this.getBoundingBox().inflate(1.0D, 0.25D, 1.0D))) {
                                if (nearbyEntity != this && nearbyEntity != livingTarget &&
                                        !this.isAlliedTo(nearbyEntity) && nearbyEntity instanceof Mob && isHostileMob(nearbyEntity)) {
                                    nearbyEntity.hurt(this.damageSources().mobAttack(this), (float) sweepDamage);
                                }
                            }
                        }
                    }

                } catch (Exception e) {
                    System.err.println("Error applying weapon enchantment effects: " + e.getMessage());
                }
            }
        }

        return true;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.isShiftKeyDown() && hand == InteractionHand.MAIN_HAND) {
            if (!this.level().isClientSide()) {
                player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (containerId, playerInventory, playerEntity) ->
                                new GuardianInventoryMenu(containerId, playerInventory, this),
                        this.getDisplayName()
                ), buf -> {
                    buf.writeInt(this.getId());
                });
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }

        if (!this.level().isClientSide() && stateChangeCooldown <= 0) {
            switch (currentState) {
                case STAY -> setState(GuardState.FOLLOW);
                case FOLLOW -> setState(GuardState.WANDER_AREA);
                case WANDER_AREA -> setState(GuardState.STAY);
            }

            stateChangeCooldown = 20;
            player.displayClientMessage(Component.literal("Zombie Guardian mode: " + getStateName()), true);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private void setState(GuardState newState) {
        this.currentState = newState;

        switch (newState) {
            case FOLLOW:
                Player nearestPlayer = this.level().getNearestPlayer(this, 15.0D);
                if (nearestPlayer != null) {
                    this.followingPlayer = nearestPlayer;
                }
                break;
            case STAY:
            case WANDER_AREA:
                this.followingPlayer = null;
                if (this.getTarget() == null) {
                    this.getNavigation().stop();
                }
                break;
        }
    }

    private String getStateName() {
        return switch (currentState) {
            case STAY -> "§eSTAY§r (Fica parado)";
            case FOLLOW -> "§aFOLLOW§r (Segue jogador)";
            case WANDER_AREA -> "§bWANDER§r (Vaga pela área)";
        };
    }

    public boolean isHostileMob(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (!(entity instanceof Mob)) return false;

        Mob mob = (Mob) entity;

        if (mob instanceof Creeper) {
            return false;
        }

        if (mob instanceof Monster) {
            if (mob instanceof ZombieGuardian) {
                return false;
            }
            return true;
        }

        if (mob instanceof ZombifiedPiglin || mob instanceof AbstractPiglin || mob instanceof Slime || mob instanceof MagmaCube) {
            return true;
        }

        return false;
    }

    public boolean isHoldingBow() {
        ItemStack mainhand = this.getItemBySlot(EquipmentSlot.MAINHAND);
        return mainhand.getItem() instanceof BowItem;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        ItemStack mainhand = this.getItemBySlot(EquipmentSlot.MAINHAND);
        ItemStack offhand = this.getItemBySlot(EquipmentSlot.OFFHAND);

        AbstractArrow arrow = createArrow(mainhand, offhand);

        double distanceX = target.getX() - this.getX();
        double distanceY = target.getY(0.3333333333333333D) - arrow.getY();
        double distanceZ = target.getZ() - this.getZ();
        double horizontalDistance = Math.sqrt(distanceX * distanceX + distanceZ * distanceZ);

        arrow.shoot(distanceX, distanceY + horizontalDistance * 0.20000000298023224D, distanceZ, 1.6F, 14.0F);

        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));

        this.level().addFreshEntity(arrow);
    }

    private AbstractArrow createArrow(ItemStack bowStack, ItemStack offhandStack) {
        ItemStack arrowStack = new ItemStack(Items.ARROW);

        if (offhandStack.getItem() instanceof ArrowItem && !offhandStack.isEmpty()) {
            arrowStack = offhandStack.copy();
            arrowStack.setCount(1);
        }

        Arrow arrow = new Arrow(this.level(), this, arrowStack, null);
        arrow.setBaseDamage(2.0D);

        try {
            var enchantmentRegistry = this.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);

            int powerLevel = bowStack.getEnchantmentLevel(enchantmentRegistry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.POWER));
            if (powerLevel > 0) {
                arrow.setBaseDamage(arrow.getBaseDamage() + (double)powerLevel * 0.5D + 0.5D);
            }

            int punchLevel = bowStack.getEnchantmentLevel(enchantmentRegistry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.PUNCH));
            if (punchLevel > 0) {
                CompoundTag arrowTag = new CompoundTag();
                arrowTag.putByte("knockback", (byte) punchLevel);
                arrow.load(arrowTag);
            }

            if (bowStack.getEnchantmentLevel(enchantmentRegistry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FLAME)) > 0) {
                arrow.igniteForSeconds(100);
            }
        } catch (Exception e) {
            System.err.println("Error applying bow enchantments: " + e.getMessage());
        }

        arrow.setOwner(this);
        arrow.setCritArrow(true);

        return arrow;
    }

    private static class GuardianRangedBowAttackGoal extends RangedBowAttackGoal<ZombieGuardian> {
        private final ZombieGuardian guardian;

        public GuardianRangedBowAttackGoal(ZombieGuardian guardian, double speedModifier, int attackInterval, float attackRadius) {
            super(guardian, speedModifier, attackInterval, attackRadius);
            this.guardian = guardian;
        }

        @Override
        public boolean canUse() {
            return guardian.isHoldingBow() &&
                    guardian.getTarget() != null &&
                    guardian.isHostileMob(guardian.getTarget()) &&
                    super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return guardian.isHoldingBow() &&
                    guardian.getTarget() != null &&
                    guardian.isHostileMob(guardian.getTarget()) &&
                    super.canContinueToUse();
        }
    }

    private class GuardianMeleeAttackGoal extends MeleeAttackGoal {
        public GuardianMeleeAttackGoal() {
            super(ZombieGuardian.this, 1.4D, true);
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
    }

    private class GuardianAttackHostilesGoal extends NearestAttackableTargetGoal<LivingEntity> {
        public GuardianAttackHostilesGoal() {
            super(ZombieGuardian.this, LivingEntity.class, 20, true, false,
                    entity -> entity instanceof Mob mob && isHostileMob(mob));
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (target == null) return false;

        boolean isDefending = this.getLastHurtByMob() == target;

        if (target instanceof Player ||
                target instanceof IronGolem ||
                target instanceof SnowGolem ||
                target instanceof Wolf ||
                target instanceof Villager ||
                target instanceof ZombieGuardian) {

            return isDefending;
        }

        return isHostileMob(target);
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
        return spawnGroupData;
    }
}