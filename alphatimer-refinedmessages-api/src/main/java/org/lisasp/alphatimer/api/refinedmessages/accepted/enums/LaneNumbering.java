package org.lisasp.alphatimer.api.refinedmessages.accepted.enums;

/** Where the lane numbers of the source start; the lanes themselves keep the numbers of the source. */
public enum LaneNumbering {
    ZeroBased,
    OneBased,
    /** No lane 0 has been seen and nothing says otherwise; may turn out to be {@link #ZeroBased}. */
    AssumedOneBased
}
