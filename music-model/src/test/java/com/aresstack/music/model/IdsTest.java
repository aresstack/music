package com.aresstack.music.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public final class IdsTest {
    @Test
    public void idsAreShortValidAndDistinct() {
        Ids ids = Ids.random();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10000; i++) {
            String id = ids.next();
            assertEquals(Ids.LENGTH, id.length());
            assertTrue(id, Ids.valid(id));
            assertTrue("old track ids allow it too", id.matches("[a-z][a-z0-9_-]{0,28}"));
            seen.add(id);
        }
        assertEquals(10000, seen.size());
    }

    @Test
    public void aSeedGivesTheSameIds() {
        Ids first = Ids.seeded(42);
        Ids second = Ids.seeded(42);
        for (int i = 0; i < 100; i++) {
            assertEquals(first.next(), second.next());
        }
        assertNotEquals(Ids.seeded(1).next(), Ids.seeded(2).next());
    }
}
