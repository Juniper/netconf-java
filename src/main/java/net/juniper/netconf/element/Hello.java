package net.juniper.netconf.element;

import net.juniper.netconf.NetconfConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Class to represent a NETCONF hello element - <a href="https://datatracker.ietf.org/doc/html/rfc6241#section-8.1">...</a>
 */
public class Hello extends AbstractNetconfElement {

    private static final Logger log = Logger.getLogger(Hello.class.getName());

    /**
     * Validates that the supplied string is a syntactically correct URI.
     *
     * @param uri the capability string
     * @throws IllegalArgumentException if the string is not a valid URI
     */
    private static void assertValidUri(String uri) {
        try {
            new java.net.URI(uri);
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException("Capability MUST be a valid URI per RFC 3986: " + uri, e);
        }
    }

    private static final String XPATH_HELLO = getXpathFor("hello");
    private static final String XPATH_HELLO_SESSION_ID = XPATH_HELLO + getXpathFor("session-id");
    private static final String XPATH_HELLO_CAPABILITIES = XPATH_HELLO + getXpathFor("capabilities");
    private static final String XPATH_HELLO_CAPABILITIES_CAPABILITY = XPATH_HELLO_CAPABILITIES + getXpathFor("capability");

    private final String sessionId;
    private final List<String> capabilities;

    /**
     * Constructs an immutable {@code Hello} element.
     *
     * @param namespacePrefix optional XML namespace prefix, may be {@code null}
     * @param originalDocument original DOM document to wrap or {@code null} to build a new one
     * @param sessionId session-id presented by the NETCONF peer (may be {@code null} for client hello)
     * @param capabilities list of capability URIs; a defensive copy is taken
     */
    public Hello(final String namespacePrefix, final Document originalDocument, final String sessionId, final List<String> capabilities) {
        super(getDocument(originalDocument, namespacePrefix, sessionId, capabilities));
        this.sessionId = sessionId;
        this.capabilities = capabilities != null ? List.copyOf(capabilities) : Collections.emptyList();
    }

    /**
     * Returns the NETCONF session-id conveyed in this &lt;hello&gt;.
     *
     * @return session-id or {@code null} if absent
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Returns an immutable copy of the capability URIs advertised in this hello.
     *
     * @return list of capability strings (never {@code null})
     */
    public List<String> getCapabilities() {
        return new ArrayList<>(capabilities);
    }

    /**
     * Checks whether the given capability is present in this hello.
     *
     * @param capability capability URI to test
     * @return {@code true} if the capability list contains the URI
     */
    public boolean hasCapability(final String capability) {
        return capability != null && capabilities.contains(capability);
    }

    /**
     * Returns a new {@link Builder} for programmatically constructing a {@code Hello}.
     *
     * @return fresh {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for creating {@link Hello} objects.
     */
    public static class Builder {
        /**
         * Creates an empty {@code Builder}.
         */
        public Builder() {
        }
        private String sessionId;
        private List<String> capabilities = new java.util.ArrayList<>();
        private String namespacePrefix;

        /**
         * Sets the {@code session-id} to embed in the hello.
         *
         * @param sessionId session identifier
         * @return this {@code Builder}
         */
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        /**
         * Replaces the entire capability list with the supplied collection.
         * <p>
         * A <em>defensive copy</em> is made, so subsequent modifications to the
         * caller‑supplied {@code List} will not affect the builder.
         * Each capability URI is validated against RFC&nbsp;3986; an
         * {@link IllegalArgumentException} is thrown if any entry is malformed.
         *
         * @param capabilities list of capability URIs, or {@code null} to clear
         *                     the current list
         * @return this {@code Builder} for fluent chaining
         * @throws IllegalArgumentException if any capability is not a valid URI
         */
        public Builder capabilities(List<String> capabilities) {
            if (capabilities != null) {
                for (String cap : capabilities) {
                    assertValidUri(cap);
                }
                this.capabilities = new java.util.ArrayList<>(capabilities); // defensive copy
            }
            return this;
        }

        /**
         * Adds a single capability URI to the list.
         *
         * @param capability capability URI to add; ignored if {@code null}
         * @return this {@code Builder}
         */
        public Builder capability(String capability) {
            if (capability != null) {
                assertValidUri(capability);
                this.capabilities.add(capability);
            }
            return this;
        }

        /**
         * Sets the XML namespace prefix to use when generating elements.
         *
         * @param namespacePrefix prefix string, e.g., "nc"
         * @return this {@code Builder}
         */
        public Builder namespacePrefix(String namespacePrefix) {
            this.namespacePrefix = namespacePrefix;
            return this;
        }

        /**
         * Builds a new immutable {@link Hello} instance using the configured values.
         *
         * @return constructed {@link Hello}
         */
        public Hello build() {
            // RFC 6241 § 8.1 — each peer MUST advertise at least the base 1.1 capability
            final String BASE_11 = "urn:ietf:params:netconf:base:1.1";

            List<String> caps = capabilities == null
                    ? new java.util.ArrayList<>()
                    : new java.util.ArrayList<>(capabilities);

            if (!caps.contains(BASE_11)) {
                caps.add(BASE_11);
            }

            return new Hello(
                namespacePrefix,  // prefix (may be null)
                null,             // originalDocument (none when building)
                sessionId,
                caps
            );
        }
    }

    /**
     * Creates a Hello object based on the supplied XML.
     *
     * @param xml The XML of the NETCONF &lt;hello&gt;
     * @return an new, immutable, Hello object.
     * @throws ParserConfigurationException If the XML parser cannot be created
     * @throws IOException                  If the XML cannot be read
     * @throws SAXException                 If the XML cannot be parsed
     * @throws XPathExpressionException     If there is a problem in the parsing expressions
     */
    public static Hello from(final String xml)
            throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {

        // RFC 6241 §3.2: Reject XML that contains a DOCTYPE declaration
        if (xml.contains("<!DOCTYPE")) {
            throw new IllegalArgumentException("DOCTYPE declarations are not allowed in NETCONF messages (RFC 6241 §3.2)");
        }

        final Document document = createDocumentBuilderFactory().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
        final XPath xPath = XPathFactory.newInstance().newXPath();
        final String sessionId = xPath.evaluate(XPATH_HELLO_SESSION_ID, document);
        final NodeList capabilitiesNodes = (NodeList) xPath.evaluate(XPATH_HELLO_CAPABILITIES_CAPABILITY, document, XPathConstants.NODESET);
        final List<String> capabilities = new ArrayList<>();
        for (int i = 0; i < capabilitiesNodes.getLength(); i++) {
            final Node node = capabilitiesNodes.item(i);
            final String capability = node.getTextContent();
            assertValidUri(capability);
            capabilities.add(capability);
        }
        final Hello hello = new Hello(
            null,          // namespacePrefix
            document,      // originalDocument
            sessionId,
            capabilities
        );
        log.info("hello is: " + hello.getXml());
        return hello;
    }

    private static Document getDocument(
        final Document originalDocument,
        final String namespacePrefix,
        final String sessionId,
        final List<String> capabilities) {
        return Objects.requireNonNullElseGet(originalDocument, () -> createDocument(namespacePrefix, sessionId, capabilities));
    }

    private static Document createDocument(
            final String namespacePrefix,
            final String sessionId,
            final List<String> capabilities) {

        final Document createdDocument = createBlankDocument();

        final Element helloElement
                = createdDocument.createElementNS(NetconfConstants.URN_XML_NS_NETCONF_BASE_1_0, "hello");
        helloElement.setPrefix(namespacePrefix);
        createdDocument.appendChild(helloElement);

        final Element capabilitiesElement
                = createdDocument.createElementNS(NetconfConstants.URN_XML_NS_NETCONF_BASE_1_0, "capabilities");
        capabilitiesElement.setPrefix(namespacePrefix);
        if (capabilities != null) {
            for (String capability : capabilities) {
                final Element capabilityElement =
                        createdDocument.createElementNS(NetconfConstants.URN_XML_NS_NETCONF_BASE_1_0, "capability");
                capabilityElement.setTextContent(capability);
                capabilityElement.setPrefix(namespacePrefix);
                capabilitiesElement.appendChild(capabilityElement);
            }
        }
        helloElement.appendChild(capabilitiesElement);

        if (sessionId != null) {
            final Element sessionIdElement
                    = createdDocument.createElementNS(NetconfConstants.URN_XML_NS_NETCONF_BASE_1_0, "session-id");
            sessionIdElement.setPrefix(namespacePrefix);
            sessionIdElement.setTextContent(sessionId);
            helloElement.appendChild(sessionIdElement);
        }
        return createdDocument;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Hello hello)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(sessionId, hello.sessionId) &&
                Objects.equals(capabilities, hello.capabilities);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), sessionId, capabilities);
    }

    @Override
    public String toString() {
        return "Hello{" +
                "sessionId='" + sessionId + '\'' +
                ", capabilities=" + capabilities +
                "} " + super.toString();
    }
}
