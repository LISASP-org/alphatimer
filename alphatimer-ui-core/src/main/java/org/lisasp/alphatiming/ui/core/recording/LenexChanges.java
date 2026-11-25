package org.lisasp.alphatiming.ui.core.recording;

/** How many refined messages a recorded version of the LENEX file caused and how many heats they concern. */
public record LenexChanges(int changes, int heats) {
}
