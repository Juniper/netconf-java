package net.juniper.netconf;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;

public class XMLBuilderTest {

    @Test
    public void createNewConfig_twoElements_createsExpectedXML() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xml = builder.createNewConfig("system", "services");
        assertThat(xml.toString())
            .containsIgnoringWhitespaces(
                "<configuration><system><services/></system></configuration>");
    }

    @Test
    public void createNewConfig_oneElement_createsExpectedXML() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xml = builder.createNewConfig("system");
        assertThat(xml.toString()).containsIgnoringWhitespaces("<configuration><system/></configuration>");
    }

    @Test
    public void createNewConfig_threeElements_createsExpectedXML() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xml = builder.createNewConfig("system", "services", "ftp");
        assertThat(xml.toString()).containsIgnoringWhitespaces("<configuration><system><services><ftp/></services></system></configuration>");
    }

    @Test
    public void createNewConfig_list_createsExpectedXML() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xml = builder.createNewConfig(Arrays.asList("system", "services", "ftp"));
        assertThat(xml.toString()).containsIgnoringWhitespaces("<configuration><system><services><ftp/></services></system></configuration>");
    }

    @Test
    public void createNewConfig_emptyList_returnsNull() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xml = builder.createNewConfig(Collections.emptyList());
        assertThat(xml).isNull();
    }

    @Test
    public void createNewRPC_twoElements_createsExpectedXML() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xml = builder.createNewRPC("get-interface-information", "terse");
        String xmlStr = xml.toString();

        // Verify opening <rpc> tag has message‑id and correct namespace (order irrelevant)
        assertThat(xmlStr)
            .matches("(?s).*<rpc\\s+[^>]*message-id=\"\\d+\"[^>]*xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"[^>]*>.*");

        // Verify payload hierarchy, ignoring whitespace/line breaks
        assertThat(xmlStr)
            .containsIgnoringWhitespaces("<get-interface-information><terse/></get-interface-information>");
    }

    @Test
    public void createNewXML_fourElements_createsExpectedXML() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xml = builder.createNewXML("top", "middle", "sub", "leaf");
        assertThat(xml.toString()).containsIgnoringWhitespaces("<top><middle><sub><leaf/></sub></middle></top>");
    }

    @Test
    public void createNewXML_list_createsExpectedXML() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xml = builder.createNewXML(Arrays.asList("a", "b", "c"));
        assertThat(xml.toString()).containsIgnoringWhitespaces("<a><b><c/></b></a>");
    }

    @Test
    public void createNewXML_emptyList_returnsNull() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xml = builder.createNewXML(Collections.emptyList());
        assertThat(xml).isNull();
    }

    @Test
    public void createNewRPC_autoAddsMessageIdAndNamespace() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        XML xml = builder.createNewRPC("get", "running");
        String xmlStr = xml.toString();

        // Assert the rpc element has a message-id attribute with a numeric value
        assertThat(xmlStr)
            .contains("message-id=\"")
            .matches("(?s).*<rpc\\s+[^>]*message-id=\"\\d+\"[^>]*xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"[^>]*>.*");

        // Ensure hierarchy is intact
        assertThat(xmlStr)
            .containsIgnoringWhitespaces("<get><running/></get>");
    }
}
