# AresStack Music

Independent Java libraries for musical structures, notation, composition and future audio production.

This is **not a toolkit or a microservice**: each module exposes its own API and can be used separately.

## Implemented libraries

| Gradle module | Role | Dependencies |
| --- | --- | --- |
| `music-model` | Immutable composition domain: sections, parts, voices, tracks, MIDI/audio clips, time maps, dynamics, scoped editing, revisions and mix descriptions | JDK only |
| `music-notation` | Versioned, lossless composition JSON; detailed AI-readable/writable note batches; deterministic compact musical context | `music-model`, Gson |

### Three notation responsibilities

- **Complete score:** `CompositionJson` reads and writes `aresstack-composition` V1/V2 without using Audio Studio.
- **Writing music:** `AiNotesJson` reads and writes note batches for a specific part/section. An optional caller limit can bound untrusted input; the interchange format itself does not impose 256 notes.
- **Explaining music to an AI:** `ContextBuilder` produces reproducible text and SHA-256 digests. The caller selects the exact note window and the independent look-back setting. A compact context is **not** a replacement for the complete composition.

## Build and test

Requires JDK **21** (same as the source domain model). The included Gradle wrapper is self-contained as build tooling:

```sh
bash ./gradlew clean build
bash ./gradlew :music-model:test :music-notation:test
```

These projects are libraries, not applications; no Swing desktop, audio device, model provider, web server or Audio Studio build is required.

## Independence from Audio Studio

The existing [`aresstack/audio`](https://github.com/aresstack/audio) was read as a compatibility reference. **It is not modified**, and there is no dependency from these libraries back to Audio Studio. Original sources and their tests remain in that repository.

See [the architectural boundaries](docs/architecture.md) and [Issue #1](https://github.com/aresstack/music/issues/1).

## Distribution

Local Gradle builds produce library JARs. They are **not published to Maven Central**. Package stability, licensing, minimum Java version and release coordinates are subject to a separate publication review.
