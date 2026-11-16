package net.parz.skyblock;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Static list of Hypixel SkyBlock skills and collections with the item that
 * SkyBlock uses as icon. Collections found in /collection that are not in this
 * list are added automatically at runtime.
 */
public final class SkyBlockCatalog {
	public record Entry(String name, String category, String itemId) {
		public String key() {
			return name.toLowerCase(Locale.ROOT);
		}
	}

	public static final List<Entry> SKILLS = new ArrayList<>();
	/** Category -> collections. */
	public static final Map<String, List<Entry>> COLLECTIONS = new LinkedHashMap<>();
	public static final List<String> COLLECTION_CATEGORIES = List.of("Farming", "Mining", "Combat", "Foraging", "Fishing");

	private static final Map<String, ItemStack> STACK_CACHE = new HashMap<>();

	private SkyBlockCatalog() {
	}

	private static void skill(String name, String item) {
		SKILLS.add(new Entry(name, "Skill", item));
	}

	private static void col(String category, String name, String item) {
		COLLECTIONS.computeIfAbsent(category, k -> new ArrayList<>()).add(new Entry(name, category, item));
	}

	static {
		skill("Farming", "golden_hoe");
		skill("Mining", "stone_pickaxe");
		skill("Combat", "stone_sword");
		skill("Foraging", "jungle_sapling");
		skill("Fishing", "fishing_rod");
		skill("Enchanting", "enchanting_table");
		skill("Alchemy", "brewing_stand");
		skill("Taming", "ghast_spawn_egg");
		skill("Carpentry", "crafting_table");
		skill("Runecrafting", "magma_cream");
		skill("Social", "emerald");
		skill("Hunting", "lead");

		// Farming
		col("Farming", "Cactus", "cactus");
		col("Farming", "Carrot", "carrot");
		col("Farming", "Cocoa Beans", "cocoa_beans");
		col("Farming", "Feather", "feather");
		col("Farming", "Leather", "leather");
		col("Farming", "Melon", "melon_slice");
		col("Farming", "Mushroom", "red_mushroom");
		col("Farming", "Mutton", "mutton");
		col("Farming", "Nether Wart", "nether_wart");
		col("Farming", "Potato", "potato");
		col("Farming", "Pumpkin", "pumpkin");
		col("Farming", "Raw Chicken", "chicken");
		col("Farming", "Raw Porkchop", "porkchop");
		col("Farming", "Raw Rabbit", "rabbit");
		col("Farming", "Seeds", "wheat_seeds");
		col("Farming", "Sugar Cane", "sugar_cane");
		col("Farming", "Wheat", "wheat");

		// Mining
		col("Mining", "Cobblestone", "cobblestone");
		col("Mining", "Coal", "coal");
		col("Mining", "Diamond", "diamond");
		col("Mining", "Emerald", "emerald");
		col("Mining", "End Stone", "end_stone");
		col("Mining", "Gemstone", "amethyst_shard");
		col("Mining", "Glacite", "packed_ice");
		col("Mining", "Glowstone Dust", "glowstone_dust");
		col("Mining", "Gold Ingot", "gold_ingot");
		col("Mining", "Gravel", "gravel");
		col("Mining", "Hard Stone", "stone");
		col("Mining", "Ice", "ice");
		col("Mining", "Iron Ingot", "iron_ingot");
		col("Mining", "Lapis Lazuli", "lapis_lazuli");
		col("Mining", "Mithril", "prismarine_crystals");
		col("Mining", "Mycelium", "mycelium");
		col("Mining", "Nether Quartz", "quartz");
		col("Mining", "Netherrack", "netherrack");
		col("Mining", "Obsidian", "obsidian");
		col("Mining", "Red Sand", "red_sand");
		col("Mining", "Redstone", "redstone");
		col("Mining", "Sand", "sand");
		col("Mining", "Sulphur", "glowstone_dust");
		col("Mining", "Tungsten", "clay_ball");
		col("Mining", "Umber", "brown_terracotta");

		// Combat
		col("Combat", "Blaze Rod", "blaze_rod");
		col("Combat", "Bone", "bone");
		col("Combat", "Chili Pepper", "red_dye");
		col("Combat", "Ender Pearl", "ender_pearl");
		col("Combat", "Ghast Tear", "ghast_tear");
		col("Combat", "Gunpowder", "gunpowder");
		col("Combat", "Magma Cream", "magma_cream");
		col("Combat", "Rotten Flesh", "rotten_flesh");
		col("Combat", "Slimeball", "slime_ball");
		col("Combat", "Spider Eye", "spider_eye");
		col("Combat", "String", "string");

		// Foraging
		col("Foraging", "Acacia Wood", "acacia_log");
		col("Foraging", "Birch Wood", "birch_log");
		col("Foraging", "Dark Oak Wood", "dark_oak_log");
		col("Foraging", "Jungle Wood", "jungle_log");
		col("Foraging", "Oak Wood", "oak_log");
		col("Foraging", "Spruce Wood", "spruce_log");

		// Fishing
		col("Fishing", "Clay", "clay_ball");
		col("Fishing", "Clownfish", "tropical_fish");
		col("Fishing", "Ink Sac", "ink_sac");
		col("Fishing", "Lily Pad", "lily_pad");
		col("Fishing", "Magmafish", "cod");
		col("Fishing", "Prismarine Crystals", "prismarine_crystals");
		col("Fishing", "Prismarine Shard", "prismarine_shard");
		col("Fishing", "Pufferfish", "pufferfish");
		col("Fishing", "Raw Fish", "cod");
		col("Fishing", "Raw Salmon", "salmon");
		col("Fishing", "Sponge", "sponge");
	}

	public static String skillItem(String skill) {
		for (Entry e : SKILLS) {
			if (e.name().equalsIgnoreCase(skill)) {
				return e.itemId();
			}
		}
		return "book";
	}

	public static Entry findCollection(String name) {
		String key = name.toLowerCase(Locale.ROOT);
		for (List<Entry> list : COLLECTIONS.values()) {
			for (Entry e : list) {
				if (e.key().equals(key)) {
					return e;
				}
			}
		}
		return null;
	}

	/** ItemStack for an item id like "wheat" or "minecraft:wheat" (cached). */
	public static ItemStack stack(String itemId) {
		if (itemId == null || itemId.isEmpty()) {
			itemId = "barrier";
		}
		return STACK_CACHE.computeIfAbsent(itemId, id -> {
			try {
				Identifier identifier = id.contains(":") ? Identifier.parse(id) : Identifier.withDefaultNamespace(id);
				Item item = BuiltInRegistries.ITEM.getValue(identifier);
				return new ItemStack(item);
			} catch (Exception e) {
				return ItemStack.EMPTY;
			}
		});
	}
}
