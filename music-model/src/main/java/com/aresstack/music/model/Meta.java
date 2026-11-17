package com.aresstack.music.model;

/**
 * What a piece is called and what it was meant to be. Every field may be {@code null}.
 *
 * @param title       the name of the piece as the user sees it
 * @param genre       the kind of music, in the user's words
 * @param mood        the mood, in the user's words
 * @param brief       the description the piece was made from
 * @param plannedFrom the description the plan of the piece was made from, if it differs
 * @param remarks     notes on the arrangement as a whole
 */
public record Meta(String title, String genre, String mood, String brief, String plannedFrom, String remarks) {
    public static final Meta EMPTY = new Meta(null, null, null, null, null, null);

    public Meta withTitle(String newTitle) {
        return new Meta(newTitle, genre, mood, brief, plannedFrom, remarks);
    }

    public Meta withGenre(String newGenre) {
        return new Meta(title, newGenre, mood, brief, plannedFrom, remarks);
    }
}
