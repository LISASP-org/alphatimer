package org.lisasp.alphatiming.lenex;

import java.util.List;

/**
 * @param status the heat status as written (e.g. {@code OFFICIAL}); empty if the file has none
 */
public record LenexHeat(int number, String status, List<LenexResult> results) {

    public LenexHeat {
        results = List.copyOf(results);
    }
}
