package net.parz.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.NativeImage;
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
	private static final int PANEL_H = 312;
	/** The color wheel: a picture of 84x84 pixels (hue around, white in the middle) made once. */
	private static final int WHEEL = 84;
	private static Identifier wheelTexture = null;
	private static final int CELL_H = 16;

	private boolean json;
	private EditBox textBox;
	private EditBox jsonBox;
	private Button modeButton;
	private Button allButton;
	private Button toJsonButton;

	private List<String> units = new ArrayList<>();
	private List<String> unitColors = new ArrayList<>();
	private String baseColor = "#FF5555";
	private int selected = 0;
	/** The color that is picked: hue, saturation and brightness (0..1). */
	private float hue = 0f;
	private float sat = 1f;
	private float val = 1f;
	/** 0 = nothing is dragged, 1 = the wheel, 2 = the brightness bar. */
	private int dragTarget = 0;

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

		ensureWheelTexture();
		allButton = Button.builder(Component.literal("Color of ALL letters"), b -> {
			baseColor = hex(currentRgb());
			for (int i = 0; i < unitColors.size(); i++) {
				unitColors.set(i, "");
			}
			lastSignature = "";
		}).bounds(px + 128, py + 218, 150, 20).build();
		this.addRenderableWidget(allButton);

		jsonBox = new EditBox(this.font, x, py + 68, PANEL_W - 28, 18, Component.literal("json"));
		jsonBox.setMaxLength(1500);
		jsonBox.setValue(data.jsonText == null ? "" : data.jsonText);
		jsonBox.setResponder(text -> lastSignature = "");
		this.addRenderableWidget(jsonBox);

		this.addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(x, py + PANEL_H - 28, 80, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
			BadgeConfig.reset();
			status = "Back to the default [ዞ] Name";
			this.rebuildWidgets();
		}).bounds(x + 88, py + PANEL_H - 28, 80, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
				.bounds(px + PANEL_W - 94, py + PANEL_H - 28, 80, 20).build());
		updateVisibility();
		showColorOfSelected();
	}

	private void updateVisibility() {
		modeButton.setMessage(Component.literal(json ? "Mode: JSON text" : "Mode: letters"));
		for (AbstractWidget widget : new AbstractWidget[]{textBox, allButton, toJsonButton}) {
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

	/** The picked color goes to the selected letter. */
	private void applyPicked() {
		if (selected >= 0 && selected < unitColors.size()) {
			unitColors.set(selected, hex(currentRgb()));
			lastSignature = "";
		}
	}

	/** The wheel and the bar show the color of the selected letter. */
	private void showColorOfSelected() {
		String own = selected >= 0 && selected < unitColors.size() ? unitColors.get(selected) : "";
		int rgb = BadgeFormat.parseColor(own.isEmpty() ? baseColor : own);
		if (rgb < 0) {
			return;
		}
		float[] hsv = rgbToHsv(rgb);
		hue = hsv[0];
		sat = hsv[1];
		val = hsv[2];
	}

	private int currentRgb() {
		return hsvToRgb(hue, sat, val);
	}

	private static String hex(int rgb) {
		return String.format("#%06X", rgb);
	}

	private static int hsvToRgb(float h, float s, float v) {
		float hh = (h - (float) Math.floor(h)) * 6f;
		int sector = (int) Math.floor(hh);
		float f = hh - sector;
		float p = v * (1 - s);
		float q = v * (1 - s * f);
		float t = v * (1 - s * (1 - f));
		float r;
		float g;
		float b;
		switch (sector % 6) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
	}

	private static float[] rgbToHsv(int rgb) {
		float r = ((rgb >> 16) & 255) / 255f;
		float g = ((rgb >> 8) & 255) / 255f;
		float b = (rgb & 255) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float d = max - min;
		float h;
		if (d == 0) {
			h = 0;
		} else if (max == r) {
			h = ((g - b) / d) % 6f;
		} else if (max == g) {
			h = (b - r) / d + 2f;
		} else {
			h = (r - g) / d + 4f;
		}
		h /= 6f;
		if (h < 0) {
			h += 1f;
		}
		return new float[]{h, max == 0 ? 0 : d / max, max};
	}

	/** The wheel picture: red at the top, then around like the rainbow, white in the middle. */
	private static void ensureWheelTexture() {
		if (wheelTexture != null) {
			return;
		}
		NativeImage image = new NativeImage(WHEEL, WHEEL, true);
		float radius = WHEEL / 2f;
		for (int y = 0; y < WHEEL; y++) {
			for (int x = 0; x < WHEEL; x++) {
				float dx = x + 0.5f - radius;
				float dy = y + 0.5f - radius;
				float r = (float) Math.sqrt(dx * dx + dy * dy);
				if (r > radius) {
					image.setPixel(x, y, 0x00000000);
					continue;
				}
				float angle = (float) Math.atan2(dx, -dy);
				float around = (angle < 0 ? angle + (float) (Math.PI * 2) : angle) / (float) (Math.PI * 2);
				int rgb = hsvToRgb(1f - around, Math.min(1f, r / radius), 1f);
				image.setPixel(x, y, 0xFF000000 | rgb);
			}
		}
		Identifier id = Identifier.fromNamespaceAndPath("parz", "ui/color_wheel");
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "parz color wheel", image));
		wheelTexture = id;
	}

	private int wheelX() {
		return px + 14;
	}

	private int wheelY() {
		return py + 158;
	}

	private int barX() {
		return wheelX() + WHEEL + 8;
	}

	/** A click or drag on the wheel (target 1) or on the brightness bar (target 2). */
	private void pick(int target, double mouseX, double mouseY) {
		if (target == 1) {
			float radius = WHEEL / 2f;
			double dx = mouseX - (wheelX() + radius);
			double dy = mouseY - (wheelY() + radius);
			double r = Math.sqrt(dx * dx + dy * dy);
			double angle = Math.atan2(dx, -dy);
			double around = (angle < 0 ? angle + Math.PI * 2 : angle) / (Math.PI * 2);
			hue = (float) (1.0 - around);
			sat = (float) Math.min(1.0, r / radius);
		} else {
			val = (float) Math.max(0.0, Math.min(1.0, 1.0 - (mouseY - wheelY()) / WHEEL));
		}
		applyPicked();
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
			graphics.text(font, "Click a letter, then pick its color:", x, py + 92, 0xFF9A9AA6, true);
			drawLetters(graphics, x, py + 104);
			drawPicker(graphics);
		}
		graphics.text(font, "Preview:", x, py + 252, 0xFF9A9AA6, true);
		graphics.text(font, preview, x + 50, py + 252, 0xFFFFFFFF, true);
		if (!error.isEmpty()) {
			graphics.text(font, error, x, py + 266, 0xFFFF5555, true);
		} else if (!status.isEmpty()) {
			graphics.text(font, status, x, py + 266, 0xFF55FF55, true);
		}
	}

	/** The color wheel, the brightness bar next to it and the picked color. */
	private void drawPicker(GuiGraphicsExtractor graphics) {
		int wx = wheelX();
		int wy = wheelY();
		graphics.blit(RenderPipelines.GUI_TEXTURED, wheelTexture, wx, wy, 0f, 0f, WHEEL, WHEEL, WHEEL, WHEEL, WHEEL, WHEEL);
		// where the picked color is on the wheel
		float radius = WHEEL / 2f;
		double angle = (1.0 - hue) * Math.PI * 2;
		int mx = (int) Math.round(wx + radius + Math.sin(angle) * sat * radius);
		int my = (int) Math.round(wy + radius - Math.cos(angle) * sat * radius);
		graphics.outline(mx - 3, my - 3, 7, 7, 0xFF000000);
		graphics.outline(mx - 2, my - 2, 5, 5, 0xFFFFFFFF);
		// brightness: from the full color down to black
		int bx = barX();
		int full = hsvToRgb(hue, sat, 1f);
		for (int i = 0; i < WHEEL; i++) {
			float v = 1f - i / (float) (WHEEL - 1);
			int r = Math.round(((full >> 16) & 255) * v);
			int g = Math.round(((full >> 8) & 255) * v);
			int b = Math.round((full & 255) * v);
			graphics.fill(bx, wy + i, bx + 10, wy + i + 1, 0xFF000000 | (r << 16) | (g << 8) | b);
		}
		graphics.outline(bx - 1, wy - 1, 12, WHEEL + 2, 0xFF3B3C45);
		int markY = wy + Math.round((1f - val) * (WHEEL - 1));
		graphics.fill(bx - 3, markY, bx + 13, markY + 1, 0xFFFFFFFF);
		// the picked color of the selected letter
		int tx = px + 128;
		String letter = selected >= 0 && selected < units.size() ? units.get(selected) : "";
		graphics.text(font, "Selected letter: " + (letter.equals("{name}") ? "name" : letter), tx, wy, 0xFF9A9AA6, true);
		graphics.fill(tx, wy + 14, tx + 28, wy + 42, 0xFF000000 | currentRgb());
		graphics.outline(tx, wy + 14, 28, 28, 0xFFFFFFFF);
		graphics.text(font, hex(currentRgb()), tx + 36, wy + 24, 0xFFFFFFFF, true);
		graphics.text(font, "Click the wheel, drag the bar for dark", tx, wy + 48, 0xFF70707C, false);
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
		if (!json && event.button() == 0) {
			double radius = WHEEL / 2.0;
			double dx = event.x() - (wheelX() + radius);
			double dy = event.y() - (wheelY() + radius);
			if (dx * dx + dy * dy <= radius * radius) {
				dragTarget = 1;
				pick(1, event.x(), event.y());
				return true;
			}
			if (event.x() >= barX() && event.x() < barX() + 10 && event.y() >= wheelY() && event.y() < wheelY() + WHEEL) {
				dragTarget = 2;
				pick(2, event.x(), event.y());
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragTarget != 0) {
			pick(dragTarget, event.x(), event.y());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragTarget = 0;
		return super.mouseReleased(event);
	}


	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
