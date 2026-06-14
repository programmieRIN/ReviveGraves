package de.programmierin.revivegraves.block.custom;

import com.mojang.serialization.MapCodec;
import de.programmierin.revivegraves.ReviveGraves;
import de.programmierin.revivegraves.advancement.ModAdvancements;
import de.programmierin.revivegraves.advancement.PlayerStatsState;
import de.programmierin.revivegraves.config.ModConfig;
import de.programmierin.revivegraves.entity.GravestoneBlockEntity;
import de.programmierin.revivegraves.ghost.GhostChickenState;
import de.programmierin.revivegraves.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class GravestoneBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<GravestoneBlock> CODEC = simpleCodec(GravestoneBlock::new);
    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 4.0, 14.0, 16.0, 13.0);

    public GravestoneBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, this.defaultBlockState().getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GravestoneBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // Prevent manual placement — gravestone is only created programmatically on death
        return null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state,
                                               Level world,
                                               BlockPos pos,
                                               Player clicker,
                                               BlockHitResult hit) {
        if (world.isClientSide()) return InteractionResult.PASS;

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof GravestoneBlockEntity gbe)) {
            return super.useWithoutItem(state, world, pos, clicker, hit);
        }

        UUID ownerUuid = gbe.getOwner();
        if (ownerUuid == null) {
            return super.useWithoutItem(state, world, pos, clicker, hit);
        }

        ServerPlayer dead = ((ServerLevel) world)
                .getServer()
                .getPlayerList()
                .getPlayer(ownerUuid);

        GhostChickenState ghostState = GhostChickenState.get(((ServerLevel) world).getServer());
        if (dead != null && ghostState.isGhost(dead.getUUID()) && dead.getHealth() > 0) {

            // Check for revive token first
            ItemStack main = clicker.getMainHandItem();
            ItemStack off  = clicker.getOffhandItem();
            boolean hasToken = main.getItem() == ModItems.REVIVE_TOKEN
                    || off.getItem() == ModItems.REVIVE_TOKEN;

            if (!hasToken) {
                clicker.sendSystemMessage(Component.translatable("message.revivegraves.need_token"));
                return InteractionResult.CONSUME;
            }

            ServerLevel serverWorld = (ServerLevel) world;
            ReviveGraves.teleportToGrave(dead, serverWorld, pos);

            // Consume token after successful teleport
            if (main.getItem() == ModItems.REVIVE_TOKEN) {
                main.shrink(1);
            } else {
                off.shrink(1);
            }

            // 1. Remove from ghost tracking FIRST (stops packet suppression)
            ghostState.removeGhost(dead.getUUID());
            ReviveGraves.clearGhostTickData(dead.getUUID());

            // 2. Remove effects (invisibility, invulnerable, speed, re-list in tab)
            GhostChickenState.removeGhostState(dead);

            // 3. Explicitly clear invisible flag (safety — ensures DataTracker has correct value)
            dead.setInvisible(false);

            // 4. Restore original game mode BEFORE tracker refresh
            //    so the fresh spawn packet has the correct game mode
            GameType originalMode = gbe.getOriginalGameMode();
            dead.setGameMode(originalMode != null ? originalMode : GameType.SURVIVAL);

            // 4b. Restore stored inventory
            if (ModConfig.INSTANCE.gravestone.storeItems && !gbe.getStoredItems().isEmpty()) {
                for (ItemStackWithSlot item : gbe.getStoredItems()) {
                    if (item.slot() >= 0 && item.slot() < dead.getInventory().getContainerSize()) {
                        dead.getInventory().setItem(item.slot(), item.stack().copy());
                    } else {
                        dead.drop(item.stack().copy(), false);
                    }
                }
            }

            // 4c. Restore stored XP
            if (ModConfig.INSTANCE.gravestone.storeXp && gbe.getStoredXp() > 0) {
                dead.giveExperiencePoints(gbe.getStoredXp());
            }

            // 5. Force entity tracker refresh so other clients see a player again
            //    This sends fresh spawn + metadata packets with all current state
            serverWorld.getChunkSource().removeEntity(dead);
            serverWorld.getChunkSource().addEntity(dead);

            // Revive particles: purple portal mist + white light points
            serverWorld.sendParticles(
                    ParticleTypes.REVERSE_PORTAL,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    25,
                    0.3, 0.5, 0.3,
                    0.05
            );
            serverWorld.sendParticles(
                    ParticleTypes.END_ROD,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    12,
                    0.2, 0.4, 0.2,
                    0.02
            );

            // Revive sound: deep resonant anchor tone
            serverWorld.playSound(
                    null,
                    pos,
                    SoundEvents.RESPAWN_ANCHOR_SET_SPAWN,
                    SoundSource.BLOCKS,
                    1f,
                    1f
            );

            gbe.discardHologram(serverWorld);
            world.removeBlock(pos, false);

            // Track revive stats and grant advancements for the reviver
            if (clicker instanceof ServerPlayer reviver) {
                PlayerStatsState statsState = PlayerStatsState.get(serverWorld.getServer());
                statsState.incrementReviveCount(reviver.getUUID());
                ModAdvancements.checkReviveAdvancements(reviver, statsState);
            }

            return InteractionResult.SUCCESS;
        }

        return super.useWithoutItem(state, world, pos, clicker, hit);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof GravestoneBlockEntity gbe) {
            gbe.discardHologram(world);
        }
        super.affectNeighborsAfterRemoval(state, world, pos, moved);
    }
}
