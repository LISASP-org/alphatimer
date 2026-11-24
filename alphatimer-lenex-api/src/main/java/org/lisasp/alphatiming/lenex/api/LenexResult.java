package org.lisasp.alphatiming.lenex.api;

import java.util.List;
import java.util.OptionalInt;

/**
 * @param status the result status as written (e.g. {@code DSQ}); empty for a regular result
 */
public record LenexResult(int lane, OptionalInt swimTimeInMillis, String status, List<LenexSplit> splits) {

    public LenexResult {
        splits = List.copyOf(splits);
    }
}
