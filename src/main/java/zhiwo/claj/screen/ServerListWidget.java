package zhiwo.claj.screen;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import zhiwo.claj.ClajServers;

/**
 * Scrollable server list with no fixed row limit, built on Minecraft's native selection list
 * (scroll bar, wheel scrolling and selection highlight included).
 *
 * <p>Manage screen: click a row to select it. Join screen: click a custom server to delete it.
 */
public class ServerListWidget extends ObjectSelectionList<ServerListWidget.ServerEntry> {
	public interface Handler {
		void onClick(ClajManageScreen.ServerInfo server);
	}

	private final Handler handler;
	/** Whether the "click to delete" hint is shown for custom servers (join screen only). */
	private final boolean showDeleteHint;

	public ServerListWidget(Minecraft minecraft, int width, int height, int y, Handler handler, boolean showDeleteHint) {
		super(minecraft, width, height, y, 24);
		this.handler = handler;
		this.showDeleteHint = showDeleteHint;
	}

	/** Replaces all entries and restores the selection with the given name (may be null). */
	public void setServers(List<ClajManageScreen.ServerInfo> servers, String selectName) {
		clearEntries();
		for (ClajManageScreen.ServerInfo server : servers) {
			ServerEntry entry = new ServerEntry(server);
			addEntry(entry);
			if (selectName != null && selectName.equals(server.name())) {
				setSelected(entry);
			}
		}
	}

	/**
	 * Incrementally updates one row when its ping arrives - no rebuild needed,
	 * the entry simply re-renders on the next frame.
	 */
	public void updateServer(String name, int ping, boolean compatible) {
		for (ServerEntry entry : children()) {
			if (entry.server.name().equals(name)) {
				ClajManageScreen.ServerInfo old = entry.server;
				entry.server = new ClajManageScreen.ServerInfo(old.name(), old.host(), old.port(), ping, compatible);
				return;
			}
		}
	}

	@Override
	protected int scrollBarX() {
		return getRight() - 6;
	}

	public class ServerEntry extends Entry<ServerEntry> {
		public ClajManageScreen.ServerInfo server;

		ServerEntry(ClajManageScreen.ServerInfo server) {
			this.server = server;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor extractor, int mouseX, int mouseY, boolean isMouseOver, float partialTick) {
			int x = getContentX();
			int y = getContentY();
			int color = isSelected() ? 0x8033AAFF : isMouseOver ? 0x40FFFFFF : 0x25000000;
			extractor.fill(x, y, getContentRight(), getContentBottom(), color);
			String text = label();
			int maxWidth = getContentRight() - x - 12;
			if (minecraft.font.width(text) > maxWidth && maxWidth > 20) {
				text = minecraft.font.plainSubstrByWidth(text, maxWidth - 8) + "…";
			}
			extractor.text(minecraft.font, text, x + 6, y + 6, isSelected() ? 0xFFFFAA00 : 0xFFFFFFFF);
		}

		private boolean isSelected() {
			return ServerListWidget.this.getSelected() == this;
		}

		private String label() {
			String suffix;
			if (server.ping() < 0) {
				suffix = "";
			} else if (server.ping() == Integer.MAX_VALUE) {
				suffix = Component.translatable("claj.manage.offline").getString();
			} else {
				suffix = " " + server.ping() + "ms" + (server.compatible() ? "" : Component.translatable("claj.manage.incompatible").getString());
			}
			String base = server.name() + " [" + server.host() + ":" + server.port() + "]" + suffix;
			if (showDeleteHint && ClajServers.custom.containsKey(server.name())) {
				base += "  " + Component.translatable("claj.manage.delete-server.hint").getString();
			}
			return base;
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean flag) {
			if (isMouseOver(event.x(), event.y())) {
				ServerListWidget.this.setSelected(this);
				handler.onClick(server);
				return true;
			}
			return false;
		}

		@Override
		public Component getNarration() {
			return Component.literal(label());
		}
	}
}
