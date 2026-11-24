package org.lisasp.alphatiming.ui.core.recording;

/**
 * What a recorded version of the LENEX file changed.
 *
 * @param changes the refined messages it caused
 * @param heats   the heats they concern
 */
public record LenexChanges(int changes, int heats) {
}
