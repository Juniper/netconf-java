package net.juniper.netconf;

/**
 * Central location for NETCONF protocol constants used across the library.
 * <p>
 * The values defined here correspond to RFC 6241 (base 1.0) and related drafts
 * so that all modules reference a single, canonical source of truth rather than
 * scattering string literals throughout the codebase.
 * <p>
 * This class is a simple constant holder and is therefore marked {@code final}
 * and given a private constructor to prevent instantiation.
 */
public class TestConstants {

    public static final String CORRECT_HELLO = "<hello xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n" +
            "<capabilities>\n" +
            "<capability>urn:ietf:params:netconf:base:1.0</capability>\n" +
            "<capability>urn:ietf:params:netconf:base:1.0#candidate</capability>\n" +
            "<capability>urn:ietf:params:netconf:base:1.0#confirmed-commit</capability>\n" +
            "<capability>urn:ietf:params:netconf:base:1.0#validate</capability>\n" +
            "<capability>urn:ietf:params:netconf:base:1.0#url?protocol=http,ftp,file</capability>\n" +
            "</capabilities>\n" +
            "</hello>";
    public static final String LLDP_REQUEST = "<rpc><get-lldp-neighbors-information></get-lldp-neighbors-information></rpc>";
}
