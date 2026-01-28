package com.hellwaves.hellwavesmod.Items;

import com.hellwaves.hellwavesmod.regivents.HWDeferredRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.MobSpawnType;

public class ZombieGuardianSummonerItem extends Item {

    public ZombieGuardianSummonerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }

        BlockPos spawnPos = context.getClickedPos().above();

        HWDeferredRegister.ZOMBIE_GUARDIAN.get().spawn(
                level,
                null,
                context.getPlayer(),
                spawnPos,
                MobSpawnType.TRIGGERED,
                true,
                false
        );


        // 🔥 consume item (remove this line if you don't want consumption)
        context.getItemInHand().shrink(1);

        return InteractionResult.CONSUME;
    }
}
