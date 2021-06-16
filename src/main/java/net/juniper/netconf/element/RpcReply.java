package net.juniper.netconf.element;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
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

import static java.util.Optional.ofNullable;

/**
 * Class to represent a NETCONF rpc-reply element - https://datatracker.ietf.org/doc/html/rfc6241#section-4.2
 */
@Slf4j
@Value
@NonFinal
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class RpcReply extends AbstractNetconfElement {

    protected static final String XPATH_RPC_REPLY = getXpathFor("rpc-reply");
    private static final String XPATH_RPC_REPLY_OK = XPATH_RPC_REPLY + getXpathFor("ok");
    private static final String XPATH_RPC_REPLY_ERROR = XPATH_RPC_REPLY + getXpathFor("rpc-error");
    private static final String XPATH_RPC_REPLY_ERROR_TYPE = getXpathFor("error-type");
    private static final String XPATH_RPC_REPLY_ERROR_TAG = getXpathFor("error-tag");
    private static final String XPATH_RPC_REPLY_ERROR_SEVERITY = getXpathFor("error-severity");
    private static final String XPATH_RPC_REPLY_ERROR_PATH = getXpathFor("error-path");
    private static final String XPATH_RPC_REPLY_ERROR_MESSAGE = getXpathFor("error-message");
    private static final String XPATH_RPC_REPLY_ERROR_INFO = getXpathFor("error-info");
    private static final String XPATH_RPC_REPLY_ERROR_INFO_BAD_ATTRIBUTE = XPATH_RPC_REPLY_ERROR_INFO + getXpathFor("bad-attribute");
    private static final String XPATH_RPC_REPLY_ERROR_INFO_BAD_ELEMENT = XPATH_RPC_REPLY_ERROR_INFO + getXpathFor("bad-element");
    private static final String XPATH_RPC_REPLY_ERROR_INFO_BAD_NAMESPACE = XPATH_RPC_REPLY_ERROR_INFO + getXpathFor("bad-namespace");
    private static final String XPATH_RPC_REPLY_ERROR_INFO_SESSION_ID = XPATH_RPC_REPLY_ERROR_INFO + getXpathFor("session-id");
    private static final String XPATH_RPC_REPLY_ERROR_INFO_OK_ELEMENT = XPATH_RPC_REPLY_ERROR_INFO + getXpathFor("ok-element");
    private static final String XPATH_RPC_REPLY_ERROR_INFO_ERR_ELEMENT = XPATH_RPC_REPLY_ERROR_INFO + getXpathFor("err-element");
    private static final String XPATH_RPC_REPLY_ERROR_INFO_NO_OP_ELEMENT = XPATH_RPC_REPLY_ERROR_INFO + getXpathFor("noop-element");

    String messageId;
    boolean ok;
    List<RpcError> errors;

    public boolean hasErrorsOrWarnings() {
        return !errors.isEmpty();
    }

    public boolean hasErrors() {
        return errors.stream().anyMatch(error -> error.getErrorSeverity() == RpcError.ErrorSeverity.ERROR);
    }

    public boolean hasWarnings() {
        return errors.stream().anyMatch(error -> error.getErrorSeverity() == RpcError.ErrorSeverity.WARNING);
    }

    @SuppressWarnings("unchecked")
    public static <T extends AbstractNetconfElement> T from(final String xml)
        throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {

        final Document document = createDocumentBuilderFactory().newDocumentBuilder()
            .parse(new InputSource(new StringReader(xml)));
        final XPath xPath = XPathFactory.newInstance().newXPath();

        final Element loadConfigResultsElement = (Element) xPath.evaluate(RpcReplyLoadConfigResults.XPATH_RPC_REPLY_LOAD_CONFIG_RESULT, document, XPathConstants.NODE);
        if (loadConfigResultsElement != null) {
            return (T) RpcReplyLoadConfigResults.from(xml);
        }

        final Element rpcReplyElement = (Element) xPath.evaluate(XPATH_RPC_REPLY, document, XPathConstants.NODE);
        final Element rpcReplyOkElement = (Element) xPath.evaluate(XPATH_RPC_REPLY_OK, document, XPathConstants.NODE);
        final List<RpcError> errorList = getRpcErrors(document, xPath, XPATH_RPC_REPLY_ERROR);

        final RpcReply rpcReply = RpcReply.builder()
            .messageId(getAttribute(rpcReplyElement, "message-id"))
            .ok(rpcReplyOkElement != null)
            .errors(errorList)
            .originalDocument(document)
            .build();
        log.info("rpc-reply is: {}", rpcReply.getXml());
        return (T) rpcReply;
    }

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
            final RpcError.RpcErrorBuilder errorBuilder = RpcError.builder()
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

    @Builder
    protected RpcReply(
        final Document originalDocument,
        final String namespacePrefix,
        final String messageId,
        final boolean ok,
        @Singular("error") final List<RpcError> errors) {
        super(getDocument(originalDocument, namespacePrefix, messageId, ok, errors));
        this.messageId = messageId;
        this.ok = ok;
        this.errors = errors;
    }

    private static Document getDocument(
        final Document originalDocument,
        final String namespacePrefix,
        final String messageId,
        final boolean ok,
        final List<RpcError> errors) {
        if (originalDocument != null) {
            return originalDocument;
        } else {
            return createDocument(namespacePrefix, messageId, ok, errors);
        }
    }

    private static Document createDocument(
        final String namespacePrefix,
        final String messageId,
        final boolean ok,
        final List<RpcError> errors) {
        final Document createdDocument = createBlankDocument();

        final Element rpcReplyElement = createdDocument.createElementNS(NetconfConstants.URN_XML_NS_NETCONF_BASE_1_0, "rpc-reply");
        rpcReplyElement.setPrefix(namespacePrefix);
        rpcReplyElement.setAttribute("message-id", messageId);
        createdDocument.appendChild(rpcReplyElement);
        if (ok) {
            final Element okElement = createdDocument.createElementNS(NetconfConstants.URN_XML_NS_NETCONF_BASE_1_0, "ok");
            okElement.setPrefix(namespacePrefix);
            rpcReplyElement.appendChild(okElement);
        }
        appendErrors(namespacePrefix, errors, createdDocument, rpcReplyElement);

        return createdDocument;
    }

    protected static void appendErrors(final String namespacePrefix, final List<RpcError> errors, final Document createdDocument, final Element parentElement) {
        errors.forEach(error -> {
            final Element errorElement = createdDocument.createElementNS(NetconfConstants.URN_XML_NS_NETCONF_BASE_1_0, "rpc-error");
            errorElement.setPrefix(namespacePrefix);
            parentElement.appendChild(errorElement);
            ofNullable(error.getErrorType())
                .ifPresent(errorType-> appendElementWithText(createdDocument, errorElement, namespacePrefix, "error-type", errorType.getTextContent()));
            ofNullable(error.getErrorTag())
                .ifPresent(errorTag -> appendElementWithText(createdDocument, errorElement, namespacePrefix, "error-tag", errorTag.getTextContent()));
            ofNullable(error.getErrorSeverity())
                .ifPresent(errorSeverity -> appendElementWithText(createdDocument, errorElement, namespacePrefix, "error-severity", errorSeverity.getTextContent()));
            appendElementWithText(createdDocument, errorElement, namespacePrefix, "error-path", error.getErrorPath());
            final Element errorMessageElement = appendElementWithText(createdDocument, errorElement, namespacePrefix, "error-message", error.getErrorMessage());
            ofNullable(error.getErrorMessageLanguage())
                .ifPresent(errorMessageLanguage -> errorMessageElement.setAttribute("xml:lang", errorMessageLanguage));
            ofNullable(error.getErrorInfo()).ifPresent(errorInfo -> {
                final Element errorInfoElement = createdDocument.createElementNS(NetconfConstants.URN_XML_NS_NETCONF_BASE_1_0, "error-info");
                errorInfoElement.setPrefix(namespacePrefix);
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
}
