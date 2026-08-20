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
			int right = getContentRight();
			int bottom = getContentBottom();
			int color = isSelected() ? 0x8033AAFF : isMouseOver ? 0x40FFFFFF : 0x25000000;
			extractor.fill(x, y, right, bottom, color);

			int textColor = isSelected() ? 0xFFFFAA00 : 0xFFFFFFFF;
			String rightText = rightLabel();
			int rightWidth = rightText.isEmpty() ? 0 : minecraft.font.width(rightText);

			if (!rightText.isEmpty()) {
				int rightX = right - 6 - rightWidth;
				extractor.text(minecraft.font, rightText, rightX, y + 6, textColor);
			}

			String leftText = leftLabel();
			int maxLeftWidth = rightText.isEmpty() ? (right - x - 12) : (right - 6 - rightWidth - 6 - (x + 6));
			if (maxLeftWidth > 0 && minecraft.font.width(leftText) > maxLeftWidth) {
				if (maxLeftWidth > 20) {
					leftText = minecraft.font.plainSubstrByWidth(leftText, maxLeftWidth - 8) + "…";
				} else {
					leftText = minecraft.font.plainSubstrByWidth(leftText, maxLeftWidth);
				}
			}
			if (maxLeftWidth > 0) {
				extractor.text(minecraft.font, leftText, x + 6, y + 6, textColor);
			}
		}

		private boolean isSelected() {
			return ServerListWidget.this.getSelected() == this;
		}

		private String leftLabel() {
			return server.name() + " [" + server.host() + ":" + server.port() + "]";
		}

		private String rightLabel() {
			StringBuilder sb = new StringBuilder();
			if (server.ping() == Integer.MAX_VALUE) {
				sb.append(Component.translatable("claj.manage.offline").getString().trim());
			} else if (server.ping() >= 0) {
				sb.append(server.ping()).append("ms");
				if (!server.compatible()) {
					sb.append(" ").append(Component.translatable("claj.manage.incompatible").getString().trim());
				}
			}
			if (showDeleteHint && ClajServers.custom.containsKey(server.name())) {
				if (!sb.isEmpty()) {
					sb.append(" ");
				}
				sb.append(Component.translatable("claj.manage.delete-server.hint").getString().trim());
			}
			return sb.toString();
		}

		private String label() {
			String left = leftLabel();
			String right = rightLabel();
			return right.isEmpty() ? left : left + " " + right;
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
