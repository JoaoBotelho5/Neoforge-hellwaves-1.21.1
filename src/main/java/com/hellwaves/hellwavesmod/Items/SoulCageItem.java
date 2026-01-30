package com.hellwaves.hellwavesmod.Items;

import com.hellwaves.hellwavesmod.HWMobs.IGuardian;
import com.hellwaves.hellwavesmod.HWMobs.SkeletonGuardian;
import com.hellwaves.hellwavesmod.HWMobs.ZombieGuardian;
import com.hellwaves.hellwavesmod.regivents.HWDeferredRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class SoulCageItem extends Item {

    public SoulCageItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());

        return releaseGuardian(stack, (ServerLevel) level, player, Vec3.atBottomCenterOf(pos));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }

        // Release at player's position if used in air
        Vec3 spawnPos = player.position();
        InteractionResult result = releaseGuardian(stack, (ServerLevel) level, player, spawnPos);

        return new InteractionResultHolder<>(result, stack);
    }

    private InteractionResult releaseGuardian(ItemStack stack, ServerLevel level, Player player, Vec3 spawnPos) {
        // Use DataComponents instead of getTag
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            player.displayClientMessage(Component.literal("§cError: No guardian data found!"), true);
            return InteractionResult.FAIL;
        }

        CompoundTag cageData = customData.copyTag();
        if (!cageData.contains("GuardianData") || !cageData.contains("GuardianType")) {
            player.displayClientMessage(Component.literal("§cError: Invalid guardian data!"), true);
            return InteractionResult.FAIL;
        }

        // Get guardian type
        String guardianType = cageData.getString("GuardianType");

        // Create appropriate guardian based on type
        Mob guardian = null;
        if (guardianType.equals("hellwavesmod:zombie_guardian")) {
            guardian = HWDeferredRegister.ZOMBIE_GUARDIAN.get().create(level);
        } else if (guardianType.equals("hellwavesmod:skeleton_guardian")) {
            guardian = HWDeferredRegister.SKELETON_GUARDIAN.get().create(level);
        } else {
            player.displayClientMessage(Component.literal("§cError: Unknown guardian type: " + guardianType), true);
            return InteractionResult.FAIL;
        }

        if (guardian == null || !(guardian instanceof IGuardian iGuardian)) {
            player.displayClientMessage(Component.literal("§cError: Failed to create guardian!"), true);
            return InteractionResult.FAIL;
        }

        // Set restoring flag BEFORE loading data
        iGuardian.setRestoringFromCage(true);

        // Load guardian's NBT data
        CompoundTag guardianData = cageData.getCompound("GuardianData");
        guardian.load(guardianData);

        // Restore specific stats
        if (cageData.contains("GuardianLevel")) {
            iGuardian.setGuardianLevel(cageData.getInt("GuardianLevel"));
        }
        if (cageData.contains("Health")) {
            guardian.setHealth(cageData.getFloat("Health"));
        }

        // Restore custom name
        if (cageData.contains("CustomName")) {
            Component customName = Component.Serializer.fromJson(cageData.getString("CustomName"), level.registryAccess());
            if (customName != null) {
                guardian.setCustomName(customName);
            }
        }

        // Set guardian position
        guardian.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, level.getRandom().nextFloat() * 360.0F, 0.0F);

        // Finalize spawn - this will NOT clear equipment because restoringFromCage is true
        guardian.finalizeSpawn(level, level.getCurrentDifficultyAt(guardian.blockPosition()),
                MobSpawnType.MOB_SUMMONED, null);

        // Reset flag AFTER spawn is complete
        iGuardian.setRestoringFromCage(false);

        // Add guardian to world
        level.addFreshEntity(guardian);

        // Replace full Soul Cage with empty Soul Cage
        stack.shrink(1);
        ItemStack emptyCage = new ItemStack(HWDeferredRegister.EMPTY_SOUL_CAGE.get());
        if (!player.getInventory().add(emptyCage)) {
            player.drop(emptyCage, false);
        }

        player.displayClientMessage(
                Component.literal("§aGuardian released! Level: " + iGuardian.getGuardianLevel()),
                true
        );

        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        // Use DataComponents instead of getTag
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);

        if (customData != null) {
            CompoundTag cageData = customData.copyTag();

            if (cageData.contains("GuardianType")) {
                String type = cageData.getString("GuardianType");
                String displayType = type.contains("skeleton") ? "§7Skeleton Guardian" : "§aZombie Guardian";
                tooltip.add(Component.literal("§6Type: " + displayType));
            }

            if (cageData.contains("GuardianLevel")) {
                int guardianLevel = cageData.getInt("GuardianLevel");
                float health = cageData.getFloat("Health");
                float maxHealth = cageData.getFloat("MaxHealth");

                tooltip.add(Component.literal("§6Level: " + guardianLevel));
                tooltip.add(Component.literal("§cHealth: " + String.format("%.1f", health) + "/" + String.format("%.1f", maxHealth)));

                if (cageData.contains("CustomName")) {
                    Component customName = Component.Serializer.fromJson(cageData.getString("CustomName"), context.registries());
                    if (customName != null) {
                        tooltip.add(Component.literal("§7Name: ").append(customName));
                    }
                }
            }
        }

        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§7Right-click on ground to release"));

        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // Make it glow like an enchanted item
    }

    @Override
    public Component getName(ItemStack stack) {
        // Use DataComponents instead of getTag
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);

        if (customData != null) {
            CompoundTag cageData = customData.copyTag();

            if (cageData.contains("GuardianLevel") && cageData.contains("GuardianType")) {
                int level = cageData.getInt("GuardianLevel");
                String type = cageData.getString("GuardianType");
                String displayType = type.contains("skeleton") ? "Skeleton" : "Zombie";
                return Component.literal("Soul Cage §7(" + displayType + " Lv." + level + ")");
            }
        }

        return super.getName(stack);
    }
}