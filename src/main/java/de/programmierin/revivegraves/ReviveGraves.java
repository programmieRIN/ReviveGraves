package de.programmierin.revivegraves;

import de.programmierin.revivegraves.advancement.ModAdvancements;
import de.programmierin.revivegraves.advancement.PlayerStatsState;
import de.programmierin.revivegraves.config.ModConfig;
import de.programmierin.revivegraves.item.ModItemGroups;
import de.programmierin.revivegraves.loot.ModLootTableModifiers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import de.programmierin.revivegraves.block.ModBlocks;
import de.programmierin.revivegraves.block.custom.GravestoneBlock;
import de.programmierin.revivegraves.entity.GravestoneBlockEntity;
import de.programmierin.revivegraves.entity.ModBlockEntities;
import de.programmierin.revivegraves.item.ModItems;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.animal.chicken.ChickenSoundVariants;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import de.programmierin.revivegraves.ghost.GhostChickenState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ReviveGraves implements ModInitializer {
	public static final String MOD_ID = "revivegraves";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Tick tracking for ghost chicken sounds (random 5-15s intervals)
	private static final Map<UUID, Integer> nextSoundTick = new HashMap<>();
	// Tick when ghost was added (for time-limited gravestone sync)
	private static final Map<UUID, Integer> ghostStartTick = new HashMap<>();
	// Pending advancement announcements for JOIN-granted advancements (need delay for chat to work)
	private static final Map<UUID, List<Identifier>> pendingAnnouncements = new HashMap<>();
	private static final Map<UUID, Integer> pendingAnnouncementTick = new HashMap<>();
	// Temporary storage for inventory/XP between ALLOW_DEATH and AFTER_DEATH
	private static final Map<UUID, List<ItemStackWithSlot>> savedInventories = new HashMap<>();
	private static final Map<UUID, Integer> savedXp = new HashMap<>();

	@Override
	public void onInitialize() {
		ModConfig.load();
		ModItems.registerModItems();
		ModBlocks.registerModBlocks();
		ModItemGroups.registerItemGroups();
		ModLootTableModifiers.register();

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof ServerPlayer player)) return;

			Level raw = player.level();

			if (!(raw instanceof ServerLevel world)) return;

			GameType originalMode = player.gameMode.getGameModeForPlayer();

			double px = player.getX(), pz = player.getZ();
			BlockPos deathPos;
			if (source == world.damageSources().fellOutOfWorld()) {
				int y = world.getMinY() + 1;
				deathPos = findSafePlacement(world, new BlockPos(Mth.floor(px), y, Mth.floor(pz)));
			} else {
				deathPos = findSafePlacement(world, player.blockPosition());
			}
			world.setBlock(deathPos,
				ModBlocks.GRAVESTONE.defaultBlockState()
					.setValue(HorizontalDirectionalBlock.FACING, player.getDirection()),
				3);
			// Prevent void-fall after revive: place stone under gravestone if air below
			BlockPos below = deathPos.below();
			if (world.getBlockState(below).isAir()) {
				world.setBlock(below, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
			}
			BlockEntity be = world.getBlockEntity(deathPos);
			if (be instanceof GravestoneBlockEntity gbe) {
				gbe.setOwner(player.getUUID());
				gbe.setOwnerName(player.getGameProfile().name());
				gbe.setOriginalGameMode(originalMode);

				// Store skin texture for client-side skull rendering (independent of PlayerList)
				com.mojang.authlib.properties.Property textures = player.getGameProfile()
						.properties().get("textures").stream().findFirst().orElse(null);
				if (textures != null) {
					gbe.setSkinTexture(textures.value(), textures.signature());
				}

				// Store saved inventory in gravestone
				List<ItemStackWithSlot> items = savedInventories.remove(player.getUUID());
				if (items != null && !items.isEmpty()) {
					gbe.setStoredItems(items);
				}

				// Store saved XP in gravestone
				Integer xp = savedXp.remove(player.getUUID());
				if (xp != null && xp > 0) {
					gbe.setStoredXp(xp);
				}

				gbe.spawnHologram(world);
				gbe.setCreationTick(world.getServer().getTickCount());

				// Explicitly send BlockEntity data to all clients.
				// updateListeners() alone may not trigger a ClientboundBlockEntityDataPacket in 1.21.9.
				net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet =
						net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(gbe);
				if (packet != null) {
					for (ServerPlayer onlinePlayer : world.getServer().getPlayerList().getPlayers()) {
						onlinePlayer.connection.send(packet);
					}
				}
			}

			// Track ghost state and enforce one-gravestone-per-player invariant
			GhostChickenState ghostState = GhostChickenState.get(world.getServer());
			GhostChickenState.GraveLocation existingGrave = ghostState.getGravestoneLocation(player.getUUID());
			if (existingGrave != null) {
				ServerLevel graveWorld = existingGrave.resolveWorld(world.getServer());
				if (graveWorld != null) {
					graveWorld.removeBlock(existingGrave.pos(), false);
				}
			}
			String dimension = world.dimension().identifier().toString();
			ghostState.addGhost(player.getUUID(), dimension, deathPos);
			ghostStartTick.put(player.getUUID(), world.getServer().getTickCount());

			// Track death count and grant death advancements
			PlayerStatsState statsState = PlayerStatsState.get(world.getServer());
			statsState.incrementDeathCount(player.getUUID());
			ModAdvancements.checkDeathAdvancements(player, statsState);
		});

		ServerPlayerEvents.AFTER_RESPAWN.register((ServerPlayer oldPlayer,
												   ServerPlayer newPlayer,
												   boolean alive) -> {
			if (!alive) {
				GhostChickenState ghostState = GhostChickenState.get(((ServerLevel) newPlayer.level()).getServer());
				if (ghostState.isGhost(newPlayer.getUUID())) {
					GhostChickenState.applyGhostState(newPlayer);

					// Teleport ghost to gravestone if configured
					if (ModConfig.INSTANCE.ghost.spawnAtGravestone) {
						GhostChickenState.GraveLocation graveLoc = ghostState.getGravestoneLocation(newPlayer.getUUID());
						if (graveLoc != null) {
							ServerLevel graveWorld = graveLoc.resolveWorld(((ServerLevel) newPlayer.level()).getServer());
							if (graveWorld != null) {
								teleportToGrave(newPlayer, graveWorld, graveLoc.pos());
							}
						}
					}
				}
			}
		});

		// Block all damage from/to ghost players
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			// Ghost takes no damage
			if (entity instanceof ServerPlayer target) {
				GhostChickenState gs = GhostChickenState.get(((ServerLevel) target.level()).getServer());
				if (gs.isGhost(target.getUUID())) {
					return false;
				}
			}
			// Ghost deals no damage
			if (source.getEntity() instanceof ServerPlayer attacker) {
				GhostChickenState gs = GhostChickenState.get(((ServerLevel) attacker.level()).getServer());
				if (gs.isGhost(attacker.getUUID())) {
					return false;
				}
			}
			return true;
		});

		// Suppress death for ghost players + save inventory/XP before death
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player) {
				GhostChickenState ghostState = GhostChickenState.get(((ServerLevel) player.level()).getServer());
				if (ghostState.isGhost(player.getUUID())) {
					return false;
				}

				// Save inventory before death processing drops items
				if (ModConfig.INSTANCE.gravestone.storeItems) {
					List<ItemStackWithSlot> items = new ArrayList<>();
					var inv = player.getInventory();
					for (int i = 0; i < inv.getContainerSize(); i++) {
						ItemStack stack = inv.getItem(i);
						if (!stack.isEmpty()) {
							items.add(new ItemStackWithSlot(i, stack.copy()));
						}
					}
					savedInventories.put(player.getUUID(), items);
					inv.clearContent();
				}

				// Save XP before death processing drops orbs
				if (ModConfig.INSTANCE.gravestone.storeXp) {
					savedXp.put(player.getUUID(), player.totalExperience);
					player.experienceLevel = 0;
					player.experienceProgress = 0;
					player.totalExperience = 0;
				}
			}
			return true;
		});

		Registry.register(
				BuiltInRegistries.BLOCK_ENTITY_TYPE,
				Identifier.fromNamespaceAndPath(MOD_ID, "gravestone"),
				ModBlockEntities.GRAVESTONE
		);

		// Block ghost chickens from interacting with containers (furnaces, chests, etc.)
		// Only allow doors, trapdoors, fence gates, buttons, levers
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world2, hand, hitResult) -> {
			if (world2.isClientSide()) return net.minecraft.world.InteractionResult.PASS;
			if (!(player instanceof ServerPlayer serverPlayer)) return net.minecraft.world.InteractionResult.PASS;
			GhostChickenState gs = GhostChickenState.get(((ServerLevel) world2).getServer());
			if (!gs.isGhost(serverPlayer.getUUID())) return net.minecraft.world.InteractionResult.PASS;

			// Allow interaction with doors, trapdoors, fence gates, buttons, levers, gravestone
			net.minecraft.world.level.block.Block block = world2.getBlockState(hitResult.getBlockPos()).getBlock();
			if (block instanceof net.minecraft.world.level.block.DoorBlock
					|| block instanceof net.minecraft.world.level.block.TrapDoorBlock
					|| block instanceof net.minecraft.world.level.block.FenceGateBlock
					|| block instanceof net.minecraft.world.level.block.ButtonBlock
					|| block instanceof net.minecraft.world.level.block.LeverBlock
					|| block instanceof GravestoneBlock) {
				return net.minecraft.world.InteractionResult.PASS;
			}

			// Block everything else (containers, crafting tables, etc.)
			return net.minecraft.world.InteractionResult.FAIL;
		});

		// Block ghost chickens from interacting with entities (armor stands, item frames, etc.)
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world2, hand, entity2, hitResult) -> {
			if (world2.isClientSide()) return net.minecraft.world.InteractionResult.PASS;
			if (!(player instanceof ServerPlayer serverPlayer)) return net.minecraft.world.InteractionResult.PASS;
			GhostChickenState gs2 = GhostChickenState.get(((ServerLevel) world2).getServer());
			if (!gs2.isGhost(serverPlayer.getUUID())) return net.minecraft.world.InteractionResult.PASS;
			return net.minecraft.world.InteractionResult.FAIL;
		});

		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> {
			if (state.getBlock() instanceof GravestoneBlock) {
				if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer) {
					serverPlayer.sendSystemMessage(Component.translatable("message.revivegraves.gravestone_indestructible"), false);
				}
				return false;
			}
			return true;
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			// Process pending advancement announcements (delayed from JOIN)
			if (!pendingAnnouncementTick.isEmpty()) {
				int tick = server.getTickCount();
				for (var it = pendingAnnouncementTick.entrySet().iterator(); it.hasNext(); ) {
					var entry = it.next();
					if (tick >= entry.getValue()) {
						UUID uuid = entry.getKey();
						ServerPlayer player = server.getPlayerList().getPlayer(uuid);
						List<Identifier> advancements = pendingAnnouncements.remove(uuid);
						it.remove();
						if (player != null && advancements != null) {
							for (Identifier advId : advancements) {
								ModAdvancements.announceAdvancement(player, advId);
							}
						}
					}
				}
			}

			GhostChickenState ghostState = GhostChickenState.get(server);
			PlayerStatsState statsState = PlayerStatsState.get(server);
			int tick = server.getTickCount();

			for (UUID uuid : new java.util.ArrayList<>(ghostState.getGhostPlayerUuids())) {
				ServerPlayer ghost = server.getPlayerList().getPlayer(uuid);
				if (ghost == null) continue;

				// Hardcore worlds force the player back to SPECTATOR right after our
				// AFTER_RESPAWN handler applied ADVENTURE (vanilla handleClientCommand override
				// runs after PlayerList.respawn returns), which let ghosts fly and phase through
				// walls. Re-assert the ghost game mode for living (already respawned) ghosts.
				// Guarded on health so we never touch a still-dead player on the death screen.
				// setGameMode is a no-op when the mode is already unchanged.
				if (ghost.getHealth() > 0.0F && ghost.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
					ghost.setGameMode(GameType.ADVENTURE);
				}

				// Soul particles every 10 ticks
				if (ModConfig.INSTANCE.ghost.particlesEnabled && tick % 10 == 0) {
					ServerLevel ghostWorld = (ServerLevel) ghost.level();
					ghostWorld.sendParticles(
							ParticleTypes.SOUL_FIRE_FLAME,
							ghost.getX(), ghost.getY() + 0.3, ghost.getZ(),
							2,
							0.15, 0.1, 0.15,
							0.0
					);
				}

				// Slow falling: disable in water so ghost can swim, re-add on land
				if (ModConfig.INSTANCE.ghost.slowFallingEnabled) {
					if (ghost.isInWater()) {
						ghost.removeEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING);
					} else if (!ghost.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING)) {
						ghost.addEffect(new net.minecraft.world.effect.MobEffectInstance(
								net.minecraft.world.effect.MobEffects.SLOW_FALLING,
								net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION,
								0, true, false, false
						));
					}
				}

				// Block sprinting for ghosts
				if (!ModConfig.INSTANCE.ghost.sprintEnabled && ghost.isSprinting()) {
					ghost.setSprinting(false);
				}

				// Chicken sounds at random 5-15 second intervals
				int nextTick = nextSoundTick.getOrDefault(uuid, 0);
				if (tick >= nextTick) {
					ServerLevel ghostWorld = (ServerLevel) ghost.level();
					ghostWorld.playSound(
							null,
							ghost.blockPosition(),
							SoundEvents.CHICKEN_SOUNDS.get(ChickenSoundVariants.SoundSet.CLASSIC)
									.adultSounds().ambientSound().value(),
							SoundSource.NEUTRAL,
							1.0f, 1.0f
					);
					nextSoundTick.put(uuid, tick + 100 + ThreadLocalRandom.current().nextInt(201));
				}

				// Void protection: teleport to gravestone if too far below world
				if (ghost.getY() < ghost.level().getMinY() - 10) {
					GhostChickenState.GraveLocation graveLoc = ghostState.getGravestoneLocation(uuid);
					if (graveLoc != null) {
						ServerLevel graveWorld = graveLoc.resolveWorld(server);
						if (graveWorld != null) {
							teleportToGrave(ghost, graveWorld, graveLoc.pos());
						}
					}
				}

				// Re-trigger gravestone block entity sync for the first 5 seconds only
				int startTick = ghostStartTick.getOrDefault(uuid, tick);
				if (tick % 20 == 0 && tick - startTick < 100) {
					GhostChickenState.GraveLocation graveLoc = ghostState.getGravestoneLocation(uuid);
					if (graveLoc != null) {
						ServerLevel graveWorld = graveLoc.resolveWorld(server);
						if (graveWorld != null) {
							BlockPos gravePos = graveLoc.pos();
							BlockEntity be = graveWorld.getBlockEntity(gravePos);
							if (be instanceof GravestoneBlockEntity) {
								graveWorld.sendBlockUpdated(gravePos, graveWorld.getBlockState(gravePos),
										graveWorld.getBlockState(gravePos), 3);
							}
						}
					}
				}

				// Firefly particles around gravestone every 10 ticks
				if (ModConfig.INSTANCE.gravestone.fireflyParticlesEnabled && tick % 10 == 0) {
					GhostChickenState.GraveLocation fireflyLoc = ghostState.getGravestoneLocation(uuid);
					if (fireflyLoc != null) {
						ServerLevel fireflyWorld = fireflyLoc.resolveWorld(server);
						if (fireflyWorld != null) {
							BlockPos gPos = fireflyLoc.pos();
							fireflyWorld.sendParticles(
									ParticleTypes.FIREFLY,
									gPos.getX() + 0.5, gPos.getY() + 0.5, gPos.getZ() + 0.5,
									3,
									0.6, 0.5, 0.6,
									0.0
							);
						}
					}
				}

				// Gravestone timer + hologram countdown (every second)
				if (ModConfig.INSTANCE.gravestone.timerEnabled && tick % 20 == 0) {
					GhostChickenState.GraveLocation graveLoc = ghostState.getGravestoneLocation(uuid);
					if (graveLoc != null) {
						ServerLevel graveWorld = graveLoc.resolveWorld(server);
						if (graveWorld != null) {
							BlockEntity timerBe = graveWorld.getBlockEntity(graveLoc.pos());
							if (timerBe instanceof GravestoneBlockEntity timerGbe) {
								long creationTick = timerGbe.getCreationTick();
								if (creationTick >= 0) {
									long elapsedSeconds = (tick - creationTick) / 20;
									long remainingSeconds = ModConfig.INSTANCE.gravestone.timerSeconds - elapsedSeconds;

									if (remainingSeconds <= 0) {
										// Timer expired — remove gravestone + hologram, ghost stays ghost
										timerGbe.discardHologram(graveWorld);
										graveWorld.removeBlock(graveLoc.pos(), false);
										ghostState.setGravestoneExpired(uuid);
										ghost.sendSystemMessage(Component.translatable("message.revivegraves.gravestone_expired"), false);
									} else {
										// Update hologram with countdown
										UUID holoId = timerGbe.getHologram();
										if (holoId != null) {
											Entity holo = graveWorld.getEntity(holoId);
											if (holo != null) {
												int minutes = (int)(remainingSeconds / 60);
												int seconds = (int)(remainingSeconds % 60);
												String name = timerGbe.getOwnerName() != null ? timerGbe.getOwnerName() : "???";
												holo.setCustomName(Component.literal(
														String.format("%s - %d:%02d", name, minutes, seconds)));
											}
										}
									}
								}
							}
						}
					}
				}

				// Accumulate ghost time and check ghost time advancements (every second)
				if (tick % 20 == 0) {
					statsState.addGhostTicks(uuid, 20);
					ModAdvancements.checkGhostTimeAdvancements(ghost, statsState);
				}

			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();

			// Grant root advancement (creates mod tab, idempotent)
			ModAdvancements.grant(player, ModAdvancements.ROOT);

			// Migrate old advancement marker: grant EMERGENCY_SUPPLIES if player had old first_join_tokens
			Identifier oldAdvId = Identifier.fromNamespaceAndPath(ReviveGraves.MOD_ID, "first_join_tokens");
			var oldAdvancement = server.getAdvancements().get(oldAdvId);
			if (oldAdvancement != null && player.getAdvancements().getOrStartProgress(oldAdvancement).isDone()) {
				ModAdvancements.grant(player, ModAdvancements.EMERGENCY_SUPPLIES);
			}

			// Start token grant for new players
			List<Identifier> announcements = new ArrayList<>();
			if (ModConfig.INSTANCE.startTokens.enabled) {
				if (!ModAdvancements.isGranted(player, ModAdvancements.EMERGENCY_SUPPLIES)) {
					int amount = ModConfig.INSTANCE.startTokens.amount;
					if (amount > 0) {
						net.minecraft.world.item.ItemStack tokens = new net.minecraft.world.item.ItemStack(
								ModItems.REVIVE_TOKEN, amount);
						if (!player.getInventory().add(tokens)) {
							player.drop(tokens, false);
						}
						LOGGER.info("Granted {} start tokens to new player {}",
								amount, player.getGameProfile().name());
					}
					ModAdvancements.grant(player, ModAdvancements.EMERGENCY_SUPPLIES);
					announcements.add(ModAdvancements.EMERGENCY_SUPPLIES);
				}
			}

			// Schedule announcements for 2 seconds later (chat not ready during JOIN)
			if (!announcements.isEmpty()) {
				pendingAnnouncements.put(player.getUUID(), announcements);
				pendingAnnouncementTick.put(player.getUUID(), server.getTickCount() + 40);
			}

			GhostChickenState ghostState = GhostChickenState.get(server);
			if (!ghostState.isGhost(player.getUUID())) return;

			// Verify gravestone still exists (cross-dimension)
			GhostChickenState.GraveLocation graveLoc = ghostState.getGravestoneLocation(player.getUUID());
			if (graveLoc != null) {
				ServerLevel graveWorld = graveLoc.resolveWorld(server);
				if (graveWorld != null) {
					graveWorld.getChunk(graveLoc.pos());
					BlockEntity be = graveWorld.getBlockEntity(graveLoc.pos());
					if (be instanceof GravestoneBlockEntity gbe && player.getUUID().equals(gbe.getOwner())) {
						GhostChickenState.applyGhostState(player);
						return;
					}
				}
			}
			// Gravestone gone — clean up
			ghostState.removeGhost(player.getUUID());
			clearGhostTickData(player.getUUID());
		});

		// Clean up pending announcements on disconnect
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID uuid = handler.getPlayer().getUUID();
			pendingAnnouncements.remove(uuid);
			pendingAnnouncementTick.remove(uuid);
		});
	}

	/**
	 * Removes tick tracking data for a ghost player (called on revive or cleanup).
	 */
	public static void teleportToGrave(ServerPlayer player, ServerLevel graveWorld, BlockPos gravePos) {
		player.teleportTo(graveWorld,
				gravePos.getX() + 0.5, gravePos.getY() + 1.0, gravePos.getZ() + 0.5,
				EnumSet.noneOf(Relative.class), player.getYRot(), player.getXRot(), false);
	}

	public static void clearGhostTickData(UUID uuid) {
		nextSoundTick.remove(uuid);
		ghostStartTick.remove(uuid);
	}

	private static BlockPos findSafePlacement(ServerLevel world, BlockPos pos) {
		if (world.getBlockState(pos).canBeReplaced()) {
			return pos;
		}
		// Search upward for a replaceable block
		for (int dy = 1; dy <= 5; dy++) {
			BlockPos up = pos.above(dy);
			if (world.getBlockState(up).canBeReplaced()) {
				return up;
			}
		}
		// Fallback: place at original position regardless
		return pos;
	}
}
