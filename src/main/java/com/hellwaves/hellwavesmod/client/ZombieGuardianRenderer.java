package com.hellwaves.hellwavesmod.client;

import com.hellwaves.hellwavesmod.HWMobs.ZombieGuardian;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ZombieGuardianRenderer extends HumanoidMobRenderer<ZombieGuardian, ZombieModel<ZombieGuardian>> {
    private static final ResourceLocation ZOMBIE_GUARDIAN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("hellwavesmod", "textures/entity/fallen.png");

    public ZombieGuardianRenderer(EntityRendererProvider.Context context) {
        super(context, new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);

        // Adicionar layer para mostrar items na mão (incluindo arco)
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new HumanoidArmorLayer<>(
                this,
                new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)),
                context.getModelManager()
        ));
    }


    @Override
    public ResourceLocation getTextureLocation(ZombieGuardian entity) {
        return ZOMBIE_GUARDIAN_TEXTURE;
    }
}