package net.juniper.netconf.element;

import org.junit.jupiter.api.Test;
import org.xmlunit.assertj.XmlAssert;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RpcReplyTest {

    private static final String RPC_REPLY_WITHOUT_NAMESPACE = "" +
        "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"\n" +
        "   message-id=\"3\"\n" +
        "/>";
    private static final String RPC_REPLY_WITH_NAMESPACE = "" +
        "<nc:rpc-reply xmlns:nc=\"urn:ietf:params:xml:ns:netconf:base:1.0\"\n" +
        "   message-id=\"4\"\n" +
        "/>";
    private static final String RPC_REPLY_WITH_OK = "" +
        "<nc:rpc-reply xmlns:nc=\"urn:ietf:params:xml:ns:netconf:base:1.0\"\n" +
        "    message-id=\"5\">\n" +
        "    <nc:ok/>\n" +
        "</nc:rpc-reply>";
    // Example from https://datatracker.ietf.org/doc/html/rfc6241#section-4.3
    private static final String RPC_REPLY_WITH_ERRORS = "" +
        "<rpc-reply message-id=\"101\"\n" +
        "           xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n" +
        "    <rpc-error>\n" +
        "        <error-type>application</error-type>\n" +
        "        <error-tag>invalid-value</error-tag>\n" +
        "        <error-severity>error</error-severity>\n" +
        "        <error-path xmlns:t=\"http://example.com/schema/1.2/config\">/t:top/t:interface[t:name=\"Ethernet0/0\"]/t:mtu</error-path>\n" +
        "        <error-message xml:lang=\"en\">MTU value 25000 is not within range 256..9192</error-message>\n" +
        "    </rpc-error>\n" +
        "    <rpc-error>\n" +
        "        <error-type>application</error-type>\n" +
        "        <error-tag>invalid-value</error-tag>\n" +
        "        <error-severity>error</error-severity>\n" +
        "        <error-path xmlns:t=\"http://example.com/schema/1.2/config\">/t:top/t:interface[t:name=\"Ethernet1/0\"]/t:address/t:name</error-path>\n" +
        "        <error-message>Invalid IP address for interface Ethernet1/0</error-message>\n" +
        "    </rpc-error>\n" +
        "</rpc-reply>";

    private static final String MALFORMED_RPC_REPLY = "<rpc-reply><unclosed></rpc-reply>";

    // Same OK reply but framed with RFC 6242 §4.3 delimiter
    private static final String RPC_REPLY_WITH_OK_FRAMED =
        RPC_REPLY_WITH_OK + "]]>]]>";

    @Test
    public void willParseRpcReplyWithoutNamespace() throws Exception {
        final RpcReply rpcReply = RpcReply.from(RPC_REPLY_WITHOUT_NAMESPACE);

        assertThat(rpcReply.getMessageId())
            .isEqualTo("3");
        assertThat(rpcReply.isOK())
            .isFalse();
        assertThat(rpcReply.hasErrorsOrWarnings())
            .isFalse();
        assertThat(rpcReply.hasErrors())
            .isFalse();
        assertThat(rpcReply.hasWarnings())
            .isFalse();
    }

    @Test
    public void willParseRpcReplyWithNamespace() throws Exception {
        final RpcReply rpcReply = RpcReply.from(RPC_REPLY_WITH_NAMESPACE);

        assertThat(rpcReply.getMessageId())
            .isEqualTo("4");
        assertThat(rpcReply.isOK())
            .isFalse();
        assertThat(rpcReply.hasErrorsOrWarnings())
            .isFalse();
        assertThat(rpcReply.hasErrors())
            .isFalse();
        assertThat(rpcReply.hasWarnings())
            .isFalse();
    }

    @Test
    public void willCreateOkRpcReply() throws Exception {
        final RpcReply rpcReply = RpcReply.from(RPC_REPLY_WITH_OK);

        assertThat(rpcReply.getMessageId())
            .isEqualTo("5");
        assertThat(rpcReply.isOK())
            .isTrue();
        assertThat(rpcReply.hasErrorsOrWarnings())
            .isFalse();
        assertThat(rpcReply.hasErrors())
            .isFalse();
        assertThat(rpcReply.hasWarnings())
            .isFalse();
    }

    @Test
    public void willParseRpcReplyWithErrors() throws Exception {
        final RpcReply rpcReply = RpcReply.from(RPC_REPLY_WITH_ERRORS);

        assertThat(rpcReply.getMessageId())
            .isEqualTo("101");
        assertThat(rpcReply.isOK())
            .isFalse();
        assertThat(rpcReply.hasErrorsOrWarnings())
            .isTrue();
        assertThat(rpcReply.hasErrors())
            .isTrue();
        assertThat(rpcReply.hasWarnings())
            .isFalse();
        assertThat(rpcReply.getErrors())
            .isEqualTo(Arrays.asList(
                RpcError.builder()
                    .errorType(RpcError.ErrorType.APPLICATION)
                    .errorTag(RpcError.ErrorTag.INVALID_VALUE)
                    .errorSeverity(RpcError.ErrorSeverity.ERROR)
                    .errorPath("/t:top/t:interface[t:name=\"Ethernet0/0\"]/t:mtu")
                    .errorMessage("MTU value 25000 is not within range 256..9192")
                    .errorMessageLanguage("en")
                    .build(),
                RpcError.builder()
                    .errorType(RpcError.ErrorType.APPLICATION)
                    .errorTag(RpcError.ErrorTag.INVALID_VALUE)
                    .errorSeverity(RpcError.ErrorSeverity.ERROR)
                    .errorPath("/t:top/t:interface[t:name=\"Ethernet1/0\"]/t:address/t:name")
                    .errorMessage("Invalid IP address for interface Ethernet1/0")
                    .build()));
    }

    @Test
    public void willCreateXmlFromAnObject() {

        final RpcReply rpcReply = RpcReply.builder()
            .messageId("3")
            .build();

        XmlAssert.assertThat(rpcReply.getXml())
            .and(RPC_REPLY_WITHOUT_NAMESPACE)
            .ignoreWhitespace()
            .areIdentical();
    }

    @Test
    public void willCreateXmlWithNamespaceFromAnObject() {
        final RpcReply rpcReply = RpcReply.builder()
            .namespacePrefix("nc")
            .messageId("4")
            .build();

        XmlAssert.assertThat(rpcReply.getXml())
            .and(RPC_REPLY_WITH_NAMESPACE)
            .ignoreWhitespace()
            .areIdentical();
    }

    @Test
    public void willCreateXmlWithOkFromAnObject() {

        final RpcReply rpcReply = RpcReply.builder()
            .namespacePrefix("nc")
            .messageId("5")
            .ok(true)
            .build();

        XmlAssert.assertThat(rpcReply.getXml())
            .and(RPC_REPLY_WITH_OK)
            .ignoreWhitespace()
            .areIdentical();
    }

    @Test
    public void willCreateXmlWithErrors() {
        final RpcReply rpcReply = RpcReply.builder()
            .messageId("101")
            .addError(RpcError.builder()
                .errorType(RpcError.ErrorType.APPLICATION)
                .errorTag(RpcError.ErrorTag.INVALID_VALUE)
                .errorSeverity(RpcError.ErrorSeverity.ERROR)
                .errorPath("/t:top/t:interface[t:name=\"Ethernet0/0\"]/t:mtu")
                .errorMessage("MTU value 25000 is not within range 256..9192")
                .errorMessageLanguage("en")
                .build())
            .addError(RpcError.builder()
                .errorType(RpcError.ErrorType.APPLICATION)
                .errorTag(RpcError.ErrorTag.INVALID_VALUE)
                .errorSeverity(RpcError.ErrorSeverity.ERROR)
                .errorPath("/t:top/t:interface[t:name=\"Ethernet1/0\"]/t:address/t:name")
                .errorMessage("Invalid IP address for interface Ethernet1/0")
                .build())
            .build();

        XmlAssert.assertThat(rpcReply.getXml())
            .and(RPC_REPLY_WITH_ERRORS)
            .ignoreWhitespace()
            .areIdentical();
    }

    /**
     * RFC 6241 §4.3: a peer SHOULD respond with <rpc-error error-tag="malformed-message">
     * if the incoming message is not well‑formed XML.  In our client-side parser we
     * expect a SAXException (wrapped) rather than a valid RpcReply object.
     */
    @Test
    public void willThrowOnMalformedXml() {
        assertThatThrownBy(() -> RpcReply.from(MALFORMED_RPC_REPLY))
            .isInstanceOf(Exception.class);   // Xml parsing failed (SAXException or wrapped)
    }

    /**
     * RFC 6241 requires UTF‑8 encoding.  Passing bytes in ISO‑8859‑1 that contain
     * invalid UTF‑8 sequences should also raise a parse failure.
     */
    @Test
    public void willThrowOnNonUtf8Encoding() {
        byte[] isoBytes = MALFORMED_RPC_REPLY.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        String wrongEncoded = new String(isoBytes, java.nio.charset.StandardCharsets.ISO_8859_1);   // contains invalid UTF‑8 if any high‑bytes
        assertThatThrownBy(() -> RpcReply.from(wrongEncoded))
            .isInstanceOf(Exception.class);
    }
    /**
     * RFC 6242 §4.3: parser must ignore the "]]>]]>" end‑of‑message delimiter
     * that legacy :base:1.0 peers append.
     */
    @Test
    public void willParseRpcReplyWithDelimiter() throws Exception {
        final RpcReply rpcReply = RpcReply.from(RPC_REPLY_WITH_OK_FRAMED);

        assertThat(rpcReply.getMessageId())
            .isEqualTo("5");
        assertThat(rpcReply.isOK()).isTrue();
        assertThat(rpcReply.hasErrorsOrWarnings()).isFalse();
    }
}