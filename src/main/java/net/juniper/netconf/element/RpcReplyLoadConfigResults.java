package net.juniper.netconf.element;

import net.juniper.netconf.NetconfConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Objects;

/**
 * Represents a NETCONF &lt;rpc-reply&gt; that contains a
 * &lt;load-configuration-results&gt; element.  Provides both a static
 * {@link #from(String)} parser and a fluent {@link Builder} for
 * programmatic construction.
 */
public class RpcReplyLoadConfigResults extends RpcReply {

    static final String XPATH_RPC_REPLY_LOAD_CONFIG_RESULT = RpcReply.XPATH_RPC_REPLY + "/*[local-name()='load-configuration-results']";
    private static final String XPATH_RPC_REPLY_LOAD_CONFIG_RESULT_OK = XPATH_RPC_REPLY_LOAD_CONFIG_RESULT + getXpathFor("ok");
    private static final String XPATH_RPC_REPLY_LOAD_CONFIG_RESULT_ERROR = XPATH_RPC_REPLY_LOAD_CONFIG_RESULT + getXpathFor("rpc-error");

    private final String action;

    /**
     * Parses the supplied XML string into an immutable
     * {@code RpcReplyLoadConfigResults}.
     *
     * @param xml raw XML containing a &lt;rpc-reply&gt;/&lt;load-configuration-results&gt;
     * @return parsed {@code RpcReplyLoadConfigResults} instance
     * @throws ParserConfigurationException if the XML parser cannot be configured
     * @throws IOException                  if the XML cannot be read
     * @throws SAXException                 if the XML is not well‑formed
     * @throws XPathExpressionException     if required nodes cannot be located
     */
    public static RpcReplyLoadConfigResults from(final String xml)
        throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {

        final Document document = createDocumentBuilderFactory().newDocumentBuilder()
            .parse(new InputSource(new StringReader(xml)));
        final XPath xPath = XPathFactory.newInstance().newXPath();

        final Element rpcReplyElement = (Element) xPath.evaluate(XPATH_RPC_REPLY, document, XPathConstants.NODE);
        final Element loadConfigResultsElement = (Element) xPath.evaluate(RpcReplyLoadConfigResults.XPATH_RPC_REPLY_LOAD_CONFIG_RESULT, document, XPathConstants.NODE);
        final Element rpcReplyOkElement = (Element) xPath.evaluate(XPATH_RPC_REPLY_LOAD_CONFIG_RESULT_OK, document, XPathConstants.NODE);
        final List<RpcError> errorList = getRpcErrors(document, xPath, XPATH_RPC_REPLY_LOAD_CONFIG_RESULT_ERROR);

        return RpcReplyLoadConfigResults.loadConfigResultsBuilder()
            .originalDocument(document)
            .namespacePrefix(null)
            .messageId(getAttribute(rpcReplyElement, "message-id"))
            .action(getAttribute(loadConfigResultsElement, "action"))
            .ok(rpcReplyOkElement != null)
            .errors(errorList)
            .build();
    }

    private RpcReplyLoadConfigResults(
        final String namespacePrefix,
        final Document originalDocument,
        final String messageId,
        final String action,
        final boolean ok,
        final List<RpcError> errors) {
        super(
            namespacePrefix,
            getDocument(originalDocument, namespacePrefix, messageId, action, ok, errors),
            messageId,
            ok,
            errors,
            null,
            null
        );
        this.action = action;
    }

    /**
     * Returns a new {@link Builder} for constructing
     * {@code RpcReplyLoadConfigResults} objects.
     *
     * @return a fresh {@link Builder}
     */
    public static Builder loadConfigResultsBuilder() {
        return new Builder();
    }

    /**
     * Returns the value of the {@code action} attribute found in the
     * &lt;load-configuration-results&gt; element.
     *
     * @return action attribute string
     */
    public String getAction() {
        return action;
    }

    /**
     * Builder for {@link RpcReplyLoadConfigResults}.
     * Use this builder to construct immutable instances of RpcReplyLoadConfigResults.
     */
    public static class Builder {
        /**
         * Creates an empty {@code Builder}.
         */
        public Builder() {
        }
        private Document originalDocument;
        private String namespacePrefix;
        private String messageId;
        private String action;
        private boolean ok;
        private List<RpcError> errors = new java.util.ArrayList<>();

        /**
         * Sets the original XML Document for the reply.
         * @param originalDocument the XML Document, must not be null
         * @return this Builder
         * @throws NullPointerException if originalDocument is null
         */
        public Builder originalDocument(Document originalDocument) {
            this.originalDocument = Objects.requireNonNull(originalDocument, "originalDocument must not be null");
            return this;
        }

        /**
         * Sets the namespace prefix for the reply.
         * @param namespacePrefix the prefix, may be null
         * @return this Builder
         */
        public Builder namespacePrefix(String namespacePrefix) {
            this.namespacePrefix = namespacePrefix;
            return this;
        }

        /**
         * Sets the message-id for the reply.
         * @param messageId the message id, must not be null
         * @return this Builder
         * @throws NullPointerException if messageId is null
         */
        public Builder messageId(String messageId) {
            this.messageId = Objects.requireNonNull(messageId, "messageId must not be null");
            return this;
        }

        /**
         * Sets the action attribute for the reply.
         * @param action the action, must not be null
         * @return this Builder
         * @throws NullPointerException if action is null
         */
        public Builder action(String action) {
            this.action = Objects.requireNonNull(action, "action must not be null");
            return this;
        }

        /**
         * Sets the ok flag for the reply.
         * @param ok true if reply is ok, false otherwise
         * @return this Builder
         */
        public Builder ok(boolean ok) {
            this.ok = ok;
            return this;
        }

        /**
         * Sets the list of errors for the reply.
         * @param errors the list of errors, must not be null
         * @return this Builder
         * @throws NullPointerException if errors is null
         */
        public Builder errors(List<RpcError> errors) {
            this.errors = new java.util.ArrayList<>(Objects.requireNonNull(errors, "errors list must not be null"));
            return this;
        }

        /**
         * Adds a single error to the reply.
         * @param error the error to add, ignored if null
         * @return this Builder
         */
        public Builder addError(RpcError error) {
            if (error != null) {
                this.errors.add(error);
            }
            return this;
        }

        /**
         * Builds the immutable RpcReplyLoadConfigResults instance.
         * @return the built RpcReplyLoadConfigResults
         */
        public RpcReplyLoadConfigResults build() {
            return new RpcReplyLoadConfigResults(
                namespacePrefix,          // 1) prefix
                originalDocument,         // 2) document
                messageId,                // 3) message-id
                action,                   // 4) action
                ok,                       // 5) ok flag
                errors                    // 6) error list
            );
        }
    }

    private static Document getDocument(
        final Document originalDocument,
        final String namespacePrefix,
        final String messageId,
        final String action,
        final boolean ok,
        final List<RpcError> errors) {
        if (originalDocument != null) {
            return originalDocument;
        } else {
            return createDocument(namespacePrefix, messageId, action, ok, errors);
        }
    }

    private static Document createDocument(
        final String namespacePrefix,
        final String messageId,
        final String action,
        final boolean ok,
        final List<RpcError> errors) {
        final Document createdDocument = createBlankDocument();
        final Element rpcReplyElement = createdDocument.createElementNS(NetconfConstants.URN_XML_NS_NETCONF_BASE_1_0, "rpc-reply");
        rpcReplyElement.setPrefix(namespacePrefix);
        rpcReplyElement.setAttribute("message-id", messageId);
        createdDocument.appendChild(rpcReplyElement);
        final Element loadConfigResultsElement = createdDocument.createElement("load-configuration-results");
        loadConfigResultsElement.setAttribute("action", action);
        rpcReplyElement.appendChild(loadConfigResultsElement);
        appendErrors(namespacePrefix, errors, createdDocument, loadConfigResultsElement);
        if (ok) {
            final Element okElement = createdDocument.createElementNS(NetconfConstants.URN_XML_NS_NETCONF_BASE_1_0, "ok");
            okElement.setPrefix(namespacePrefix);
            loadConfigResultsElement.appendChild(okElement);
        }

        return createdDocument;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RpcReplyLoadConfigResults)) return false;
        if (!super.equals(o)) return false;
        RpcReplyLoadConfigResults that = (RpcReplyLoadConfigResults) o;
        return Objects.equals(action, that.action);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), action);
    }

    @Override
    public String toString() {
        return "RpcReplyLoadConfigResults{" +
            "action='" + action + '\'' +
            "} " + super.toString();
    }

    /**
     * Returns a defensive copy of the errors list to avoid exposing internal
     * representation.
     *
     * @return copy of the error list
     */
    @SuppressWarnings("unchecked") // parent class returns raw List
    public List<RpcError> getErrors() {
        return new java.util.ArrayList<>((List<RpcError>) super.getErrors());
    }
}