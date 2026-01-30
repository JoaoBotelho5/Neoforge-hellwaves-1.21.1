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

import javax.annotation.Nullable;
import java.util.List;

public class SoulCageItem extends Item {

    public SoulCageItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());

        InteractionResult result = releaseGuardian(stack, (ServerLevel) level, player, Vec3.atBottomCenterOf(pos));
        return result;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.success(stack);

        InteractionResult result = releaseGuardian(stack, (ServerLevel) level, player, player.position());
        return new InteractionResultHolder<>(result, stack);
    }

    private InteractionResult releaseGuardian(ItemStack stack, ServerLevel level, Player player, Vec3 spawnPos) {
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

        String guardianType = cageData.getString("GuardianType");
        Mob guardian = null;

        if (guardianType.equals("hellwavesmod:zombie_guardian")) {
            guardian = HWDeferredRegister.ZOMBIE_GUARDIAN.get().create(level);
        } else if (guardianType.equals("hellwavesmod:skeleton_guardian")) {
            guardian = HWDeferredRegister.SKELETON_GUARDIAN.get().create(level);
        } else {
            player.displayClientMessage(Component.literal("§cError: Unknown guardian type: " + guardianType), true);
            return InteractionResult.FAIL;
        }

        if (!(guardian instanceof IGuardian iGuardian)) {
            player.displayClientMessage(Component.literal("§cError: Failed to create guardian!"), true);
            return InteractionResult.FAIL;
        }

        iGuardian.setRestoringFromCage(true);
        guardian.load(cageData.getCompound("GuardianData"));

        if (cageData.contains("GuardianLevel")) iGuardian.setGuardianLevel(cageData.getInt("GuardianLevel"));
        if (cageData.contains("Health")) guardian.setHealth(cageData.getFloat("Health"));

        if (cageData.contains("CustomName")) {
            Component customName = Component.Serializer.fromJson(cageData.getString("CustomName"), level.registryAccess());
            if (customName != null) guardian.setCustomName(customName);
        }

        guardian.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        guardian.finalizeSpawn(level, level.getCurrentDifficultyAt(guardian.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
        iGuardian.setRestoringFromCage(false);

        level.addFreshEntity(guardian);

        if (player.isCreative()) {
            // No Creative, substitui o stack cheio pelo empty cage
            ItemStack emptyCage = new ItemStack(HWDeferredRegister.EMPTY_SOUL_CAGE.get());
            player.setItemInHand(player.getUsedItemHand(), emptyCage);
        } else {
            // Survival: remove 1 cage e dá a empty cage
            stack.shrink(1);
            ItemStack emptyCage = new ItemStack(HWDeferredRegister.EMPTY_SOUL_CAGE.get());
            if (!player.getInventory().add(emptyCage)) player.drop(emptyCage, false);
        }

        player.displayClientMessage(Component.literal("§aGuardian released! Level: " + iGuardian.getGuardianLevel()), true);

        return InteractionResult.SUCCESS; // <- changed from CONSUME
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        var customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            var cageData = customData.copyTag();

            // Tipo de Guardian
            if (cageData.contains("GuardianType")) {
                String type = cageData.getString("GuardianType");
                String displayType = switch (type) {
                    case "hellwavesmod:zombie_guardian" -> "Zombie Guardian";
                    case "hellwavesmod:skeleton_guardian" -> "Skeleton Guardian";
                    default -> "Unknown Guardian";
                };
                tooltip.add(Component.literal("Type: §b" + displayType));
            }

            // Vida atual / máxima
            float health = cageData.contains("Health") ? cageData.getFloat("Health") : 0f;
            float maxHealth = cageData.contains("MaxHealth") ? cageData.getFloat("MaxHealth") : 0f;
            if (health > 0 && maxHealth > 0) {
                tooltip.add(Component.literal(String.format("Health: §c%.1f§r / §a%.1f", health, maxHealth)));
            }

            // Nível
            int guardianLevel = cageData.contains("GuardianLevel") ? cageData.getInt("GuardianLevel") : 1;
            tooltip.add(Component.literal("Level: §e" + guardianLevel));
        } else {
            tooltip.add(Component.literal("§7Empty Soul Cage"));
        }
    }

}
