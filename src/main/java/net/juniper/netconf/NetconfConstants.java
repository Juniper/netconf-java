package net.juniper.netconf;

/**
 * @author Jonas Glass
 */
public class NetconfConstants {

    private NetconfConstants() {
    }

    /**
     * Device prompt for the framing protocol.
     * https://tools.ietf.org/html/rfc6242#section-4.1
     */
    public static final String DEVICE_PROMPT = "]]>]]>";

    /**
     * XML Schema prefix.
     */
    public static final String XML_VERSION = "<?xml version=\"1.0\" encoding=\"utf-8\"?>";

    /**
     * XML Namespace for NETCONF Base 1.0
     * https://tools.ietf.org/html/rfc6241#section-8.1
     */
    public static final String URN_XML_NS_NETCONF_BASE_1_0 = "urn:ietf:params:xml:ns:netconf:base:1.0";

    /**
     * URN for NETCONF Base 1.0
     * https://tools.ietf.org/html/rfc6241#section-8.1
     */
    public static final String URN_IETF_PARAMS_NETCONF_BASE_1_0 = "urn:ietf:params:netconf:base:1.0";

    public static final String EMPTY_LINE = "";
    public static final String LF = "\n";

}
