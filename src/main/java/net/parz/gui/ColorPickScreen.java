package net.parz.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;
import java.util.function.Consumer;

/** "Custom" and "Gradient" of the item name: pick one color, or the two ends of a gradient. */
final class ColorPickScreen extends Screen {
	private static final int W = 270;
	private static final int H = 150;
	private static final int WHEEL = NameScreen.WHEEL;

	private final Screen parent;
	private final String sample;
	private final Consumer<int[]> chosen;
	/** One color, or the start and the end of the gradient. */
	private final int[] colors;
	private int editing = 0;
	private float hue;
	private float sat;
	private float val;
	/** 0 = nothing is dragged, 1 = the wheel, 2 = the brightness bar. */
	private int dragTarget = 0;
	private boolean loading = false;
	private EditBox hexBox;
	private int px;
	private int py;

	ColorPickScreen(Screen parent, String sample, boolean gradient, int first, int second, Consumer<int[]> chosen) {
		super(Component.literal(gradient ? "Gradient" : "Custom color"));
		this.parent = parent;
		this.sample = sample;
		this.chosen = chosen;
		this.colors = gradient ? new int[]{first, second} : new int[]{first};
	}

	@Override
	protected void init() {
		px = (this.width - W) / 2;
		py = (this.height - H) / 2;
		NameScreen.ensureWheelTexture();
		hexBox = new EditBox(this.font, rightX(), py + 62, 74, 18, Component.literal("color"));
		hexBox.setMaxLength(7);
		this.addRenderableWidget(hexBox);
		show();
		hexBox.setResponder(text -> {
			if (loading) {
				return;
			}
			try {
				colors[editing] = ArmorScreen.parseColor(text);
				toWheel();
			} catch (IllegalArgumentException ignored) {
				// not complete yet
			}
		});
	}

	/** The wheel, the bar and the hex field show the color that is edited. */
	private void show() {
		toWheel();
		writeHex();
	}

	private void toWheel() {
		float[] hsv = NameScreen.rgbToHsv(colors[editing]);
		hue = hsv[0];
		sat = hsv[1];
		val = hsv[2];
	}

	private void writeHex() {
		loading = true;
		hexBox.setValue(String.format(Locale.ROOT, "#%06X", colors[editing]));
		loading = false;
	}

	private int wheelX() {
		return px + 12;
	}

	private int wheelY() {
		return py + 24;
	}

	private int barX() {
		return wheelX() + WHEEL + 8;
	}

	private int rightX() {
		return px + 128;
	}

	private void pick(int target, double mouseX, double mouseY) {
		if (target == 1) {
			float radius = WHEEL / 2f;
			double dx = mouseX - (wheelX() + radius);
			double dy = mouseY - (wheelY() + radius);
			double angle = Math.atan2(dx, -dy);
			double around = (angle < 0 ? angle + Math.PI * 2 : angle) / (Math.PI * 2);
			hue = (float) (1.0 - around);
			sat = (float) Math.min(1.0, Math.sqrt(dx * dx + dy * dy) / radius);
			if (val < 0.05f) {
				val = 1f; // from black the wheel would change nothing
			}
		} else {
			val = (float) Math.max(0.0, Math.min(1.0, 1.0 - (mouseY - wheelY()) / WHEEL));
		}
		colors[editing] = NameScreen.hsvToRgb(hue, sat, val);
		writeHex();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		CustomUi.panel(graphics, px, py, W, H);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.text(font, this.title, px + 12, py + 9, CustomUi.TEXT, false);

		int wx = wheelX();
		int wy = wheelY();
		graphics.blit(RenderPipelines.GUI_TEXTURED, NameScreen.wheelTexture, wx, wy, 0f, 0f, WHEEL, WHEEL, WHEEL, WHEEL, WHEEL, WHEEL);
		float radius = WHEEL / 2f;
		double angle = (1.0 - hue) * Math.PI * 2;
		int mx = (int) Math.round(wx + radius + Math.sin(angle) * sat * radius);
		int my = (int) Math.round(wy + radius - Math.cos(angle) * sat * radius);
		graphics.outline(mx - 3, my - 3, 7, 7, 0xFF000000);
		graphics.outline(mx - 2, my - 2, 5, 5, 0xFFFFFFFF);
		int full = NameScreen.hsvToRgb(hue, sat, 1f);
		for (int i = 0; i < WHEEL; i++) {
			graphics.fill(barX(), wy + i, barX() + 10, wy + i + 1, 0xFF000000 | ItemScreen.mix(full, 0, i / (float) (WHEEL - 1)));
		}
		int markY = wy + Math.round((1f - val) * (WHEEL - 1));
		graphics.fill(barX() - 3, markY, barX() + 13, markY + 1, 0xFFFFFFFF);

		int x = rightX();
		if (colors.length > 1) {
			for (int i = 0; i < 2; i++) {
				int sx = x + i * 64;
				graphics.fill(sx, py + 24, sx + 58, py + 44, 0xFF000000 | colors[i]);
				graphics.outline(sx, py + 24, 58, 20, i == editing ? CustomUi.SELECTED_EDGE : CustomUi.BOX_EDGE);
				graphics.text(font, i == 0 ? "Start" : "End", sx, py + 48, i == editing ? CustomUi.TEXT : CustomUi.TEXT_DIM, false);
			}
		} else {
			graphics.fill(x, py + 24, x + 58, py + 44, 0xFF000000 | colors[0]);
		}
		// the marked text in the color(s)
		String text = font.plainSubstrByWidth(sample, W - 140);
		MutableComponent preview = Component.literal("");
		for (int i = 0; i < text.length(); i++) {
			int rgb = colors.length > 1 ? ItemScreen.mix(colors[0], colors[1], sample.length() < 2 ? 0 : i / (float) (sample.length() - 1))
					: colors[0];
			preview.append(Component.literal(String.valueOf(text.charAt(i))).withColor(rgb));
		}
		graphics.text(font, preview, x, py + 90, CustomUi.TEXT, true);

		CustomUi.button(graphics, font, "Apply", px + W - 140, py + H - 26, 60, 18, mouseX, mouseY);
		CustomUi.button(graphics, font, "Cancel", px + W - 72, py + H - 26, 60, 18, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			double mx = event.x();
			double my = event.y();
			double radius = WHEEL / 2.0;
			double dx = mx - (wheelX() + radius);
			double dy = my - (wheelY() + radius);
			if (dx * dx + dy * dy <= radius * radius) {
				dragTarget = 1;
				pick(1, mx, my);
				return true;
			}
			if (CustomUi.inside(mx, my, barX(), wheelY(), 10, WHEEL)) {
				dragTarget = 2;
				pick(2, mx, my);
				return true;
			}
			for (int i = 0; colors.length > 1 && i < 2; i++) {
				if (CustomUi.inside(mx, my, rightX() + i * 64, py + 24, 58, 32)) {
					editing = i;
					show();
					return true;
				}
			}
			if (CustomUi.inside(mx, my, px + W - 140, py + H - 26, 60, 18)) {
				onClose();
				chosen.accept(colors.clone());
				return true;
			}
			if (CustomUi.inside(mx, my, px + W - 72, py + H - 26, 60, 18)) {
				onClose();
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
	public void onClose() {
		Minecraft.getInstance().gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
