/*
 Copyright (c) 2013 Juniper Networks, Inc.
 All Rights Reserved

 Use is subject to license terms.

*/

package net.juniper.netconf;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.xml.parsers.*;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * An <code>XMLBuilder</code> is used to create an XML object.This is useful to
 * create XML RPC's and configurations.
 * <p>
 * As an example, one
 * <ol>
 * <li>creates a {@link #XMLBuilder() XMLBuilder} object.</li>
 * <li>create an RPC as an XML object.</li>
 * <li>Call the executeRPC(XML) method on Device</li>
 * </ol>
 */
public class XMLBuilder {

    private DOMImplementation impl;
    private DocumentBuilder builder;
    /** Monotonic counter used to populate the mandatory {@code message-id} attribute on <rpc> elements. */
    private static final AtomicLong MSG_ID_GEN = new AtomicLong(1);
    /**
     * Creates an empty &lt;rpc&gt; root element with the NETCONF base namespace
     * and an auto‑incremented {@code message-id} attribute as required by
     * RFC&nbsp;6241 §4.1.
     */
    private Document createRpcRoot() {
        // Create <rpc> in the NETCONF base namespace so the xmlns attribute is correct
        Document doc = impl.createDocument(
                "urn:ietf:params:xml:ns:netconf:base:1.0",
                "rpc",
                null);
        Element root = doc.getDocumentElement();
        root.setAttribute("message-id", String.valueOf(MSG_ID_GEN.getAndIncrement()));
        return doc;
    }

    /**
     * Prepares a new &lt;code&gt;&lt;XMLBuilder&lt;/code&gt; object.
     * @throws ParserConfigurationException if there are issues parsing the configuration.
     */
    public XMLBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance() ;
        builder = factory.newDocumentBuilder() ;
        impl = builder.getDOMImplementation();
    }

    /**
     * Create a new configuration(single-level hierarchy) as an XML object.
     * <p>Convenience method for creating a single‑level configuration.</p>
     * @param elementLevelOne
     *          The element at the top-most hierarchy. For example, to create a
     *          configuration,
     *          "&lt;configuration&gt;&lt;system/&gt;&lt;/configuration&gt;" the
     *          String to be passed is "system".
     * @return XML object.
     */
    public XML createNewConfig(String elementLevelOne) {
        Document doc = impl.createDocument(null, "configuration", null);
        Element rootElement = doc.getDocumentElement();
        Element elementOne = doc.createElement(elementLevelOne);
        rootElement.appendChild(elementOne);
        return new XML(elementOne);
    }

    /**
     * Create a new configuration(two-level hierarchy) as an XML object.
     * <p>Creates a configuration with two nested hierarchy levels.</p>
     * @param elementLevelOne
     *          The element at the top-most hierarchy.
     * @param elementLevelTwo
     *          The element at level-2 hierarchy.
     * @return XML object.
     */
    public XML createNewConfig(String elementLevelOne, String elementLevelTwo) {
        Document doc = impl.createDocument(null, "configuration", null);
        Element rootElement = doc.getDocumentElement();
        Element elementOne = doc.createElement(elementLevelOne);
        Element elementTwo = doc.createElement(elementLevelTwo);
        elementOne.appendChild(elementTwo);
        rootElement.appendChild(elementOne);
        return new XML(elementTwo);
    }

    /**
     * Create a new configuration(three-level hierarchy) as an XML object.
     * <p>Creates a configuration with three nested hierarchy levels.</p>
     * @param elementLevelOne
     *          The element at the top-most hierarchy.
     * @param elementLevelTwo
     *          The element at level-2 hierarchy.
     * @param elementLevelThree
     *          The element at level-3 hierarchy.
     * @return XML object.
     */
    public XML createNewConfig(String elementLevelOne, String elementLevelTwo,
            String elementLevelThree) {
        Document doc = impl.createDocument(null, "configuration", null);
        Element rootElement = doc.getDocumentElement();
        Element elementOne = doc.createElement(elementLevelOne);
        Element elementTwo = doc.createElement(elementLevelTwo);
        Element elementThree = doc.createElement(elementLevelThree);
        elementTwo.appendChild(elementThree);
        elementOne.appendChild(elementTwo);
        rootElement.appendChild(elementOne);
        return new XML(elementThree);
    }

    /**
     * Create a new configuration(four-level hierarchy) as an XML object.
     * <p>Creates a configuration with four nested hierarchy levels.</p>
     * @param elementLevelOne
     *          The element at the top-most hierarchy.
     * @param elementLevelTwo
     *          The element at level-2 hierarchy.
     * @param elementLevelThree
     *          The element at level-3 hierarchy.
     * @param elementLevelFour
     *          The element at level-4 hierarchy.
     * @return XML object.
     */
    public XML createNewConfig(String elementLevelOne, String elementLevelTwo,
            String elementLevelThree, String elementLevelFour) {
        Document doc = impl.createDocument(null, "configuration", null);
        Element rootElement = doc.getDocumentElement();
        Element elementOne = doc.createElement(elementLevelOne);
        Element elementTwo = doc.createElement(elementLevelTwo);
        Element elementThree = doc.createElement(elementLevelThree);
        Element elementFour = doc.createElement(elementLevelFour);
        elementThree.appendChild(elementFour);
        elementTwo.appendChild(elementThree);
        elementOne.appendChild(elementTwo);
        rootElement.appendChild(elementOne);
        return new XML(elementFour);
    }

    /**
     * Create a new configuration as an XML object.
     * <p>Creates a configuration with a variable-depth hierarchy defined by the supplied list.</p>
     * @param elementList
     *          The list of elements to be included in the XML. For example,
     *          to create a configuration,
     *          "&lt;configuration&gt;&lt;system&gt;&lt;services&gt;&lt;ftp/&gt;
     *          &lt;/services&gt;&lt;/system&gt;&lt;/configuration&gt;" the
     *          list should be {"system","services","ftp"}
     * @return XML object.
     */
    public XML createNewConfig(List<String> elementList) {
        if (elementList.isEmpty())
            return null;
        Document doc = impl.createDocument(null, "configuration", null);
        Element rootElement = doc.getDocumentElement();
        Element elementLevelLast = doc.createElement(elementList.
                get(elementList.size()-1));
        Element last = elementLevelLast;
        for (int i = elementList.size()-1; i>0; i--) {
            Element secondLast = doc.createElement(elementList.get(i-1));
            secondLast.appendChild(last);
            last = secondLast;
        }
        rootElement.appendChild(last);
        return new XML(elementLevelLast);
    }

    /**
     * <p>Convenience method for a single‑level RPC.  The builder automatically sets the mandatory {@code message-id} attribute and the NETCONF base namespace.</p>
     * @param elementLevelOne
     *          The element at the top-most hierarchy.
     * @return XML object.
     */
    public XML createNewRPC(String elementLevelOne) {
        Document doc = createRpcRoot();
        Element rootElement = doc.getDocumentElement();
        Element elementOne = doc.createElement(elementLevelOne);
        rootElement.appendChild(elementOne);
        return new XML(elementOne);
    }

    /**
     * Create a new RPC(two-level hierarchy) as an XML object.
     * <p>Creates an RPC with two nested hierarchy levels.</p>
     * The {@code message-id} attribute and base namespace are filled in automatically.
     * @param elementLevelOne
     *          The element at the top-most hierarchy.
     * @param elementLevelTwo
     *          The element at level-2 hierarchy.
     * @return XML object.
     */
    public XML createNewRPC(String elementLevelOne, String elementLevelTwo) {
        Document doc = createRpcRoot();
        Element rootElement = doc.getDocumentElement();
        Element elementOne = doc.createElement(elementLevelOne);
        Element elementTwo = doc.createElement(elementLevelTwo);
        elementOne.appendChild(elementTwo);
        rootElement.appendChild(elementOne);
        return new XML(elementTwo);
    }

    /**
     * Create a new RPC(three-level hierarchy) as an XML object.
     * <p>Creates an RPC with three nested hierarchy levels.</p>
     * The {@code message-id} attribute and base namespace are filled in automatically.
     * @param elementLevelOne
     *          The element at the top-most hierarchy.
     * @param elementLevelTwo
     *          The element at level-2 hierarchy.
     * @param elementLevelThree
     *          The element at level-3 hierarchy.
     * @return XML object.
     */
    public XML createNewRPC(String elementLevelOne, String elementLevelTwo,
            String elementLevelThree) {
        Document doc = createRpcRoot();
        Element rootElement = doc.getDocumentElement();
        Element elementOne = doc.createElement(elementLevelOne);
        Element elementTwo = doc.createElement(elementLevelTwo);
        Element elementThree = doc.createElement(elementLevelThree);
        elementTwo.appendChild(elementThree);
        elementOne.appendChild(elementTwo);
        rootElement.appendChild(elementOne);
        return new XML(elementThree);
    }

    /**
     * Create a new RPC(four-level hierarchy) as an XML object.
     * <p>Creates an RPC with four nested hierarchy levels.</p>
     * The {@code message-id} attribute and base namespace are filled in automatically.
     * @param elementLevelOne
     *          The element at the top-most hierarchy.
     * @param elementLevelTwo
     *          The element at level-2 hierarchy.
     * @param elementLevelThree
     *          The element at level-3 hierarchy.
     * @param elementLevelFour
     *          The element at level-4 hierarchy.
     * @return XML object.
     */
    public XML createNewRPC(String elementLevelOne, String elementLevelTwo,
            String elementLevelThree, String elementLevelFour) {
        Document doc = createRpcRoot();
        Element rootElement = doc.getDocumentElement();
        Element elementOne = doc.createElement(elementLevelOne);
        Element elementTwo = doc.createElement(elementLevelTwo);
        Element elementThree = doc.createElement(elementLevelThree);
        Element elementFour = doc.createElement(elementLevelFour);
        elementThree.appendChild(elementFour);
        elementTwo.appendChild(elementThree);
        elementOne.appendChild(elementTwo);
        rootElement.appendChild(elementOne);
        return new XML(elementFour);
    }

    /**
     * Create a new RPC as an XML object.
     * <p>Creates an RPC with hierarchy defined by the supplied element list.</p>
     * The {@code message-id} attribute and base namespace are filled in automatically.
     * @param elementList
     *          The list of elements to be included in the XML. For example, the
     *          list {"get-interface-information","terse"} will create the RPC-
     *          "&lt;rpc&gt;&lt;get-interface-information&gt;&lt;terse/&gt;
     *          &lt;/get-interface-information&gt;&lt;/rpc&gt;"
     * @return XML object.
     */
    public XML createNewRPC(List<String> elementList) {
        if (elementList.isEmpty())
            return null;
        Document doc = createRpcRoot();
        Element rootElement = doc.getDocumentElement();
        Element elementLevelLast = doc.createElement(elementList.
                get(elementList.size()-1));
        Element last = elementLevelLast;
        for (int i = elementList.size()-1; i>0; i--) {
            Element secondLast = doc.createElement(elementList.get(i-1));
            secondLast.appendChild(last);
            last = secondLast;
        }
        rootElement.appendChild(last);
        return new XML(elementLevelLast);
    }

    /**
     * Create a new xml(one-level hierarchy) as an XML object.
     * <p>Convenience method for a single‑level XML document.</p>
     * @param elementLevelOne
     *          The element at the top-most hierarchy.
     * @return XML object.
     */
    public XML createNewXML(String elementLevelOne) {
        Document doc = impl.createDocument(null, elementLevelOne, null);
        Element rootElement = doc.getDocumentElement();
        return new XML(rootElement);
    }

    /**
     * Create a new xml(two-level hierarchy) as an XML object.
     * <p>Creates an XML document with two nested hierarchy levels.</p>
     * @param elementLevelOne
     *          The element at the top-most hierarchy.
     * @param elementLevelTwo
     *          The element at level-2 hierarchy.
     * @return XML object.
     */
    public XML createNewXML(String elementLevelOne, String elementLevelTwo) {
        Document doc = impl.createDocument(null, elementLevelOne, null);
        Element rootElement = doc.getDocumentElement();
        Element elementTwo = doc.createElement(elementLevelTwo);
        rootElement.appendChild(elementTwo);
        return new XML(elementTwo);
    }

    /**
     * Create a new xml(three-level hierarchy) as an XML object.
     * <p>Creates an XML document with three nested hierarchy levels.</p>
     * @param elementLevelOne
     *          The element at the top-most hierarchy.
     * @param elementLevelTwo
     *          The element at level-2 hierarchy.
     * @param elementLevelThree
     *          The element at level-3 hierarchy.
     * @return XML object.
     */
    public XML createNewXML(String elementLevelOne, String elementLevelTwo,
            String elementLevelThree) {
        Document doc = impl.createDocument(null, elementLevelOne, null);
        Element rootElement = doc.getDocumentElement();
        Element elementTwo = doc.createElement(elementLevelTwo);
        Element elementThree = doc.createElement(elementLevelThree);
        elementTwo.appendChild(elementThree);
        rootElement.appendChild(elementTwo);
        return new XML(elementThree);
    }

    /**
     * Create a new xml(four-level hierarchy) as an XML object.
     * <p>Creates an XML document with four nested hierarchy levels.</p>
     * @param elementLevelOne
     *          The element at the top-most hierarchy.
     * @param elementLevelTwo
     *          The element at level-2 hierarchy.
     * @param elementLevelThree
     *          The element at level-3 hierarchy.
     * @param elementLevelFour
     *          The element at level-4 hierarchy.
     * @return XML object.
     */
    public XML createNewXML(String elementLevelOne, String elementLevelTwo,
            String elementLevelThree, String elementLevelFour) {
        Document doc = impl.createDocument(null, elementLevelOne, null);
        Element rootElement = doc.getDocumentElement();
        Element elementTwo = doc.createElement(elementLevelTwo);
        Element elementThree = doc.createElement(elementLevelThree);
        Element elementFour = doc.createElement(elementLevelFour);
        elementThree.appendChild(elementFour);
        elementTwo.appendChild(elementThree);
        rootElement.appendChild(elementTwo);
        return new XML(elementFour);
    }

    /**
     * Create a new xml as an XML object.
     * <p>Creates an XML document with hierarchy defined by the supplied element list.</p>
     * @param elementList
     *          The list of elements to be included in the XML. For example, the
     *          list {"firstElement","secondElement"} will create the xml-
     *          "&lt;firstElement&gt;&lt;secondElement/&gt;&lt;/firstElement&gt;
     * @return XML object.
     */
    public XML createNewXML(List<String> elementList) {
        if (elementList.isEmpty())
            return null;
        String elementLevelOne = elementList.get(0);
        Document doc = impl.createDocument(null, elementLevelOne, null);
        Element rootElement = doc.getDocumentElement();
        if (elementList.size() == 1)
            return new XML(rootElement);
        Element elementLevelLast = doc.createElement(elementList.
                get(elementList.size()-1));
        Element last = elementLevelLast;
        for (int i = elementList.size()-1; i>1; i--) {
            Element secondLast = doc.createElement(elementList.get(i-1));
            secondLast.appendChild(last);
            last = secondLast;
        }
        rootElement.appendChild(last);
        return new XML(elementLevelLast);
    }
}
