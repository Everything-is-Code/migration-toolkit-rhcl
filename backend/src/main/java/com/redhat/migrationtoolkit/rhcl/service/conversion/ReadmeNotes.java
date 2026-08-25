package com.redhat.migrationtoolkit.rhcl.service.conversion;

import java.util.ArrayList;
import java.util.List;

/**
 * Cross-cutting README markdown sections collected during convert (#170).
 * Prefer {@link ReadmeSupport#build} with a single {@link ReadmeNotes} collector
 * over growing positional note args.
 */
public final class ReadmeNotes {
    private final List<String> notes = new ArrayList<>();

    public void add(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return;
        }
        notes.add(markdown);
    }

    public List<String> all() {
        return List.copyOf(notes);
    }
}
