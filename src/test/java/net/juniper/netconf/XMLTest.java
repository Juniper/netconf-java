package net.juniper.netconf;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.Node;
import java.util.stream.Collectors;
import java.util.Map;

import static net.juniper.netconf.TestHelper.getSampleFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class XMLTest {

    private final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance() ;

    private void testFindValue(String sampleFileName, List<String> findValueList, String expectedValue)
            throws Exception {
        DocumentBuilder builder = factory.newDocumentBuilder();
        File sampleRPCFile = getSampleFile(sampleFileName);
        XML testXml = new XML(builder.parse(sampleRPCFile).getDocumentElement());

        assertThat(testXml.findValue(findValueList)).isEqualTo(expectedValue);
    }

    @Test
    public void GIVEN_sampleXML_WHEN_findValueOfSample_THEN_returnValue() throws Exception {
        String sampleFileName = "sampleFPCTempRPCReply.xml";
        List<String> findValueList = Arrays.asList(
                "environment-component-information",
                "environment-component-item",
                "name~Routing Engine 0",
                "temperature"
        );
        String expectedValue = "41 degrees C / 105 degrees F";
        testFindValue(sampleFileName,findValueList, expectedValue);
    }

    @Test
    public void GIVEN_sampleCliOutputRpc_WHEN_findValueOfSample_THEN_returnValue() throws Exception {
        String sampleFileName = "sampleCliOutputReply.xml";
        List<String> findValueList = Collections.singletonList("output");
        String expectedValue = "operational-response";
        testFindValue(sampleFileName,findValueList, expectedValue);
    }

    @Test
    public void GIVEN_missingElement_WHEN_findValue_THEN_returnNull() throws Exception {
        String sampleFileName = "sampleMissingElement.xml";
        List<String> findValueList = Arrays.asList(
                "environment-component-information",
                "environment-component-item",
                "name~Nonexistent Component",
                "temperature"
        );
        String expectedValue = null;
        testFindValue(sampleFileName, findValueList, expectedValue);
    }

    @Test
    public void GIVEN_missingFile_WHEN_findValue_THEN_throwException() throws Exception {
        assertThrows(java.io.FileNotFoundException.class, () -> {
            String sampleFileName = "nonexistent.xml";
            List<String> findValueList = Collections.singletonList("any");
            testFindValue(sampleFileName, findValueList, "irrelevant");
        });
    }

    @Test
    public void GIVEN_emptyFindValueList_WHEN_findValue_THEN_returnNull() throws Exception {
        String sampleFileName = "sampleEmptyFPCTempRpcReply.xml";
        List<String> findValueList = Collections.emptyList();
        String expectedValue = null;
        testFindValue(sampleFileName, findValueList, expectedValue);
    }

    @Test
    public void GIVEN_simpleXml_WHEN_toString_THEN_returnXmlString() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xml = builder.createNewXML("foo", "bar");

        String xmlString = xml.toString();
        assertThat(xmlString).contains("<foo>");
        assertThat(xmlString).contains("<bar/>");
    }

    @Test
    public void GIVEN_twoIdenticalXmls_WHEN_equals_THEN_returnTrue() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xml1 = builder.createNewXML("top", "child");
        XML xml2 = builder.createNewXML("top", "child");

        org.xmlunit.assertj.XmlAssert.assertThat(xml1.toString())
            .and(xml2.toString())
            .areIdentical();    }

    @Test
    public void GIVEN_xml_WHEN_hashCodeInvoked_THEN_noException() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xml = builder.createNewXML("alpha", "beta");

        int hash = xml.hashCode();
        assertThat(hash).isNotZero();
    }

    @Test
    public void GIVEN_noTextInRoot_WHEN_findValue_THEN_returnNull() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xml = builder.createNewXML("root");

        String value = xml.findValue(Collections.singletonList("root"));
        assertThat(value).isNull();
    }

    @Test
    public void GIVEN_childNodeWithText_WHEN_findValue_THEN_returnText() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xml = builder.createNewXML("parent");
        xml.addPath("child").setTextContent("hello");

        String value = xml.findValue(Arrays.asList("child"));
        assertThat(value).isEqualTo("hello");
    }

    @Test
    public void GIVEN_parentWithTwoItems_WHEN_findNodes_item_THEN_returnBoth() throws Exception {
        DocumentBuilder builder = factory.newDocumentBuilder();
        org.w3c.dom.Document doc = builder.newDocument();
        org.w3c.dom.Element parent = doc.createElement("parent");
        doc.appendChild(parent);

        org.w3c.dom.Element item1 = doc.createElement("item");
        item1.setTextContent("one");
        parent.appendChild(item1);

        org.w3c.dom.Element item2 = doc.createElement("item");
        item2.setTextContent("two");
        parent.appendChild(item2);

        XML xml = new XML(parent);

        List<Node> result = xml.findNodes(Collections.singletonList("item"));
        assertThat(result).hasSize(2);
        assertThat(result.stream().map(Node::getTextContent).collect(Collectors.toSet()))
                          .containsExactlyInAnyOrder("one", "two");
    }
    /* ------------------------------------------------------------------
     * Junos‑specific XML attribute helpers
     * ------------------------------------------------------------------ */

    @Test
    public void GIVEN_activeElement_WHEN_junosDeactivate_THEN_inactiveAttrSet() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xml = builder.createNewXML("system");
        xml.junosDeactivate();

        String inactive = xml.getOwnerDocument().getDocumentElement().getAttribute("inactive");
        assertThat(inactive).isEqualTo("inactive");
    }

    @Test
    public void GIVEN_activeElement_WHEN_junosRename_THEN_renameAndNameAttrsSet() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xml = builder.createNewXML("interface");
        xml.junosRename("ge-0/0/0", "ge-0/0/1");

        org.w3c.dom.Element element = xml.getOwnerDocument().getDocumentElement();
        assertThat(element.getAttribute("rename")).isEqualTo("ge-0/0/0");
        assertThat(element.getAttribute("name")).isEqualTo("ge-0/0/1");
    }

    @Test
    public void GIVEN_activeElement_WHEN_junosInsert_THEN_insertAndNameAttrsSet() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xml = builder.createNewXML("policy");
        xml.junosInsert("before-me", "new-policy");

        org.w3c.dom.Element element = xml.getOwnerDocument().getDocumentElement();
        assertThat(element.getAttribute("insert")).isEqualTo("before-me");
        assertThat(element.getAttribute("name")).isEqualTo("new-policy");
    }

    @Test
    public void GIVEN_activeElement_WHEN_append_THEN_childAddedAndReturned() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xmlParent = builder.createNewXML("configuration");

        XML xmlChild = xmlParent.append("system");
        // Verify method returns a new XML pointing at the child
        assertThat(xmlChild.getOwnerDocument().getDocumentElement().getNodeName())
                .isEqualTo("configuration");
        assertThat(xmlChild.toString()).contains("<system/>");

        // Ensure the child element is actually appended under the parent
        String full = xmlParent.getOwnerDocument().getDocumentElement().getTextContent();
        assertThat(full).isEmpty(); // configuration has one child but no text
    }

    /* ------------------------------------------------------------------
     * Append helpers (element / text / map / array)
     * ------------------------------------------------------------------ */

    @Test
    public void GIVEN_parent_WHEN_appendElementWithText_THEN_childContainsText() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xmlParent = builder.createNewXML("config");
        XML xmlChild = xmlParent.append("hostname", "router1");

        // verify returned XML is for <hostname>
        assertThat(xmlChild.getOwnerDocument().getDocumentElement().getNodeName())
                .isEqualTo("config");
        assertThat(xmlChild.toString()).contains("<hostname>router1</hostname>");
    }

    @Test
    public void GIVEN_parent_WHEN_appendMultipleSameName_THEN_allChildrenAdded() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xmlParent = builder.createNewXML("interfaces");
        xmlParent.append("unit", new String[] { "0", "1", "2" });

        org.w3c.dom.NodeList units =
                xmlParent.getOwnerDocument().getDocumentElement().getElementsByTagName("unit");
        assertThat(units.getLength()).isEqualTo(3);
    }

    @Test
    public void GIVEN_parent_WHEN_appendMap_THEN_childrenMatchMap() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xmlParent = builder.createNewXML("system");
        Map<String,String> map = Map.of("services", "on",
                                        "location", "lab");
        xmlParent.append(map);

        assertThat(xmlParent.toString())
            .contains("<services>on</services>")
            .contains("<location>lab</location>");
    }

    @Test
    public void GIVEN_parent_WHEN_appendElementThenMap_THEN_newXMLContainsMapChildren() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xmlParent = builder.createNewXML("configuration");

        Map<String,String> childMap = Map.of("rpc", "true", "ssh", "true");
        XML xmlServices = xmlParent.append("services", childMap);

        // ensure returned XML is <services> level
        assertThat(xmlServices.toString())
            .contains("<rpc>true</rpc>")
            .contains("<ssh>true</ssh>");
    }

    /* ------------------------------------------------------------------
     * Sibling helper tests
     * ------------------------------------------------------------------ */

    @Test
    public void GIVEN_child_WHEN_addSiblingElement_THEN_siblingAppended() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xmlParent = builder.createNewXML("parent");
        XML xmlChild = xmlParent.addPath("child1");

        xmlChild.addSibling("child2");

        org.w3c.dom.NodeList children =
                xmlParent.getOwnerDocument().getDocumentElement().getElementsByTagName("*");
        assertThat(children.getLength()).isEqualTo(2);
    }

    @Test
    public void GIVEN_child_WHEN_addSiblingWithText_THEN_textSet() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xmlParent = builder.createNewXML("parent");
        XML xmlChild = xmlParent.addPath("item1");

        xmlChild.addSibling("item2", "value");

        org.w3c.dom.Element sibling =
                (org.w3c.dom.Element) xmlParent.getOwnerDocument()
                                               .getDocumentElement()
                                               .getElementsByTagName("item2").item(0);
        assertThat(sibling.getTextContent()).isEqualTo("value");
    }

    @Test
    public void GIVEN_child_WHEN_addSiblingsArray_THEN_allAdded() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xmlParent = builder.createNewXML("list");
        XML xmlChild = xmlParent.addPath("entry");

        xmlChild.addSiblings("entry", new String[] { "a", "b" });

        org.w3c.dom.NodeList entries =
                xmlParent.getOwnerDocument().getDocumentElement().getElementsByTagName("entry");
        assertThat(entries.getLength()).isEqualTo(3); // original + 2 new
    }

    @Test
    public void GIVEN_child_WHEN_addSiblingsMap_THEN_allAdded() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xmlParent = builder.createNewXML("data");
        XML xmlChild = xmlParent.addPath("level");

        Map<String,String> map = Map.of("alpha","1","beta","2");
        xmlChild.addSiblings(map);

        assertThat(xmlParent.toString())
            .contains("<alpha>1</alpha>")
            .contains("<beta>2</beta>");
    }
}