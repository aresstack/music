# Architecture and module boundaries

This repository contains independently usable music libraries, not a single shared runtime.

## First module: music-model

- `com.aresstack.music.model`: immutable composition, score-related domain state (sections, parts, voices, tracks, clips, notes, time maps and dynamics).
- `com.aresstack.music.model.edit`: domain-level scoped editing, patching, revisions and history.
- `com.aresstack.music.model.mix`: immutable stage/mix description only; no signal processing or studio UI.

`music-model` has **no dependencies outside the JDK** at runtime. JUnit is test-only. A dependency/architecture test enforces the source boundary.

The initial code is adapted from `aresstack/audio`'s `composition` module, with a new library package namespace. No changes are made in the source repository.

## Next component: music-notation

Define separate APIs for the lossless `aresstack-composition` versioned JSON document, model-generated note-writing JSON for one part and section, and deterministic compact musical context. The compact format is a derived view and **must not** silently become the sole exchange or storage format. Do not move AI model clients, retry logic or prompt orchestration into notation.

## Compatibility and publication

The initial port targets Java 21, matching the source model's records and sealed types. It does not guarantee Java 8 compatibility. Versions and Maven Central publication require a separate API and license review. No audio processing, UI or GitHub Actions runtime is included in `music-model`.
