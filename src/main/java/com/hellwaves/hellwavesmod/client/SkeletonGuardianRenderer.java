package com.hellwaves.hellwavesmod.client;

import com.hellwaves.hellwavesmod.HWMobs.SkeletonGuardian;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

public class SkeletonGuardianRenderer extends HumanoidMobRenderer<SkeletonGuardian, SkeletonModel<SkeletonGuardian>> {

    private static final ResourceLocation SKELETON_GUARDIAN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/skeleton/skeleton.png");

    public SkeletonGuardianRenderer(EntityRendererProvider.Context context) {
        super(context, new SkeletonModel<>(context.bakeLayer(ModelLayers.SKELETON)), 0.5F);

        // CRITICAL: Add armor layer so armor shows on the model
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.SKELETON_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.SKELETON_OUTER_ARMOR)),
                context.getModelManager()));

        // Layer para itens na mão (ex: arco, espada)
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(SkeletonGuardian entity) {
        return SKELETON_GUARDIAN_TEXTURE;
    }
}