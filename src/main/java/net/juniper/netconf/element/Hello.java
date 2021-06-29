package net.juniper.netconf.element;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.juniper.netconf.NetconfConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

/**
 * Class to represent a NETCONF hello element - https://datatracker.ietf.org/doc/html/rfc6241#section-8.1
 */
@Data
@Slf4j
public class Hello {

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private final Document document;

    @ToString.Exclude
    private final String xml;

    private final String sessionId;

    @Singular("capability")
    private final List<String> capabilities;

    public boolean hasCapability(final String capability) {
        return capabilities.contains(capability);
    }

    public String toXML() {
        return xml;
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

        final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        final Document document = documentBuilderFactory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
        final XPath xPath = XPathFactory.newInstance().newXPath();
        final String sessionId = xPath.evaluate("/*[namespace-uri()='urn:ietf:params:xml:ns:netconf:base:1.0' and local-name()='hello']/*[namespace-uri()='urn:ietf:params:xml:ns:netconf:base:1.0' and local-name()='session-id']", document);
        final HelloBuilder builder = Hello.builder()
                .originalDocument(document)
                .sessionId(sessionId);
        final NodeList capabilities = (NodeList) xPath.evaluate("/*[namespace-uri()='urn:ietf:params:xml:ns:netconf:base:1.0' and local-name()='hello']/*[namespace-uri()='urn:ietf:params:xml:ns:netconf:base:1.0' and local-name()='capabilities']/*[namespace-uri()='urn:ietf:params:xml:ns:netconf:base:1.0' and local-name()='capability']", document, XPathConstants.NODESET);
        for (int i = 0; i < capabilities.getLength(); i++) {
            final Node node = capabilities.item(i);
            builder.capability(node.getTextContent());
        }
        final Hello hello = builder.build();
        if (log.isInfoEnabled()) {
            log.info("hello is: {}", hello.toXML());
        }
        return hello;
    }

    @Builder
    private Hello(
            final Document originalDocument,
            final String namespacePrefix,
            final String sessionId,
            @Singular("capability") final List<String> capabilities) {
        this.sessionId = sessionId;
        this.capabilities = capabilities;
        if (originalDocument != null) {
            this.document = originalDocument;
        } else {
            this.document = createDocument(namespacePrefix, sessionId, capabilities);
        }
        this.xml = createXml(document);
    }

    private static Document createDocument(
            final String namespacePrefix,
            final String sessionId,
            final List<String> capabilities) {

        final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        final Document createdDocument;
        try {
            createdDocument = documentBuilderFactory.newDocumentBuilder().newDocument();
        } catch (final ParserConfigurationException e) {
            throw new IllegalStateException("Unable to create document builder", e);
        }

        final Element helloElement
                = createdDocument.createElementNS(NetconfConstants.URN_XML_NS_NETCONF_BASE_1_0, "hello");
        helloElement.setPrefix(namespacePrefix);
        createdDocument.appendChild(helloElement);

        final Element capabilitiesElement
                = createdDocument.createElementNS(NetconfConstants.URN_XML_NS_NETCONF_BASE_1_0, "capabilities");
        capabilitiesElement.setPrefix(namespacePrefix);
        capabilities.forEach(capability -> {
            final Element capabilityElement =
                    createdDocument.createElementNS(NetconfConstants.URN_XML_NS_NETCONF_BASE_1_0, "capability");
            capabilityElement.setTextContent(capability);
            capabilityElement.setPrefix(namespacePrefix);
            capabilitiesElement.appendChild(capabilityElement);
        });
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

    private static String createXml(final Document document) {
        try {
            final TransformerFactory transformerFactory = TransformerFactory.newInstance();
            final Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            final StringWriter stringWriter = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(stringWriter));
            return stringWriter.toString();
        } catch (final TransformerException e) {
            throw new IllegalStateException("Unable to transform document to XML", e);
        }
    }

}
