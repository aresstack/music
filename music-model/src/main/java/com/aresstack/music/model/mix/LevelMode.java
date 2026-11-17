package com.aresstack.music.model.mix;

/** How the level of the finished mix is set. */
public enum LevelMode {
    /** As every piece from before the model: the whole mix is brought to a peak level. */
    LEGACY_PEAK,
    /** Fixed levels and a limiter on the master; normalising is a matter of export (work package D1b). */
    FIXED
}
