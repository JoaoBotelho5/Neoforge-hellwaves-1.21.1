package com.hellwaves.hellwavesmod.Items;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;

public class GreatswordItem extends SwordItem {
    public GreatswordItem(Item.Properties properties) {
        super(new GreatswordTier(), properties);
    }
}

