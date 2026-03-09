package org.voxeleers.game.rooms;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

public class Room {
    public Int2IntOpenHashMap cells;
    public Room() {
        cells = new Int2IntOpenHashMap();
    }
    public Room(Int2IntOpenHashMap cells) {
        this.cells = cells;
    }
}
