package com.aresstack.music.model;

/**
 * A sound file that is source material of the piece, for example a recording or a sound
 * effect, as opposed to anything rendered from it.
 *
 * @param path where the file lies, relative to the folder of the piece, with '/' between folders
 */
public record Asset(String id, String name, String path) {
    public Asset {
        Ids.require(id, "an asset");
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("An asset needs the path of its file: " + id);
        }
    }
}
