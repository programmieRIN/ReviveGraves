package de.programmierin.revivegraves;

import de.programmierin.revivegraves.item.ModItemGroups;
import de.programmierin.revivegraves.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import de.programmierin.revivegraves.block.ModBlocks;
import de.programmierin.revivegraves.block.custom.GravestoneBlock;
import de.programmierin.revivegraves.entity.GravestoneBlockEntity;
import de.programmierin.revivegraves.entity.ModBlockEntities;
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
		ModItems.registerModItems();
		ModBlocks.registerModBlocks();
		ModItemGroups.registerItemGroups();

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof ServerPlayerEntity player)) return;
			World raw = player.getWorld();
			if (!(raw instanceof ServerWorld world)) return;

			GameMode originalMode = player.interactionManager.getGameMode();

			double px = player.getX(), pz = player.getZ();
			BlockPos deathPos;
			if (source == world.getDamageSources().outOfWorld()) {
				int y = world.getBottomY() + 1;
				deathPos = new BlockPos(MathHelper.floor(px), y, MathHelper.floor(pz));
			} else {
				deathPos = player.getBlockPos();
			}

			BlockPos safePos = findSafePlacement(world, deathPos);
			world.setBlockState(safePos, ModBlocks.GRAVESTONE.getDefaultState(), 3);
			BlockEntity be = world.getBlockEntity(safePos);
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
	}

	private static BlockPos findSafePlacement(ServerWorld world, BlockPos origin) {
		if (isReplaceable(world, origin)) return origin;

		for (int dy = 1; dy <= 5; dy++) {
			BlockPos up = origin.up(dy);
			if (isReplaceable(world, up)) return up;
			BlockPos down = origin.down(dy);
			if (down.getY() >= world.getBottomY() && isReplaceable(world, down)) return down;
		}

		for (BlockPos nearby : BlockPos.iterate(origin.add(-2, -2, -2), origin.add(2, 2, 2))) {
			if (isReplaceable(world, nearby)) return nearby.toImmutable();
		}

		return origin;
	}

	private static boolean isReplaceable(ServerWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return state.isReplaceable();
	}
}
