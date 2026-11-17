# Architecture and module boundaries

This repository contains independently usable music libraries, not a single shared runtime.

## music-model

- `com.aresstack.music.model`: immutable composition domain (sections, parts, voices, tracks, clips, notes, time maps and dynamics).
- `com.aresstack.music.model.edit`: scoped editing, patching, revisions and history.
- `com.aresstack.music.model.mix`: immutable mix descriptions, without signal processing or UI.

The domain depends on the **JDK only**. Architecture tests prevent imports from AI and application packages.

## music-notation

- `notation.json.CompositionJson`: lossless, versioned `aresstack-composition` (V1/V2) JSON codec; preserves existing Audio Studio wire format. JSON file helpers remain internal implementation details.
- `notation.ai.AiNotesJson`: independent detailed note batches for one part/section. Parser accepts object notes, short note rows and legacy aliases. Optional caller-supplied resource limit; no arbitrary format-level 256-note cap.
- `notation.context.ContextBuilder`: deterministic, hashable AI reading views built from the canonical composition, with exact notes in requested windows and summaries elsewhere. Context look-back is an **explicit call argument**, not mutable global process state.

These represent **different interfaces**, not three interchangeable storage formats. The compact context is a derived, potentially lossy presentation and is never the canonical score. Chat providers, prompts, retries, file-cache orchestration, UI, and live audio remain outside the library.

## Independence

The original sources in `aresstack/audio` remain unmodified and are referenced read-only for compatibility. No refactoring, new dependencies, or builds are required in Audio Studio.

## Publication

The initial port uses **Java 21**, consistent with the existing model's records. Public Maven Central releases, the artifact coordinates, minimum Java version and legal notices require separate review. No server or running microservice is necessary.
