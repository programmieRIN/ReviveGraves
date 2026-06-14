package de.programmierin.revivegraves.mixin.client;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Accessor for EntityRenderDispatcher's private renderers map.
 * Used to obtain the ChickenRenderer for ghost chicken rendering.
 */
@Mixin(EntityRenderDispatcher.class)
public interface EntityRenderManagerAccessor {
    @Accessor("renderers")
    Map<EntityType<?>, EntityRenderer<?, ?>> revivegraves$getRenderers();
}
