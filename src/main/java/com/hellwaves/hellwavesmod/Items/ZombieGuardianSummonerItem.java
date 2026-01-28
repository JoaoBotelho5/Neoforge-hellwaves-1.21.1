package com.hellwaves.hellwavesmod.Items;

import com.hellwaves.hellwavesmod.HWMobs.ZombieGuardian;
import com.hellwaves.hellwavesmod.regivents.HWDeferredRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class ZombieGuardianSummonerItem extends Item {

    public ZombieGuardianSummonerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        Vec3 spawnPos = Vec3.atBottomCenterOf(pos);

        // Create and spawn the Guardian Zombie
        ZombieGuardian guardian = HWDeferredRegister.ZOMBIE_GUARDIAN.get().create((ServerLevel) level);
        if (guardian == null) {
            return InteractionResult.FAIL;
        }

        // Set position
        guardian.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, level.getRandom().nextFloat() * 360.0F, 0.0F);

        // Finalize spawn
        guardian.finalizeSpawn((ServerLevel) level, level.getCurrentDifficultyAt(guardian.blockPosition()),
                MobSpawnType.MOB_SUMMONED, null);

        // Add to world
        level.addFreshEntity(guardian);

        // Consume the item
        context.getItemInHand().shrink(1);

        if (context.getPlayer() != null) {
            context.getPlayer().displayClientMessage(
                    Component.literal("§aGuardian Zombie summoned!"),
                    true
            );
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7Right-click on ground"));
        tooltip.add(Component.literal("§7to summon a Guardian Zombie"));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}