package net.juniper.netconf.element;

import java.util.Locale;

/**
 * Datastore
 * <p>
 * As defined by RFC-8342.
 * See <a href="https://datatracker.ietf.org/doc/html/rfc8342#section-5">...</a>
 */
public enum Datastore {
    /**
     * The running configuration datastore as defined by RFC-8342.
     */
    RUNNING("running"),
    /**
     * The candidate configuration datastore as defined by RFC-8342.
     */
    CANDIDATE("candidate"),
    /**
     * The startup configuration datastore as defined by RFC-8342.
     */
    STARTUP("startup"),
    /**
     * The intended configuration datastore as defined by RFC-8342.
     */
    INTENDED("intended"),
    /**
     * The operational state datastore as defined by RFC-8342.
     */
    OPERATIONAL("operational");

    private final String xmlName;

    Datastore(String xmlName) {
        this.xmlName = xmlName.toLowerCase(Locale.US);
    }

    /**
     * Returns the XML name (lowercase) for this datastore.
     */
    @Override
    public String toString() {
        return xmlName;
    }

    /**
     * Returns the Datastore enum constant corresponding to the given XML name (case-insensitive).
     * @param name the XML name to lookup
     * @return the Datastore enum constant
     * @throws IllegalArgumentException if no matching constant exists
     */
    public static Datastore fromXmlName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Datastore XML name cannot be null");
        }
        String nameLc = name.toLowerCase(Locale.US);
        for (Datastore ds : values()) {
            if (ds.xmlName.equals(nameLc)) {
                return ds;
            }
        }
        throw new IllegalArgumentException("Unknown Datastore XML name: " + name);
    }
}
