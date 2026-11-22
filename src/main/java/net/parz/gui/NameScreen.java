package net.parz.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.parz.badge.BadgeConfig;
import net.parz.badge.BadgeFormat;

import java.util.List;

/**
 * /pz customname - how your name is shown to the other Parz users (tab list and above your head): the simple fields
 * (brackets, symbol and colors) or a Minecraft text component in JSON.
 */
public class NameScreen extends Screen {
	private static final int PANEL_W = 340;
	private static final int PANEL_H = 214;

	private boolean json;
	private EditBox left;
	private EditBox symbol;
	private EditBox right;
	private EditBox bracketColor;
	private EditBox symbolColor;
	private EditBox nameColor;
	private EditBox jsonBox;
	private Button modeButton;

	private String error = "";
	private String status = "";
	private Component preview = Component.empty();
	private String lastSignature = "";
	private int px;
	private int py;

	public NameScreen() {
		super(Component.literal("Parz Name"));
	}

	@Override
	protected void init() {
		BadgeConfig.Data data = BadgeConfig.data();
		json = data.json;
		px = (this.width - PANEL_W) / 2;
		py = (this.height - PANEL_H) / 2;
		int x = px + 14;

		modeButton = Button.builder(Component.literal(""), b -> {
			json = !json;
			updateVisibility();
			lastSignature = "";
		}).bounds(x, py + 28, 150, 20).build();
		this.addRenderableWidget(modeButton);

		left = box(x, py + 74, 36, 6, data.left);
		symbol = box(x + 44, py + 74, 70, 12, data.symbol);
		right = box(x + 122, py + 74, 36, 6, data.right);
		bracketColor = box(x, py + 114, 90, 9, data.bracketColor);
		symbolColor = box(x + 100, py + 114, 90, 9, data.symbolColor);
		nameColor = box(x + 200, py + 114, 90, 9, data.nameColor);
		jsonBox = box(x, py + 74, PANEL_W - 28, 1500, data.jsonText);

		this.addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(x, py + PANEL_H - 30, 80, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
			BadgeConfig.reset();
			status = "Back to the default [ዞ] Name";
			this.rebuildWidgets();
		}).bounds(x + 88, py + PANEL_H - 30, 80, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
				.bounds(px + PANEL_W - 94, py + PANEL_H - 30, 80, 20).build());
		updateVisibility();
	}

	private EditBox box(int x, int y, int w, int maxLength, String value) {
		EditBox box = new EditBox(this.font, x, y, w, 18, Component.literal("field"));
		box.setMaxLength(maxLength);
		box.setValue(value == null ? "" : value);
		box.setResponder(text -> lastSignature = "");
		this.addRenderableWidget(box);
		return box;
	}

	private void updateVisibility() {
		modeButton.setMessage(Component.literal(json ? "Mode: JSON text" : "Mode: simple"));
		for (EditBox simple : new EditBox[]{left, symbol, right, bracketColor, symbolColor, nameColor}) {
			simple.visible = !json;
			simple.active = !json;
		}
		jsonBox.visible = json;
		jsonBox.active = json;
	}

	private BadgeConfig.Data fromFields() {
		BadgeConfig.Data data = new BadgeConfig.Data();
		data.json = json;
		data.left = left.getValue();
		data.symbol = symbol.getValue();
		data.right = right.getValue();
		data.bracketColor = bracketColor.getValue();
		data.symbolColor = symbolColor.getValue();
		data.nameColor = nameColor.getValue();
		data.jsonText = jsonBox.getValue();
		return data;
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

	/** Makes the preview from the fields (only when something changed). */
	private void updatePreview() {
		String signature = json + "|" + left.getValue() + "|" + symbol.getValue() + "|" + right.getValue() + "|" + bracketColor.getValue()
				+ "|" + symbolColor.getValue() + "|" + nameColor.getValue() + "|" + jsonBox.getValue();
		if (signature.equals(lastSignature)) {
			return;
		}
		lastSignature = signature;
		String name = Minecraft.getInstance().getUser().getName();
		try {
			List<BadgeFormat.Run> runs = BadgeConfig.compute(fromFields());
			preview = BadgeFormat.render(runs, name);
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
		if (json) {
			graphics.text(font, "Minecraft text component (JSON). {name} = your name.", x, py + 60, 0xFF9A9AA6, true);
			graphics.text(font, "Example: {\"text\":\"\",\"extra\":[{\"text\":\"[H] \",\"color\":\"#FF5555\"},{\"text\":\"{name}\",\"color\":\"#FFAA00\"}]}",
					x, py + 98, 0xFF70707C, false);
		} else {
			graphics.text(font, "Left", x, py + 62, 0xFF9A9AA6, true);
			graphics.text(font, "Symbol (any text)", x + 44, py + 62, 0xFF9A9AA6, true);
			graphics.text(font, "Right", x + 122, py + 62, 0xFF9A9AA6, true);
			graphics.text(font, "Bracket color", x, py + 102, 0xFF9A9AA6, true);
			graphics.text(font, "Symbol color", x + 100, py + 102, 0xFF9A9AA6, true);
			graphics.text(font, "Name color", x + 200, py + 102, 0xFF9A9AA6, true);
			graphics.text(font, "Colors like #FF5555", x, py + 138, 0xFF70707C, false);
		}
		graphics.text(font, "Preview:", x, py + 152, 0xFF9A9AA6, true);
		graphics.text(font, preview, x + 50, py + 152, 0xFFFFFFFF, true);
		if (!error.isEmpty()) {
			graphics.text(font, error, x, py + 168, 0xFFFF5555, true);
		} else if (!status.isEmpty()) {
			graphics.text(font, status, x, py + 168, 0xFF55FF55, true);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
