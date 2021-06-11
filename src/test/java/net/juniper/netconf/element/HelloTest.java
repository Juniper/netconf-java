package net.juniper.netconf.element;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.xmlunit.assertj.XmlAssert;

public class HelloTest {

    // Samples taken from https://www.juniper.net/documentation/us/en/software/junos/netconf/topics/concept/netconf-session-rfc-compliant.html
    public static final String HELLO_WITHOUT_NAMESPACE = ""
            + "<hello xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "  <capabilities>\n"
            + "    <capability>urn:ietf:params:netconf:base:1.0</capability>\n"
            + "    <capability>urn:ietf:params:netconf:base:1.0#candidate</capability>\n"
            + "    <capability>urn:ietf:params:netconf:base:1.0#confirmed-commit</capability>\n"
            + "    <capability>urn:ietf:params:netconf:base:1.0#validate</capability>\n"
            + "    <capability>urn:ietf:params:netconf:base:1.0#url?protocol=http,ftp,file</capability>\n"
            + "  </capabilities>\n"
            + "  <session-id>27700</session-id>\n"
            + "</hello>";

    public static final String HELLO_WITH_NAMESPACE = "" +
            "<nc:hello xmlns:nc=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "  <nc:capabilities>\n"
            + "    <nc:capability>urn:ietf:params:netconf:base:1.0</nc:capability>\n"
            + "    <nc:capability>urn:ietf:params:netconf:base:1.0#candidate</nc:capability>\n"
            + "    <nc:capability>urn:ietf:params:netconf:base:1.0#confirmed-commit</nc:capability>\n"
            + "    <nc:capability>urn:ietf:params:netconf:base:1.0#validate</nc:capability>\n"
            + "    <nc:capability>urn:ietf:params:netconf:base:1.0#url?protocol=http,ftp,file</nc:capability>\n"
            + "  </nc:capabilities>\n"
            + "  <nc:session-id>27703</nc:session-id>\n"
            + "</nc:hello>";

    @Test
    public void willCreateAnObjectFromPacketWithoutNamespace() throws Exception {

        final Hello hello = Hello.from(HELLO_WITHOUT_NAMESPACE);

        assertThat(hello.getSessionId())
                .isEqualTo("27700");
        assertThat(hello.getCapabilities())
                .containsExactly(
                        "urn:ietf:params:netconf:base:1.0",
                        "urn:ietf:params:netconf:base:1.0#candidate",
                        "urn:ietf:params:netconf:base:1.0#confirmed-commit",
                        "urn:ietf:params:netconf:base:1.0#validate",
                        "urn:ietf:params:netconf:base:1.0#url?protocol=http,ftp,file");
        assertThat(hello.hasCapability("urn:ietf:params:netconf:base:1.0#candidate"))
                .isTrue();
    }

    @Test
    public void willCreateAnObjectFromPacketWithNamespace() throws Exception {

        final Hello hello = Hello.from(HELLO_WITH_NAMESPACE);

        assertThat(hello.getSessionId())
                .isEqualTo("27703");
        assertThat(hello.getCapabilities())
                .containsExactly(
                        "urn:ietf:params:netconf:base:1.0",
                        "urn:ietf:params:netconf:base:1.0#candidate",
                        "urn:ietf:params:netconf:base:1.0#confirmed-commit",
                        "urn:ietf:params:netconf:base:1.0#validate",
                        "urn:ietf:params:netconf:base:1.0#url?protocol=http,ftp,file");
        assertThat(hello.hasCapability("urn:ietf:params:netconf:base:1.0#candidate"))
                .isTrue();
    }

    @Test
    public void willCreateXmlFromAnObject() {

        final Hello hello = Hello.builder()
                .capability("urn:ietf:params:netconf:base:1.0")
                .capability("urn:ietf:params:netconf:base:1.0#candidate")
                .capability("urn:ietf:params:netconf:base:1.0#confirmed-commit")
                .capability("urn:ietf:params:netconf:base:1.0#validate")
                .capability("urn:ietf:params:netconf:base:1.0#url?protocol=http,ftp,file")
                .sessionId("27700")
                .build();

        XmlAssert.assertThat(hello.toXML())
                .and(HELLO_WITHOUT_NAMESPACE)
                .ignoreWhitespace()
                .areIdentical();
    }

    @Test
    public void willCreateXmlWithNamespaceFromAnObject() {

        final Hello hello = Hello.builder()
                .namespacePrefix("nc")
                .capability("urn:ietf:params:netconf:base:1.0")
                .capability("urn:ietf:params:netconf:base:1.0#candidate")
                .capability("urn:ietf:params:netconf:base:1.0#confirmed-commit")
                .capability("urn:ietf:params:netconf:base:1.0#validate")
                .capability("urn:ietf:params:netconf:base:1.0#url?protocol=http,ftp,file")
                .sessionId("27703")
                .build();

        XmlAssert.assertThat(hello.toXML())
                .and(HELLO_WITH_NAMESPACE)
                .ignoreWhitespace()
                .areIdentical();
    }
}