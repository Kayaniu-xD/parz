package net.parz.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.parz.badge.BadgeConfig;
import net.parz.badge.BadgeFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * /pz customname - how your name is shown to the other Parz users (tab list and above your head).
 * Letter mode: ONE text ("{name}" = your real name) with one color for everything or a color for every single
 * letter (click a letter, then type its color). JSON mode: a Minecraft text component.
 */
public class NameScreen extends Screen {
	private static final int PANEL_W = 340;
	private static final int PANEL_H = 250;
	private static final int CELL_H = 16;

	private boolean json;
	private EditBox textBox;
	private EditBox colorBox;
	private EditBox jsonBox;
	private Button modeButton;
	private Button allButton;
	private Button toJsonButton;

	private List<String> units = new ArrayList<>();
	private List<String> unitColors = new ArrayList<>();
	private String baseColor = "#FF5555";
	private int selected = 0;
	private boolean settingColorBox = false;

	private String error = "";
	private String status = "";
	private Component preview = Component.empty();
	private String lastSignature = "";
	private int px;
	private int py;
	/** The clickable letters: x, y, w, h of every unit (filled while drawing). */
	private final List<int[]> cells = new ArrayList<>();

	public NameScreen() {
		super(Component.literal("Parz Name"));
	}

	@Override
	protected void init() {
		BadgeConfig.Data data = BadgeConfig.data();
		json = data.json;
		baseColor = data.baseColor;
		units = BadgeFormat.tokenize(data.text);
		unitColors = new ArrayList<>();
		for (int i = 0; i < units.size(); i++) {
			unitColors.add(i < data.colors.size() && data.colors.get(i) != null ? data.colors.get(i) : "");
		}
		selected = 0;
		px = (this.width - PANEL_W) / 2;
		py = (this.height - PANEL_H) / 2;
		int x = px + 14;

		modeButton = Button.builder(Component.literal(""), b -> {
			json = !json;
			updateVisibility();
			lastSignature = "";
		}).bounds(x, py + 26, 150, 20).build();
		this.addRenderableWidget(modeButton);
		toJsonButton = Button.builder(Component.literal("Make JSON from this"), b -> makeJson())
				.bounds(x + 158, py + 26, 150, 20).build();
		this.addRenderableWidget(toJsonButton);

		textBox = new EditBox(this.font, x, py + 68, PANEL_W - 28, 18, Component.literal("text"));
		textBox.setMaxLength(BadgeFormat.MAX_CHARS);
		textBox.setValue(data.text);
		textBox.setResponder(this::onTextChanged);
		this.addRenderableWidget(textBox);

		colorBox = new EditBox(this.font, x, py + 150, 90, 18, Component.literal("color"));
		colorBox.setMaxLength(9);
		colorBox.setResponder(this::onColorTyped);
		this.addRenderableWidget(colorBox);
		allButton = Button.builder(Component.literal("Color of ALL letters"), b -> {
			int rgb = BadgeFormat.parseColor(colorBox.getValue());
			if (rgb >= 0) {
				baseColor = colorBox.getValue().trim();
				for (int i = 0; i < unitColors.size(); i++) {
					unitColors.set(i, "");
				}
				lastSignature = "";
			} else {
				error = "A color must look like #FF5555";
			}
		}).bounds(x + 100, py + 149, 140, 20).build();
		this.addRenderableWidget(allButton);

		jsonBox = new EditBox(this.font, x, py + 68, PANEL_W - 28, 18, Component.literal("json"));
		jsonBox.setMaxLength(1500);
		jsonBox.setValue(data.jsonText == null ? "" : data.jsonText);
		jsonBox.setResponder(text -> lastSignature = "");
		this.addRenderableWidget(jsonBox);

		this.addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(x, py + PANEL_H - 30, 80, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
			BadgeConfig.reset();
			status = "Back to the default [ዞ] Name";
			this.rebuildWidgets();
		}).bounds(x + 88, py + PANEL_H - 30, 80, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
				.bounds(px + PANEL_W - 94, py + PANEL_H - 30, 80, 20).build());
		updateVisibility();
		showColorOfSelected();
	}

	private void updateVisibility() {
		modeButton.setMessage(Component.literal(json ? "Mode: JSON text" : "Mode: letters"));
		for (AbstractWidget widget : new AbstractWidget[]{textBox, colorBox, allButton, toJsonButton}) {
			widget.visible = !json;
			widget.active = !json;
		}
		jsonBox.visible = json;
		jsonBox.active = json;
	}

	/** The text changed: the letters change, every letter that stays keeps its color. */
	private void onTextChanged(String text) {
		units = BadgeFormat.tokenize(text);
		while (unitColors.size() < units.size()) {
			unitColors.add("");
		}
		while (unitColors.size() > units.size()) {
			unitColors.remove(unitColors.size() - 1);
		}
		selected = Math.max(0, Math.min(selected, units.size() - 1));
		lastSignature = "";
	}

	/** A color was typed: it goes to the selected letter as soon as it is a full color. */
	private void onColorTyped(String text) {
		if (settingColorBox || selected < 0 || selected >= unitColors.size()) {
			return;
		}
		if (BadgeFormat.parseColor(text) >= 0) {
			unitColors.set(selected, text.trim());
			lastSignature = "";
		}
	}

	private void showColorOfSelected() {
		settingColorBox = true;
		String own = selected >= 0 && selected < unitColors.size() ? unitColors.get(selected) : "";
		colorBox.setValue(own.isEmpty() ? baseColor : own);
		settingColorBox = false;
	}

	private BadgeConfig.Data fromFields() {
		BadgeConfig.Data data = new BadgeConfig.Data();
		data.json = json;
		data.text = textBox.getValue();
		data.baseColor = baseColor;
		data.colors = new ArrayList<>(unitColors);
		data.jsonText = jsonBox.getValue();
		return data;
	}

	/** The letters with their colors as JSON, in the JSON box, so you can go on by hand. */
	private void makeJson() {
		try {
			BadgeConfig.Data data = fromFields();
			data.json = false;
			jsonBox.setValue(BadgeFormat.toJson(BadgeConfig.compute(data)));
			json = true;
			updateVisibility();
			lastSignature = "";
		} catch (IllegalArgumentException e) {
			error = e.getMessage();
		}
	}

	private void save() {
		try {
			BadgeConfig.apply(fromFields());
			status = "Saved - the other Parz users see it within a minute";
			error = "";
		} catch (IllegalArgumentException e) {
			error = e.getMessage();
			status = "";
		}
	}

	private void updatePreview() {
		String signature = json + "|" + textBox.getValue() + "|" + baseColor + "|" + unitColors + "|" + jsonBox.getValue();
		if (signature.equals(lastSignature)) {
			return;
		}
		lastSignature = signature;
		String name = Minecraft.getInstance().getUser().getName();
		try {
			preview = BadgeFormat.render(BadgeConfig.compute(fromFields()), name);
			error = "";
		} catch (IllegalArgumentException e) {
			error = e.getMessage();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(px, py, px + PANEL_W, py + PANEL_H, 0xF00E0E14);
		graphics.outline(px, py, PANEL_W, PANEL_H, 0xFF8A6A1F);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		updatePreview();

		int x = px + 14;
		graphics.text(font, "Your name for other Parz users", x, py + 10, 0xFFFFAA00, true);
		cells.clear();
		if (json) {
			graphics.text(font, "Minecraft text component (JSON). {name} = your name.", x, py + 56, 0xFF9A9AA6, true);
			graphics.text(font, "Tip: \"Make JSON from this\" in letter mode writes it for you.", x, py + 92, 0xFF70707C, false);
		} else {
			graphics.text(font, "Text ({name} = your real name)", x, py + 56, 0xFF9A9AA6, true);
			graphics.text(font, "Click a letter, then type its color:", x, py + 92, 0xFF9A9AA6, true);
			drawLetters(graphics, x, py + 104);
			graphics.text(font, "Color like #FF5555", x, py + 138, 0xFF9A9AA6, true);
		}
		graphics.text(font, "Preview:", x, py + 182, 0xFF9A9AA6, true);
		graphics.text(font, preview, x + 50, py + 182, 0xFFFFFFFF, true);
		if (!error.isEmpty()) {
			graphics.text(font, error, x, py + 198, 0xFFFF5555, true);
		} else if (!status.isEmpty()) {
			graphics.text(font, status, x, py + 198, 0xFF55FF55, true);
		}
	}

	/** Every letter in a small box in its own color; the selected one has a bright frame. */
	private void drawLetters(GuiGraphicsExtractor graphics, int x, int y) {
		int cx = x;
		int cy = y;
		int maxX = px + PANEL_W - 14;
		int base = BadgeFormat.parseColor(baseColor);
		for (int i = 0; i < units.size(); i++) {
			String unit = units.get(i);
			String label = unit.equals("{name}") ? "name" : unit;
			int w = Math.max(12, font.width(label) + 6);
			if (cx + w > maxX) {
				cx = x;
				cy += CELL_H + 2;
			}
			int rgb = i < unitColors.size() && !unitColors.get(i).isEmpty() ? BadgeFormat.parseColor(unitColors.get(i)) : base;
			graphics.fill(cx, cy, cx + w, cy + CELL_H, 0xFF1B1C20);
			graphics.outline(cx, cy, w, CELL_H, i == selected ? 0xFFFFFFFF : 0xFF3B3C45);
			graphics.text(font, label, cx + (w - font.width(label)) / 2, cy + 4, 0xFF000000 | (rgb < 0 ? 0xFFFFFF : rgb), true);
			cells.add(new int[]{cx, cy, w, CELL_H});
			cx += w + 2;
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (!json && event.button() == 0) {
			for (int i = 0; i < cells.size(); i++) {
				int[] cell = cells.get(i);
				if (event.x() >= cell[0] && event.x() < cell[0] + cell[2] && event.y() >= cell[1] && event.y() < cell[1] + cell[3]) {
					selected = i;
					showColorOfSelected();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
