package net.parz.pets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.parz.ParzClient;
import net.parz.dashboard.DashboardData;
import net.parz.dashboard.DashboardData.OwnedPet;
import net.parz.dashboard.DashboardTracker;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which pets you own. Every page of the pets menu is read when it is shown (the list of all pets, with their
 * highest rarity, comes from the SkyBlock wiki and is stored in pets.json). A page that is read again replaces
 * what was known about it, so pets that you sold disappear and new pets are added. Saved with the dashboard data.
 */
public final class PetCollection {
	public static final String[] RARITY_NAMES = {"Common", "Uncommon", "Rare", "Epic", "Legendary", "Mythic"};
	public static final int[] RARITY_COLORS = {0xFFFFFFFF, 0xFF55FF55, 0xFF6E8CFF, 0xFFC25BFF, 0xFFFFAA00, 0xFFFF55FF};
	private static final int[] GAME_COLORS = {0xFFFFFF, 0x55FF55, 0x5555FF, 0xAA00AA, 0xFFAA00, 0xFF55FF};
	private static final int PAGE_SIZE = 28;
	/** Pets that are not shown in the collection (event pets). */
	private static final java.util.Set<String> HIDDEN = java.util.Set.of("Bingo", "Montezuma");
	private static final Pattern PAGE = Pattern.compile("^\\((\\d+)/(\\d+)\\)");
	private static final Pattern SYMBOLS = Pattern.compile("[^A-Za-z' \\-]");

	/** A pet of the wiki list. */
	public record CatalogPet(String name, int max, String skill) {
	}

	/** One line of the collection: the pet, its highest rarity and what you own. best = -1: you do not own it. */
	public record Row(String name, int max, int best, int level, ItemStack icon, String skill, boolean extra) {
		public boolean owned() {
			return best >= 0;
		}

		public boolean maxed() {
			return best >= max;
		}
	}

	/** A pet read from one slot of the menu. */
	public record Scanned(String name, int rarity, int level, ItemStack stack) {
	}

	private static List<CatalogPet> catalog = null;
	private static int version = 0;
	private static int cachedVersion = -1;
	private static List<Row> cachedRows = new ArrayList<>();
	private static final Map<String, ItemStack> ICONS = new HashMap<>();
	private static String lastSignature = "";

	private PetCollection() {
	}

	// ======================================================================================
	// Catalog
	// ======================================================================================

	private static synchronized List<CatalogPet> catalog() {
		if (catalog != null) {
			return catalog;
		}
		List<CatalogPet> list = new ArrayList<>();
		try (InputStream in = PetCollection.class.getResourceAsStream("/assets/parz/pets.json")) {
			if (in != null) {
				JsonArray array = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
				for (JsonElement element : array) {
					JsonObject pet = element.getAsJsonObject();
					list.add(new CatalogPet(pet.get("name").getAsString(), pet.get("max").getAsInt(),
							pet.has("skill") ? pet.get("skill").getAsString() : ""));
				}
			}
		} catch (Exception e) {
			ParzClient.LOGGER.warn("Could not read pets.json", e);
		}
		catalog = list;
		return list;
	}

	// ======================================================================================
	// Reading the menu
	// ======================================================================================

	/** Pet name without the level and without symbols (a skin star, a favourite star), e.g. "Golden Dragon". */
	public static String canonical(String shown) {
		String name = SYMBOLS.matcher(shown).replaceAll("").trim().replaceAll("\\s+", " ");
		if (name.endsWith(" Wisp")) {
			return "Wisp"; // Droplet, Frost, Flame ... Wisp are all the Wisp pet
		}
		if (name.endsWith(" Dragon Egg")) {
			return name.substring(0, name.length() - " Egg".length()); // the dragon pets are eggs until level 100
		}
		return name;
	}

	/** Rarity (0-5) from the color of the name, -1 if it has no color. */
	public static int rarityOf(Component name) {
		int[] found = {-1};
		name.visit((style, text) -> {
			if (found[0] < 0 && text.chars().anyMatch(Character::isLetter)) {
				TextColor color = style.getColor();
				if (color != null) {
					found[0] = nearestRarity(color.getValue());
				}
			}
			return Optional.empty();
		}, Style.EMPTY);
		return found[0];
	}

	private static int nearestRarity(int rgb) {
		int best = 0;
		long bestDistance = Long.MAX_VALUE;
		for (int i = 0; i < GAME_COLORS.length; i++) {
			int dr = ((rgb >> 16) & 0xFF) - ((GAME_COLORS[i] >> 16) & 0xFF);
			int dg = ((rgb >> 8) & 0xFF) - ((GAME_COLORS[i] >> 8) & 0xFF);
			int db = (rgb & 0xFF) - (GAME_COLORS[i] & 0xFF);
			long d = (long) dr * dr + (long) dg * dg + (long) db * db;
			if (d < bestDistance) {
				bestDistance = d;
				best = i;
			}
		}
		return best;
	}

	/**
	 * Called with the pets of the menu page that is open. title is e.g. "(2/4) Pets". The page is stored; if the
	 * view looks filtered (search) or cut short, pets are only added and nothing is removed.
	 */
	public static synchronized void observe(int containerId, String title, List<Scanned> pets) {
		StringBuilder signature = new StringBuilder().append(containerId).append('|').append(title);
		for (Scanned pet : pets) {
			signature.append('|').append(pet.name()).append(pet.rarity()).append(pet.level());
		}
		if (signature.toString().equals(lastSignature)) {
			return;
		}
		lastSignature = signature.toString();

		int page = 1;
		int total = 1;
		Matcher m = PAGE.matcher(title.trim());
		if (m.find()) {
			page = Integer.parseInt(m.group(1));
			total = Integer.parseInt(m.group(2));
		}
		DashboardData data = DashboardData.get();
		List<OwnedPet> fresh = new ArrayList<>();
		for (Scanned scanned : pets) {
			OwnedPet owned = new OwnedPet();
			owned.name = canonical(scanned.name());
			owned.rarity = Math.max(0, scanned.rarity());
			owned.level = scanned.level();
			ItemStack icon = scanned.stack().copy();
			icon.remove(DataComponents.LORE);
			owned.iconData = DashboardData.encodeIcon(icon);
			fresh.add(owned);
		}

		String key = String.valueOf(page);
		boolean complete = (page < total && pets.size() >= PAGE_SIZE)
				|| (page == total && (total >= 2 || data.petPageCount <= 1));
		if (complete) {
			data.petPages.put(key, fresh);
			data.petPageCount = total;
			final int pageCount = total;
			data.petPages.keySet().removeIf(k -> parsePage(k) > pageCount);
		} else {
			// only add what is new on this page
			List<OwnedPet> known = data.petPages.computeIfAbsent(key, k -> new ArrayList<>());
			for (OwnedPet pet : fresh) {
				boolean present = false;
				for (OwnedPet old : known) {
					present |= old.name.equals(pet.name) && old.rarity == pet.rarity;
				}
				if (!present) {
					known.add(pet);
				}
			}
			data.petPageCount = Math.max(data.petPageCount, total);
		}
		final int shownPage = page;
		final int shownTotal = total;
		net.parz.Debug.log("pets", () -> "page " + shownPage + "/" + shownTotal + ": " + fresh.size() + " pets read, "
				+ (complete ? "the page was replaced" : "only new pets were added (filtered or incomplete view)")
				+ ", " + ownedCount() + " of " + totalCount() + " pets owned");
		version++;
		DashboardTracker.markDirty();
	}

	private static int parsePage(String key) {
		try {
			return Integer.parseInt(key);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	// ======================================================================================
	// The collection
	// ======================================================================================

	public static synchronized List<Row> rows() {
		if (cachedVersion == version && !cachedRows.isEmpty()) {
			return cachedRows;
		}
		DashboardData data = DashboardData.get();
		Map<String, OwnedPet> best = new LinkedHashMap<>();
		for (List<OwnedPet> page : data.petPages.values()) {
			for (OwnedPet pet : page) {
				String name = canonical(pet.name); // older saves may still have "Jade Dragon Egg"
				if (HIDDEN.contains(name)) {
					continue;
				}
				OwnedPet current = best.get(name);
				if (current == null || pet.rarity > current.rarity || (pet.rarity == current.rarity && pet.level > current.level)) {
					best.put(name, pet);
				}
			}
		}
		List<Row> rows = new ArrayList<>();
		List<String> known = new ArrayList<>();
		for (CatalogPet pet : catalog()) {
			if (HIDDEN.contains(pet.name())) {
				continue;
			}
			known.add(pet.name());
			OwnedPet owned = best.get(pet.name());
			rows.add(new Row(pet.name(), pet.max(), owned != null ? owned.rarity : -1, owned != null ? owned.level : 0,
					owned != null ? iconOf(owned) : ItemStack.EMPTY, pet.skill(), false));
		}
		// pets the wiki list does not have (new pets) are added as they are found
		List<String> extras = new ArrayList<>(best.keySet());
		extras.removeAll(known);
		extras.sort(String.CASE_INSENSITIVE_ORDER);
		for (String name : extras) {
			OwnedPet owned = best.get(name);
			rows.add(new Row(name, owned.rarity, owned.rarity, owned.level, iconOf(owned), "", true));
		}
		// pets you own first, the highest rarity on top; then the missing ones, those that can get rarer first
		rows.sort((a, b) -> {
			if (a.owned() != b.owned()) {
				return a.owned() ? -1 : 1;
			}
			int rarityA = a.owned() ? a.best() : a.max();
			int rarityB = b.owned() ? b.best() : b.max();
			if (rarityA != rarityB) {
				return Integer.compare(rarityB, rarityA);
			}
			if (a.owned() && a.level() != b.level()) {
				return Integer.compare(b.level(), a.level());
			}
			return a.name().compareToIgnoreCase(b.name());
		});
		cachedRows = rows;
		cachedVersion = version;
		return rows;
	}

	private static ItemStack iconOf(OwnedPet pet) {
		if (pet.iconData == null || pet.iconData.isEmpty()) {
			return new ItemStack(Items.PLAYER_HEAD);
		}
		ItemStack cached = ICONS.get(pet.iconData);
		if (cached == null) {
			cached = DashboardData.decodeIcon(pet.iconData);
			if (cached == null) {
				cached = new ItemStack(Items.PLAYER_HEAD);
			}
			ICONS.put(pet.iconData, cached);
		}
		return cached;
	}

	public static int ownedCount() {
		int count = 0;
		for (Row row : rows()) {
			if (row.owned()) {
				count++;
			}
		}
		return count;
	}

	public static int maxedCount() {
		int count = 0;
		for (Row row : rows()) {
			if (row.owned() && row.maxed()) {
				count++;
			}
		}
		return count;
	}

	public static int totalCount() {
		return rows().size();
	}

	public static int scannedPages() {
		return DashboardData.get().petPages.size();
	}

	public static int totalPages() {
		return Math.max(DashboardData.get().petPageCount, scannedPages());
	}

	public static String rarityName(int rarity) {
		return rarity >= 0 && rarity < RARITY_NAMES.length ? RARITY_NAMES[rarity] : "?";
	}

	public static int rarityColor(int rarity) {
		return rarity >= 0 && rarity < RARITY_COLORS.length ? RARITY_COLORS[rarity] : 0xFF8E8E96;
	}
}
