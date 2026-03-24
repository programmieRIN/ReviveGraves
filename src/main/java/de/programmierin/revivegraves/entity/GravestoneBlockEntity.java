package de.programmierin.revivegraves.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;

import java.util.UUID;
import java.util.function.Function;

public class GravestoneBlockEntity extends BlockEntity {
    private UUID owner;
    private UUID hologram;
    private GameMode originalGameMode;

    public GravestoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GRAVESTONE, pos, state);
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        markDirty();
    }

    public UUID getOwner() {
        return owner;
    }

    public UUID getHologram() {
        return hologram;
    }

    public GameMode getOriginalGameMode() {
        return originalGameMode;
    }

    public void setOriginalGameMode(GameMode mode) {
        this.originalGameMode = mode;
        markDirty();
    }

    public void spawnHologram(ServerWorld world) {
        if (owner == null || hologram != null) return;

        ServerPlayerEntity player = world.getServer()
                .getPlayerManager()
                .getPlayer(owner);
        if (player == null) return;
        String name = player.getGameProfile().getName();

        NbtCompound tag = new NbtCompound();
        tag.putString("id", "minecraft:armor_stand");
        tag.putString("CustomName", Text.literal(name).toString());
        tag.putBoolean("CustomNameVisible", true);
        tag.putBoolean("Invisible", true);
        tag.putBoolean("NoGravity", true);
        tag.putBoolean("Invulnerable", true);
        tag.putBoolean("Small", true);
        tag.putBoolean("NoBasePlate", true);
        tag.putBoolean("Marker", true);

        Entity loaded = EntityType.loadEntityWithPassengers(tag, world, SpawnReason.TRIGGERED, Function.identity());
        if (!(loaded instanceof ArmorStandEntity stand)) return;

        stand.setCustomName(Text.literal(name));
        stand.setCustomNameVisible(true);

        Direction facing = world.getBlockState(getPos()).get(HorizontalFacingBlock.FACING);
        double offset = 0.25;
        double dx = -facing.getOffsetX() * offset;
        double dz = -facing.getOffsetZ() * offset;
        double x = getPos().getX() + 0.5 + dx;
        double y = getPos().getY() + 1.1;
        double z = getPos().getZ() + 0.5 + dz;
        stand.refreshPositionAndAngles(x, y, z, 0f, 0f);

        world.spawnEntity(stand);
        this.hologram = stand.getUuid();
        markDirty();
    }

    public void removeHologram(ServerWorld world) {
        if (hologram != null) {
            Entity holo = world.getEntity(hologram);
            if (holo != null) {
                holo.discard();
            }
            hologram = null;
        }
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Override
    protected void writeData(net.minecraft.storage.WriteView view) {
        super.writeData(view);
        if (owner != null) {
            view.putString("Owner", owner.toString());
        }
        if (hologram != null) {
            view.putString("Hologram", hologram.toString());
        }
        if (originalGameMode != null) {
            view.putString("OriginalGameMode", originalGameMode.name());
        }
    }

    @Override
    protected void readData(net.minecraft.storage.ReadView view) {
        super.readData(view);
        view.getOptionalString("Owner").ifPresent(u -> {
            try {
                this.owner = UUID.fromString(u);
            } catch (IllegalArgumentException e) {
                this.owner = null;
            }
        });
        view.getOptionalString("Hologram").ifPresent(u -> {
            try {
                this.hologram = UUID.fromString(u);
            } catch (IllegalArgumentException e) {
                this.hologram = null;
            }
        });
        view.getOptionalString("OriginalGameMode").ifPresent(s -> {
            try {
                this.originalGameMode = GameMode.valueOf(s);
            } catch (IllegalArgumentException e) {
                this.originalGameMode = GameMode.SURVIVAL;
            }
        });
    }
}
