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

    private GuardState currentState = GuardState.WANDER_AREA;
    private Player followingPlayer = null;
    private int stateChangeCooldown = 0;
    private GuardianInventory guardianInventory;

    // Leveling System
    private int guardianLevel = 1;
    private static final int MAX_LEVEL = 5;

    // Regeneração natural
    private static final int BASE_REGEN_INTERVAL = 100; // 5 segundos (20 ticks * 5)
    private int regenTimer = 0;

    // AOE Ability (Level 5)
    private static final int AOE_COOLDOWN = 150; // 7.5 segundos (20 ticks * 7.5)
    private static final float AOE_RADIUS = 1.5F;
    private static final float AOE_DAMAGE = 3.0F;
    private int aoeCooldownTimer = 0;
    private boolean wasInCombat = false;

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

    // ===== LEVELING SYSTEM METHODS =====

    public int getGuardianLevel() {
        return guardianLevel;
    }

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
            case 1 -> new ItemStack(Items.BOOK, 5); // Level 1 -> 2: 5 books
            case 2 -> new ItemStack(Items.DIAMOND_BLOCK, 1); // Level 2 -> 3: 1 diamond block
            case 3 -> new ItemStack(Items.NETHERITE_SCRAP, 1); // Level 3 -> 4: 1 netherite scrap
            case 4 -> new ItemStack(Items.NETHER_STAR, 1); // Level 4 -> 5: 1 nether star
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

        // Check if player has the required items
        if (!player.getAbilities().instabuild && !hasRequiredItems(player, cost)) {
            player.displayClientMessage(
                    Component.literal("§cYou need " + cost.getCount() + "x " +
                            cost.getHoverName().getString() + " to upgrade!"), true);
            return false;
        }

        // Consume items (if not in creative)
        if (!player.getAbilities().instabuild) {
            consumeItems(player, cost);
        }

        // Perform upgrade
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
        // Level 2: Improved regeneration (already handled in getRegenAmount())

        // Level 3: +10 health and heal to 30
        if (newLevel == 3) {
            var maxHealthAttr = this.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealthAttr != null) {
                double currentMax = maxHealthAttr.getBaseValue();
                maxHealthAttr.setBaseValue(currentMax + 10.0D);

                // Set health to 30 (new current HP after level 3)
                this.setHealth(30.0F);
            }
        }

        // Level 4: Permanent Resistance I
        if (newLevel == 4) {
            // Remove any existing resistance to refresh it
            this.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            // Add permanent resistance (999999 ticks = ~13.8 hours, effectively permanent)
            this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 999999, 0, false, false));
        }

        // Level 5: AOE ability (handled in tick())
    }

    private float getRegenAmount() {
        // Level 1: 1 HP per 5 seconds
        // Level 2+: 2 HP per 5 seconds
        return guardianLevel >= 2 ? 2.0F : 1.0F;
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
                .add(Attributes.MAX_HEALTH, 20.0D) // Changed from 50 to 20
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

            // Handle Level 5 AOE ability
            if (guardianLevel >= 5) {
                handleAOEAbility();
            }

            // FIXED: Continuously search for new targets after killing one
            if (this.getTarget() == null || !this.getTarget().isAlive()) {
                findNewTarget();
            }
        }
    }

    // FIXED: Add method to find new hostile targets
    private void findNewTarget() {
        if (this.level().isClientSide()) {
            return;
        }

        // Search for hostile mobs within follow range
        double range = this.getAttributeValue(Attributes.FOLLOW_RANGE);
        AABB searchBox = this.getBoundingBox().inflate(range, range / 2, range);

        java.util.List<LivingEntity> nearbyMobs = this.level().getEntitiesOfClass(
                LivingEntity.class,
                searchBox,
                entity -> entity != this &&
                        entity.isAlive() &&
                        isHostileMob(entity) &&
                        !(entity instanceof ZombieGuardian)  // FIXED: Don't target other guardians
        );

        if (!nearbyMobs.isEmpty()) {
            // Find closest hostile mob
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

    private void handleAOEAbility() {
        boolean inCombat = this.getTarget() != null;

        // Reset cooldown when leaving combat
        if (!inCombat && wasInCombat) {
            aoeCooldownTimer = 0;
        }

        wasInCombat = inCombat;

        // Only trigger in combat
        if (!inCombat) {
            return;
        }

        // Update cooldown
        aoeCooldownTimer++;

        // Trigger AOE when cooldown is ready
        if (aoeCooldownTimer >= AOE_COOLDOWN) {
            performAOEAttack();
            aoeCooldownTimer = 0;
        }
    }

    private void performAOEAttack() {
        if (this.level().isClientSide()) {
            return;
        }

        // Get all entities in radius
        AABB area = new AABB(
                this.getX() - AOE_RADIUS, this.getY() - 0.5, this.getZ() - AOE_RADIUS,
                this.getX() + AOE_RADIUS, this.getY() + 2.0, this.getZ() + AOE_RADIUS
        );

        List<LivingEntity> entities = this.level().getEntitiesOfClass(
                LivingEntity.class, area,
                entity -> entity != this && entity.isAlive() && isHostileMob(entity)
        );

        // Damage all hostile entities
        for (LivingEntity entity : entities) {
            entity.hurt(this.damageSources().mobAttack(this), AOE_DAMAGE);
        }

        // Visual and sound effects
        this.level().playSound(null, this.blockPosition(),
                SoundEvents.PLAYER_ATTACK_SWEEP, this.getSoundSource(), 1.0F, 1.0F);

        if (this.level() instanceof ServerLevel serverLevel) {
            // Sweep particles
            serverLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                    this.getX(), this.getY() + 1.0D, this.getZ(),
                    10, AOE_RADIUS, 0.5D, AOE_RADIUS, 0.0D
            );

            // Damage indicator particles
            serverLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR,
                    this.getX(), this.getY() + 1.0D, this.getZ(),
                    15, AOE_RADIUS * 0.5, 0.5D, AOE_RADIUS * 0.5, 0.1D
            );
        }
    }

    private void handleNaturalRegen() {
        // Incrementar timer
        regenTimer++;

        // A cada 5 segundos (100 ticks), regenerar 1 ou 2 HP baseado no level
        if (regenTimer >= BASE_REGEN_INTERVAL) {
            regenTimer = 0;

            // Só regenerar se não estiver com vida cheia
            if (this.getHealth() < this.getMaxHealth()) {
                this.heal(getRegenAmount());

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
            System.err.println("Error calculating enchantment bonus: " + e.getMessage());
        }

        return bonus;
    }

    public GuardState getState() {
        return currentState;
    }

    public void setState(GuardState newState) {
        if (stateChangeCooldown > 0) {
            return;
        }

        this.currentState = newState;
        this.stateChangeCooldown = 20;

        if (newState != GuardState.FOLLOW) {
            this.followingPlayer = null;
        }

        this.navigation.stop();
    }

    public void setFollowingPlayer(Player player) {
        this.followingPlayer = player;
        setState(GuardState.FOLLOW);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ItemStack itemInHand = player.getItemInHand(hand);

        // State changing with items
        if (itemInHand.is(Items.STICK)) {
            cycleState(player);
            return InteractionResult.SUCCESS;
        }

        // Open inventory with empty hand or while sneaking
        if (itemInHand.isEmpty() || player.isShiftKeyDown()) {
            player.openMenu(new net.minecraft.world.MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.literal("Zombie Guardian");
                }

                @Override
                public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                        int containerId,
                        net.minecraft.world.entity.player.Inventory playerInventory,
                        Player player) {
                    return new GuardianInventoryMenu(containerId, playerInventory, ZombieGuardian.this);
                }
            }, buf -> buf.writeInt(this.getId()));
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private void cycleState(Player player) {
        GuardState nextState = switch (currentState) {
            case STAY -> GuardState.FOLLOW;
            case FOLLOW -> GuardState.WANDER_AREA;
            case WANDER_AREA -> GuardState.STAY;
        };

        setState(nextState);

        if (nextState == GuardState.FOLLOW) {
            setFollowingPlayer(player);
        }

        String stateMessage = switch (nextState) {
            case STAY -> "§eGuardian will now STAY";
            case FOLLOW -> "§aGuardian will now FOLLOW you";
            case WANDER_AREA -> "§bGuardian will now WANDER";
        };

        player.displayClientMessage(Component.literal(stateMessage), true);
    }

    public boolean isHostileMob(LivingEntity entity) {
        if (entity == null) return false;

        return entity instanceof Enemy
                || entity instanceof Slime
                || entity instanceof AbstractPiglin
                || entity instanceof Ghast
                || entity instanceof EnderMan
                || entity instanceof Shulker;
    }

    private boolean isHoldingBow() {
        ItemStack mainhand = this.getItemBySlot(EquipmentSlot.MAINHAND);
        return mainhand.getItem() instanceof BowItem;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float pullProgress) {
        if (!isHoldingBow() || !isHostileMob(target)) {
            return;
        }

        ItemStack bow = this.getItemBySlot(EquipmentSlot.MAINHAND);
        Arrow arrow = createArrow(bow, pullProgress);

        double dx = target.getX() - this.getX();
        double dy = target.getY(0.3333) - arrow.getY();
        double dz = target.getZ() - this.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        arrow.shoot(dx, dy + distance * 0.2, dz, 1.6F, 14 - this.level().getDifficulty().getId() * 4);

        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
        this.level().addFreshEntity(arrow);
    }

    private Arrow createArrow(ItemStack bow, float pullProgress) {
        Arrow arrow = new Arrow(this.level(), this, new ItemStack(Items.ARROW), null);
        arrow.setBaseDamage(arrow.getBaseDamage() + this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.3);

        try {
            var enchantmentRegistry = this.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);

            int powerLevel = bow.getEnchantmentLevel(enchantmentRegistry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.POWER));
            if (powerLevel > 0) {
                arrow.setBaseDamage(arrow.getBaseDamage() + (double) powerLevel * 0.5 + 0.5);
            }

            int punchLevel = bow.getEnchantmentLevel(enchantmentRegistry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.PUNCH));
            if (punchLevel > 0) {
                CompoundTag arrowTag = new CompoundTag();
                arrowTag.putByte("knockback", (byte) punchLevel);
                arrow.load(arrowTag);
            }

            int flameLevel = bow.getEnchantmentLevel(enchantmentRegistry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FLAME));
            if (flameLevel > 0) {
                arrow.igniteForSeconds(100);
            }
        } catch (Exception e) {
            System.err.println("Error applying bow enchantments: " + e.getMessage());
        }

        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;

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
                    entity -> entity instanceof Mob mob &&
                            isHostileMob(mob) &&
                            !(entity instanceof ZombieGuardian));  // FIXED: Don't target other guardians
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
            // Apply level without triggering effects on load
            this.guardianLevel = savedLevel;
            // Re-apply permanent effects that may have been lost
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
        return spawnGroupData;
    }
}