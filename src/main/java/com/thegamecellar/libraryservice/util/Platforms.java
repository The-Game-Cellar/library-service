package com.thegamecellar.libraryservice.util;

import java.util.LinkedHashSet;
import java.util.List;

public final class Platforms {

    private Platforms() {
    }

    // The list wins when it holds anything after cleaning; the single value is the field the v1
    // client sends. Order is kept, so the first entry stays the main platform.
    public static List<String> normalise(List<String> platforms, String platform) {
        List<String> fromList = clean(platforms);
        if (!fromList.isEmpty()) return fromList;
        return platform == null ? List.of() : clean(List.of(platform));
    }

    private static List<String> clean(List<String> values) {
        if (values == null) return List.of();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) continue;
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) seen.add(trimmed);
        }
        return List.copyOf(seen);
    }
}
