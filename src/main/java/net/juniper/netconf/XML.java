/*
  Copyright (c) 2013 Juniper Networks, Inc.
  All Rights Reserved

  Use is subject to license terms.

 */

package net.juniper.netconf;
import java.util.logging.Logger;

import com.google.common.base.Preconditions;

import javax.xml.XMLConstants;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static net.juniper.netconf.NetconfConstants.LF;

/**
 * An <code>XML</code> object represents XML content and provides methods to
 * manipulate it.
 * The <code>XML</code> object has an 'active' element, which represents the
 * hierarchy at which the XML can be manipulated.
 * <p>
 * As an example, one
 * <ol>
 * <li>creates a XMLBuilder object.</li>
 * <li>create a configuration as an XML object.</li>
 * <li>Call the loadXMLConfiguration(XML) method on Device</li>
 * </ol>
 *
 */
public class XML {

    private static final Logger logger = Logger.getLogger(XML.class.getName());
    private final Element activeElement;
    private final Document ownerDoc;

    /**
     * Creates a new {@code XML} wrapper with the supplied DOM {@link Element} as its
     * initial <em>active</em> element.
     * <p>
     * This constructor is intentionally <code>protected</code>; normal clients are
     * expected to obtain {@code XML} instances via {@link XMLBuilder} rather than
     * constructing them directly.  Keeping the constructor non‑public ensures the
     * internal DOM is manipulated through the provided fluent API.
     *
     * @param active the DOM element that will serve as the current active node;
     *               must not be {@code null}
     */
    protected XML(Element active) {
        this.activeElement = active;
        ownerDoc = active.getOwnerDocument();
    }

    private String trim(String str) {
        String st = str.trim();
        if (st.startsWith(LF))
            st = st.substring(st.indexOf(LF) + 1);
        if (st.endsWith(LF)) {
            st = st.substring(0, st.lastIndexOf(LF));
        }
        return st;
    }



    /**
     * Get the owner Document for the XML object.
     * @return The org.w3c.dom.Document object for the XML
     */
    public Document getOwnerDocument() {
        return (Document) ownerDoc.cloneNode(true); // deep copy
    }

    /**
     * Append an element under the active element of XML object. The new element
     * now becomes the active element.
     * @param element
     *           The name of element to append.
     * @return The modified XML after appending the element.
     */
    public XML append(String element) {
        Element newElement = ownerDoc.createElement(element);
        activeElement.appendChild(newElement);
        return new XML(newElement);
    }

    /**
     * Append an element, with text, under the active element of XML object.
     * The new element now becomes the active element.
     * @param element
     *           The name of element to append.
     * @param text
     *           The text value of the element to be appended
     * @return The modified XML after appending the element.
     */
    public XML append(String element, String text) {
        Element newElement = ownerDoc.createElement(element);
        Node textNode = ownerDoc.createTextNode(text);
        newElement.appendChild(textNode);
        activeElement.appendChild(newElement);
        return new XML(newElement);
    }

    /**
     * Append multiple elements, with same name but different text under the
     * active element of XML object.
     * @param element
     *            The name of elements to append.
     * @param text
     *            The array containing text value for each element.
     */
    public void append(String element, String[] text) {
        for (String s : text) {
            Element newElement = ownerDoc.createElement(element);
            Node textNode = ownerDoc.createTextNode(s);
            newElement.appendChild(textNode);
            activeElement.appendChild(newElement);
        }
    }

    /**
     * Append multiple elements with different names and different text,
     * under the active element of XML object.
     * @param map
     *         The map of each entry containing element name as the key and
     *         text value as the key value.
     */
    public void append(Map<String, String> map) {
        List<String> keyList = new ArrayList<>(map.keySet());
        for (Object element : keyList) {
            String elementName = (String) element;
            String text = map.get(element);
            Element newElement = ownerDoc.createElement(elementName);
            Node textNode = ownerDoc.createTextNode(text);
            newElement.appendChild(textNode);
            activeElement.appendChild(newElement);
        }
    }

    /**
     * Append an element under the active element of XML object. The new element
     * now becomes the active element.
     * Then, append multiple elements with different names and different text,
     * under the new active element.
     * @param element
     *           The name of the element to be appended.
     * @param map
     *         The map of each entry containing element name as the key and
     *         text value as the key value.
     * @return The modified XML after appending the element.
     */
    public XML append(String element, Map<String, String> map) {
        Element newElement = ownerDoc.createElement(element);
        activeElement.appendChild(newElement);
        XML newXML = new XML(newElement);
        newXML.append(map);
        return newXML;
    }

    /**
     * Add a sibling element with the active element of XML object.
     * @param element
     *           The name of the new element to be added.
     */
    public void addSibling(String element) {
        Element newElement = ownerDoc.createElement(element);
        Node parentNode = activeElement.getParentNode();
        parentNode.appendChild(newElement);
    }

    /**
     * Append a sibling element, with text, with the active element of XML
     * object.
     * @param element
     *           The name of element to add.
     * @param text
     *           The text value of the element to be appended.
     */
    public void addSibling(String element, String text) {
        Element newElement = ownerDoc.createElement(element);
        Node textNode = ownerDoc.createTextNode(text);
        newElement.appendChild(textNode);
        Node parentNode = activeElement.getParentNode();
        parentNode.appendChild(newElement);
    }

    /**
     * Add multiple sibling elements,with same names but different text, with
     * the active element of XML object.
     * @param element
     *            The name of elements to add.
     * @param text
     *            The array containing text value for each element.
     */
    public void addSiblings(String element, String[] text) {
        Node parentNode = activeElement.getParentNode();
        for (String s : text) {
            Element newElement = ownerDoc.createElement(element);
            Node textNode = ownerDoc.createTextNode(s);
            newElement.appendChild(textNode);
            parentNode.appendChild(newElement);
        }
    }

    /**
     * Add multiple siblings with different names and different text, with the
     * active element of XML object.
     * @param map
     *         The map of each entry containing element name as the key and
     *         text value as the key value.
     */
    public void addSiblings(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return;
        }

        Node parent = activeElement.getParentNode();          // get the parent once
        if (parent == null) {
            throw new IllegalStateException(
                "Cannot add siblings: active element has no parent");
        }

        for (Map.Entry<String, String> entry : map.entrySet()) {
            Element e = ownerDoc.createElement(entry.getKey());
            e.appendChild(ownerDoc.createTextNode(entry.getValue()));
            parent.appendChild(e);                             // add to the *parent*
        }
    }

    /**
     * Append multiple elements under the active element of XML object, by
     * specifying the path.The bottommost hierarchy element now becomes the
     * active element.
     * @param path
     *          The path to be added. For example, to add the hierarchy:
     *          &lt;a&gt;
     *           &lt;b&gt;
     *            &lt;c/&gt;
     *           &lt;/b&gt;
     *          &lt;/a&gt;
     *          The path should be "a/b/c"
     * @return The modified XML
     */
    public XML addPath(String path) {
        String[] elements = path.split("/");
        Preconditions.checkArgument(elements.length >= 1);
        Element newElement = null;
        for (String element : elements) {
            newElement = ownerDoc.createElement(element);
            activeElement.appendChild(newElement);
        }
        return new XML(Objects.requireNonNull(newElement));
    }

    /**
     * Set attribute for the active element of XML object.
     * @param name
     *         The name of the attribute.
     * @param value
     *         The value of the attribute.
     */
    public void setAttribute(String name, String value) {
        activeElement.setAttribute(name, value);
    }

    /**
     * Set text for the active element of XML object.
     * @param text
     *         The text value to be set
     */
    public void setText(String text) {
        Node firstChild = activeElement.getFirstChild();
        if (firstChild == null || firstChild.getNodeType() != Node.TEXT_NODE) {
            Node textNode = ownerDoc.createTextNode(text);
            activeElement.appendChild(textNode);
        } else {
            firstChild.setNodeValue(text);
        }
    }

    /**
     * Sets the text content of the active XML element.
     *
     * @param text The text content to set on the current active element.
     */
    public void setTextContent(String text) {
        activeElement.setTextContent(text);
    }

    /**
     * Sets the attribute ("delete","delete") for the active element of XML
     * object.
     */
    public void junosDelete() {
        activeElement.setAttribute("delete", "delete");
    }

    /**
     * Sets the attribute ("active","active") for the active element of XML
     * object.
     */
    public void junosActivate() {
        activeElement.setAttribute("active", "active");
    }

    /**
     * Sets the attribute ("inactive","inactive") for the active element of XML
     * object.
     */
    public void junosDeactivate() {
        activeElement.setAttribute("inactive", "inactive");
    }

    /**
     * Sets the attribute ("rename") and ("name") for the active element of XML
     * object.
     * @param toBeRenamed current name of the element
     * @param newName new name of the element
     */
    public void junosRename(String toBeRenamed, String newName) {
        activeElement.setAttribute("rename", toBeRenamed);
        activeElement.setAttribute("name", newName);
    }

    /**
     * Sets the attribute ("insert") and ("name") for the active element of XML
     * object.
     * @param before refers to the name of the junos element the new element should be inserted before
     * @param name the name of the new object that should be inserted before the before param object
     */
    public void junosInsert(String before, String name) {
        activeElement.setAttribute("insert", before);
        activeElement.setAttribute("name", name);
    }

    /**
     * Finds the text value of an element in the XML hierarchy specified by the list.
     *
     * @param list
     *        A list of strings representing the XML path. Each entry corresponds to an XML tag.
     *        To apply a text filter, use "tag~text" syntax (e.g., "name~FPC 0 CPU").
     *        For example, to extract &lt;temperature&gt; from:
     *        &lt;rpc-reply&gt;
     *          &lt;environment-information&gt;
     *            &lt;environment-item&gt;
     *              &lt;name&gt;FPC 0 CPU&lt;/name&gt;
     *              &lt;temperature&gt;55&lt;/temperature&gt;
     *        the list should be:
     *        {"environment-information", "environment-item", "name~FPC 0 CPU", "temperature"}
     *
     * @return The trimmed text value of the target element,
     *         or {@code null} if the path is invalid, the list is empty/null,
     *         or if the element has no text content.
     */
    public String findValue(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }

        Element nextElement = ownerDoc.getDocumentElement();
        boolean nextElementFound;
        for (int k=0; k<list.size(); k++) {
            nextElementFound = false;
            String nextElementName = list.get(k);
            if (!nextElementName.contains("~")) {
                NodeList nextElementList =
                        nextElement != null
                                ? nextElement.getElementsByTagName(nextElementName)
                                : null;
                if (nextElementList == null || nextElementList.getLength() == 0) {
                    logger.fine("Element '" + nextElementName + "' not found in findValue()");
                    return null;
                }

                /* If the next‑to‑next (n2n) element is a filter based on
                 * text value, then do the required filtering.
                 */
                String n2nString = null;
                if (k<list.size()-1)
                    n2nString = list.get(k+1);
                if (n2nString != null && n2nString.contains("~")) {
                    /* Since the n2n element is a filter based on text
                     * value( decided by '~')
                     * we now traverse the entire NodeList to find the
                     * correct nextElement
                     * based on the text value of the filter.
                     */
                    String n2nText = n2nString.substring(n2nString.
                            indexOf("~") + 1);
                    String n2nElementName = n2nString.substring(0,
                            n2nString.indexOf("~"));
                    for (int i=0; i<nextElementList.getLength(); i++) {
                        nextElement = (Element)nextElementList.item(i);
                        NodeList nodes = nextElement.getElementsByTagName(n2nElementName);
                        if (nodes.getLength() == 0) continue;
                        Element n2nElement = (Element) nodes.item(0);
                        if (n2nElement == null || n2nElement.getFirstChild() == null) continue;
                        String text = trim(n2nElement.getFirstChild().getNodeValue());
                        if (text.equals(n2nText)) {
                            nextElementFound = true;
                            break;
                        }
                    }
                    if (!nextElementFound)
                        return null;
                } else {
                    nextElement = (Element)nextElementList.item(0);
                }
            }
        }
        if (nextElement == null) {
            return null;
        }
        Node first = nextElement.getFirstChild();
        if (first == null) {
            return null;
        }
        String value = first.getNodeValue();
        return (value != null) ? trim(value) : null;
    }

    /**
     * Get all the nodes at a given hierarchy, as list of org.w3c.dom.Node objects.
     * @param list
     *          The String based list of elements which determine the hierarchy.
     *          For example, for the below XML:
     *          &lt;rpc-reply&gt;
     *           &lt;environment-information&gt;
     *            &lt;environment-item&gt;
     *             &lt;name&gt;FPC 0 CPU&lt;/name&gt;
     *          To get the 'environment-item' Node with name 'FPC 0 CPU',
     *          , the list should be- {"environment-information",
     *          "environment-item",
     *          "name~FPC 0 CPU"}
     * @return The list containing Nodes as org.w3c.dom.Node objects.
     */
    public List<Node> findNodes(List<String> list) {
        Element nextElement = ownerDoc.getDocumentElement();
        boolean nextElementFound;
        List<Node> finalList = new ArrayList<>();
        for (int k=0; k<list.size(); k++) {
            nextElementFound = false;
            String nextElementName = list.get(k);
            if (!nextElementName.contains("~")) {
                NodeList nextElementList =
                        nextElement != null
                                ? nextElement.getElementsByTagName(nextElementName)
                                : null;
                if (nextElementList == null || nextElementList.getLength() == 0) {
                    logger.fine("Element '" + nextElementName + "' not found in findNodes()");
                    return null;
                }
                /* If the next to next(n2n) element is a filter based on
                 * text value,
                 * then do the required filtering.
                 * For example,
                 * ....
                 *     <physical-interface>
                 *         <name>ge-1/0/0</name>
                 *         <logical-interface>
                 *             ....
                 * In this case, the list passed to findValue function
                 * should contain (..,"physical-interface","name~ge-1/0/0",
                 * "logical-interface",..)
                 * This will fetch me the required element.
                 */
                String n2nString = null;
                if (k<list.size()-1)
                    n2nString = list.get(k+1);
                if (n2nString != null && n2nString.contains("~")) {
                    /* Since the n2n element is a filter based on text value
                     * ( decided by '~')
                     * we now traverse the entire NodeList to find the
                     * correct nextElement
                     * based on the text value of the filter.
                     */
                    String n2nText = n2nString.substring(n2nString.
                            indexOf("~") + 1);
                    String n2nElementName = n2nString.substring(0,
                            n2nString.indexOf("~"));
                    for (int i=0; i<nextElementList.getLength(); i++) {
                        nextElement = (Element)nextElementList.item(i);
                        Element n2nElement = (Element)nextElement.
                                getElementsByTagName(n2nElementName).item(0);
                        String text = n2nElement.getFirstChild().
                                getNodeValue();
                        text = trim(text);
                        if (text.equals(n2nText)) {
                            nextElementFound = true;
                            break;
                        }
                    }
                    if (!nextElementFound)
                        return null;
                } else {
                    nextElement = (Element)nextElementList.item(0);
                }
            }
        }
        if (nextElement == null) {
            return null;
        }
        String nodeName = nextElement.getNodeName();
        String listLastEntry = list.get(list.size()-1);
        if (listLastEntry.contains("~")) {
            finalList.add(nextElement);
            return finalList;

        } else {
            Element parent = (Element)nextElement.getParentNode();
            NodeList nodeList = parent.getElementsByTagName(nodeName);
            for (int i=0; i<nodeList.getLength(); i++) {
                finalList.add(nodeList.item(i));
            }
            return finalList;

        }
    }

    /**
     * Get the xml string of the XML object.
     * @return The XML data as a string
     */
    public String toString() {
        String str;
        try {
            TransformerFactory transFactory = TransformerFactory.newInstance();
            transFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");
            transFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET,"");
            Transformer transformer = transFactory.newTransformer();
            StringWriter buffer = new StringWriter();
            transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC,"yes");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty
                    ("{http://xml.apache.org/xslt}indent-amount", "4");
            Element root = ownerDoc.getDocumentElement();
            transformer.transform(new DOMSource(root), new StreamResult(buffer));
            str = buffer.toString();
        } catch (TransformerException ex) {
            str = "Could not transform: Transformer exception";
        }
        return str;
    }
}