package com.redhat.migrationtoolkit.rhcl.service.conversion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadmeNotesTest {

    @Test
    void add_skipsNullAndBlank() {
        ReadmeNotes notes = new ReadmeNotes();
        notes.add(null);
        notes.add("   ");
        notes.add("## Note\n");

        assertEquals(List.of("## Note\n"), notes.all());
    }

    @Test
    void all_returnsImmutableCopy() {
        ReadmeNotes notes = new ReadmeNotes();
        notes.add("one");

        List<String> snapshot = notes.all();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add("two"));
        notes.add("two");

        assertEquals(List.of("one"), snapshot);
        assertNotSame(snapshot, notes.all());
        assertTrue(notes.all().contains("two"));
    }
}
