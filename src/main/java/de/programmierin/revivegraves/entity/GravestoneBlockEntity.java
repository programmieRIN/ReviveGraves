package de.programmierin.revivegraves.entity;

import de.programmierin.revivegraves.ReviveGraves;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GravestoneBlockEntity extends BlockEntity {
    private UUID owner;
    private String ownerName;
    private UUID hologram;
    private GameType originalGameMode;
    private String skinTextureValue;
    private String skinTextureSignature;
    private long creationTick = -1;
    private List<ItemStackWithSlot> storedItems = new ArrayList<>();
    private int storedXp = 0;

    public GravestoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GRAVESTONE, pos, state);
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
        if (getLevel() != null && !getLevel().isClientSide()) {
            getLevel().sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName != null && ownerName.length() > 16
                ? ownerName.substring(0, 16) : ownerName;
        setChanged();
    }

    public String getOwnerName() {
        return ownerName;
    }

    public UUID getOwner() {
        return owner;
    }

    public UUID getHologram() {
        return hologram;
    }

    public void discardHologram(ServerLevel world) {
        if (hologram == null) return;
        Entity holo = world.getEntity(hologram);
        if (holo != null) holo.discard();
    }

    public GameType getOriginalGameMode() {
        return originalGameMode;
    }

    public void setOriginalGameMode(GameType mode) {
        this.originalGameMode = mode;
        setChanged();
    }

    public void setSkinTexture(String value, String signature) {
        this.skinTextureValue = value;
        this.skinTextureSignature = signature;
        setChanged();
    }

    public String getSkinTextureValue() {
        return skinTextureValue;
    }

    public String getSkinTextureSignature() {
        return skinTextureSignature;
    }

    public long getCreationTick() {
        return creationTick;
    }

    public void setCreationTick(long tick) {
        this.creationTick = tick;
        setChanged();
    }

    public List<ItemStackWithSlot> getStoredItems() {
        return storedItems;
    }

    public void setStoredItems(List<ItemStackWithSlot> items) {
        this.storedItems = items != null ? new ArrayList<>(items) : new ArrayList<>();
        setChanged();
    }

    public int getStoredXp() {
        return storedXp;
    }

    public void setStoredXp(int xp) {
        this.storedXp = xp;
        setChanged();
    }

    public void spawnHologram(ServerLevel world) {
        if (owner == null || hologram != null) return;

        ServerPlayer player = world.getServer()
                .getPlayerList()
                .getPlayer(owner);
        if (player == null) return;
        String name = player.getGameProfile().name();

        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:armor_stand");
        tag.putBoolean("Invisible", true);
        tag.putBoolean("NoGravity", true);
        tag.putBoolean("Invulnerable", true);
        tag.putBoolean("Small", true);
        tag.putBoolean("NoBasePlate", true);
        tag.putBoolean("Marker", true);

        Entity loaded = EntityType.loadEntityRecursive(tag, world, EntitySpawnReason.TRIGGERED, EntityProcessor.NOP);
        if (!(loaded instanceof ArmorStand stand)) return;

        stand.setCustomName(Component.literal(name));
        stand.setCustomNameVisible(true);

        double x = getBlockPos().getX() + 0.5;
        double y = getBlockPos().getY() + 0.8;
        double z = getBlockPos().getZ() + 0.5;
        stand.snapTo(x, y, z, 0f, 0f);

        world.addFreshEntity(stand);
        this.hologram = stand.getUUID();
        setChanged();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput view) {
        super.saveAdditional(view);
        if (owner != null) {
            view.putString("Owner", owner.toString());
        }
        if (ownerName != null) {
            view.putString("OwnerName", ownerName);
        }
        if (hologram != null) {
            view.putString("Hologram", hologram.toString());
        }
        if (originalGameMode != null) {
            view.putString("OriginalGameMode", originalGameMode.name());
        }
        if (skinTextureValue != null) {
            view.putString("SkinTexture", skinTextureValue);
        }
        if (skinTextureSignature != null) {
            view.putString("SkinSignature", skinTextureSignature);
        }
        if (creationTick >= 0) {
            view.putString("CreationTick", String.valueOf(creationTick));
        }
        if (!storedItems.isEmpty()) {
            net.minecraft.world.level.storage.ValueOutput.TypedOutputList<ItemStackWithSlot> list =
                    view.list("StoredItems", ItemStackWithSlot.CODEC);
            for (ItemStackWithSlot item : storedItems) {
                list.add(item);
            }
        }
        if (storedXp > 0) {
            view.putInt("StoredXp", storedXp);
        }
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput view) {
        super.loadAdditional(view);
        view.getString("Owner").ifPresent(u -> {
            try {
                this.owner = UUID.fromString(u);
            } catch (IllegalArgumentException e) {
                ReviveGraves.LOGGER.warn("Invalid Owner UUID in gravestone NBT: {}", u);
            }
        });
        view.getString("OwnerName").ifPresent(n ->
            this.ownerName = n.length() > 16 ? n.substring(0, 16) : n
        );
        view.getString("Hologram").ifPresent(u -> {
            try {
                this.hologram = UUID.fromString(u);
            } catch (IllegalArgumentException e) {
                ReviveGraves.LOGGER.warn("Invalid Hologram UUID in gravestone NBT: {}", u);
            }
        });
        view.getString("OriginalGameMode").ifPresent(g -> {
            try {
                this.originalGameMode = GameType.valueOf(g);
            } catch (IllegalArgumentException e) {
                ReviveGraves.LOGGER.warn("Invalid GameMode in gravestone NBT: {}", g);
            }
        });
        view.getString("SkinTexture").ifPresent(v -> this.skinTextureValue = v);
        view.getString("SkinSignature").ifPresent(v -> this.skinTextureSignature = v);
        view.getString("CreationTick").ifPresent(t -> {
            try {
                this.creationTick = Long.parseLong(t);
            } catch (NumberFormatException e) {
                ReviveGraves.LOGGER.warn("Invalid CreationTick in gravestone NBT: {}", t);
            }
        });
        view.list("StoredItems", ItemStackWithSlot.CODEC).ifPresent(list -> {
            this.storedItems = new ArrayList<>();
            for (ItemStackWithSlot item : list) {
                this.storedItems.add(item);
            }
        });
        this.storedXp = view.getIntOr("StoredXp", 0);
    }
}
