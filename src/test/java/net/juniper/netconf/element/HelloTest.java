package net.juniper.netconf.element;

import org.junit.jupiter.api.Test;
import org.xmlunit.assertj.XmlAssert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.URISyntaxException;

public class HelloTest {

    // Samples taken from https://www.juniper.net/documentation/us/en/software/junos/netconf/topics/concept/netconf-session-rfc-compliant.html
    public static final String HELLO_WITHOUT_NAMESPACE = """
        \
        <hello xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
          <capabilities>
            <capability>urn:ietf:params:netconf:base:1.0</capability>
            <capability>urn:ietf:params:netconf:base:1.0#candidate</capability>
            <capability>urn:ietf:params:netconf:base:1.0#confirmed-commit</capability>
            <capability>urn:ietf:params:netconf:base:1.0#validate</capability>
            <capability>urn:ietf:params:netconf:base:1.0#url?protocol=http,ftp,file</capability>
            <capability>urn:ietf:params:netconf:base:1.1</capability>
          </capabilities>
          <session-id>27700</session-id>
        </hello>""";

    public static final String HELLO_WITH_NAMESPACE = """
        \
        <nc:hello xmlns:nc="urn:ietf:params:xml:ns:netconf:base:1.0">
          <nc:capabilities>
            <nc:capability>urn:ietf:params:netconf:base:1.0</nc:capability>
            <nc:capability>urn:ietf:params:netconf:base:1.0#candidate</nc:capability>
            <nc:capability>urn:ietf:params:netconf:base:1.0#confirmed-commit</nc:capability>
            <nc:capability>urn:ietf:params:netconf:base:1.0#validate</nc:capability>
            <nc:capability>urn:ietf:params:netconf:base:1.0#url?protocol=http,ftp,file</nc:capability>
            <nc:capability>urn:ietf:params:netconf:base:1.1</nc:capability>
          </nc:capabilities>
          <nc:session-id>27703</nc:session-id>
        </nc:hello>""";

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
                "urn:ietf:params:netconf:base:1.0#url?protocol=http,ftp,file",
                "urn:ietf:params:netconf:base:1.1");
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
                "urn:ietf:params:netconf:base:1.0#url?protocol=http,ftp,file",
                "urn:ietf:params:netconf:base:1.1");
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
            .capability("urn:ietf:params:netconf:base:1.1")

            .sessionId("27700")
            .build();

        XmlAssert.assertThat(hello.getXml())
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

        XmlAssert.assertThat(hello.getXml())
            .and(HELLO_WITH_NAMESPACE)
            .ignoreWhitespace()
            .areIdentical();
    }

    @Test
    public void willHandleEmptyCapabilities() {
        Hello hello = Hello.builder()
            .sessionId("99999")
            .build();

        // Base capability 1.1 is auto‑injected by the builder
        assertThat(hello.getCapabilities())
            .containsExactly("urn:ietf:params:netconf:base:1.1");
        assertThat(hello.getSessionId()).isEqualTo("99999");
    }

    @Test
    public void willHandleNullSessionId() {
        Hello hello = Hello.builder()
            .capability("urn:ietf:params:netconf:base:1.0")
            .build();

        assertThat(hello.getSessionId()).isNull();
    }

    @Test
    public void willThrowOnMalformedXml() {
        String badXml = "<hello><capabilities><capability>urn</capabilities>";
        assertThatThrownBy(() -> Hello.from(badXml))
            .isInstanceOf(Exception.class);
    }

    @Test
    public void willRoundTripNamespaceXml() throws Exception {
        Hello original = Hello.from(HELLO_WITH_NAMESPACE);
        Hello roundTripped = Hello.from(original.getXml());
        assertThat(roundTripped).isEqualTo(original);
    }

    @Test
    public void differentObjectsNotEqual() {
        Hello h1 = Hello.builder().sessionId("1").build();
        Hello h2 = Hello.builder().sessionId("2").build();
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    public void willHandleNullCapabilityCheck() {
        Hello hello = Hello.builder().build();
        assertThat(hello.hasCapability(null)).isFalse();
    }

    /**
     * RFC 6241 §3.1 – capability names MUST be valid URIs.
     * Supplying an invalid capability string to the builder should
     * throw an IllegalArgumentException.
     */
    @Test
    public void willRejectNonUriCapability() {
        String bogus = "not a uri";
        assertThatThrownBy(() -> Hello.builder()
                                      .sessionId("42")
                                      .capability(bogus)
                                      .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Capability MUST be a valid URI per RFC 3986:");
    }

    @Test
    void builderAddsBase11CapabilityByDefault() {
        Hello hello = Hello.builder().sessionId("123").build();
        assertThat(hello.getCapabilities())
            .contains("urn:ietf:params:netconf:base:1.1");
    }

    @Test
    public void willRejectDtdInXml() {
        String withDtd = """
        <!DOCTYPE hello [
           <!ELEMENT hello ANY >
        ]>
        <hello xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
          <session-id>1</session-id>
        </hello>
        """;

        assertThatThrownBy(() -> Hello.from(withDtd))
            .isInstanceOf(Exception.class)
            .hasMessageContaining("DOCTYPE");
    }

    /**
     * Ensures every capability returned by Hello#getCapabilities() parses as a URI.
     */
    @Test
    public void capabilitiesAreUris() throws URISyntaxException {
        Hello hello = Hello.builder()
            .sessionId("99")
            .capability("urn:ietf:params:netconf:base:1.0")
            .capability("urn:ietf:params:netconf:capability:writable-running:1.0")
            .build();

        for (String cap : hello.getCapabilities()) {
            new URI(cap);   // throws URISyntaxException if invalid
        }
    }
}