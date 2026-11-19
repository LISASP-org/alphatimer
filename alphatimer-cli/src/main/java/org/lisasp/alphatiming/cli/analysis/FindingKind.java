package org.lisasp.alphatiming.cli.analysis;

public enum FindingKind {
    UNFRAMED_DATA(true),
    INVALID_MESSAGE(true),
    DAMAGED_MESSAGE(true),
    MESSAGE_1_WITHOUT_MESSAGE_2(true),
    MESSAGE_2_WITHOUT_MESSAGE_1(true),
    UNKNOWN_MESSAGE(false),
    NOT_REFINED(false);

    private final boolean defect;

    FindingKind(boolean defect) {
        this.defect = defect;
    }

    public boolean isDefect() {
        return defect;
    }
}
