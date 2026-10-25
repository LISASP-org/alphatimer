package org.lisasp.alphatiming.ui.common;

import org.lisasp.alphatiming.ui.core.project.TimingDocument;

/**
 * The document a settings form shows. A field being edited keeps its text while the same document is refreshed, but
 * not when the document is replaced: that text belongs to the previous project.
 */
public final class ShownDocument {

    private TimingDocument shown;

    public boolean replacedBy(TimingDocument current) {
        boolean replaced = current != shown;
        shown = current;
        return replaced;
    }
}
