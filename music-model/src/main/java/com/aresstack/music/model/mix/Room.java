package com.aresstack.music.model.mix;

/**
 * The room the stage stands in.
 *
 * @param kind          the kind of room as the studio names it, for example "hall"
 * @param reverbSeconds how long the room rings
 * @param amount        how much of the room is heard, 0 to 1
 */
public record Room(String kind, double reverbSeconds, double amount) {
    public Room {
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("A room has a kind.");
        }
        if (!(reverbSeconds >= 0) || !(amount >= 0 && amount <= 1)) {
            throw new IllegalArgumentException("Invalid room: " + reverbSeconds + " s, amount " + amount);
        }
    }
}
