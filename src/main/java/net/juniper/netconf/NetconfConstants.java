package net.juniper.netconf;

/**
 * Centralised collection of string literals and protocol constants used
 * throughout the NETCONF client library.
 * <p>
 * The class is {@code final} and has a private constructor – it cannot be
 * instantiated or extended.  All members are {@code public static final}
 * to encourage direct use without additional indirection.
 * </p>
 *
 * @author Jonas Glass
 */
public class NetconfConstants {

    /* ------------------------------------------------------------------
     * Framing protocol
     * ------------------------------------------------------------------ */

    /**
     * Device prompt used by the NETCONF chunked framing protocol.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc6242#section-4.1">RFC&nbsp;6242&nbsp;§4.1</a>
     */
    public static final String DEVICE_PROMPT = "]]>]]>";

    /* ------------------------------------------------------------------
     * XML preamble & namespaces
     * ------------------------------------------------------------------ */

    /**
     * XML declaration emitted at the top of NETCONF messages.
     */
    public static final String XML_VERSION = "<?xml version=\"1.0\" encoding=\"utf-8\"?>";

    /**
     * XML namespace for NETCONF Base 1.0
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc6241#section-8.1">RFC&nbsp;6241&nbsp;§8.1</a>
     */
    public static final String URN_XML_NS_NETCONF_BASE_1_0 = "urn:ietf:params:xml:ns:netconf:base:1.0";

    /**
     * URI form of the NETCONF Base 1.0 capability identifier.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc6241#section-8.1">RFC&nbsp;6241&nbsp;§8.1</a>
     */
    public static final String URN_IETF_PARAMS_NETCONF_BASE_1_0 = "urn:ietf:params:netconf:base:1.0";

    /* ------------------------------------------------------------------
     * Misc helpers
     * ------------------------------------------------------------------ */

    /** Empty line helper constant. */
    public static final String EMPTY_LINE = "";

    /** Line feed (Unix‑style newline). */
    public static final String LF = "\n";

    /** Carriage return (use with {@code LF} for CRLF sequences). */
    public static final String CR = "\r";

    /** UTF‑8 charset literal used throughout the library. */
    public static final String CHARSET_UTF8 = "utf-8";

    /**
     * Not instantiable – utility holder only.
     */
    private NetconfConstants() { /* no‑op */ }
}
