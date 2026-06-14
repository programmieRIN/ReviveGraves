package de.programmierin.revivegraves.advancement;

import de.programmierin.revivegraves.ReviveGraves;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Advancement ID constants and helper methods for granting advancements.
 */
public class ModAdvancements {

	// Advancement IDs
	public static final Identifier ROOT = Identifier.fromNamespaceAndPath(ReviveGraves.MOD_ID, "root");
	public static final Identifier EMERGENCY_SUPPLIES = Identifier.fromNamespaceAndPath(ReviveGraves.MOD_ID, "emergency_supplies");
	public static final Identifier FIRST_FALL = Identifier.fromNamespaceAndPath(ReviveGraves.MOD_ID, "first_fall");
	public static final Identifier FREQUENT_FLYER = Identifier.fromNamespaceAndPath(ReviveGraves.MOD_ID, "frequent_flyer");
	public static final Identifier LINGERING_SPIRIT = Identifier.fromNamespaceAndPath(ReviveGraves.MOD_ID, "lingering_spirit");
	public static final Identifier ETERNAL_HAUNTING = Identifier.fromNamespaceAndPath(ReviveGraves.MOD_ID, "eternal_haunting");
	public static final Identifier HELPING_HAND = Identifier.fromNamespaceAndPath(ReviveGraves.MOD_ID, "helping_hand");
	public static final Identifier MEDIC = Identifier.fromNamespaceAndPath(ReviveGraves.MOD_ID, "medic");

	// Ghost time thresholds in ticks
	public static final long GHOST_TICKS_30_MIN = 30L * 60 * 20;      // 36,000
	public static final long GHOST_TICKS_2_HOURS = 2L * 60 * 60 * 20; // 144,000

	/**
	 * Grants an advancement to a player if not already completed.
	 * @return true if the advancement was newly granted
	 */
	public static boolean grant(ServerPlayer player, Identifier advancementId) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return false;

		AdvancementHolder advancementEntry = server.getAdvancements().get(advancementId);
		if (advancementEntry == null) return false;

		AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancementEntry);
		if (progress.isDone()) return false;

		player.getAdvancements().award(advancementEntry, "granted");
		return true;
	}

	/**
	 * Broadcasts an advancement chat announcement manually.
	 * Used for advancements granted during JOIN where vanilla's announcement doesn't fire.
	 */
	public static void announceAdvancement(ServerPlayer player, Identifier advancementId) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return;

		AdvancementHolder advancementEntry = server.getAdvancements().get(advancementId);
		if (advancementEntry == null) return;

		DisplayInfo display = advancementEntry.value().display().orElse(null);
		if (display == null || !display.shouldAnnounceChat()) return;

		String translationKey = switch (display.getType()) {
			case GOAL -> "chat.type.advancement.goal";
			case CHALLENGE -> "chat.type.advancement.challenge";
			default -> "chat.type.advancement.task";
		};
		// Use vanilla's formatted text (brackets + hover with description)
		Component advancementText = Advancement.name(advancementEntry);
		Component message = Component.translatable(translationKey, player.getDisplayName(), advancementText);
		for (ServerPlayer onlinePlayer : server.getPlayerList().getPlayers()) {
			onlinePlayer.sendSystemMessage(message, false);
		}
	}

	/**
	 * Checks if a player has already completed an advancement.
	 */
	public static boolean isGranted(ServerPlayer player, Identifier advancementId) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return false;

		AdvancementHolder advancementEntry = server.getAdvancements().get(advancementId);
		if (advancementEntry == null) return false;

		return player.getAdvancements().getOrStartProgress(advancementEntry).isDone();
	}

	/**
	 * Checks and grants death-related advancements based on current stats.
	 */
	public static void checkDeathAdvancements(ServerPlayer player, PlayerStatsState statsState) {
		int deaths = statsState.getStats(player.getUUID()).deathCount();
		if (deaths >= 1) grant(player, FIRST_FALL);
		if (deaths >= 5) grant(player, FREQUENT_FLYER);
	}

	/**
	 * Checks and grants revive-related advancements based on current stats.
	 */
	public static void checkReviveAdvancements(ServerPlayer player, PlayerStatsState statsState) {
		int revives = statsState.getStats(player.getUUID()).reviveCount();
		if (revives >= 1) grant(player, HELPING_HAND);
		if (revives >= 5) grant(player, MEDIC);
	}

	/**
	 * Checks and grants ghost time advancements based on cumulative ghost ticks.
	 */
	public static void checkGhostTimeAdvancements(ServerPlayer player, PlayerStatsState statsState) {
		long ghostTicks = statsState.getStats(player.getUUID()).ghostTicks();
		if (ghostTicks >= GHOST_TICKS_30_MIN) grant(player, LINGERING_SPIRIT);
		if (ghostTicks >= GHOST_TICKS_2_HOURS) grant(player, ETERNAL_HAUNTING);
	}
}
