package zhiwo.claj.screen;

import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

import net.minecraft.network.chat.Component;

import zhiwo.claj.ClajServers;

/**
 * Add a custom CLaJ relay server (name + address). After adding, returns to the screen it was
 * opened from (join screen or manage screen), which then refreshes its server list.
 */
public class AddServerScreen extends Screen {
	private final Screen parent;
	private final Consumer<String> onAdded;
	private EditBox nameBox;
	private EditBox addressBox;
	private String savedName = "";
	private String savedAddress = "";

	public AddServerScreen(Screen parent, Consumer<String> onAdded) {
		super(Component.translatable("claj.add-server.title"));
		this.parent = parent;
		this.onAdded = onAdded;
	}

	@Override
	protected void init() {
		buildWidgets();
	}

	@Override
	protected void repositionElements() {
		buildWidgets();
	}

	private void buildWidgets() {
		if (nameBox != null) savedName = nameBox.getValue();
		if (addressBox != null) savedAddress = addressBox.getValue();
		clearWidgets();

		int w = Math.min(340, width - 60);
		int x = (width - w) / 2;
		int y = Math.max(24, (height - 170) / 2);

		nameBox = new EditBox(font, x, y, w, 20, Component.translatable("claj.add-server.name"));
		nameBox.setMaxLength(64);
		nameBox.setHint(Component.translatable("claj.add-server.name.hint"));
		nameBox.setValue(savedName);
		addRenderableWidget(nameBox);
		y += 28;

		addressBox = new EditBox(font, x, y, w, 20, Component.translatable("claj.add-server.address"));
		addressBox.setMaxLength(128);
		addressBox.setHint(Component.translatable("claj.add-server.address.hint"));
		addressBox.setValue(savedAddress);
		addRenderableWidget(addressBox);
		y += 32;

		addRenderableWidget(Button.builder(Component.translatable("claj.add-server.add"), b -> add()).bounds(x, y, (w - 10) / 2, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> goBack())
				.bounds(x + (w - 10) / 2 + 10, y, (w - 10) / 2, 20).build());
	}

	private void add() {
		String name = nameBox.getValue().trim();
		String address = addressBox.getValue().trim();
		if (name.isEmpty() || address.isEmpty()) return;
		ClajServers.custom.put(name, address);
		ClajServers.saveCustom();
		// Return to the screen this was opened from and refresh its server list.
		// NOTE: setScreenAndShow(parent) only repositions an already-initialized screen,
		// so the caller's onAdded callback performs the actual refresh.
		if (minecraft != null) {
			if (parent != null) minecraft.setScreenAndShow(parent);
			else minecraft.setScreenAndShow(null);
			if (onAdded != null) onAdded.accept(name);
		}
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
		super.extractRenderState(extractor, mouseX, mouseY, partialTick);
	}
}
