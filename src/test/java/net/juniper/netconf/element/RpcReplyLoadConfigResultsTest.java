package net.juniper.netconf.element;

import org.junit.jupiter.api.Test;
import org.xmlunit.assertj.XmlAssert;

import static org.assertj.core.api.Assertions.assertThat;

public class RpcReplyLoadConfigResultsTest {

    private static final String LOAD_CONFIG_RESULTS_OK_NO_NAMESPACE = """
        \
        <rpc-reply xmlns="urn:ietf:params:xml:ns:netconf:base:1.0"\
                     xmlns:junos="http://xml.juniper.net/junos/20.4R0/junos"\
                     message-id="3">
            <load-configuration-results action="set">
                <ok/>
            </load-configuration-results>
        </rpc-reply>""";

    private static final String LOAD_CONFIG_RESULTS_OK_WITH_NAMESPACE = """
        \
        <nc:rpc-reply xmlns:nc="urn:ietf:params:xml:ns:netconf:base:1.0"\
                           xmlns:junos="http://xml.juniper.net/junos/20.4R0/junos"\
                           message-id="4">
            <load-configuration-results action="set">
                <nc:ok/>
            </load-configuration-results>
        </nc:rpc-reply>""";

    private static final String LOAD_CONFIG_RESULTS_ERROR_NO_NAMESPACE = ""
        + "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"" +
        "               xmlns:junos=\"http://xml.juniper.net/junos/20.4R0/junos\"\n" +
        "               message-id=\"5\">\n" +
        "    <load-configuration-results action=\"set\">\n" +
        "        <rpc-error>\n" +
        "            <error-type>protocol</error-type>\n" +
        "            <error-tag>operation-failed</error-tag>\n" +
        "            <error-severity>error</error-severity>\n" +
        "            <error-message>syntax error</error-message>\n" +
        "            <error-info>\n" +
        "                <bad-element>foobar</bad-element>\n" +
        "            </error-info>\n" +
        "        </rpc-error>\n" +
        "        <ok/>\n" +
        "    </load-configuration-results>\n" +
        "</rpc-reply>\n";

    private static final String LOAD_CONFIG_RESULTS_ERROR_WITH_NAMESPACE = """
        \
        <nc:rpc-reply xmlns:nc="urn:ietf:params:xml:ns:netconf:base:1.0"\
                           xmlns:junos="http://xml.juniper.net/junos/20.4R0/junos"
                           message-id="6">
            <load-configuration-results action="set">
                <nc:rpc-error>
                    <nc:error-type>protocol</nc:error-type>
                    <nc:error-tag>operation-failed</nc:error-tag>
                    <nc:error-severity>error</nc:error-severity>
                    <nc:error-message>syntax error</nc:error-message>
                    <nc:error-info>
                        <nc:bad-element>foobar</nc:bad-element>
                    </nc:error-info>
                </nc:rpc-error>
                <nc:ok/>
            </load-configuration-results>
        </nc:rpc-reply>
        """;

    @Test
    public void willParseAnOkResponseWithNoNamespacePrefix() throws Exception {

        final RpcReplyLoadConfigResults rpcReply = RpcReply.from(LOAD_CONFIG_RESULTS_OK_NO_NAMESPACE);

        assertThat(rpcReply.getMessageId())
            .isEqualTo("3");
        assertThat(rpcReply.getAction())
            .isEqualTo("set");
        assertThat(rpcReply.isOK())
            .isTrue();
        assertThat(rpcReply.hasErrorsOrWarnings())
            .isFalse();
        assertThat(rpcReply.hasErrors())
            .isFalse();
        assertThat(rpcReply.hasWarnings())
            .isFalse();
        assertThat(rpcReply.getErrors())
            .isEmpty();

    }

    @Test
    public void willParseAnOkResponseWithNamespacePrefix() throws Exception {

        final RpcReplyLoadConfigResults rpcReply = RpcReply.from(LOAD_CONFIG_RESULTS_OK_WITH_NAMESPACE);

        assertThat(rpcReply.getMessageId())
            .isEqualTo("4");
        assertThat(rpcReply.getAction())
            .isEqualTo("set");
        assertThat(rpcReply.isOK())
            .isTrue();
        assertThat(rpcReply.hasErrorsOrWarnings())
            .isFalse();
        assertThat(rpcReply.hasErrors())
            .isFalse();
        assertThat(rpcReply.hasWarnings())
            .isFalse();
        assertThat(rpcReply.getErrors())
            .isEmpty();

    }

    @Test
    public void willParseAnErrorResponseWithoutNamespacePrefix() throws Exception {

        final RpcReplyLoadConfigResults rpcReply = RpcReply.from(LOAD_CONFIG_RESULTS_ERROR_NO_NAMESPACE);

        assertThat(rpcReply.getMessageId())
            .isEqualTo("5");
        assertThat(rpcReply.getAction())
            .isEqualTo("set");
        assertThat(rpcReply.isOK())
            .isTrue();
        assertThat(rpcReply.hasErrorsOrWarnings())
            .isTrue();
        assertThat(rpcReply.hasErrors())
            .isTrue();
        assertThat(rpcReply.hasWarnings())
            .isFalse();
        assertThat(rpcReply.getErrors())
            .containsExactly(RpcError.builder()
                .errorType(RpcError.ErrorType.PROTOCOL)
                .errorTag(RpcError.ErrorTag.OPERATION_FAILED)
                .errorSeverity(RpcError.ErrorSeverity.ERROR)
                .errorMessage("syntax error")
                .errorInfo(RpcError.RpcErrorInfo.builder()
                    .badElement("foobar")
                    .build())
                .build());
    }

    @Test
    public void willParseAnErrorResponseWithNamespacePrefix() throws Exception {

        final RpcReplyLoadConfigResults rpcReply = RpcReply.from(LOAD_CONFIG_RESULTS_ERROR_WITH_NAMESPACE);

        assertThat(rpcReply.getMessageId())
            .isEqualTo("6");
        assertThat(rpcReply.getAction())
            .isEqualTo("set");
        assertThat(rpcReply.isOK())
            .isTrue();
        assertThat(rpcReply.hasErrorsOrWarnings())
            .isTrue();
        assertThat(rpcReply.hasErrors())
            .isTrue();
        assertThat(rpcReply.hasWarnings())
            .isFalse();
        assertThat(rpcReply.getErrors())
            .containsExactly(RpcError.builder()
                .errorType(RpcError.ErrorType.PROTOCOL)
                .errorTag(RpcError.ErrorTag.OPERATION_FAILED)
                .errorSeverity(RpcError.ErrorSeverity.ERROR)
                .errorMessage("syntax error")
                .errorInfo(RpcError.RpcErrorInfo.builder()
                    .badElement("foobar")
                    .build())
                .build());
    }

    @Test
    public void willCreateXmlOkWithoutNamespace() {

        final RpcReply rpcReply = RpcReplyLoadConfigResults.loadConfigResultsBuilder()
            .messageId("3")
            .action("set")
            .ok(true)
            .build();

        XmlAssert.assertThat(rpcReply.getXml())
            .and(LOAD_CONFIG_RESULTS_OK_NO_NAMESPACE)
            .ignoreWhitespace()
            .areIdentical();
    }

    @Test
    public void willCreateXmlOkWithNamespace() {

        final RpcReply rpcReply = RpcReplyLoadConfigResults.loadConfigResultsBuilder()
            .namespacePrefix("nc")
            .messageId("4")
            .action("set")
            .ok(true)
            .build();

        XmlAssert.assertThat(rpcReply.getXml())
            .and(LOAD_CONFIG_RESULTS_OK_WITH_NAMESPACE)
            .ignoreWhitespace()
            .areIdentical();
    }

    @Test
    public void willCreateXmlErrorWithoutNamespace() {

        final RpcReply rpcReply = RpcReplyLoadConfigResults.loadConfigResultsBuilder()
            .messageId("5")
            .action("set")
            .ok(true)
            .addError(RpcError.builder()
                .errorType(RpcError.ErrorType.PROTOCOL)
                .errorTag(RpcError.ErrorTag.OPERATION_FAILED)
                .errorSeverity(RpcError.ErrorSeverity.ERROR)
                .errorMessage("syntax error")
                .errorInfo(RpcError.RpcErrorInfo.builder()
                    .badElement("foobar")
                    .build())
                .build())
            .build();

        XmlAssert.assertThat(rpcReply.getXml())
            .and(LOAD_CONFIG_RESULTS_ERROR_NO_NAMESPACE)
            .ignoreWhitespace()
            .areIdentical();
    }

    @Test
    public void willCreateXmlErrorWithNamespace() {

        final RpcReply rpcReply = RpcReplyLoadConfigResults.loadConfigResultsBuilder()
            .namespacePrefix("nc")
            .messageId("6")
            .action("set")
            .ok(true)
            .addError(RpcError.builder()
                .errorType(RpcError.ErrorType.PROTOCOL)
                .errorTag(RpcError.ErrorTag.OPERATION_FAILED)
                .errorSeverity(RpcError.ErrorSeverity.ERROR)
                .errorMessage("syntax error")
                .errorInfo(RpcError.RpcErrorInfo.builder()
                    .badElement("foobar")
                    .build())
                .build())
            .build();

        XmlAssert.assertThat(rpcReply.getXml())
            .and(LOAD_CONFIG_RESULTS_ERROR_WITH_NAMESPACE)
            .ignoreWhitespace()
            .areIdentical();
    }

}