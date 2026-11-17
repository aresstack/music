# AresStack Music

A collection of independently usable Java libraries for musical structures, notation, composition, and future audio production.

## Scope

This repository contains reusable **libraries**, not a toolkit, a server, or a desktop application. Each module has an explicit API and can be consumed independently.

- `music-model`: immutable composition model (parts, voices, tracks, clips, notes, time maps, dynamics, scoped editing).
- Future modules may include notation and AI-friendly formats, instrument models, synthesis, and album export. Their APIs and boundaries will be determined individually.

The existing [Audio Studio](https://github.com/aresstack/audio) is a **read-only reference** for compatibility. This repository does not change its sources, build, or workflows.

## Building

The initial model keeps **Java 21** source compatibility with its current implementation (including records and sealed interfaces).

```sh
bash ./gradlew clean build
```

For the scope and future steps, see [Issue #1](https://github.com/aresstack/music/issues/1).

## Publication

No Maven Central artifact is released yet. Artifact coordinates, licensing, minimum supported Java version, and versioning must be approved before public publication.
