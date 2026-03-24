package de.programmierin.revivegraves;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import de.programmierin.revivegraves.block.ModBlocks;
import de.programmierin.revivegraves.block.custom.GravestoneBlock;
import de.programmierin.revivegraves.entity.GravestoneBlockEntity;
import de.programmierin.revivegraves.entity.ModBlockEntities;
import de.programmierin.revivegraves.item.ModItemGroups;
import de.programmierin.revivegraves.item.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReviveGraves implements ModInitializer {
	public static final String MOD_ID = "revivegraves";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof ServerPlayerEntity player)) return;
			World raw = player.getWorld();
			if (!(raw instanceof ServerWorld world)) return;

			GameMode originalMode = player.interactionManager.getGameMode();

			double px = player.getX(), pz = player.getZ();
			BlockPos deathPos;
			if (source == world.getDamageSources().outOfWorld()) {
				int y = world.getBottomY() + 1;
				deathPos = findSafePlacement(world, new BlockPos(MathHelper.floor(px), y, MathHelper.floor(pz)));
			} else {
				deathPos = findSafePlacement(world, player.getBlockPos());
			}
			world.setBlockState(deathPos, ModBlocks.GRAVESTONE.getDefaultState(), 3);
			BlockEntity be = world.getBlockEntity(deathPos);
			if (be instanceof GravestoneBlockEntity gbe) {
				gbe.setOwner(player.getUuid());
				gbe.setOriginalGameMode(originalMode);
				gbe.spawnHologram(world);
			}
		});

		ServerPlayerEvents.AFTER_RESPAWN.register((ServerPlayerEntity oldPlayer,
												   ServerPlayerEntity newPlayer,
												   boolean alive) -> {
			if (!alive) {
				newPlayer.changeGameMode(GameMode.SPECTATOR);
			}
		});

		Registry.register(
				Registries.BLOCK_ENTITY_TYPE,
				Identifier.of(MOD_ID, "gravestone"),
				ModBlockEntities.GRAVESTONE
		);

		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> {
			if (state.getBlock() instanceof GravestoneBlock) {
				if (!world.isClient) {
					player.sendMessage(Text.translatable("message.revivegraves.gravestone_indestructible"), false);
				}
				return false;
			}
			return true;
		});

		ModItemGroups.registerItemGroups();
		ModItems.registerModItems();
		ModBlocks.registerModBlocks();
	}

	private static BlockPos findSafePlacement(ServerWorld world, BlockPos pos) {
		BlockPos.Mutable mutable = pos.mutableCopy();
		int maxY = world.getTopY() - 1;
		while (mutable.getY() < maxY) {
			BlockState state = world.getBlockState(mutable);
			if (state.isAir()) {
				return mutable.toImmutable();
			}
			mutable.move(0, 1, 0);
		}
		return pos;
	}
}
