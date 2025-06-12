package net.juniper.netconf.element;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DatastoreTest {

    @Test
    void testToStringReturnsLowercaseName() {
        assertEquals("running", Datastore.RUNNING.toString());
        assertEquals("candidate", Datastore.CANDIDATE.toString());
        assertEquals("startup", Datastore.STARTUP.toString());
        assertEquals("intended", Datastore.INTENDED.toString());
        assertEquals("operational", Datastore.OPERATIONAL.toString());
    }

    @Test
    void testFromXmlNameIsCaseInsensitive() {
        assertEquals(Datastore.RUNNING, Datastore.fromXmlName("RUNNING"));
        assertEquals(Datastore.STARTUP, Datastore.fromXmlName("Startup"));
        assertEquals(Datastore.OPERATIONAL, Datastore.fromXmlName("operational"));
    }

    @Test
    void testFromXmlNameThrowsOnUnknown() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            Datastore.fromXmlName("bogus");
        });
        assertTrue(ex.getMessage().contains("Unknown Datastore XML name"));
    }

    @Test
    void testFromXmlNameThrowsOnNull() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            Datastore.fromXmlName(null);
        });
        assertTrue(ex.getMessage().contains("cannot be null"));
    }
}