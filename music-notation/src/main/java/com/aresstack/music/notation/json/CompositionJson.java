package com.aresstack.music.notation.json;

import com.aresstack.music.model.AudioClip;
import com.aresstack.music.model.Asset;
import com.aresstack.music.model.Articulation;
import com.aresstack.music.model.Clip;
import com.aresstack.music.model.Composition;
import com.aresstack.music.model.Dynamics;
import com.aresstack.music.model.Key;
import com.aresstack.music.model.Meta;
import com.aresstack.music.model.MidiClip;
import com.aresstack.music.model.Note;
import com.aresstack.music.model.Part;
import com.aresstack.music.model.Section;
import com.aresstack.music.model.TimeMap;
import com.aresstack.music.model.Track;
import com.aresstack.music.model.Voice;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * {@code composition.json}: the model of a piece as a file, and the binding truth about it.
 * Reading is strict: a file that does not say what the model needs is refused with a message
 * that names what is wrong, and a file of a newer version is refused instead of being misread.
 */
public final class CompositionJson {
    public static final String FILE_NAME = "composition.json";
    public static final String FORMAT = "aresstack-composition";
    /** The newest version this studio reads and writes. */
    public static final int VERSION = 2;
    /**
     * The version of a file that says nothing beyond the first model: it is still written so,
     * so that an older studio keeps reading such pieces. Version 2 adds gliding tempos, dynamic
     * marks and hairpins, and articulations of notes; only a piece that has one of them is
     * written as version 2, which an older studio refuses instead of losing them silently.
     */
    public static final int FIRST_VERSION = 1;

    private CompositionJson() {
    }

    /** The model of the piece in a folder. */
    public static Composition load(Path folder) throws IOException {
        return read(JsonText.read(folder.resolve(FILE_NAME)));
    }

    public static void save(Path folder, Composition composition) throws IOException {
        JsonText.atomically(folder.resolve(FILE_NAME), write(composition));
    }

    public static JsonObject write(Composition composition) {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("version", beyondFirst(composition) ? VERSION : FIRST_VERSION);
        Meta meta = composition.meta();
        JsonObject m = new JsonObject();
        put(m, "title", meta.title());
        put(m, "genre", meta.genre());
        put(m, "mood", meta.mood());
        put(m, "brief", meta.brief());
        put(m, "plannedFrom", meta.plannedFrom());
        put(m, "remarks", meta.remarks());
        root.add("meta", m);
        root.add("timeMap", time(composition.timeMap()));
        root.add("sections", array(composition.sections(), CompositionJson::section));
        root.add("parts", array(composition.parts(), CompositionJson::part));
        root.add("tracks", array(composition.tracks(), CompositionJson::track));
        root.add("assets", array(composition.assets(), asset -> {
            JsonObject a = new JsonObject();
            a.addProperty("id", asset.id());
            put(a, "name", asset.name());
            a.addProperty("path", asset.path());
            return a;
        }));
        if (!composition.dynamics().equals(Dynamics.NONE)) {
            root.add("dynamics", dynamics(composition.dynamics()));
        }
        return root;
    }

    /** Whether the piece says something only version 2 can. */
    static boolean beyondFirst(Composition composition) {
        if (!composition.dynamics().equals(Dynamics.NONE)) {
            return true;
        }
        for (TimeMap.Tempo tempo : composition.timeMap().tempos()) {
            if (tempo.glide()) {
                return true;
            }
        }
        for (Track track : composition.tracks()) {
            for (Clip clip : track.clips()) {
                if (clip instanceof MidiClip midi) {
                    for (Note note : midi.notes()) {
                        if (note.articulation() != null) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static JsonObject dynamics(Dynamics dynamics) {
        JsonObject d = new JsonObject();
        d.add("marks", array(dynamics.marks(), mark -> {
            JsonObject e = new JsonObject();
            e.addProperty("tick", mark.tick());
            e.addProperty("level", mark.level().symbol());
            put(e, "part", mark.partId());
            return e;
        }));
        d.add("hairpins", array(dynamics.hairpins(), pin -> {
            JsonObject e = new JsonObject();
            e.addProperty("start", pin.startTick());
            e.addProperty("end", pin.endTick());
            e.addProperty("target", pin.target().symbol());
            put(e, "part", pin.partId());
            return e;
        }));
        JsonObject scale = new JsonObject();
        for (Dynamics.Level level : Dynamics.Level.values()) {
            scale.addProperty(level.symbol(), dynamics.scale().velocity(level));
        }
        d.add("scale", scale);
        d.addProperty("reference", dynamics.scale().reference().symbol());
        JsonObject articulations = new JsonObject();
        for (Articulation articulation : Articulation.values()) {
            JsonObject a = new JsonObject();
            a.addProperty("length", dynamics.articulations().length(articulation));
            a.addProperty("velocity", dynamics.articulations().velocity(articulation));
            articulations.add(articulation.token(), a);
        }
        d.add("articulations", articulations);
        return d;
    }

    private static Dynamics dynamics(JsonObject d) {
        if (d == null) {
            return Dynamics.NONE;
        }
        List<Dynamics.Mark> marks = list(d, "marks", e -> new Dynamics.Mark(e.get("tick").getAsLong(),
                Dynamics.Level.parse(text(e, "level")), text(e, "part")));
        List<Dynamics.Hairpin> hairpins = list(d, "hairpins", e -> new Dynamics.Hairpin(e.get("start").getAsLong(),
                e.get("end").getAsLong(), Dynamics.Level.parse(text(e, "target")), text(e, "part")));
        Dynamics.Scale scale = Dynamics.Scale.STANDARD;
        if (d.has("scale")) {
            java.util.EnumMap<Dynamics.Level, Integer> velocities = new java.util.EnumMap<>(Dynamics.Level.class);
            JsonObject s = d.getAsJsonObject("scale");
            for (Dynamics.Level level : Dynamics.Level.values()) {
                velocities.put(level, s.has(level.symbol()) ? s.get(level.symbol()).getAsInt()
                        : Dynamics.Scale.STANDARD.velocity(level));
            }
            scale = new Dynamics.Scale(velocities, d.has("reference") ? Dynamics.Level.parse(text(d, "reference"))
                    : Dynamics.Scale.STANDARD.reference());
        }
        Dynamics.Articulations articulations = Dynamics.Articulations.STANDARD;
        if (d.has("articulations")) {
            JsonObject a = d.getAsJsonObject("articulations");
            for (Articulation articulation : Articulation.values()) {
                if (a.has(articulation.token())) {
                    JsonObject factors = a.getAsJsonObject(articulation.token());
                    if (factors.has("length")) {
                        articulations = articulations.withLength(articulation, factors.get("length").getAsDouble());
                    }
                    if (factors.has("velocity")) {
                        articulations = articulations.withVelocity(articulation, factors.get("velocity").getAsDouble());
                    }
                }
            }
        }
        return new Dynamics(marks, hairpins, scale, articulations);
    }

    public static Composition read(JsonObject root) throws IOException {
        try {
            if (!FORMAT.equals(text(root, "format"))) {
                throw new IOException("Not a composition: format is " + text(root, "format"));
            }
            int version = root.get("version").getAsInt();
            if (version > VERSION) {
                throw new IOException("The composition was written by a newer studio (version " + version
                        + ", this one reads up to " + VERSION + ").");
            }
            JsonObject m = root.getAsJsonObject("meta");
            Meta meta = m == null ? Meta.EMPTY : new Meta(text(m, "title"), text(m, "genre"), text(m, "mood"),
                    text(m, "brief"), text(m, "plannedFrom"), text(m, "remarks"));
            List<Section> sections = list(root, "sections", CompositionJson::section);
            List<Part> parts = list(root, "parts", CompositionJson::part);
            List<Track> tracks = list(root, "tracks", CompositionJson::track);
            List<Asset> assets = list(root, "assets",
                    a -> new Asset(text(a, "id"), text(a, "name"), text(a, "path")));
            return new Composition(meta, time(root.getAsJsonObject("timeMap")), sections, parts, tracks, assets,
                    dynamics(root.getAsJsonObject("dynamics")));
        } catch (RuntimeException invalid) {
            throw new IOException("The composition cannot be read: " + invalid.getMessage(), invalid);
        }
    }

    private static JsonObject time(TimeMap time) {
        JsonObject t = new JsonObject();
        t.addProperty("ppq", TimeMap.PPQ);
        t.add("tempos", array(time.tempos(), tempo -> {
            JsonObject e = new JsonObject();
            e.addProperty("tick", tempo.tick());
            e.addProperty("bpm", tempo.bpm());
            if (tempo.glide()) {
                e.addProperty("glide", true);
            }
            return e;
        }));
        t.add("meters", array(time.meters(), meter -> {
            JsonObject e = new JsonObject();
            e.addProperty("tick", meter.tick());
            e.addProperty("numerator", meter.numerator());
            e.addProperty("denominator", meter.denominator());
            return e;
        }));
        t.add("keys", array(time.keys(), key -> {
            JsonObject e = new JsonObject();
            e.addProperty("tick", key.tick());
            e.addProperty("tonic", key.key().tonic());
            e.addProperty("mode", key.key().mode().name().toLowerCase(Locale.ROOT));
            if (key.key().flats() != new Key(key.key().tonic(), key.key().mode()).flats()) {
                e.addProperty("flats", key.key().flats()); // G flat major, D sharp minor
            }
            return e;
        }));
        return t;
    }

    private static TimeMap time(JsonObject t) throws IOException {
        if (t == null) {
            throw new IOException("The composition has no time map.");
        }
        if (t.has("ppq") && t.get("ppq").getAsInt() != TimeMap.PPQ) {
            throw new IOException("Only " + TimeMap.PPQ + " ticks per quarter are supported, not " + t.get("ppq"));
        }
        return new TimeMap(
                list(t, "tempos", e -> new TimeMap.Tempo(e.get("tick").getAsLong(), e.get("bpm").getAsDouble(),
                        e.has("glide") && e.get("glide").getAsBoolean())),
                list(t, "meters", e -> new TimeMap.Meter(e.get("tick").getAsLong(), e.get("numerator").getAsInt(),
                        e.get("denominator").getAsInt())),
                list(t, "keys", e -> new TimeMap.KeyChange(e.get("tick").getAsLong(), key(e))));
    }

    private static JsonObject section(Section section) {
        JsonObject s = new JsonObject();
        s.addProperty("id", section.id());
        put(s, "name", section.name());
        put(s, "function", section.function());
        s.addProperty("start", section.startTick());
        s.addProperty("length", section.lengthTicks());
        if (section.intensity() != null) {
            s.addProperty("intensity", section.intensity());
        }
        JsonArray harmony = new JsonArray();
        section.harmony().forEach(harmony::add);
        s.add("harmony", harmony);
        put(s, "instruction", section.instruction());
        return s;
    }

    private static Section section(JsonObject s) {
        List<String> harmony = new ArrayList<>();
        if (s.has("harmony")) {
            s.getAsJsonArray("harmony").forEach(chord -> harmony.add(chord.getAsString()));
        }
        return new Section(text(s, "id"), text(s, "name"), text(s, "function"), s.get("start").getAsLong(),
                s.get("length").getAsLong(), s.has("intensity") ? s.get("intensity").getAsDouble() : null, harmony,
                text(s, "instruction"));
    }

    private static JsonObject part(Part part) {
        JsonObject p = new JsonObject();
        p.addProperty("id", part.id());
        p.addProperty("name", part.name());
        put(p, "role", part.role());
        put(p, "instrument", part.instrument());
        p.addProperty("program", part.program());
        p.addProperty("libraryId", part.libraryId());
        p.addProperty("staffLayout", part.staffLayout().name().toLowerCase(Locale.ROOT));
        p.add("voices", array(part.voices(), voice -> {
            JsonObject v = new JsonObject();
            v.addProperty("id", voice.id());
            put(v, "name", voice.name());
            put(v, "staff", voice.staff() == null ? null : voice.staff().name().toLowerCase(Locale.ROOT));
            v.addProperty("stem", voice.stem().name().toLowerCase(Locale.ROOT));
            return v;
        }));
        if (part.legacyChannel() != null) {
            p.addProperty("legacyChannel", part.legacyChannel());
        }
        return p;
    }

    private static Part part(JsonObject p) {
        List<Voice> voices = list(p, "voices", v -> new Voice(text(v, "id"), text(v, "name"),
                text(v, "staff") == null ? null : Voice.Staff.valueOf(text(v, "staff").toUpperCase(Locale.ROOT)),
                text(v, "stem") == null ? null : Voice.Stem.valueOf(text(v, "stem").toUpperCase(Locale.ROOT))));
        return new Part(text(p, "id"), text(p, "name"), text(p, "role"), text(p, "instrument"),
                p.get("program").getAsInt(), text(p, "libraryId"),
                text(p, "staffLayout") == null ? null
                        : Part.StaffLayout.valueOf(text(p, "staffLayout").toUpperCase(Locale.ROOT)),
                voices, p.has("legacyChannel") ? p.get("legacyChannel").getAsInt() : null);
    }

    private static JsonObject track(Track track) {
        JsonObject t = new JsonObject();
        t.addProperty("id", track.id());
        t.addProperty("name", track.name());
        t.addProperty("kind", track.kind().name().toLowerCase(Locale.ROOT));
        put(t, "defaultPart", track.defaultPartId());
        t.add("clips", array(track.clips(), CompositionJson::clip));
        return t;
    }

    private static Track track(JsonObject t) {
        return new Track(text(t, "id"), text(t, "name"), Track.Kind.valueOf(text(t, "kind").toUpperCase(Locale.ROOT)),
                text(t, "defaultPart"), list(t, "clips", CompositionJson::clip));
    }

    private static JsonObject clip(Clip clip) {
        JsonObject c = new JsonObject();
        if (clip instanceof MidiClip midi) {
            c.addProperty("type", "midi");
            c.addProperty("id", midi.id());
            c.addProperty("start", midi.startTick());
            c.addProperty("length", midi.lengthTicks());
            put(c, "part", midi.partId());
            c.add("notes", array(midi.notes(), note -> {
                JsonObject n = new JsonObject();
                n.addProperty("id", note.id());
                n.addProperty("tick", note.tick());
                n.addProperty("length", note.length());
                n.addProperty("pitch", note.pitch());
                n.addProperty("velocity", note.velocity());
                n.addProperty("voice", note.voiceId());
                if (note.articulation() != null) {
                    n.addProperty("articulation", note.articulation().token());
                }
                return n;
            }));
        } else {
            AudioClip audio = (AudioClip) clip;
            c.addProperty("type", "audio");
            c.addProperty("id", audio.id());
            c.addProperty("start", audio.startTick());
            c.addProperty("lengthSamples", audio.lengthSamples());
            c.addProperty("asset", audio.assetId());
            c.addProperty("gainDb", audio.gainDb());
            c.addProperty("fadeInSamples", audio.fadeInSamples());
            c.addProperty("fadeOutSamples", audio.fadeOutSamples());
        }
        return c;
    }

    private static Clip clip(JsonObject c) {
        String type = text(c, "type");
        if ("midi".equals(type)) {
            return new MidiClip(text(c, "id"), c.get("start").getAsLong(), c.get("length").getAsLong(), text(c, "part"),
                    list(c, "notes", n -> new Note(text(n, "id"), n.get("tick").getAsLong(), n.get("length").getAsLong(),
                            n.get("pitch").getAsInt(), n.get("velocity").getAsInt(), text(n, "voice"),
                            articulation(text(n, "articulation")))));
        }
        if ("audio".equals(type)) {
            return new AudioClip(text(c, "id"), c.get("start").getAsLong(), c.get("lengthSamples").getAsLong(),
                    text(c, "asset"), c.get("gainDb").getAsDouble(), c.get("fadeInSamples").getAsLong(),
                    c.get("fadeOutSamples").getAsLong());
        }
        throw new IllegalArgumentException("Unknown kind of clip: " + type);
    }

    /** The articulation a file names; a word this studio does not know is refused, not dropped. */
    private static Articulation articulation(String token) {
        if (token == null) {
            return null;
        }
        Articulation articulation = Articulation.parse(token);
        if (articulation == null) {
            throw new IllegalArgumentException("Unknown articulation: " + token);
        }
        return articulation;
    }

    static <T> JsonArray array(List<T> items, Function<T, JsonObject> write) {
        JsonArray array = new JsonArray();
        for (T item : items) {
            array.add(write.apply(item));
        }
        return array;
    }

    static <T> List<T> list(JsonObject parent, String name, Function<JsonObject, T> read) {
        List<T> items = new ArrayList<>();
        JsonElement list = parent.get(name);
        if (list != null && !list.isJsonNull()) {
            for (JsonElement element : list.getAsJsonArray()) {
                items.add(read.apply(element.getAsJsonObject()));
            }
        }
        return items;
    }

    static void put(JsonObject object, String name, String value) {
        if (value != null) {
            object.addProperty(name, value);
        }
    }

    /** A key as written; {@code flats} only for a key of six sharps or six flats that is not written the usual way. */
    private static Key key(JsonObject e) {
        int tonic = e.get("tonic").getAsInt();
        Key.Mode mode = Key.Mode.valueOf(text(e, "mode").toUpperCase(Locale.ROOT));
        return e.has("flats") ? new Key(tonic, mode, e.get("flats").getAsBoolean()) : new Key(tonic, mode);
    }

    static String text(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || value instanceof JsonNull ? null : value.getAsString();
    }
}
