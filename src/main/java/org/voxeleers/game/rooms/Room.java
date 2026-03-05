package org.voxeleers.game.rooms;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;

public class Room {
    public Int2ObjectArrayMap<Cell> cells;
    public Room() {
        cells = new Int2ObjectArrayMap<>();
    }
    public Room(Int2ObjectArrayMap<Cell> cells) {
        this.cells = cells;
    }
}
