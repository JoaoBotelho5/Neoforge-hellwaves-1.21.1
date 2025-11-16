package com.hellwaves.hellwavesmod.client;

import com.hellwaves.hellwavesmod.UndeadLord;
import net.minecraft.client.model.PiglinModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

public class UndeadLordRenderer extends MobRenderer<UndeadLord, PiglinModel<UndeadLord>> {
    private static final ResourceLocation PIGLIN_BRUTE_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/piglin/piglin_brute.png");

    public UndeadLordRenderer(EntityRendererProvider.Context context) {
        super(context, new PiglinModel<>(context.bakeLayer(ModelLayers.PIGLIN_BRUTE)), 0.5F);

        // Adiciona a layer para mostrar itens na mão
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));

        // Adiciona layers de armadura (opcional - se quiser que a armadura seja visível)
        this.addLayer(new HumanoidArmorLayer<>(this,
                new PiglinModel<>(context.bakeLayer(ModelLayers.PIGLIN_BRUTE_INNER_ARMOR)),
                new PiglinModel<>(context.bakeLayer(ModelLayers.PIGLIN_BRUTE_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(UndeadLord entity) {
        return PIGLIN_BRUTE_TEXTURE;
    }
}