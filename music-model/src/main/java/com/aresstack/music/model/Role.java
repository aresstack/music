package com.aresstack.music.model;

import java.util.List;

/** The musical roles a part can have. A part may also have none. */
public final class Role {
    public static final String MELODY = "melody";
    public static final String HARMONY = "harmony";
    public static final String BASS = "bass";
    public static final String DRUMS = "drums";
    public static final String TEXTURE = "texture";
    public static final List<String> ALL = List.of(MELODY, HARMONY, BASS, DRUMS, TEXTURE);

    private Role() {
    }
}
