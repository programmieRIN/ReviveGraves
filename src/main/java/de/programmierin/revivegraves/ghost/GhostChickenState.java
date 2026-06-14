package de.programmierin.revivegraves.ghost;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import de.programmierin.revivegraves.config.ModConfig;

import java.util.EnumSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

/**
 * Persistent state manager for ghost chicken players.
 * Tracks which players are ghosts and stores their gravestone locations
 * for cross-dimension revive support.
 */
public class GhostChickenState extends SavedData {

    private static final Identifier SPEED_MODIFIER_ID = Identifier.fromNamespaceAndPath("revivegraves", "ghost_chicken_speed");
    private static final Identifier SCALE_MODIFIER_ID = Identifier.fromNamespaceAndPath("revivegraves", "ghost_chicken_scale");
    private static final Identifier STEP_HEIGHT_MODIFIER_ID = Identifier.fromNamespaceAndPath("revivegraves", "ghost_chicken_step");

    private final Map<UUID, GraveLocation> gravestoneLocations;
    private final Set<UUID> ghostPlayers;

    /**
     * Stores gravestone dimension + position for cross-dimension support.
     */
    public record GraveLocation(String dimension, BlockPos pos) {
        public static final Codec<GraveLocation> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.STRING.fieldOf("dimension").forGetter(GraveLocation::dimension),
                        BlockPos.CODEC.fieldOf("pos").forGetter(GraveLocation::pos)
                ).apply(instance, GraveLocation::new)
        );

        public ServerLevel resolveWorld(MinecraftServer server) {
            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension()));
            return server.getLevel(dimKey);
        }
    }

    /**
     * Internal record for codec serialization of a single ghost entry.
     */
    private record GhostEntry(String uuid, Optional<GraveLocation> location) {
        public static final Codec<GhostEntry> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.STRING.fieldOf("uuid").forGetter(GhostEntry::uuid),
                        GraveLocation.CODEC.optionalFieldOf("location").forGetter(GhostEntry::location)
                ).apply(instance, GhostEntry::new)
        );
    }

    public static final Codec<GhostChickenState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    GhostEntry.CODEC.listOf().optionalFieldOf("ghost_players", List.of())
                            .forGetter(GhostChickenState::toEntryList)
            ).apply(instance, GhostChickenState::fromEntryList)
    );

    public static final SavedDataType<GhostChickenState> STATE_TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace("revivegraves_ghost"),
            GhostChickenState::new,
            CODEC,
            null
    );

    public GhostChickenState() {
        this.ghostPlayers = new HashSet<>();
        this.gravestoneLocations = new HashMap<>();
    }

    private GhostChickenState(Map<UUID, GraveLocation> locations, Set<UUID> ghosts) {
        this.gravestoneLocations = new HashMap<>(locations);
        this.ghostPlayers = new HashSet<>(ghosts);
    }

    /**
     * Converts internal state to a list of entries for codec serialization.
     */
    private List<GhostEntry> toEntryList() {
        return ghostPlayers.stream()
                .map(uuid -> new GhostEntry(uuid.toString(), Optional.ofNullable(gravestoneLocations.get(uuid))))
                .toList();
    }

    /**
     * Reconstructs state from a list of entries (codec deserialization).
     */
    private static GhostChickenState fromEntryList(List<GhostEntry> entries) {
        Map<UUID, GraveLocation> locations = new HashMap<>();
        Set<UUID> ghosts = new HashSet<>();
        for (GhostEntry entry : entries) {
            try {
                UUID uuid = UUID.fromString(entry.uuid());
                ghosts.add(uuid);
                entry.location().ifPresent(loc -> locations.put(uuid, loc));
            } catch (IllegalArgumentException e) {
                // Skip invalid UUIDs
            }
        }
        GhostChickenState state = new GhostChickenState(locations, ghosts);
        return state;
    }

    /**
     * Retrieves (or creates) the ghost chicken state from the overworld's persistent state manager.
     */
    public static GhostChickenState get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(STATE_TYPE);
    }

    public boolean isGhost(UUID uuid) {
        return ghostPlayers.contains(uuid);
    }

    public GraveLocation getGravestoneLocation(UUID uuid) {
        return gravestoneLocations.get(uuid);
    }

    public void addGhost(UUID uuid, String dimension, BlockPos gravestonePos) {
        ghostPlayers.add(uuid);
        gravestoneLocations.put(uuid, new GraveLocation(dimension, gravestonePos));
        setDirty();
    }

    public void removeGhost(UUID uuid) {
        ghostPlayers.remove(uuid);
        gravestoneLocations.remove(uuid);
        setDirty();
    }

    /**
     * Marks the gravestone as expired (removes location reference but keeps ghost state).
     * The player remains a ghost until revived by other means or the server handles cleanup.
     */
    public void setGravestoneExpired(UUID uuid) {
        gravestoneLocations.remove(uuid);
        setDirty();
    }

    public Set<UUID> getGhostPlayerUuids() {
        return Collections.unmodifiableSet(ghostPlayers);
    }

    /**
     * Applies ghost state to a player: Adventure mode, invulnerable, invisible, slow speed.
     */
    public static void applyGhostState(ServerPlayer player) {
        player.setGameMode(GameType.ADVENTURE);
        player.setInvulnerable(true);

        player.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY,
                MobEffectInstance.INFINITE_DURATION,
                0, true, false, false
        ));

        if (ModConfig.INSTANCE.ghost.slowFallingEnabled) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.SLOW_FALLING,
                    MobEffectInstance.INFINITE_DURATION,
                    0, true, false, false
            ));
        }

        AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(SPEED_MODIFIER_ID);
            double basePlayerSpeed = 0.1;
            double modifier = (ModConfig.INSTANCE.ghost.speedMultiplier * basePlayerSpeed) - basePlayerSpeed;
            speedAttr.addTransientModifier(new AttributeModifier(
                    SPEED_MODIFIER_ID, modifier, AttributeModifier.Operation.ADD_VALUE
            ));
        }

        // Scale player down to chicken size (1.8 * 0.389 = 0.7 height)
        AttributeInstance scaleAttr = player.getAttribute(Attributes.SCALE);
        if (scaleAttr != null) {
            scaleAttr.removeModifier(SCALE_MODIFIER_ID);
            scaleAttr.addTransientModifier(new AttributeModifier(
                    SCALE_MODIFIER_ID, -0.611, AttributeModifier.Operation.ADD_MULTIPLIED_BASE
            ));
        }

        // Compensate step height so ghost can still walk up slabs/stairs
        AttributeInstance stepAttr = player.getAttribute(Attributes.STEP_HEIGHT);
        if (stepAttr != null) {
            stepAttr.removeModifier(STEP_HEIGHT_MODIFIER_ID);
            stepAttr.addTransientModifier(new AttributeModifier(
                    STEP_HEIGHT_MODIFIER_ID, 0.6, AttributeModifier.Operation.ADD_VALUE
            ));
        }

        // Hide ghost from tab list but keep profile/skin data in playerListEntries
        // (needed for gravestone skull rendering on other clients).
        // DON'T remove from player list — just set listed=false via UPDATE_LISTED.
        // This keeps the GameProfile available for skin resolution.
        MinecraftServer server = player.level().getServer();
        if (server != null) {
            // Create UPDATE_LISTED packet with listed=false
            ClientboundPlayerInfoUpdatePacket unlistPacket = new ClientboundPlayerInfoUpdatePacket(
                    EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED), List.of(player));
            // entryFromPlayer creates entries with listed=true, override to false
            var accessor = (de.programmierin.revivegraves.mixin.PlayerListS2CPacketAccessor) (Object) unlistPacket;
            List<ClientboundPlayerInfoUpdatePacket.Entry> unlisted = accessor.revivegraves$getEntries().stream()
                    .map(e -> new ClientboundPlayerInfoUpdatePacket.Entry(
                            e.profileId(), e.profile(), false, e.latency(),
                            e.gameMode(), e.displayName(), e.showHat(), e.listOrder(), e.chatSession()))
                    .toList();
            accessor.revivegraves$setEntries(unlisted);

            for (ServerPlayer onlinePlayer : server.getPlayerList().getPlayers()) {
                onlinePlayer.connection.send(unlistPacket);
            }
        }
    }

    /**
     * Removes ghost state from a player: removes invulnerability, invisibility, and speed modifier.
     */
    public static void removeGhostState(ServerPlayer player) {
        player.setInvulnerable(false);
        player.removeEffect(MobEffects.INVISIBILITY);
        player.removeEffect(MobEffects.SLOW_FALLING);

        AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(SPEED_MODIFIER_ID);
        }

        AttributeInstance scaleAttr = player.getAttribute(Attributes.SCALE);
        if (scaleAttr != null) {
            scaleAttr.removeModifier(SCALE_MODIFIER_ID);
        }

        AttributeInstance stepAttr = player.getAttribute(Attributes.STEP_HEIGHT);
        if (stepAttr != null) {
            stepAttr.removeModifier(STEP_HEIGHT_MODIFIER_ID);
        }

        // Re-list player in tab (set listed=true)
        MinecraftServer server = player.level().getServer();
        if (server != null) {
            ClientboundPlayerInfoUpdatePacket relistPacket = new ClientboundPlayerInfoUpdatePacket(
                    EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED), List.of(player));
            // Entry from player will have listed=true by default — which is what we want
            for (ServerPlayer onlinePlayer : server.getPlayerList().getPlayers()) {
                onlinePlayer.connection.send(relistPacket);
            }
        }
    }

}
