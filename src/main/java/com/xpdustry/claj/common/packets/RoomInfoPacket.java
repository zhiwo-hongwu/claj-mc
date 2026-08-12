/**
 * This file is part of CLaJ. The system that allows you to play with your friends,
 * just by creating a room, copying the link and sending it to your friends.
 * Copyright (c) 2026  Xpdustry
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.xpdustry.claj.common.packets;

import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;

import com.xpdustry.claj.common.status.ClajType;


public class RoomInfoPacket extends RoomStatePacket {
  public long roomId;
  public boolean isProtected;
  public ClajType type;
  public int clients;
  public int maxClients;

  @Override
  public void read(ByteBufferInput read) {
    roomId = read.readLong();
    isProtected = read.readBoolean();
    int size = read.readByte() & 0xff;
    if (size == 0) type = null;
    else {
      byte[] data = new byte[size];
      read.readFully(data);
      type = new ClajType(data);
    }
    clients = read.readChar();
    maxClients = read.readChar();
    super.read(read);
  }

  @Override
  public void write(ByteBufferOutput write) {
    write.writeLong(roomId);
    write.writeBoolean(isProtected);
    if (type == null) write.writeByte(0); else type.write(write);
    write.writeChar(clients);
    write.writeChar(maxClients);
    super.write(write);
  }

  @Override
  public boolean allow(boolean isServer) {
    return !isServer;
  }
}
