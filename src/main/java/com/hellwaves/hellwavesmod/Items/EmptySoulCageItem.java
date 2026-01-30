package com.hellwaves.hellwavesmod.Items;

import com.hellwaves.hellwavesmod.HWMobs.IGuardian;
import com.hellwaves.hellwavesmod.regivents.HWDeferredRegister;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

public class EmptySoulCageItem extends Item {

    public EmptySoulCageItem(Properties properties) {
        super(properties);
    }

    public static InteractionResult captureGuardian(ItemStack stack, Player player, LivingEntity guardian) {
        if (!(guardian instanceof IGuardian iGuardian)) {
            return InteractionResult.FAIL;
        }

        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        // Create a Soul Cage item with the guardian's data
        ItemStack soulCage = new ItemStack(HWDeferredRegister.SOUL_CAGE.get());
        CompoundTag guardianData = new CompoundTag();

        // Save guardian's important data
        guardian.saveWithoutId(guardianData);

        // Store specific data we want to preserve
        CompoundTag cageData = new CompoundTag();
        cageData.putInt("GuardianLevel", iGuardian.getGuardianLevel());
        cageData.putFloat("Health", guardian.getHealth());
        cageData.putFloat("MaxHealth", guardian.getMaxHealth());
        cageData.put("GuardianData", guardianData);

        // Store guardian type
        cageData.putString("GuardianType", guardian.getEncodeId());

        // Copy guardian's custom name if it has one
        if (guardian.hasCustomName()) {
            cageData.putString("CustomName", Component.Serializer.toJson(guardian.getCustomName(), player.level().registryAccess()));
        }

        // Use DataComponents instead of setTag
        soulCage.set(DataComponents.CUSTOM_DATA, CustomData.of(cageData));

        iGuardian.setRestoringFromCage(true);

        // Remove the guardian from the world
        guardian.discard();

        // Replace empty cage with filled cage in player's hand
        stack.shrink(1);
        if (!player.getInventory().add(soulCage)) {
            player.drop(soulCage, false);
        }

        player.displayClientMessage(
                Component.literal("§aGuardian captured! Level: " + iGuardian.getGuardianLevel()),
                true
        );

        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7Right-click a Guardian"));
        tooltip.add(Component.literal("§7to capture it"));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}