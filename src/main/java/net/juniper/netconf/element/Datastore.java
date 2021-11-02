package net.juniper.netconf.element;

import java.util.Locale;

/**
 * Datastore
 * <p>
 * As defined by RFC-8342.
 * See https://datatracker.ietf.org/doc/html/rfc8342#section-5
 */
public enum Datastore {
    RUNNING, CANDIDATE, STARTUP, INTENDED, OPERATIONAL;

    @Override
    public String toString() {
        return this.name().toLowerCase(Locale.US);
    }
}
