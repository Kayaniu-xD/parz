package net.parz.dungeon;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.HashMap;
import java.util.Map;

/**
 * Hypixel's dungeons were built in Minecraft 1.8, and the room data (rooms.json) identifies a room by a hash of
 * the 1.8 numeric block ids of one column. This translates a modern block to the id it had in 1.8.
 * Blocks that did not exist in 1.8 return -1.
 */
public final class LegacyBlocks {
	public static final int UNKNOWN = -1;
	private static final Map<String, Integer> BY_NAME = new HashMap<>();

	private LegacyBlocks() {
	}

	private static void put(int id, String... names) {
		for (String name : names) {
			BY_NAME.put(name, id);
		}
	}

	static {
		put(0, "air", "cave_air", "void_air");
		put(1, "stone", "granite", "polished_granite", "diorite", "polished_diorite", "andesite", "polished_andesite");
		put(2, "grass_block");
		put(3, "dirt", "coarse_dirt", "podzol");
		put(4, "cobblestone");
		put(6, "oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling", "acacia_sapling", "dark_oak_sapling");
		put(7, "bedrock");
		put(12, "sand", "red_sand");
		put(13, "gravel");
		put(14, "gold_ore");
		put(15, "iron_ore");
		put(16, "coal_ore");
		put(17, "oak_log", "spruce_log", "birch_log", "jungle_log", "oak_wood", "spruce_wood", "birch_wood", "jungle_wood");
		put(162, "acacia_log", "dark_oak_log", "acacia_wood", "dark_oak_wood");
		put(18, "oak_leaves", "spruce_leaves", "birch_leaves", "jungle_leaves");
		put(161, "acacia_leaves", "dark_oak_leaves");
		put(19, "sponge", "wet_sponge");
		put(20, "glass");
		put(21, "lapis_ore");
		put(22, "lapis_block");
		put(23, "dispenser");
		put(24, "sandstone", "chiseled_sandstone", "cut_sandstone", "smooth_sandstone");
		put(25, "note_block");
		put(27, "powered_rail");
		put(28, "detector_rail");
		put(29, "sticky_piston");
		put(30, "cobweb");
		put(31, "short_grass", "grass", "fern");
		put(32, "dead_bush");
		put(33, "piston");
		put(34, "piston_head");
		put(36, "moving_piston");
		put(37, "dandelion");
		put(38, "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy");
		put(39, "brown_mushroom");
		put(40, "red_mushroom");
		put(41, "gold_block");
		put(42, "iron_block");
		put(45, "bricks");
		put(46, "tnt");
		put(47, "bookshelf");
		put(48, "mossy_cobblestone");
		put(49, "obsidian");
		put(50, "torch", "wall_torch");
		put(51, "fire");
		put(52, "spawner");
		put(53, "oak_stairs");
		put(54, "chest");
		put(55, "redstone_wire");
		put(56, "diamond_ore");
		put(57, "diamond_block");
		put(58, "crafting_table");
		put(59, "wheat");
		put(60, "farmland");
		put(65, "ladder");
		put(66, "rail");
		put(67, "cobblestone_stairs");
		put(69, "lever");
		put(70, "stone_pressure_plate");
		put(71, "iron_door");
		put(72, "oak_pressure_plate", "spruce_pressure_plate", "birch_pressure_plate", "jungle_pressure_plate", "acacia_pressure_plate", "dark_oak_pressure_plate");
		put(77, "stone_button");
		put(78, "snow");
		put(79, "ice");
		put(80, "snow_block");
		put(81, "cactus");
		put(82, "clay");
		put(83, "sugar_cane");
		put(84, "jukebox");
		put(85, "oak_fence");
		put(86, "carved_pumpkin", "pumpkin");
		put(87, "netherrack");
		put(88, "soul_sand");
		put(89, "glowstone");
		put(90, "nether_portal");
		put(91, "jack_o_lantern");
		put(92, "cake");
		put(96, "oak_trapdoor");
		put(97, "infested_stone", "infested_cobblestone", "infested_stone_bricks", "infested_mossy_stone_bricks",
				"infested_cracked_stone_bricks", "infested_chiseled_stone_bricks");
		put(98, "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks");
		put(99, "brown_mushroom_block", "mushroom_stem");
		put(100, "red_mushroom_block");
		put(101, "iron_bars");
		put(102, "glass_pane");
		put(103, "melon");
		put(104, "pumpkin_stem", "attached_pumpkin_stem");
		put(105, "melon_stem", "attached_melon_stem");
		put(106, "vine");
		put(107, "oak_fence_gate");
		put(108, "brick_stairs");
		put(109, "stone_brick_stairs");
		put(110, "mycelium");
		put(111, "lily_pad");
		put(112, "nether_bricks");
		put(113, "nether_brick_fence");
		put(114, "nether_brick_stairs");
		put(115, "nether_wart");
		put(116, "enchanting_table");
		put(117, "brewing_stand");
		put(118, "cauldron");
		put(119, "end_portal");
		put(120, "end_portal_frame");
		put(121, "end_stone");
		put(122, "dragon_egg");
		put(127, "cocoa");
		put(128, "sandstone_stairs");
		put(129, "emerald_ore");
		put(130, "ender_chest");
		put(131, "tripwire_hook");
		put(132, "tripwire");
		put(133, "emerald_block");
		put(134, "spruce_stairs");
		put(135, "birch_stairs");
		put(136, "jungle_stairs");
		put(137, "command_block", "chain_command_block", "repeating_command_block");
		put(138, "beacon");
		put(139, "cobblestone_wall", "mossy_cobblestone_wall");
		put(140, "flower_pot");
		put(141, "carrots");
		put(142, "potatoes");
		put(143, "oak_button", "spruce_button", "birch_button", "jungle_button", "acacia_button", "dark_oak_button");
		put(144, "skeleton_skull", "wither_skeleton_skull", "zombie_head", "player_head", "creeper_head",
				"skeleton_wall_skull", "wither_skeleton_wall_skull", "zombie_wall_head", "player_wall_head", "creeper_wall_head");
		put(145, "anvil", "chipped_anvil", "damaged_anvil");
		put(146, "trapped_chest");
		put(147, "light_weighted_pressure_plate");
		put(148, "heavy_weighted_pressure_plate");
		put(151, "daylight_detector");
		put(152, "redstone_block");
		put(153, "nether_quartz_ore");
		put(154, "hopper");
		put(155, "quartz_block", "chiseled_quartz_block", "quartz_pillar");
		put(156, "quartz_stairs");
		put(157, "activator_rail");
		put(158, "dropper");
		put(163, "acacia_stairs");
		put(164, "dark_oak_stairs");
		put(165, "slime_block");
		put(166, "barrier");
		put(167, "iron_trapdoor");
		put(168, "prismarine", "prismarine_bricks", "dark_prismarine");
		put(169, "sea_lantern");
		put(170, "hay_block");
		put(172, "terracotta");
		put(173, "coal_block");
		put(174, "packed_ice");
		put(175, "sunflower", "lilac", "rose_bush", "peony", "tall_grass", "large_fern");
		put(179, "red_sandstone", "chiseled_red_sandstone", "cut_red_sandstone", "smooth_red_sandstone");
		put(180, "red_sandstone_stairs");
		put(183, "spruce_fence_gate");
		put(184, "birch_fence_gate");
		put(185, "jungle_fence_gate");
		put(186, "dark_oak_fence_gate");
		put(187, "acacia_fence_gate");
		put(188, "spruce_fence");
		put(189, "birch_fence");
		put(190, "jungle_fence");
		put(191, "dark_oak_fence");
		put(192, "acacia_fence");
		put(193, "spruce_door");
		put(194, "birch_door");
		put(195, "jungle_door");
		put(196, "acacia_door");
		put(197, "dark_oak_door");
	}

	private static final String[] PLANKS = {"oak_planks", "spruce_planks", "birch_planks", "jungle_planks", "acacia_planks", "dark_oak_planks"};

	/** The 1.8 block id of a block state (-1 if the block did not exist in 1.8). */
	public static int of(BlockState state) {
		String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();

		// blocks whose id depended on a state in 1.8
		if (state.getBlock() instanceof LiquidBlock) {
			boolean source = state.getValue(LiquidBlock.LEVEL) == 0;
			return name.equals("lava") ? (source ? 11 : 10) : (source ? 9 : 8);
		}
		if (state.getBlock() instanceof SlabBlock) {
			boolean full = state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE;
			if (name.equals("red_sandstone_slab")) {
				return full ? 181 : 182;
			}
			if (name.startsWith("oak_") || name.startsWith("spruce_") || name.startsWith("birch_")
					|| name.startsWith("jungle_") || name.startsWith("acacia_") || name.startsWith("dark_oak_")) {
				return full ? 125 : 126;
			}
			return full ? 43 : 44;
		}
		for (String planks : PLANKS) {
			if (name.equals(planks)) {
				return 5;
			}
		}
		if (name.endsWith("_wool")) {
			return 35;
		}
		if (name.endsWith("_stained_glass_pane")) {
			return 160;
		}
		if (name.endsWith("_stained_glass")) {
			return 95;
		}
		if (name.endsWith("_carpet")) {
			return 171;
		}
		if (name.endsWith("_terracotta") && !name.contains("glazed")) {
			return 159;
		}
		if (name.endsWith("_bed")) {
			return 26;
		}
		if (name.endsWith("_banner")) {
			return name.contains("wall") ? 177 : 176;
		}
		if (name.endsWith("_wall_sign")) {
			return 68;
		}
		if (name.endsWith("_sign")) {
			return 63;
		}
		if (name.equals("oak_door")) {
			return 64;
		}
		if (name.equals("furnace")) {
			return state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT) ? 62 : 61;
		}
		if (name.equals("redstone_ore")) {
			return state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT) ? 74 : 73;
		}
		if (name.equals("redstone_torch") || name.equals("redstone_wall_torch")) {
			return state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT) ? 75 : 76;
		}
		if (name.equals("redstone_lamp")) {
			return state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT) ? 124 : 123;
		}
		if (name.equals("repeater")) {
			return state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED) ? 94 : 93;
		}
		if (name.equals("comparator")) {
			return state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED) ? 150 : 149;
		}
		Integer id = BY_NAME.get(name);
		return id != null ? id : UNKNOWN;
	}
}
