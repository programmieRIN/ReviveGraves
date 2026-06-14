package de.programmierin.revivegraves.mixin;

import de.programmierin.revivegraves.ghost.GhostChickenState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Intercepts entity tracking to disguise ghost players as chickens.
 * - sendPairingData: replaces player spawn with chicken spawn for other clients
 * - sendDirtyEntityData: prevents player SynchedEntityData fields from reaching clients
 *   that have a Chicken (would cause type mismatch crash)
 */
@Mixin(ServerEntity.class)
public abstract class EntityTrackerEntryMixin {

    @Shadow @Final private Entity entity;
    @Shadow @Nullable private List<SynchedEntityData.DataValue<?>> trackedDataValues;

    @Inject(method = "sendPairingData", at = @At("HEAD"), cancellable = true)
    private void revivegraves$disguiseGhostChicken(
            ServerPlayer player,
            Consumer<Packet<ClientGamePacketListener>> sender,
            CallbackInfo ci
    ) {
        if (!(entity instanceof ServerPlayer ghostPlayer)) return;
        if (!(ghostPlayer.level() instanceof ServerLevel serverWorld)) return;

        GhostChickenState state = GhostChickenState.get(serverWorld.getServer());
        if (!state.isGhost(ghostPlayer.getUUID())) return;

        // Don't disguise packets sent to the ghost player themselves
        if (player.getUUID().equals(ghostPlayer.getUUID())) return;

        ci.cancel();

        // Send a chicken spawn packet instead of a player spawn packet
        ClientboundAddEntityPacket spawnPacket = new ClientboundAddEntityPacket(
                ghostPlayer.getId(), ghostPlayer.getUUID(),
                ghostPlayer.getX(), ghostPlayer.getY(), ghostPlayer.getZ(),
                ghostPlayer.getXRot(), ghostPlayer.getYRot(),
                EntityType.CHICKEN, 0,
                ghostPlayer.getDeltaMovement(), (double) ghostPlayer.getYHeadRot()
        );
        sender.accept(spawnPacket);

        // Send empty tracker data so the client uses chicken defaults
        sender.accept(new ClientboundSetEntityDataPacket(ghostPlayer.getId(), List.of()));
    }

    /**
     * Intercepts dirty SynchedEntityData sync to prevent player-specific fields from being
     * broadcast to clients that have a Chicken. Sends tracker data only to the
     * ghost player's own client. This prevents the field type mismatch crash
     * (e.g., Player field 16 Byte vs Chicken field 16 Boolean).
     */
    @Inject(method = "sendDirtyEntityData", at = @At("HEAD"), cancellable = true)
    private void revivegraves$suppressGhostDataSync(CallbackInfo ci) {
        if (!(entity instanceof ServerPlayer ghostPlayer)) return;
        if (!(ghostPlayer.level() instanceof ServerLevel serverWorld)) return;

        GhostChickenState state = GhostChickenState.get(serverWorld.getServer());
        if (!state.isGhost(ghostPlayer.getUUID())) return;

        ci.cancel();

        // Still sync dirty SynchedEntityData entries to the ghost's own client
        // Order matters: getNonDefaultValues() must be called before packDirty()
        // because packDirty() resets the dirty flags
        SynchedEntityData entityData = entity.getEntityData();
        this.trackedDataValues = entityData.getNonDefaultValues();
        List<SynchedEntityData.DataValue<?>> dirtyEntries = entityData.packDirty();
        if (dirtyEntries != null) {
            ghostPlayer.connection.send(
                    new ClientboundSetEntityDataPacket(entity.getId(), dirtyEntries));
        }

        // Also sync attributes only to self (player attributes differ from chicken)
        Set<AttributeInstance> tracked = ((LivingEntity) entity).getAttributes().getAttributesToSync();
        if (!tracked.isEmpty()) {
            ghostPlayer.connection.send(
                    new ClientboundUpdateAttributesPacket(entity.getId(), tracked));
            tracked.clear();
        }
    }
}
