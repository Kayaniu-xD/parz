package net.parz.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.parz.armor.HeadCatalog;
import net.parz.item.ItemLook;
import net.parz.item.ItemLooks;
import net.parz.item.ItemName;
import net.parz.mixin.EditBoxAccessor;
import net.parz.mixin.ItemModelListAccessor;

import java.util.ArrayList;
import java.util.List;

/**
 * The "Item" tab of /pz custom (like the item customization of Skyblocker): pick one of your SkyBlock items and
 * change its glint, its model and its name. Everything is saved at once and only changes your own screen.
 */
public class ItemScreen extends Screen {
	private static final int SWATCH = 14;
	private static final ChatFormatting[] COLORS = {
			ChatFormatting.BLACK, ChatFormatting.DARK_BLUE, ChatFormatting.DARK_GREEN, ChatFormatting.DARK_AQUA,
			ChatFormatting.DARK_RED, ChatFormatting.DARK_PURPLE, ChatFormatting.GOLD, ChatFormatting.GRAY,
			ChatFormatting.DARK_GRAY, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.AQUA,
			ChatFormatting.RED, ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW, ChatFormatting.WHITE};
	private static final int[] FLAGS = {ItemName.BOLD, ItemName.ITALIC, ItemName.UNDERLINE, ItemName.STRIKETHROUGH, ItemName.OBFUSCATED};
	private static final String[] FLAG_LABELS = {"§lB", "§oI", "§nU", "§mS", "§kM"};
	private static final String[] FLAG_NAMES = {"Bold", "Italic", "Underline", "Strikethrough", "Obfuscated"};

	/** The item that is edited; kept while the game runs, so the menu opens with it again. */
	private static String selected;

	private EditBox nameBox;
	private EditBox modelBox;
	/** The name in the editor with the look of every character. */
	private String text = "";
	private List<ItemName.Mark> marks = new ArrayList<>();
	/** True while the fields are filled by the screen itself (nothing is saved then). */
	private boolean loading = false;
	private String status = "";
	private boolean problem = false;
	private int px;
	private int py;

	public ItemScreen() {
		super(Component.literal("Parz Item"));
	}

	@Override
	protected void init() {
		HeadCatalog.ensureLoaded();
		px = (this.width - CustomUi.PANEL_W) / 2;
		py = (this.height - CustomUi.PANEL_H) / 2;
		if (find() == null) {
			selected = firstCustomizable();
		}

		modelBox = new EditBox(this.font, rightX() + 4, topY() + 45, boxW() - 8, 17, Component.literal("Item model override"));
		modelBox.setMaxLength(120);
		this.addRenderableWidget(modelBox);

		nameBox = new EditBox(this.font, px + CustomUi.PAD + 4, nameY() + 22, nameW() - 8, 18, Component.literal("Item name"));
		nameBox.addFormatter((visible, offset) -> {
			int from = Math.min(offset, marks.size());
			int to = Math.min(offset + visible.length(), marks.size());
			return ItemName.component(ItemName.runs(visible, marks.subList(from, to))).getVisualOrderText();
		});
		this.addRenderableWidget(nameBox);

		load();
		nameBox.setResponder(this::nameChanged);
		modelBox.setResponder(this::modelChanged);
		if (find() != null) {
			this.setInitialFocus(nameBox);
		}
	}

	// ---- the item ----------------------------------------------------------------------------

	/** The selected item in your inventory, or null. */
	private static ItemStack find() {
		Minecraft client = Minecraft.getInstance();
		if (selected == null || client.player == null) {
			return null;
		}
		Inventory inventory = client.player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && selected.equals(ItemLooks.uuidOf(stack))) {
				return stack;
			}
		}
		return null;
	}

	/** The item in your hand if it can be changed, otherwise the first one of your inventory that can, or null. */
	private static String firstCustomizable() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return null;
		}
		Inventory inventory = client.player.getInventory();
		String held = ItemLooks.uuidOf(inventory.getSelectedItem());
		if (held != null) {
			return held;
		}
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			String uuid = ItemLooks.uuidOf(inventory.getItem(i));
			if (uuid != null) {
				return uuid;
			}
		}
		return null;
	}

	/** Fills the fields with what the selected item looks like now. */
	private void load() {
		ItemStack stack = find();
		boolean there = stack != null;
		nameBox.visible = there;
		nameBox.active = there;
		modelBox.visible = there;
		modelBox.active = there;
		if (!there) {
			return;
		}
		ItemLook look = ItemLooks.get(selected);
		List<ItemName.Run> runs = look.name() != null ? look.name() : ItemName.fromComponent(stack.getHoverName());
		loading = true;
		text = ItemName.text(runs);
		marks = ItemName.marks(runs);
		nameBox.setMaxLength(Math.max(80, text.length()));
		nameBox.setValue(text);
		// letters without an own color are shown in the rarity color, like the name itself
		nameBox.setTextColor(0xFF000000 | TextColor.fromLegacyFormat(stack.getRarity().color()).getValue());
		modelBox.setValue(look.model() == null ? "" : look.model());
		modelHint(look);
		loading = false;
	}

	private void modelHint(ItemLook look) {
		HeadCatalog.Head head = look.head() == null ? null : HeadCatalog.byHash(look.head());
		String hint = look.head() == null ? "Item Model Override" : "Head: " + (head == null ? "SkyBlock head" : head.name());
		modelBox.setHint(Component.literal(hint).withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
	}

	// ---- changes -----------------------------------------------------------------------------

	private void say(String message, boolean bad) {
		status = message;
		problem = bad;
	}

	private void nameChanged(String next) {
		if (loading || find() == null) {
			return;
		}
		marks = ItemName.edited(text, marks, next);
		text = next;
		saveName();
	}

	private void saveName() {
		ItemStack stack = find();
		if (stack == null) {
			return;
		}
		List<ItemName.Run> runs = text.isEmpty() ? null : ItemName.runs(text, marks);
		if (runs != null && runs.equals(ItemName.fromComponent(stack.getHoverName()))) {
			runs = null; // the same as the real name: nothing to change
		}
		ItemLooks.set(selected, ItemLooks.get(selected).withName(runs));
		say(runs == null ? "The item has its normal name." : "Name saved.", false);
	}

	private void modelChanged(String value) {
		if (loading || find() == null) {
			return;
		}
		String typed = value.trim();
		if (typed.isEmpty()) {
			ItemLooks.set(selected, ItemLooks.get(selected).withModel(null));
			say("The item has its normal model.", false);
		} else {
			Identifier id = Identifier.tryParse(typed);
			ItemModelListAccessor models = (ItemModelListAccessor) Minecraft.getInstance().getModelManager();
			if (id == null || !models.parz$itemModels().containsKey(id)) {
				say("There is no item model called \"" + typed + "\".", true);
				return;
			}
			ItemLooks.set(selected, ItemLooks.get(selected).withModel(id.toString()));
			say("Model saved.", false);
		}
		modelHint(ItemLooks.get(selected));
	}

	private void cycleGlint() {
		ItemLook look = ItemLooks.get(selected);
		Boolean next = look.glint() == null ? Boolean.TRUE : look.glint() ? Boolean.FALSE : null;
		ItemLooks.set(selected, look.withGlint(next));
		say("Glint saved.", false);
	}

	private void reset() {
		ItemLooks.set(selected, ItemLook.NONE);
		load();
		say("Reset: this item looks normal again.", false);
	}

	/** The marked part of the name as {from, to}; the whole name if nothing is marked. */
	private int[] range() {
		int a = nameBox.getCursorPosition();
		int b = ((EditBoxAccessor) nameBox).parz$highlightPos();
		int from = Math.max(0, Math.min(Math.min(a, b), text.length()));
		int to = Math.max(0, Math.min(Math.max(a, b), text.length()));
		return from == to ? new int[]{0, text.length()} : new int[]{from, to};
	}

	/** Switches a format of the marked part on, or off if all of it already has it. */
	private void toggle(int flag) {
		int[] range = range();
		boolean all = true;
		for (int i = range[0]; i < range[1]; i++) {
			all &= marks.get(i).has(flag);
		}
		for (int i = range[0]; i < range[1]; i++) {
			marks.set(i, marks.get(i).with(flag, !all));
		}
		saveName();
	}

	/** One color, or a gradient from the first to the second color, on the characters from..to. */
	private void paint(int[] range, int[] colors) {
		int to = Math.min(range[1], marks.size());
		int length = to - range[0];
		for (int i = range[0]; i < to; i++) {
			int rgb = colors[0];
			if (colors.length > 1) {
				rgb = mix(colors[0], colors[1], length < 2 ? 0 : (i - range[0]) / (float) (length - 1));
			}
			marks.set(i, marks.get(i).withColor(rgb));
		}
		saveName();
	}

	static int mix(int from, int to, float amount) {
		int rgb = 0;
		for (int shift = 16; shift >= 0; shift -= 8) {
			int a = (from >> shift) & 255;
			int b = (to >> shift) & 255;
			rgb |= Math.round(a + (b - a) * amount) << shift;
		}
		return rgb;
	}

	private void pickColor(boolean gradient) {
		int[] range = range();
		if (range[1] <= range[0]) {
			return;
		}
		int first = marks.get(range[0]).color();
		int last = marks.get(range[1] - 1).color();
		String target = text.substring(range[0], range[1]);
		int start = first == ItemName.NO_COLOR ? 0xFF55FF : first;
		// the end of a new gradient must differ from its start, or nothing would be seen
		int end = last != ItemName.NO_COLOR && last != start ? last : start == 0x55FFFF ? 0xFF55FF : 0x55FFFF;
		Minecraft.getInstance().gui.setScreen(new ColorPickScreen(this, target, gradient, start, end,
				// the editor is filled again when this screen comes back, so the change is made after that
				colors -> paint(range, colors)));
	}

	// ---- layout ------------------------------------------------------------------------------

	private int topY() {
		return py + 30;
	}

	private int boxW() {
		return (CustomUi.PANEL_W - 2 * CustomUi.PAD - 4) / 2;
	}

	private int rightX() {
		return px + CustomUi.PAD + boxW() + 4;
	}

	private int nameY() {
		return topY() + 70;
	}

	private int nameW() {
		return CustomUi.PANEL_W - 2 * CustomUi.PAD;
	}

	private int flagX(int index) {
		return px + CustomUi.PAD + 4 + index * 18;
	}

	private int swatchX(int index) {
		return px + CustomUi.PAD + 4 + index * (SWATCH + 2);
	}

	private int customX() {
		return swatchX(COLORS.length) + 2;
	}

	// ---- drawing -----------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		ItemStack stack = find();
		if (nameBox.visible != (stack != null)) {
			load();
		}
		CustomUi.panel(graphics, px, py, CustomUi.PANEL_W, CustomUi.PANEL_H);
		CustomUi.tabs(graphics, font, px, py, CustomUi.ITEM_TAB, "Only you see your item changes", mouseX, mouseY);

		int x = px + CustomUi.PAD;
		CustomUi.box(graphics, x, topY(), boxW(), 66);
		CustomUi.button(graphics, font, "Select Item", x + 4, topY() + 44, boxW() - 8, 18, mouseX, mouseY);
		if (stack == null) {
			super.extractRenderState(graphics, mouseX, mouseY, delta);
			graphics.centeredText(font, "Nothing customizable.", px + CustomUi.PANEL_W / 2, nameY() + 30, CustomUi.TEXT);
			graphics.centeredText(font, "You need a SkyBlock item with its own ID in your inventory", px + CustomUi.PANEL_W / 2,
					nameY() + 44, CustomUi.TEXT_DIM);
			graphics.centeredText(font, "(weapons, tools, armor, accessories).", px + CustomUi.PANEL_W / 2, nameY() + 56, CustomUi.TEXT_DIM);
			return;
		}

		ItemLook look = ItemLooks.get(selected);
		CustomUi.box(graphics, rightX(), topY(), boxW(), 66);
		String glint = look.glint() == null ? "Default" : look.glint() ? "On" : "Off";
		CustomUi.button(graphics, font, "Glint: " + glint, rightX() + 4, topY() + 4, boxW() - 8, 18, mouseX, mouseY);
		CustomUi.button(graphics, font, "Select Model", rightX() + 4, topY() + 24, boxW() - 8, 18, mouseX, mouseY);

		CustomUi.box(graphics, x, nameY(), nameW(), 98);
		String tooltip = null;
		for (int i = 0; i < FLAGS.length; i++) {
			CustomUi.button(graphics, font, FLAG_LABELS[i], flagX(i), nameY() + 4, 16, 14, mouseX, mouseY);
			if (CustomUi.inside(mouseX, mouseY, flagX(i), nameY() + 4, 16, 14)) {
				tooltip = FLAG_NAMES[i] + " (marked letters, or all)";
			}
		}
		CustomUi.button(graphics, font, "Reset item", x + nameW() - 74, nameY() + 4, 70, 14, mouseX, mouseY);
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		int rowY = nameY() + 44;
		for (int i = 0; i < COLORS.length; i++) {
			int sx = swatchX(i);
			graphics.fill(sx, rowY, sx + SWATCH, rowY + SWATCH, 0xFF000000 | TextColor.fromLegacyFormat(COLORS[i]).getValue());
			boolean hover = CustomUi.inside(mouseX, mouseY, sx, rowY, SWATCH, SWATCH);
			graphics.outline(sx, rowY, SWATCH, SWATCH, hover ? CustomUi.SELECTED_EDGE : CustomUi.BOX_EDGE);
		}
		CustomUi.button(graphics, font, "Custom", customX(), rowY, 46, SWATCH, mouseX, mouseY);
		CustomUi.button(graphics, font, "Gradient", customX() + 48, rowY, 50, SWATCH, mouseX, mouseY);
		graphics.text(font, Component.literal("Leave the name field empty to remove the custom name.").withStyle(ChatFormatting.ITALIC),
				x + 4, nameY() + 66, CustomUi.TEXT_DIM, false);
		graphics.centeredText(font, stack.getStyledHoverName(), px + CustomUi.PANEL_W / 2, nameY() + 82, CustomUi.TEXT);

		// the item, big
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + boxW() / 2f - 20, topY() + 3);
		graphics.pose().scale(2.5f, 2.5f);
		graphics.item(stack, 0, 0);
		graphics.pose().popMatrix();

		if (!status.isEmpty()) {
			graphics.text(font, status, x, py + CustomUi.PANEL_H - 12, problem ? 0xFFFF7070 : 0xFF7CE08F, false);
		}
		if (tooltip != null) {
			graphics.setTooltipForNextFrame(font, Component.literal(tooltip), mouseX, mouseY);
		} else if (CustomUi.inside(mouseX, mouseY, x + boxW() / 2 - 20, topY() + 3, 40, 40)) {
			graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
		}
	}

	// ---- input -------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			double mx = event.x();
			double my = event.y();
			int x = px + CustomUi.PAD;
			if (CustomUi.clickTab(mx, my, px, py, CustomUi.ITEM_TAB)) {
				return true;
			}
			if (CustomUi.inside(mx, my, x + 4, topY() + 44, boxW() - 8, 18)) {
				Minecraft.getInstance().gui.setScreen(new ItemPickScreen(this, uuid -> {
					selected = uuid;
					status = "";
				}));
				return true;
			}
			if (find() != null && clickEditor(mx, my)) {
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** The buttons of the selected item. They do not take the focus, so the marked letters stay marked. */
	private boolean clickEditor(double mx, double my) {
		if (CustomUi.inside(mx, my, rightX() + 4, topY() + 4, boxW() - 8, 18)) {
			cycleGlint();
			return true;
		}
		if (CustomUi.inside(mx, my, rightX() + 4, topY() + 24, boxW() - 8, 18)) {
			String uuid = selected;
			Minecraft.getInstance().gui.setScreen(new ModelPickScreen(this, ItemLooks.get(uuid), look -> {
				ItemLooks.set(uuid, look);
				say("Model saved.", false);
			}));
			return true;
		}
		for (int i = 0; i < FLAGS.length; i++) {
			if (CustomUi.inside(mx, my, flagX(i), nameY() + 4, 16, 14)) {
				toggle(FLAGS[i]);
				return true;
			}
		}
		if (CustomUi.inside(mx, my, px + CustomUi.PAD + nameW() - 74, nameY() + 4, 70, 14)) {
			reset();
			return true;
		}
		int rowY = nameY() + 44;
		for (int i = 0; i < COLORS.length; i++) {
			if (CustomUi.inside(mx, my, swatchX(i), rowY, SWATCH, SWATCH)) {
				paint(range(), new int[]{TextColor.fromLegacyFormat(COLORS[i]).getValue()});
				return true;
			}
		}
		if (CustomUi.inside(mx, my, customX(), rowY, 46, SWATCH)) {
			pickColor(false);
			return true;
		}
		if (CustomUi.inside(mx, my, customX() + 48, rowY, 50, SWATCH)) {
			pickColor(true);
			return true;
		}
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
