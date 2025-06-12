package net.juniper.netconf.element;

import net.juniper.netconf.NetconfConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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
import java.util.List;
import java.util.Objects;

import static java.util.Optional.ofNullable;

/**
 * Represents a NETCONF rpc-reply element, including its message ID, status (ok or error),
 * any errors returned, session ID, and capabilities.
 * Based on RFC 6241 section 4.2.
 */
public class RpcReply extends AbstractNetconfElement {

    /** XPath for the root &lt;rpc-reply&gt; element. */
    protected static final String XPATH_RPC_REPLY = getXpathFor("rpc-reply");

    /** XPath for the &lt;ok&gt; element inside an &lt;rpc-reply&gt;. */
    private static final String XPATH_RPC_REPLY_OK = XPATH_RPC_REPLY + getXpathFor("ok");

    /** XPath for any &lt;rpc-error&gt; elements inside the reply. */
    private static final String XPATH_RPC_REPLY_ERROR = XPATH_RPC_REPLY + getXpathFor("rpc-error");

    /** XPath for the &lt;error-type&gt; child of an &lt;rpc-error&gt;. */
    private static final String XPATH_RPC_REPLY_ERROR_TYPE = getXpathFor("error-type");

    /** XPath for the &lt;error-tag&gt; child of an &lt;rpc-error&gt;. */
    private static final String XPATH_RPC_REPLY_ERROR_TAG = getXpathFor("error-tag");

    /** XPath for the &lt;error-severity&gt; child of an &lt;rpc-error&gt;. */
    private static final String XPATH_RPC_REPLY_ERROR_SEVERITY = getXpathFor("error-severity");

    /** XPath for the &lt;error-path&gt; child of an &lt;rpc-error&gt;. */
    private static final String XPATH_RPC_REPLY_ERROR_PATH = getXpathFor("error-path");

    /** XPath for the &lt;error-message&gt; child of an &lt;rpc-error&gt;. */
    private static final String XPATH_RPC_REPLY_ERROR_MESSAGE = getXpathFor("error-message");

    /** XPath for the &lt;error-info&gt; section inside an &lt;rpc-error&gt;. */
    private static final String XPATH_RPC_REPLY_ERROR_INFO = getXpathFor("error-info");

    /** XPath for the &lt;bad-attribute&gt; field within &lt;error-info&gt;. */
    private static final String XPATH_RPC_REPLY_ERROR_INFO_BAD_ATTRIBUTE = XPATH_RPC_REPLY_ERROR_INFO + getXpathFor("bad-attribute");

    /** XPath for the &lt;bad-element&gt; field within &lt;error-info&gt;. */
    private static final String XPATH_RPC_REPLY_ERROR_INFO_BAD_ELEMENT = XPATH_RPC_REPLY_ERROR_INFO + getXpathFor("bad-element");

    /** XPath for the &lt;bad-namespace&gt; field within &lt;error-info&gt;. */
    private static final String XPATH_RPC_REPLY_ERROR_INFO_BAD_NAMESPACE = XPATH_RPC_REPLY_ERROR_INFO + getXpathFor("bad-namespace");

    /** XPath for the &lt;session-id&gt; field within &lt;error-info&gt;. */
    private static final String XPATH_RPC_REPLY_ERROR_INFO_SESSION_ID = XPATH_RPC_REPLY_ERROR_INFO + getXpathFor("session-id");

    /** XPath for the &lt;ok-element&gt; field within &lt;error-info&gt;. */
    private static final String XPATH_RPC_REPLY_ERROR_INFO_OK_ELEMENT = XPATH_RPC_REPLY_ERROR_INFO + getXpathFor("ok-element");

    /** XPath for the &lt;err-element&gt; field within &lt;error-info&gt;. */
    private static final String XPATH_RPC_REPLY_ERROR_INFO_ERR_ELEMENT = XPATH_RPC_REPLY_ERROR_INFO + getXpathFor("err-element");

    /** XPath for the &lt;noop-element&gt; field within &lt;error-info&gt;. */
    private static final String XPATH_RPC_REPLY_ERROR_INFO_NO_OP_ELEMENT = XPATH_RPC_REPLY_ERROR_INFO + getXpathFor("noop-element");

    private final String messageId;
    private final boolean ok;
    private final List<RpcError> errors;
    /** Optional prefix (e.g. "nc") to apply to elements when we generate XML. */
    private final String namespacePrefix;

    private final String sessionId;
    private final List<String> capabilities;

    /* ---------------------------------------------------------------------
     * Builder
     * ------------------------------------------------------------------- */

    /**
     * Creates and returns a new {@link Builder} for constructing {@code RpcReply} instances.
     * <p>
     * The builder pattern allows callers to set only the fields relevant to their use‑case
     * and then call {@link Builder#build()} to obtain an immutable {@code RpcReply}.
     *
     * @return a fresh {@code Builder} instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for creating immutable {@link RpcReply} instances.
     * <p>
     * Configure the desired fields with the provided setter‑style methods
     * and finish by calling {@link #build()} to obtain a fully‑constructed
     * {@code RpcReply}.  The builder is not thread‑safe; use a separate
     * instance per construction sequence.
     */
    public static final class Builder {
        private Document originalDocument;
        private String namespacePrefix;
        private String messageId;
        private boolean ok;
        private List<RpcError> errors = new ArrayList<>();
        private String sessionId;
        private List<String> capabilities = new ArrayList<>();

        private Builder() { }

        /**
         * Sets the original {@link Document} this reply was parsed from.
         *
         * @param originalDocument the source DOM {@link Document}; may be {@code null}
         * @return this {@code Builder} instance for method chaining
         */
        public Builder originalDocument(Document originalDocument) {
            this.originalDocument = originalDocument;
            return this;
        }

        /**
         * Sets the XML namespace prefix to apply when generating new XML.
         *
         * @param namespacePrefix optional namespace prefix (e.g. "nc"); may be {@code null}
         * @return this {@code Builder} for chaining
         */
        public Builder namespacePrefix(String namespacePrefix) {
            this.namespacePrefix = namespacePrefix;
            return this;
        }

        /**
         * Sets the NETCONF <code>message-id</code> attribute for the reply.
         *
         * @param messageId the message ID string
         * @return this {@code Builder}
         */
        public Builder messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        /**
         * Indicates whether the reply contains an &lt;ok/&gt; element.
         *
         * @param ok {@code true} if the operation succeeded
         * @return this {@code Builder}
         */
        public Builder ok(boolean ok) {
            this.ok = ok;
            return this;
        }

        /**
         * Replaces the current list of errors with the provided list.
         *
         * @param errors list of {@link RpcError}; if {@code null} an empty list is used
         * @return this {@code Builder}
         */
        public Builder errors(List<RpcError> errors) {
            this.errors = errors != null ? errors : new ArrayList<>();
            return this;
        }

        /**
         * Adds a single {@link RpcError} to the reply.
         *
         * @param error the error to add; ignored if {@code null}
         * @return this {@code Builder}
         */
        public Builder addError(RpcError error) {
            if (error != null) {
                this.errors.add(error);
            }
            return this;
        }

        /**
         * Sets the NETCONF <code>session-id</code> associated with the reply.
         *
         * @param sessionId the session ID
         * @return this {@code Builder}
         */
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        /**
         * Replaces the current capability list.
         *
         * @param capabilities list of capability URIs; if {@code null} an empty list is used
         * @return this {@code Builder}
         */
        public Builder capabilities(List<String> capabilities) {
            this.capabilities = capabilities != null ? capabilities : new ArrayList<>();
            return this;
        }

        /**
         * Adds a single NETCONF capability URI to the list.
         *
         * @param capability capability URI to add; ignored if {@code null}
         * @return this {@code Builder}
         */
        public Builder addCapability(String capability) {
            if (capability != null) {
                this.capabilities.add(capability);
            }
            return this;
        }

        /**
         * Builds an immutable {@link RpcReply} instance using the currently configured parameters.
         *
         * @return a new {@link RpcReply}
         */
        public RpcReply build() {
            return new RpcReply(
                namespacePrefix,
                originalDocument,
                messageId,
                ok,
                errors,
                sessionId,
                capabilities
            );
        }
    }

    /**
     * Returns the message ID associated with this rpc-reply.
     *
     * @return the message ID
     */
    public String getMessageId() {
        return messageId;
    }

    /**
     * Returns true if the rpc-reply indicates success (i.e., contains an &lt;ok/&gt; element).
     *
     * @return true if the reply is OK, false otherwise
     */
    public boolean isOK() {
        return ok;
    }

    /**
     * Returns the list of errors contained in this rpc-reply.
     *
     * @return list of RpcError
     */
    public List<RpcError> getErrors() {
        return new ArrayList<>(errors);
    }

    /**
     * Returns true if the rpc-reply contains any errors or warnings.
     *
     * @return true if errors or warnings are present
     */
    public boolean hasErrorsOrWarnings() {
        return !errors.isEmpty();
    }

    /**
     * Returns true if any of the errors in the rpc-reply are of severity "error".
     *
     * @return true if there are error-severity issues
     */
    public boolean hasErrors() {
        return errors.stream().anyMatch(error -> error.errorSeverity() == RpcError.ErrorSeverity.ERROR);
    }

    /**
     * Returns true if any of the errors in the rpc-reply are of severity "warning".
     *
     * @return true if there are warning-severity issues
     */
    public boolean hasWarnings() {
        return errors.stream().anyMatch(error -> error.errorSeverity() == RpcError.ErrorSeverity.WARNING);
    }

    /**
     * Returns the session ID associated with this rpc-reply.
     *
     * @return the session ID, or null if not present
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Returns the list of NETCONF capabilities reported in the rpc-reply.
     *
     * @return list of capability URIs
     */
    public List<String> getCapabilities() {
        return new ArrayList<>(capabilities);
    }

    /** Removes the RFC 6242 ']]>]]>' delimiter from the tail of a frame. */
    private static String stripEomDelimiter(String xml) {
        return xml.replaceFirst("\\Q]]>]]>\\E\\s*$", "");
    }

    /**
     * Parses the given NETCONF XML string into an {@code RpcReply} (or subtype).
     *
     * @param <T> the concrete subtype of {@link AbstractNetconfElement} to return
     * @param xml the NETCONF XML string to parse
     * @return an {@code RpcReply} (or subclass) instance
     * @throws ParserConfigurationException if a parser cannot be configured
     * @throws IOException if the input cannot be read
     * @throws SAXException if the XML is not well-formed
     * @throws XPathExpressionException if the XPath lookup fails
     */
    @SuppressWarnings("unchecked")
    public static <T extends AbstractNetconfElement> T from(final String xml)
        throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {

        String cleaned = stripEomDelimiter(xml);
        final Document document = createDocumentBuilderFactory().newDocumentBuilder()
            .parse(new InputSource(new StringReader(cleaned)));
        final XPath xPath = XPathFactory.newInstance().newXPath();

        final Element loadConfigResultsElement = (Element) xPath.evaluate(RpcReplyLoadConfigResults.XPATH_RPC_REPLY_LOAD_CONFIG_RESULT, document, XPathConstants.NODE);
        if (loadConfigResultsElement != null) {
            return (T) RpcReplyLoadConfigResults.from(xml);
        }

        final Element rpcReplyElement = (Element) xPath.evaluate(XPATH_RPC_REPLY, document, XPathConstants.NODE);
        final Element rpcReplyOkElement = (Element) xPath.evaluate(XPATH_RPC_REPLY_OK, document, XPathConstants.NODE);
        final List<RpcError> errorList = getRpcErrors(document, xPath, XPATH_RPC_REPLY_ERROR);

        final RpcReply rpcReply = new RpcReply(
            null,                        // no explicit prefix in parsed XML
            document,
            getAttribute(rpcReplyElement, "message-id"),
            rpcReplyOkElement != null,
            errorList,
            extractSessionId(document, xPath),
            extractCapabilities(document, xPath)
        );
        return (T) rpcReply;
    }

    /**
     * Extracts the session ID from an rpc-reply XML document.
     *
     * @param document the parsed XML document
     * @param xPath the XPath instance to use
     * @return the session ID, or null if not found
     * @throws XPathExpressionException if the XPath lookup fails
     */
    private static String extractSessionId(Document document, XPath xPath) throws XPathExpressionException {
        Element sessionIdElement = (Element) xPath.evaluate(XPATH_RPC_REPLY_ERROR_INFO_SESSION_ID, document, XPathConstants.NODE);
        if (sessionIdElement != null) {
            return getTextContent(sessionIdElement);
        }
        return null;
    }

    /**
     * Extracts the list of capabilities from an rpc-reply XML document.
     *
     * @param document the parsed XML document
     * @param xPath the XPath instance to use
     * @return list of capability URIs
     * @throws XPathExpressionException if the XPath lookup fails
     */
    private static List<String> extractCapabilities(Document document, XPath xPath) throws XPathExpressionException {
        List<String> caps = new ArrayList<>();
        NodeList capNodes = (NodeList) xPath.evaluate("//capability", document, XPathConstants.NODESET);
        for (int i = 0; i < capNodes.getLength(); i++) {
            caps.add(capNodes.item(i).getTextContent());
        }
        return caps;
    }

    /**
     * Extracts all &lt;rpc-error&gt; elements from the XML document.
     *
     * @param document the parsed XML document
     * @param xPath the XPath instance to use
     * @param xpathQuery the XPath expression for locating &lt;rpc-error&gt; elements
     * @return a list of RpcError objects
     * @throws XPathExpressionException if any XPath lookup fails
     */
    protected static List<RpcError> getRpcErrors(final Document document, final XPath xPath, final String xpathQuery)
        throws XPathExpressionException {
        final NodeList errors = (NodeList) xPath.evaluate(xpathQuery, document, XPathConstants.NODESET);
        final List<RpcError> errorList = new ArrayList<>();
        for (int i = 1; i <= errors.getLength(); i++) {
            final String expressionPrefix = String.format("%s[%d]", xpathQuery, i);
            final String errorType = xPath.evaluate(expressionPrefix + XPATH_RPC_REPLY_ERROR_TYPE, document);
            final String errorTag = xPath.evaluate(expressionPrefix + XPATH_RPC_REPLY_ERROR_TAG, document);
            final String errorSeverity = xPath.evaluate(expressionPrefix + XPATH_RPC_REPLY_ERROR_SEVERITY, document);
            final Element errorMessageElement = (Element) xPath.evaluate(expressionPrefix + XPATH_RPC_REPLY_ERROR_MESSAGE, document, XPathConstants.NODE);
            final Element errorPathElement = (Element) xPath.evaluate(expressionPrefix + XPATH_RPC_REPLY_ERROR_PATH, document, XPathConstants.NODE);
            final RpcError.Builder errorBuilder = RpcError.builder()
                .errorType(RpcError.ErrorType.from(errorType))
                .errorTag(RpcError.ErrorTag.from(errorTag))
                .errorSeverity(RpcError.ErrorSeverity.from(errorSeverity))
                .errorMessage(getTextContent(errorMessageElement))
                .errorMessageLanguage(getAttribute(errorMessageElement, "xml:lang"))
                .errorPath(getTextContent(errorPathElement));

            final Element errorInfoElement = (Element) xPath.evaluate(expressionPrefix + XPATH_RPC_REPLY_ERROR_INFO, document, XPathConstants.NODE);
            if (errorInfoElement != null) {
                final Element badAttributeElement = (Element) xPath.evaluate(expressionPrefix + XPATH_RPC_REPLY_ERROR_INFO_BAD_ATTRIBUTE, document, XPathConstants.NODE);
                final Element badElementAttribute = (Element) xPath.evaluate(expressionPrefix + XPATH_RPC_REPLY_ERROR_INFO_BAD_ELEMENT, document, XPathConstants.NODE);
                final Element badNamespaceElement = (Element) xPath.evaluate(expressionPrefix + XPATH_RPC_REPLY_ERROR_INFO_BAD_NAMESPACE, document, XPathConstants.NODE);
                final Element sessionIdElement = (Element) xPath.evaluate(expressionPrefix + XPATH_RPC_REPLY_ERROR_INFO_SESSION_ID, document, XPathConstants.NODE);
                final Element okElement = (Element) xPath.evaluate(expressionPrefix + XPATH_RPC_REPLY_ERROR_INFO_OK_ELEMENT, document, XPathConstants.NODE);
                final Element errElement = (Element) xPath.evaluate(expressionPrefix + XPATH_RPC_REPLY_ERROR_INFO_ERR_ELEMENT, document, XPathConstants.NODE);
                final Element noOpElement = (Element) xPath.evaluate(expressionPrefix + XPATH_RPC_REPLY_ERROR_INFO_NO_OP_ELEMENT, document, XPathConstants.NODE);

                final RpcError.RpcErrorInfo errorInfo = RpcError.RpcErrorInfo.builder()
                    .badAttribute(getTextContent(badAttributeElement))
                    .badElement(getTextContent(badElementAttribute))
                    .badNamespace(getTextContent(badNamespaceElement))
                    .sessionId(getTextContent(sessionIdElement))
                    .okElement(getTextContent(okElement))
                    .errElement(getTextContent(errElement))
                    .noOpElement(getTextContent(noOpElement))
                    .build();
                errorBuilder.errorInfo(errorInfo);
            }
            errorList.add(errorBuilder.build());
        }
        return errorList;
    }

    /**
     * Full constructor for RpcReply.
     *
     * @param namespacePrefix optional XML namespace prefix, may be {@code null}
     * @param originalDocument the original Document, or {@code null} to build a new one
     * @param messageId the message id
     * @param ok whether the reply is ok
     * @param errors the list of {@link RpcError} (must not be {@code null})
     * @param sessionId the session id
     * @param capabilities the list of capabilities
     */
    public RpcReply(
            final String namespacePrefix,
            final Document originalDocument,
            final String messageId,
            final boolean ok,
            final List<RpcError> errors,
            final String sessionId,
            final List<String> capabilities
    ) {
        super(getDocument(originalDocument, namespacePrefix, messageId, ok, errors));
        this.namespacePrefix = namespacePrefix;
        this.messageId = messageId;
        this.ok = ok;
        this.errors = errors;
        this.sessionId = sessionId;
        this.capabilities = capabilities;
    }

    /**
     * Returns the XML Document for the reply, using the original or generating a new one.
     *
     * @param originalDocument the original XML document, or null
     * @param namespacePrefix optional XML namespace prefix
     * @param messageId the message ID
     * @param ok true if reply is ok
     * @param errors list of RpcError
     * @return a valid Document object
     */
    private static Document getDocument(
        final Document originalDocument,
        final String namespacePrefix,
        final String messageId,
        final boolean ok,
        final List<RpcError> errors) {
        return Objects.requireNonNullElseGet(originalDocument, () -> createDocument(namespacePrefix, messageId, ok, errors));
    }

    /**
     * Creates a new XML Document representing the rpc-reply.
     *
     * @param namespacePrefix optional XML namespace prefix
     * @param messageId the message ID
     * @param ok true if reply is ok
     * @param errors list of RpcError
     * @return a new Document instance
     */
    private static Document createDocument(
        final String namespacePrefix,
        final String messageId,
        final boolean ok,
        final List<RpcError> errors) {
        final Document createdDocument = createBlankDocument();

        final Element rpcReplyElement = createdDocument.createElementNS(NetconfConstants.URN_XML_NS_NETCONF_BASE_1_0, "rpc-reply");
        if (namespacePrefix != null) {
            rpcReplyElement.setPrefix(namespacePrefix);
        }
        rpcReplyElement.setAttribute("message-id", messageId);
        createdDocument.appendChild(rpcReplyElement);
        if (ok) {
            final Element okElement = createdDocument.createElementNS(NetconfConstants.URN_XML_NS_NETCONF_BASE_1_0, "ok");
            if (namespacePrefix != null) {
                okElement.setPrefix(namespacePrefix);
            }
            rpcReplyElement.appendChild(okElement);
        }
        appendErrors(namespacePrefix, errors, createdDocument, rpcReplyElement);

        return createdDocument;
    }

    /**
     * Appends error elements to the rpc-reply XML structure.
     *
     * @param namespacePrefix optional XML namespace prefix
     * @param errors list of RpcError
     * @param createdDocument the Document to which elements are added
     * @param parentElement the parent element to append to
     */
    protected static void appendErrors(final String namespacePrefix, final List<RpcError> errors, final Document createdDocument, final Element parentElement) {
        errors.forEach(error -> {
            final Element errorElement = createdDocument.createElementNS(NetconfConstants.URN_XML_NS_NETCONF_BASE_1_0, "rpc-error");
            if (namespacePrefix != null) {
                errorElement.setPrefix(namespacePrefix);
            }
            parentElement.appendChild(errorElement);
            ofNullable(error.errorType())
                .ifPresent(errorType-> appendElementWithText(createdDocument, errorElement, namespacePrefix, "error-type", errorType.getTextContent()));
            ofNullable(error.errorTag())
                .ifPresent(errorTag -> appendElementWithText(createdDocument, errorElement, namespacePrefix, "error-tag", errorTag.getTextContent()));
            ofNullable(error.errorSeverity())
                .ifPresent(errorSeverity -> appendElementWithText(createdDocument, errorElement, namespacePrefix, "error-severity", errorSeverity.getTextContent()));
            appendElementWithText(createdDocument, errorElement, namespacePrefix, "error-path", error.errorPath());
            final Element errorMessageElement = appendElementWithText(createdDocument, errorElement, namespacePrefix, "error-message", error.errorMessage());
            ofNullable(error.errorMessageLanguage())
                .ifPresent(errorMessageLanguage -> errorMessageElement.setAttribute("xml:lang", errorMessageLanguage));
            ofNullable(error.errorInfo()).ifPresent(errorInfo -> {
                final Element errorInfoElement = createdDocument.createElementNS(NetconfConstants.URN_XML_NS_NETCONF_BASE_1_0, "error-info");
                if (namespacePrefix != null) {
                    errorInfoElement.setPrefix(namespacePrefix);
                }
                errorElement.appendChild(errorInfoElement);
                appendElementWithText(createdDocument, errorInfoElement, namespacePrefix, "bad-attribute", errorInfo.getBadAttribute());
                appendElementWithText(createdDocument, errorInfoElement, namespacePrefix, "bad-element", errorInfo.getBadElement());
                appendElementWithText(createdDocument, errorInfoElement, namespacePrefix, "bad-namespace", errorInfo.getBadNamespace());
                appendElementWithText(createdDocument, errorInfoElement, namespacePrefix, "session-id", errorInfo.getSessionId());
                appendElementWithText(createdDocument, errorInfoElement, namespacePrefix, "ok-element", errorInfo.getOkElement());
                appendElementWithText(createdDocument, errorInfoElement, namespacePrefix, "err-element", errorInfo.getErrElement());
                appendElementWithText(createdDocument, errorInfoElement, namespacePrefix, "noop-element", errorInfo.getNoOpElement());
            });
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RpcReply rpcReply)) return false;
        if (!super.equals(o)) return false;
        return ok == rpcReply.ok &&
                Objects.equals(messageId, rpcReply.messageId) &&
                Objects.equals(errors, rpcReply.errors) &&
                Objects.equals(sessionId, rpcReply.sessionId) &&
                Objects.equals(capabilities, rpcReply.capabilities);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), messageId, ok, errors, sessionId, capabilities);
    }

    @Override
    public String toString() {
        return "RpcReply{" +
                "messageId='" + messageId + '\'' +
                ", ok=" + ok +
                ", errors=" + errors +
                ", sessionId='" + sessionId + '\'' +
                ", capabilities=" + capabilities +
                "} " + super.toString();
    }
}