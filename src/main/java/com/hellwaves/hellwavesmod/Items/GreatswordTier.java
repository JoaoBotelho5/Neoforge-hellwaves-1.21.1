package com.hellwaves.hellwavesmod.Items;

import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

public class GreatswordTier implements Tier {

    @Override
    public int getUses() { return 1800; } // 0 = indestrutível

    @Override
    public float getSpeed() { return 1.0f; } // pode ser 1.0 para armas

    @Override
    public float getAttackDamageBonus() { return 13f; } // dano mostrado no tooltip

    @Override
    public int getEnchantmentValue() { return 15; }

    @Override
    public Ingredient getRepairIngredient() { return Ingredient.of(Items.NETHERITE_INGOT); }

    @Override
    public TagKey<Block> getIncorrectBlocksForDrops() {
        return TagKey.create(BuiltInRegistries.BLOCK.key(), ResourceLocation.tryParse("minecraft:nonexistent"));
    }
}
