package com.example.integration.google;

import java.util.ArrayList;
import java.util.List;

/** Coerces a tool's loosely-typed {@code values} argument into the row/column shape Sheets expects. */
public final class GoogleSheetValues {

    private GoogleSheetValues() {
    }

    /**
     * Normalises the {@code values} argument into a list of rows. Accepts a 2-D array
     * ({@code [[a,b],[c,d]]}) directly, and tolerates a flat 1-D array ({@code [a,b,c]}) by treating
     * it as a single row. Throws {@link IllegalArgumentException} when the argument is missing or not a list.
     */
    @SuppressWarnings("unchecked")
    public static List<List<Object>> toRows(Object raw) {
        if (!(raw instanceof List<?> outer)) {
            throw new IllegalArgumentException(
                    "'values' must be a 2-D array of rows, e.g. [[\"a\",\"b\"],[\"c\",\"d\"]]");
        }
        List<List<Object>> rows = new ArrayList<>();
        boolean nested = !outer.isEmpty() && outer.get(0) instanceof List;
        if (nested) {
            for (Object row : outer) {
                if (row instanceof List<?> cells) {
                    rows.add(new ArrayList<>((List<Object>) cells));
                } else {
                    rows.add(new ArrayList<>(List.of(row)));
                }
            }
        } else {
            // Flat list: treat the whole thing as a single row.
            rows.add(new ArrayList<>((List<Object>) outer));
        }
        return rows;
    }
}
