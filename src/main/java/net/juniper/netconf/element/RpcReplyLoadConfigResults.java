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

@Slf4j
@Value
@NonFinal
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class RpcReplyLoadConfigResults extends RpcReply {

    static final String XPATH_RPC_REPLY_LOAD_CONFIG_RESULT = RpcReply.XPATH_RPC_REPLY + "/*[local-name()='load-configuration-results']";
    private static final String XPATH_RPC_REPLY_LOAD_CONFIG_RESULT_OK = XPATH_RPC_REPLY_LOAD_CONFIG_RESULT + getXpathFor("ok");
    private static final String XPATH_RPC_REPLY_LOAD_CONFIG_RESULT_ERROR = XPATH_RPC_REPLY_LOAD_CONFIG_RESULT + getXpathFor("rpc-error");

    String action;

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
            .messageId(getAttribute(rpcReplyElement, "message-id"))
            .action(getAttribute(loadConfigResultsElement, "action"))
            .ok(rpcReplyOkElement != null)
            .errors(errorList)
            .originalDocument(document)
            .build();
    }

    @Builder(builderMethodName = "loadConfigResultsBuilder")
    private RpcReplyLoadConfigResults(
        final Document originalDocument,
        final String namespacePrefix,
        final String messageId,
        final String action,
        final boolean ok,
        @Singular("error") final List<RpcError> errors) {
        super(getDocument(originalDocument, namespacePrefix, messageId, action, ok, errors),
            namespacePrefix, messageId, ok, errors);
        this.action = action;
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
}