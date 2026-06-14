package de.programmierin.revivegraves.mixin.client;

import de.programmierin.revivegraves.ghost.GhostChickenRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public class PlayerEntityRenderStateMixin implements GhostChickenRenderState {
    @Unique
    private boolean revivegraves$isGhostChicken = false;

    @Override
    public boolean revivegraves$isGhostChicken() {
        return revivegraves$isGhostChicken;
    }

    @Override
    public void revivegraves$setGhostChicken(boolean value) {
        this.revivegraves$isGhostChicken = value;
    }
}
