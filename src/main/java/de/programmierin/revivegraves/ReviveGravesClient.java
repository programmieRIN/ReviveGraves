package de.programmierin.revivegraves;

import de.programmierin.revivegraves.block.ModBlocks;
import de.programmierin.revivegraves.client.GravestoneBlockEntityRenderer;
import de.programmierin.revivegraves.entity.ModBlockEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;

public class ReviveGravesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRendererRegistry.register(ModBlockEntities.GRAVESTONE, GravestoneBlockEntityRenderer::new);
        // 26.1: block render layer is data-driven via "render_type" in the block model JSON
        // (Fabric's BlockRenderLayerMap was removed). See models/block/gravestone.json.
    }
}
