package com.hellwaves.hellwavesmod.Items;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;
import net.minecraft.ChatFormatting;

import java.util.List;
import java.util.Optional;

public class SoulTether extends Item {

    private static final String TAG_ENTITY_TYPE = "StoredEntityType";
    private static final String TAG_ENTITY_DATA = "StoredEntityData";
    private static final String TAG_DISPLAY_NAME = "StoredEntityName";

    public SoulTether(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        Level level = player.level();

        // ONLY work when SHIFT + RIGHT CLICK
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // Get the actual stack from player's hand
        ItemStack actualStack = player.getItemInHand(hand);

        // Check if already storing something
        CompoundTag existing = getStoredTag(actualStack);

        if (existing != null && existing.contains(TAG_ENTITY_TYPE)) {
            player.displayClientMessage(Component.literal("§cSoul Tether already contains an entity!"), true);
            return InteractionResult.SUCCESS;
        }

        EntityType<?> type = target.getType();

        // Don't allow players
        if (target instanceof Player) {
            player.displayClientMessage(Component.literal("§cCannot capture players!"), true);
            return InteractionResult.SUCCESS;
        }

        // Don't allow boss mobs
        if (!type.canSummon() || target.isInvulnerable()) {
            player.displayClientMessage(Component.literal("§cCannot capture boss mobs!"), true);
            return InteractionResult.SUCCESS;
        }

        // Only check for aggressive target on hostile mobs
        MobCategory category = type.getCategory();
        if (category == MobCategory.MONSTER) {
            // It's a hostile mob - check if it has a target
            if (target instanceof Mob mob && mob.getTarget() != null) {
                player.displayClientMessage(Component.literal("§cCannot capture aggressive mobs!"), true);
                return InteractionResult.SUCCESS;
            }
        }

        // Create storage tag
        CompoundTag storageTag = new CompoundTag();

        // Save entity type
        String entityTypeId = EntityType.getKey(type).toString();
        storageTag.putString(TAG_ENTITY_TYPE, entityTypeId);

        // Save ALL entity data
        CompoundTag entityData = new CompoundTag();
        target.saveWithoutId(entityData);
        storageTag.put(TAG_ENTITY_DATA, entityData);

        // Save display name
        String displayName = target.hasCustomName()
                ? target.getCustomName().getString()
                : target.getDisplayName().getString();
        storageTag.putString(TAG_DISPLAY_NAME, displayName);

        // Store in item
        actualStack.set(DataComponents.CUSTOM_DATA, CustomData.of(storageTag));

        // Remove entity
        target.discard();

        player.displayClientMessage(Component.literal("§aCaptured: " + displayName), true);

        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();

        // NO SHIFT REQUIRED FOR RELEASING - removed the shift check!
        if (player == null) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = context.getItemInHand();
        CompoundTag stored = getStoredTag(stack);

        if (stored == null || !stored.contains(TAG_ENTITY_TYPE)) {
            // No entity stored - don't show message, just pass
            return InteractionResult.PASS;
        }

        try {
            // Get entity type
            String entityTypeId = stored.getString(TAG_ENTITY_TYPE);
            Optional<EntityType<?>> optionalType = EntityType.byString(entityTypeId);

            if (optionalType.isEmpty()) {
                player.displayClientMessage(Component.literal("§cInvalid entity type!"), true);
                return InteractionResult.SUCCESS;
            }

            EntityType<?> entityType = optionalType.get();

            // Create entity
            Entity entity = entityType.create(level);
            if (entity == null) {
                player.displayClientMessage(Component.literal("§cFailed to create entity!"), true);
                return InteractionResult.SUCCESS;
            }

            // Load entity data
            CompoundTag entityData = stored.getCompound(TAG_ENTITY_DATA);
            entity.load(entityData);

            // Position entity
            BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
            entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, entity.getYRot(), entity.getXRot());

            // Spawn entity
            level.addFreshEntity(entity);

            // Clear data
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag()));

            String displayName = stored.getString(TAG_DISPLAY_NAME);
            player.displayClientMessage(Component.literal("§aReleased: " + displayName), true);

            return InteractionResult.SUCCESS;

        } catch (Exception ex) {
            ex.printStackTrace();
            player.displayClientMessage(Component.literal("§cError: " + ex.getMessage()), true);
            return InteractionResult.SUCCESS;
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        CompoundTag tag = getStoredTag(stack);
        return tag != null && tag.contains(TAG_ENTITY_TYPE);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        CompoundTag stored = getStoredTag(stack);
        if (stored != null && stored.contains(TAG_DISPLAY_NAME)) {
            String entityName = stored.getString(TAG_DISPLAY_NAME);
            tooltip.add(Component.literal("Stored: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(entityName).withStyle(ChatFormatting.AQUA)));
            tooltip.add(Component.literal("Right-click ground to release")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else {
            tooltip.add(Component.literal("Sneak + Right-click a non-aggressive mob to capture")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    private CompoundTag getStoredTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            return data.copyTag();
        }
        return new CompoundTag();
    }
}