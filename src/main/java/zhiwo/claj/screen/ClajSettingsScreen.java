package zhiwo.claj.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import zhiwo.claj.ClajConfig;

/**
 * Room settings: public visibility and 4-digit password protection.
 * Values are persisted and applied when creating a room. Back returns to the management screen.
 */
public class ClajSettingsScreen extends Screen {
	private final Screen parent;
	private Checkbox publicBox;
	private Checkbox protectedBox;
	private EditBox passwordBox;
	private Component status = Component.empty();
	private boolean savedPublic;
	private boolean savedProtected;
	private String savedPin = "";

	public ClajSettingsScreen(Screen parent) {
		super(Component.translatable("claj.settings.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ClajConfig.Data data = ClajConfig.data();
		savedPublic = data.roomPublic;
		savedProtected = data.roomProtected;
		savedPin = data.roomProtected ? String.format("%04d", data.roomPassword) : "";
		buildWidgets();
	}

	@Override
	protected void repositionElements() {
		buildWidgets();
	}

	private void buildWidgets() {
		if (publicBox != null) savedPublic = publicBox.selected();
		if (protectedBox != null) savedProtected = protectedBox.selected();
		if (passwordBox != null) savedPin = passwordBox.getValue();
		clearWidgets();

		int w = Math.min(340, width - 60);
		int x = (width - w) / 2;
		int y = Math.max(24, (height - 210) / 2);

		publicBox = Checkbox.builder(Component.translatable("claj.settings.public"), font)
				.pos(x, y).selected(savedPublic).build();
		addRenderableWidget(publicBox);
		y += 26;

		protectedBox = Checkbox.builder(Component.translatable("claj.settings.protected"), font)
				.pos(x, y).selected(savedProtected).build();
		addRenderableWidget(protectedBox);
		y += 30;

		passwordBox = new EditBox(font, x, y, w, 20, Component.translatable("claj.settings.password"));
		passwordBox.setMaxLength(4);
		passwordBox.setHint(Component.translatable("claj.settings.password.hint"));
		passwordBox.setValue(savedPin);
		addRenderableWidget(passwordBox);
		y += 32;

		addRenderableWidget(Button.builder(Component.translatable("claj.settings.save"), b -> save()).bounds(x, y, w, 20).build());
		y += 28;
		addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> goBack()).bounds(x, y, w, 20).build());
	}

	private void save() {
		ClajConfig.Data data = ClajConfig.data();
		data.roomPublic = publicBox.selected();
		data.roomProtected = protectedBox.selected();
		try {
			int password = Integer.parseInt(passwordBox.getValue().trim());
			if (password < 0 || password > 9999) password = 0;
			data.roomPassword = password;
		} catch (NumberFormatException e) {
			data.roomPassword = 0;
		}
		ClajConfig.save();
		status = Component.translatable("claj.settings.saved");
		// Return to the management screen this was opened from.
		if (minecraft != null && parent != null) minecraft.setScreenAndShow(parent);
		else if (minecraft != null) minecraft.setScreenAndShow(null);
	}

	private void goBack() {
		if (parent != null) minecraft.setScreenAndShow(parent);
		else onClose();
	}

	@Override
	public void onClose() {
		if (parent != null) minecraft.setScreenAndShow(parent);
		else super.onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
		extractor.fill(0, 0, width, height, 0xAA000000);
		extractor.text(font, getTitle(), (width - font.width(getTitle())) / 2, 40, 0xFFFFFF);
		if (!status.getString().isEmpty()) {
			extractor.text(font, status, (width - font.width(status)) / 2, height - 40, 0xFF55FF55);
		}
		super.extractRenderState(extractor, mouseX, mouseY, partialTick);
	}
}
