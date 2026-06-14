package de.programmierin.revivegraves.mixin;

import de.programmierin.revivegraves.ghost.GhostChickenState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents ghost chicken players from picking up items entirely.
 * Without this, items get picked up for one tick then cleared — destroying them.
 */
@Mixin(ItemEntity.class)
public class ItemEntityMixin {

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void revivegraves$preventGhostPickup(Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (serverPlayer.level() instanceof ServerLevel serverWorld) {
            GhostChickenState state = GhostChickenState.get(serverWorld.getServer());
            if (state.isGhost(serverPlayer.getUUID())) {
                ci.cancel();
            }
        }
    }
}
