package de.programmierin.revivegraves.loot;

import de.programmierin.revivegraves.ReviveGraves;
import de.programmierin.revivegraves.config.ModConfig;
import de.programmierin.revivegraves.item.ModItems;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

public class ModLootTableModifiers {
	public static void register() {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			if (!source.isBuiltin()) return;
			if (!ModConfig.INSTANCE.loot.enabled) return;

			if (BuiltInLootTables.END_CITY_TREASURE == key) {
				tableBuilder.withPool(LootPool.lootPool()
						.setRolls(ConstantValue.exactly(1))
						.when(LootItemRandomChanceCondition.randomChance(ModConfig.INSTANCE.loot.endCityChance))
						.add(LootItem.lootTableItem(ModItems.REVIVE_TOKEN))
				);
				ReviveGraves.LOGGER.debug("Injected Revive Token into End City treasure loot table (chance: {})",
						ModConfig.INSTANCE.loot.endCityChance);
			}

			if (BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS == key) {
				tableBuilder.withPool(LootPool.lootPool()
						.setRolls(ConstantValue.exactly(1))
						.when(LootItemRandomChanceCondition.randomChance(ModConfig.INSTANCE.loot.ominousVaultChance))
						.add(LootItem.lootTableItem(ModItems.REVIVE_TOKEN))
				);
				ReviveGraves.LOGGER.debug("Injected Revive Token into Ominous Trial Vault loot table (chance: {})",
						ModConfig.INSTANCE.loot.ominousVaultChance);
			}
		});
	}
}
