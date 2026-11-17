/**
 * The edit core, work package F3 (architecture review 2.3, E5): every change of a piece, by hand
 * or by an AI, is a {@link com.aresstack.music.model.edit.Patch} of operations on ids,
 * applied by {@link com.aresstack.music.model.edit.CompositionEditor} within an
 * {@link com.aresstack.music.model.edit.EditScope}. The editor refuses operations outside the
 * scope and proves with {@link com.aresstack.music.model.edit.ScopeProof} that nothing outside
 * changed; the only change outside it may make is an approved, deterministic
 * {@link com.aresstack.music.model.edit.TimeShift}.
 *
 * <p>A {@link com.aresstack.music.model.edit.Proposal} is an applied patch that is not taken
 * over yet; {@link com.aresstack.music.model.edit.EditHistory} keeps undo and redo of a
 * session; a {@link com.aresstack.music.model.edit.Revision} is a lasting full copy;
 * {@link com.aresstack.music.model.edit.ChangeSet} says which parts must be rendered again.
 *
 * <p>Like the rest of the model, the package knows no files, windows, sound or AI generator.
 */
package com.aresstack.music.model.edit;
