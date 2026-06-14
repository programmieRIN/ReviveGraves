package de.programmierin.revivegraves.datagen;

import de.programmierin.revivegraves.ReviveGraves;
import de.programmierin.revivegraves.advancement.ModAdvancements;
import de.programmierin.revivegraves.block.ModBlocks;
import de.programmierin.revivegraves.item.ModItems;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricAdvancementProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.criterion.ImpossibleTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class ModAdvancementProvider extends FabricAdvancementProvider {

	public ModAdvancementProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registryLookup) {
		super(output, registryLookup);
	}

	@Override
	public void generateAdvancement(HolderLookup.Provider wrapperLookup, Consumer<AdvancementHolder> consumer) {
		// Root — creates the mod tab with soul sand background
		AdvancementHolder root = Advancement.Builder.advancement()
				.display(
						ModBlocks.GRAVESTONE_ITEM,
						Component.translatable("advancements.revivegraves.root.title"),
						Component.translatable("advancements.revivegraves.root.description"),
						Identifier.fromNamespaceAndPath(ReviveGraves.MOD_ID, "gui/advancements/backgrounds/soul_sand"),
						AdvancementType.TASK,
						false, // showToast
						false, // announceToChat
						false  // hidden
				)
				.addCriterion("granted", new Criterion<>(CriteriaTriggers.IMPOSSIBLE, new ImpossibleTrigger.TriggerInstance()))
				.save(consumer, ModAdvancements.ROOT.toString());

		AdvancementHolder emergencySupplies = advancement(consumer, root, ModItems.REVIVE_TOKEN, ModAdvancements.EMERGENCY_SUPPLIES, AdvancementType.TASK);
		AdvancementHolder firstFall = advancement(consumer, root, Items.SKELETON_SKULL, ModAdvancements.FIRST_FALL, AdvancementType.TASK);
		advancement(consumer, firstFall, Items.TOTEM_OF_UNDYING, ModAdvancements.FREQUENT_FLYER, AdvancementType.GOAL);
		AdvancementHolder lingeringSpirit = advancement(consumer, firstFall, Items.SOUL_LANTERN, ModAdvancements.LINGERING_SPIRIT, AdvancementType.GOAL);
		advancement(consumer, lingeringSpirit, Items.SOUL_CAMPFIRE, ModAdvancements.ETERNAL_HAUNTING, AdvancementType.CHALLENGE);
		AdvancementHolder helpingHand = advancement(consumer, root, Items.GOLDEN_APPLE, ModAdvancements.HELPING_HAND, AdvancementType.TASK);
		advancement(consumer, helpingHand, Items.ENCHANTED_GOLDEN_APPLE, ModAdvancements.MEDIC, AdvancementType.CHALLENGE);
	}

	private static AdvancementHolder advancement(Consumer<AdvancementHolder> consumer,
			AdvancementHolder parent, ItemLike icon, Identifier id, AdvancementType frame) {
		String key = "advancements." + id.getNamespace() + "." + id.getPath();
		return Advancement.Builder.advancement()
				.parent(parent)
				.display(icon,
						Component.translatable(key + ".title"),
						Component.translatable(key + ".description"),
						null, frame, true, true, false)
				.addCriterion("granted", new Criterion<>(CriteriaTriggers.IMPOSSIBLE, new ImpossibleTrigger.TriggerInstance()))
				.save(consumer, id.toString());
	}
}
