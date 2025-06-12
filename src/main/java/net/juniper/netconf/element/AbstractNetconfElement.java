package net.juniper.netconf.element;

import net.juniper.netconf.NetconfConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

import static java.lang.String.format;

/**
 * Base class for all model objects that represent NETCONF XML fragments
 * (e.g.&nbsp;{@code <rpc-reply>}, {@code <hello>}).
 * <p>
 * Each subclass wraps a {@link org.w3c.dom.Document} so that:
 * <ul>
 *   <li>The DOM is <strong>immutable</strong> from the caller’s perspective—
 *       getters return defensive copies.</li>
 *   <li>An on‑demand, pre‑rendered XML {@link String} is cached for fast
 *       {@link #equals(Object)}, {@link #hashCode()}, and logging.</li>
 * </ul>
 * Common XML helper methods live here so builders and parsers can share a
 * single, RFC&nbsp;6241‑aware implementation.
 *
 * @author Juniper Networks
 */
public abstract class AbstractNetconfElement {

    private final Document document;
    private final String xml;

    /**
     * Wraps the supplied DOM {@link Document} and pre‑computes its XML string
     * representation for fast equality checks and logging.
     *
     * @param document a fully‑formed NETCONF XML document; must not be {@code null}
     * @throws NullPointerException if {@code document} is {@code null}
     */
    protected AbstractNetconfElement(final Document document) {
        this.document = document;
        this.xml = createXml(document);
    }

    /**
     * Returns a <em>defensive deep copy</em> of the underlying DOM
     * {@link Document} so callers cannot mutate the internal state.
     *
     * @return a cloned {@link Document} representing this element
     */
    public Document getDocument() {
        return (Document) document.cloneNode(true); // deep copy
    }

    /**
     * Returns the cached XML string representation of the wrapped document.
     *
     * @return XML string with no declaration (UTF‑8 assumed)
     */
    public String getXml() {
        return xml;
    }

    /**
     * Creates an empty, namespace‑aware DOM {@link Document}.
     * <p>
     * Internally delegates to {@link #createDocumentBuilderFactory()} to ensure
     * all security features are applied consistently.
     *
     * @return a brand‑new, empty {@link Document}
     * @throws IllegalStateException if the platform’s XML parser cannot be configured
     */
    protected static Document createBlankDocument() {
        try {
            return createDocumentBuilderFactory().newDocumentBuilder().newDocument();
        } catch (final ParserConfigurationException e) {
            throw new IllegalStateException("Unable to create document builder", e);
        }
    }

    /**
     * Returns a pre‑configured {@link DocumentBuilderFactory} with
     * <em>namespace awareness enabled</em>.  Additional hardening options
     * (e.g.&nbsp;disallowing DTDs) can be added here centrally so every
     * NETCONF element parser benefits.
     *
     * @return a namespace‑aware {@link DocumentBuilderFactory}
     */
    protected static DocumentBuilderFactory createDocumentBuilderFactory() {
        final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        return documentBuilderFactory;
    }

    /**
     * Serialises a DOM {@link Document} to its XML string representation.
     * <p>
     * The XML declaration is omitted because NETCONF frames are always UTF‑8
     * and the declaration is not required on the wire.
     *
     * @param document the document to serialise; must not be {@code null}
     * @return XML string (no declaration)
     * @throws IllegalStateException if a {@link TransformerException} occurs
     */
    protected static String createXml(final Document document) {
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

    /**
     * Convenience helper that builds an XPath expression matching a NETCONF
     * element in the base 1.0 namespace with the specified local‑name.
     *
     * @param elementName local name (e.g.&nbsp;{@code "rpc-reply"})
     * @return an XPath string scoped to the NETCONF base namespace
     */
    protected static String getXpathFor(final String elementName) {
        return format("/*[namespace-uri()='urn:ietf:params:xml:ns:netconf:base:1.0' and local-name()='%s']", elementName);
    }

    /**
     * Appends a child element (with optional text content) to the given parent,
     * using the NETCONF base 1.0 namespace and the provided prefix.
     *
     * @param document        owner document
     * @param parentElement   element to which the new child is appended
     * @param namespacePrefix namespace prefix to set on the new element
     * @param elementName     local name of the child element
     * @param text            text content; if {@code null} the element is skipped
     * @return the newly created element, or {@code null} if {@code text} was {@code null}
     */
    protected static Element appendElementWithText(
        final Document document,
        final Element parentElement,
        final String namespacePrefix,
        final String elementName,
        final String text) {

        if (text != null) {
            final Element childElement = document.createElementNS(NetconfConstants.URN_XML_NS_NETCONF_BASE_1_0, elementName);
            childElement.setPrefix(namespacePrefix);
            childElement.setTextContent(text);
            parentElement.appendChild(childElement);
            return childElement;
        } else {
            return null;
        }
    }

    /**
     * Safely retrieves an attribute value from the supplied DOM {@link Element}.
     *
     * @param element       the element to query; may be {@code null}
     * @param attributeName the local name of the attribute
     * @return the attribute value if the element is non‑null and the attribute
     *         exists; otherwise {@code null}
     */
    protected static String getAttribute(final Element element, final String attributeName) {
        if (element != null && element.hasAttribute(attributeName)) {
            return element.getAttribute(attributeName);
        } else {
            return null;
        }
    }

    /**
     * Returns the trimmed text content of a DOM {@link Element}.
     *
     * @param element the element whose {@code getTextContent()} should be read;
     *                may be {@code null}
     * @return trimmed text or {@code null} if the element is {@code null}
     */
    protected static String getTextContent(final Element element) {
        if (element == null) {
            return null;
        } else {
            return trim(element.getTextContent());
        }
    }

    /**
     * Convenience null‑safe {@link String#trim()} wrapper.
     *
     * @param string the input string; may be {@code null}
     * @return a trimmed copy of {@code string}, or {@code null} if the input
     *         was {@code null}
     */
    protected static String trim(final String string) {
        return string == null ? null : string.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractNetconfElement that = (AbstractNetconfElement) o;
        return xml.equals(that.xml);
    }

    @Override
    public int hashCode() {
        return xml.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{}";
    }
}
