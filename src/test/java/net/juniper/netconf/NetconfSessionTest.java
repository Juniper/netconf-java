package net.juniper.netconf;

import com.google.common.base.Charsets;
import com.jcraft.jsch.Channel;
import net.juniper.netconf.element.RpcError;
import net.juniper.netconf.element.RpcReply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlunit.assertj.XmlAssert;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NetconfSessionTest {

    private static final Logger log = LoggerFactory.getLogger(NetconfSessionTest.class);

    public static final int CONNECTION_TIMEOUT = 2000;
    public static final int COMMAND_TIMEOUT = 5000;

    private static final String FAKE_HELLO = "fake hello";

    /** Automatically closes Mockito mocks opened in {@link #setUp()}. */
    private AutoCloseable closeable;

    private static final String DEVICE_PROMPT = "]]>]]>";
    private static final byte[] DEVICE_PROMPT_BYTE = DEVICE_PROMPT.getBytes(StandardCharsets.UTF_8);
    private static final String FAKE_RPC_REPLY = "<rpc>fakedata</rpc>";
    private static final String OK_RPC_REPLY =
        "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"><ok/></rpc-reply>";
    private static final String ERROR_RPC_REPLY =
        "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">" +
            "  <rpc-error>" +
            "    <error-type>protocol</error-type>" +
            "    <error-tag>operation-failed</error-tag>" +
            "    <error-severity>error</error-severity>" +
            "  </rpc-error>" +
            "</rpc-reply>";
    private static final String DETAILED_ERROR_RPC_REPLY = """
        <rpc-reply xmlns="urn:ietf:params:xml:ns:netconf:base:1.0" message-id="1">
          <rpc-error>
            <error-type>protocol</error-type>
            <error-tag>operation-failed</error-tag>
            <error-severity>error</error-severity>
            <error-message>syntax error</error-message>
            <error-info>
              <bad-element>invalid-rpc-that-should-not-exist</bad-element>
            </error-info>
          </rpc-error>
        </rpc-reply>
        """;
    private static final String VALIDATE_ERROR_RPC_REPLY = """
        <rpc-reply xmlns="urn:ietf:params:xml:ns:netconf:base:1.0" message-id="1">
          <rpc-error>
            <error-type>protocol</error-type>
            <error-tag>operation-failed</error-tag>
            <error-severity>error</error-severity>
            <error-message>Error while forking commitd process</error-message>
          </rpc-error>
          <rpc-error>
            <error-type>protocol</error-type>
            <error-tag>operation-failed</error-tag>
            <error-severity>error</error-severity>
            <error-message>could not run commit script: Resource temporarily unavailable</error-message>
          </rpc-error>
        </rpc-reply>
        """;
    private static final String NAMESPACED_VALIDATE_ERROR_RPC_REPLY = """
        <nc:rpc-reply xmlns:nc="urn:ietf:params:xml:ns:netconf:base:1.0" message-id="2">
          <nc:rpc-error>
            <nc:error-type>protocol</nc:error-type>
            <nc:error-tag>operation-failed</nc:error-tag>
            <nc:error-severity>error</nc:error-severity>
            <nc:error-message>Error while forking commitd process</nc:error-message>
          </nc:rpc-error>
          <nc:rpc-error>
            <nc:error-type>protocol</nc:error-type>
            <nc:error-tag>operation-failed</nc:error-tag>
            <nc:error-severity>error</nc:error-severity>
            <nc:error-message>could not run commit script: Resource temporarily unavailable</nc:error-message>
          </nc:rpc-error>
          <nc:rpc-error>
            <nc:error-type>protocol</nc:error-type>
            <nc:error-tag>operation-failed</nc:error-tag>
            <nc:error-severity>error</nc:error-severity>
            <nc:error-message>could not start translation script handler</nc:error-message>
          </nc:rpc-error>
          <nc:rpc-error>
            <nc:error-type>protocol</nc:error-type>
            <nc:error-tag>operation-failed</nc:error-tag>
            <nc:error-severity>error</nc:error-severity>
            <nc:error-message>translation script failure</nc:error-message>
          </nc:rpc-error>
        </nc:rpc-reply>
        """;
    private static final String WARNING_ONLY_RPC_REPLY = """
        <rpc-reply xmlns="urn:ietf:params:xml:ns:netconf:base:1.0" message-id="1">
          <rpc-error>
            <error-severity>warning</error-severity>
            <error-message>Could not extract delta, reading the entire configuration</error-message>
          </rpc-error>
        </rpc-reply>
        """;
    private static final String NETCONF_SYNTAX_ERROR_MSG_FROM_DEVICE = "netconf error: syntax error";
    private static final List<String> DEFAULT_CLIENT_CAPABILITIES = List.of(
        NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
        NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1
    );
    private static final List<String> BASE_10_ONLY_CLIENT_CAPABILITIES = List.of(
        NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0
    );
    private static final List<String> BASE_11_ONLY_CLIENT_CAPABILITIES = List.of(
        NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1
    );

    @Mock
    private NetconfSession mockNetconfSession;
    @Mock
    private DocumentBuilder builder;
    @Mock
    private Channel mockChannel;

    private BufferedOutputStream out;
    private PipedOutputStream outPipe;
    private PipedInputStream inPipe;

    @BeforeEach
    public void setUp() throws IOException {
        closeable = MockitoAnnotations.openMocks(this);

        inPipe = new PipedInputStream(8096);
        outPipe = new PipedOutputStream(inPipe);
        PipedInputStream pipeInput = new PipedInputStream(1024);
        out = new BufferedOutputStream(new PipedOutputStream(pipeInput));

        when(mockChannel.getInputStream()).thenReturn(inPipe);
        when(mockChannel.getOutputStream()).thenReturn(out);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    public void getCandidateConfigThrowsNetconfExceptionOnSyntaxError() throws Exception {
        NetconfSession netconfSession = createNetconfSession(
            helloThenReplyStream(NETCONF_SYNTAX_ERROR_MSG_FROM_DEVICE),
            new ByteArrayOutputStream(),
            100
        );

        assertThatThrownBy(netconfSession::getCandidateConfig)
            .isInstanceOf(NetconfException.class)
            .hasMessage("Invalid <rpc-reply> message from server: netconf error: syntax error");
    }

    @Test
    public void getRunningConfigThrowsNetconfExceptionOnSyntaxError() throws Exception {
        when(mockNetconfSession.getRunningConfig()).thenCallRealMethod();
        when(mockNetconfSession.getRpcReply(anyString())).thenReturn(NETCONF_SYNTAX_ERROR_MSG_FROM_DEVICE);

        assertThatThrownBy(mockNetconfSession::getRunningConfig)
            .isInstanceOf(NetconfException.class)
            .hasMessage("Invalid <rpc-reply> message from server: netconf error: syntax error");
    }

    @Test
    public void createSessionThrowsSocketTimeoutExceptionWhenTimeoutExceeded() {
        Thread thread = new Thread(() -> {
            try {
                writeDataWithDelay();
            } catch (IOException | InterruptedException e) {
                log.error("Error in background thread", e);
            }
        });
        thread.start();

        assertThatThrownBy(() -> createNetconfSession(1000))
            .isInstanceOf(SocketTimeoutException.class)
            .hasMessage("Command timeout limit was exceeded: 1000");
    }

    @Test
    public void createSessionThrowsNetconfExceptionWhenConnectionCloses() {
        Thread thread = new Thread(() -> {
            try {
                writeDataAndClose();
            } catch (IOException | InterruptedException e) {
                log.error("Error in background thread", e);
            }
        });
        thread.start();

        assertThatThrownBy(() -> createNetconfSession(COMMAND_TIMEOUT))
            .isInstanceOf(NetconfException.class)
            .hasMessage("Input Stream has been closed during reading.");
    }

    @Test
    public void createSessionHandlesDevicePromptWithoutLineFeed() throws Exception {
        when(mockChannel.getInputStream()).thenReturn(inPipe);
        when(mockChannel.getOutputStream()).thenReturn(out);

        Thread thread = new Thread(() -> {
            try {
                writeValidResponse();
            } catch (IOException | InterruptedException e) {
                log.error("Error in background thread", e);
            }
        });
        thread.start();

        createNetconfSession(COMMAND_TIMEOUT);
    }

    @Test
    public void executeRpcReturnsCorrectResponseForLldpRequest() throws Exception {
        byte[] lldpResponse = Files.readAllBytes(TestHelper.getSampleFile("responses/lldpResponse.xml").toPath());
        String expectedResponse = new String(lldpResponse, Charsets.UTF_8)
            .replaceAll(NetconfConstants.CR, NetconfConstants.EMPTY_LINE) + NetconfConstants.LF;

        Thread thread = new Thread(() -> {
            try {
                writeLldpResponse(lldpResponse);
            } catch (IOException | InterruptedException e) {
                log.error("Error in background thread", e);
            }
        });
        thread.start();

        NetconfSession netconfSession = createNetconfSession(COMMAND_TIMEOUT);
        Thread.sleep(200);
        String deviceResponse = netconfSession.executeRPC(TestConstants.LLDP_REQUEST).toString();

        XmlAssert.assertThat(deviceResponse)
            .and(expectedResponse)
            .ignoreWhitespace()
            .areIdentical();
    }

    @Test
    public void executeRpcThrowsNetconfExceptionOnSyntaxError() throws Exception {
        when(mockNetconfSession.executeRPC(eq(TestConstants.LLDP_REQUEST))).thenCallRealMethod();
        when(mockNetconfSession.getRpcReply(anyString())).thenReturn(NETCONF_SYNTAX_ERROR_MSG_FROM_DEVICE);

        assertThatThrownBy(() -> mockNetconfSession.executeRPC(TestConstants.LLDP_REQUEST))
            .isInstanceOf(NetconfException.class)
            .hasMessage("Invalid <rpc-reply> message from server: netconf error: syntax error");
    }

    @Test
    public void executeRpcThrowsStructuredRpcErrorExceptionOnRpcErrorReply() throws Exception {
        NetconfSession netconfSession = createNetconfSession(
            helloThenReplyStream(DETAILED_ERROR_RPC_REPLY),
            new ByteArrayOutputStream(),
            100
        );

        assertThatThrownBy(() -> netconfSession.executeRPC("<invalid-rpc-that-should-not-exist/>"))
            .isInstanceOf(RpcErrorException.class)
            .hasMessageContaining("syntax error")
            .hasMessageContaining("bad-element=invalid-rpc-that-should-not-exist")
            .satisfies(throwable -> {
                RpcErrorException exception = (RpcErrorException) throwable;
                assertThat(exception.getRpcReply()).isNotNull();
                assertThat(exception.getRpcErrors()).hasSize(1);
                assertThat(exception.getRpcErrors().get(0).errorTag())
                    .isEqualTo(RpcError.ErrorTag.OPERATION_FAILED);
            });
    }

    @Test
    public void fixupRpcWrapsStringWithoutRpcTags() {
        assertThat(NetconfSession.fixupRpc("fake string"))
            .isEqualTo("<rpc><fake string/></rpc>" + DEVICE_PROMPT);
    }

    @Test
    public void fixupRpcPreservesExistingRpcTags() {
        assertThat(NetconfSession.fixupRpc("<rpc>fake string</rpc>"))
            .isEqualTo("<rpc>fake string</rpc>" + DEVICE_PROMPT);
    }

    @Test
    public void fixupRpcPreservesExistingRpcTagsWithAttributes() throws Exception {
        XMLBuilder builder = new XMLBuilder();
        String rpc = builder.createNewRPC("get-interface-information", "terse").toString();

        assertThat(NetconfSession.fixupRpc(rpc))
            .isEqualTo(rpc + DEVICE_PROMPT);
    }

    @Test
    public void fixupRpcWrapsTaggedString() {
        assertThat(NetconfSession.fixupRpc("<fake string/>"))
            .isEqualTo("<rpc><fake string/></rpc>" + DEVICE_PROMPT);
    }

    @Test
    public void fixupRpcThrowsExceptionForNullString() {
        //noinspection ConstantConditions
        assertThatThrownBy(() -> NetconfSession.fixupRpc(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Null RPC");
    }

    @Test
    public void instantiationFetchesHelloFromServer() throws Exception {
        final String hello = createHelloMessage();
        final ByteArrayInputStream is = new ByteArrayInputStream(
            (hello + NetconfConstants.DEVICE_PROMPT).getBytes(StandardCharsets.UTF_8));

        when(mockChannel.getInputStream()).thenReturn(is);

        final NetconfSession netconfSession = createNetconfSession(100);

        assertThat(netconfSession.getSessionId()).isEqualTo("27700");
        assertThat(netconfSession.getServerHello().hasCapability(NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0))
            .isTrue();
    }

    @Test
    public void sessionUsesLegacyFramingWhenNegotiatedBaseIs10() throws Exception {
        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        String rpcReply = RpcReply.builder().ok(true).messageId("1").build().getXml();
        NetconfSession netconfSession = createNetconfSession(
            helloThenReplyStream(createHelloMessageWithBase11(), rpcReply),
            capturedOutput,
            BASE_10_ONLY_CLIENT_CAPABILITIES,
            100
        );

        netconfSession.executeRPC("<get/>");

        assertThat(netconfSession.getNegotiatedCapabilities().getBaseVersion())
            .isEqualTo(NegotiatedCapabilities.BaseVersion.NETCONF_1_0);
        assertThat(netconfSession.getNegotiatedCapabilities().usesChunkedFraming()).isFalse();
        assertThat(capturedOutput.toString(StandardCharsets.UTF_8))
            .contains(NetconfConstants.DEVICE_PROMPT)
            .doesNotContain("\n#");
    }

    @Test
    public void sessionInitializationFailsWithoutSharedBaseCapability() {
        String hello = createHelloMessageWithCapabilities(NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0);

        assertThatThrownBy(() -> createNetconfSession(
            helloOnlyStream(hello),
            new ByteArrayOutputStream(),
            BASE_11_ONLY_CLIENT_CAPABILITIES,
            100
        ))
            .isInstanceOf(NetconfException.class)
            .hasMessageContaining("do not share a common NETCONF base capability");
    }

    @Test
    public void executeRpcSupportsChunkedRepliesAfterBase11Hello() throws Exception {
        final String combinedMessage = createHelloMessageWithBase11()
            + NetconfConstants.DEVICE_PROMPT
            + toChunkedMessage(OK_RPC_REPLY);
        final InputStream combinedStream = new ByteArrayInputStream(combinedMessage.getBytes(StandardCharsets.UTF_8));
        when(mockChannel.getInputStream()).thenReturn(combinedStream);

        final NetconfSession netconfSession = createNetconfSession(100);

        assertThat(netconfSession.executeRPC("<get/>").toString())
            .contains("<ok/>");
    }

    @Test
    public void getNegotiatedCapabilitiesReflectsNormalizedSessionState() throws Exception {
        String hello = createHelloMessageWithCapabilities(
            NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
            NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1,
            "urn:ietf:params:netconf:capability:candidate:1.0",
            "urn:ietf:params:netconf:capability:validate:1.1",
            "urn:ietf:params:netconf:capability:confirmed-commit:1.1",
            "urn:ietf:params:netconf:capability:writable-running:1.0"
        );
        NetconfSession netconfSession = createNetconfSession(
            helloOnlyStream(hello),
            new ByteArrayOutputStream(),
            100
        );

        NegotiatedCapabilities capabilities = netconfSession.getNegotiatedCapabilities();

        assertThat(capabilities.getBaseVersion()).isEqualTo(NegotiatedCapabilities.BaseVersion.NETCONF_1_1);
        assertThat(capabilities.supportsCandidate()).isTrue();
        assertThat(capabilities.supportsValidate()).isTrue();
        assertThat(capabilities.supportsConfirmedCommit()).isTrue();
        assertThat(capabilities.supportsConfirmedCommit11()).isTrue();
        assertThat(capabilities.supportsWritableRunning()).isTrue();
    }

    @Test
    public void executeRpcRunningStripsLegacyFramingAndStopsAfterSingleReply() throws Exception {
        String firstReply = RpcReply.builder().ok(true).messageId("1").build().getXml();
        String secondReply = RpcReply.builder().ok(true).messageId("2").build().getXml();
        NetconfSession netconfSession = createNetconfSession(
            new ByteArrayInputStream((createHelloMessage()
                + NetconfConstants.DEVICE_PROMPT
                + firstReply
                + NetconfConstants.DEVICE_PROMPT
                + secondReply
                + NetconfConstants.DEVICE_PROMPT).getBytes(StandardCharsets.UTF_8)),
            new ByteArrayOutputStream(),
            100
        );

        try (BufferedReader replyReader = netconfSession.executeRPCRunning("<get/>")) {
            String streamedReply = readAll(replyReader);
            assertThat(streamedReply).isEqualTo(firstReply);
            assertThat(streamedReply).doesNotContain(NetconfConstants.DEVICE_PROMPT);
        }

        XmlAssert.assertThat(netconfSession.executeRPC("<get/>").toString())
            .and(secondReply)
            .ignoreWhitespace()
            .areIdentical();
    }

    @Test
    public void sequentialExecuteRpcCallsStayAlignedWithLegacyFraming() throws Exception {
        String firstReply = RpcReply.builder().ok(true).messageId("1").build().getXml();
        String secondReply = RpcReply.builder().ok(true).messageId("2").build().getXml();
        NetconfSession netconfSession = createNetconfSession(
            new ByteArrayInputStream((createHelloMessage()
                + NetconfConstants.DEVICE_PROMPT
                + firstReply
                + NetconfConstants.DEVICE_PROMPT
                + secondReply
                + NetconfConstants.DEVICE_PROMPT).getBytes(StandardCharsets.UTF_8)),
            new ByteArrayOutputStream(),
            100
        );

        XmlAssert.assertThat(netconfSession.executeRPC("<get/>").toString())
            .and(firstReply)
            .ignoreWhitespace()
            .areIdentical();

        XmlAssert.assertThat(netconfSession.executeRPC("<get/>").toString())
            .and(secondReply)
            .ignoreWhitespace()
            .areIdentical();
    }

    @Test
    public void executeRpcUsesLegacyFramingForBase10SessionsEvenWhenReplyStartsWithNewline() throws Exception {
        String firstReply = "\n" + RpcReply.builder().ok(true).messageId("1").build().getXml();
        NetconfSession netconfSession = createNetconfSession(
            new ByteArrayInputStream((createHelloMessage()
                + NetconfConstants.DEVICE_PROMPT
                + firstReply
                + NetconfConstants.DEVICE_PROMPT).getBytes(StandardCharsets.UTF_8)),
            new ByteArrayOutputStream(),
            100
        );

        XmlAssert.assertThat(netconfSession.executeRPC("<get/>").toString())
            .and(firstReply.trim())
            .ignoreWhitespace()
            .areIdentical();
    }

    @Test
    public void closeOnChunkedExecuteRpcRunningDrainsCurrentReplyForNextRpc() throws Exception {
        String firstReply = """
            <rpc-reply xmlns="urn:ietf:params:xml:ns:netconf:base:1.0" message-id="1">
              <data>
                <line>alpha</line>
                <line>beta</line>
              </data>
            </rpc-reply>
            """.trim();
        String secondReply = RpcReply.builder().ok(true).messageId("2").build().getXml();
        NetconfSession netconfSession = createNetconfSession(
            new ByteArrayInputStream((createHelloMessageWithBase11()
                + NetconfConstants.DEVICE_PROMPT
                + toChunkedMessage(firstReply)
                + toChunkedMessage(secondReply)).getBytes(StandardCharsets.UTF_8)),
            new ByteArrayOutputStream(),
            100
        );

        BufferedReader replyReader = netconfSession.executeRPCRunning("<get/>");
        char[] prefix = new char[16];
        int prefixLength = replyReader.read(prefix);
        assertThat(prefixLength).isPositive();
        assertThat(new String(prefix, 0, prefixLength)).startsWith("<rpc-reply");
        replyReader.close();

        XmlAssert.assertThat(netconfSession.executeRPC("<get/>").toString())
            .and(secondReply)
            .ignoreWhitespace()
            .areIdentical();
    }

    @Test
    public void sequentialExecuteRpcCallsStayAlignedWithChunkedFraming() throws Exception {
        String firstReply = RpcReply.builder().ok(true).messageId("1").build().getXml();
        String secondReply = RpcReply.builder().ok(true).messageId("2").build().getXml();
        NetconfSession netconfSession = createNetconfSession(
            new ByteArrayInputStream((createHelloMessageWithBase11()
                + NetconfConstants.DEVICE_PROMPT
                + toChunkedMessage(firstReply)
                + toChunkedMessage(secondReply)).getBytes(StandardCharsets.UTF_8)),
            new ByteArrayOutputStream(),
            100
        );

        XmlAssert.assertThat(netconfSession.executeRPC("<get/>").toString())
            .and(firstReply)
            .ignoreWhitespace()
            .areIdentical();

        XmlAssert.assertThat(netconfSession.executeRPC("<get/>").toString())
            .and(secondReply)
            .ignoreWhitespace()
            .areIdentical();
    }

    @Test
    public void executeRpcAddsMessageIdToAttributedRpcEnvelopeWhenMissing() throws Exception {
        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        String rpcReply = RpcReply.builder().ok(true).messageId("1").build().getXml();
        NetconfSession netconfSession = createNetconfSession(
            helloThenReplyStream(rpcReply),
            capturedOutput,
            100
        );

        netconfSession.executeRPC("<rpc xmlns=\"" + NetconfConstants.URN_XML_NS_NETCONF_BASE_1_0 + "\"><get/></rpc>");

        assertThat(capturedOutput.toString(StandardCharsets.UTF_8))
            .contains("<rpc xmlns=\"" + NetconfConstants.URN_XML_NS_NETCONF_BASE_1_0 + "\" message-id=\"1\"><get/></rpc>"
                + DEVICE_PROMPT);
    }

    @Test
    public void executeRpcPreservesCallerSuppliedMessageId() throws Exception {
        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        String rpcReply = RpcReply.builder().ok(true).messageId("77").build().getXml();
        NetconfSession netconfSession = createNetconfSession(
            helloThenReplyStream(rpcReply),
            capturedOutput,
            100
        );

        netconfSession.executeRPC("<rpc xmlns=\"" + NetconfConstants.URN_XML_NS_NETCONF_BASE_1_0
            + "\" message-id=\"77\"><get/></rpc>");

        assertThat(capturedOutput.toString(StandardCharsets.UTF_8))
            .contains("<rpc xmlns=\"" + NetconfConstants.URN_XML_NS_NETCONF_BASE_1_0 + "\" message-id=\"77\"><get/></rpc>"
                + DEVICE_PROMPT);
    }

    @Test
    public void executeRpcRejectsMismatchedReplyMessageId() throws Exception {
        String mismatchedReply = RpcReply.builder().ok(true).messageId("999").build().getXml();
        NetconfSession netconfSession = createNetconfSession(
            helloThenReplyStream(mismatchedReply),
            new ByteArrayOutputStream(),
            100
        );

        assertThatThrownBy(() -> netconfSession.executeRPC("<get/>"))
            .isInstanceOf(NetconfException.class)
            .hasMessageContaining("Mismatched rpc-reply message-id")
            .hasMessageContaining("expected=1")
            .hasMessageContaining("actual=999");
    }

    @Test
    public void loadTextConfigurationSucceedsWithOkResponse() throws Exception {
        NetconfSession netconfSession = createNetconfSession(
            helloThenReplyStream(OK_RPC_REPLY),
            new ByteArrayOutputStream(),
            100
        );

        netconfSession.loadTextConfiguration("some config", "merge");
    }

    @Test
    public void loadTextConfigurationFailsWithNotOkResponse() throws Exception {
        final String helloMessage = createHelloMessage();
        final RpcReply rpcReply = RpcReply.builder()
            .ok(false)
            .messageId("1")
            .build();

        final String combinedMessage = helloMessage + NetconfConstants.DEVICE_PROMPT +
            rpcReply.getXml() + NetconfConstants.DEVICE_PROMPT;

        final InputStream combinedStream = new ByteArrayInputStream(combinedMessage.getBytes(StandardCharsets.UTF_8));
        when(mockChannel.getInputStream()).thenReturn(combinedStream);

        final NetconfSession netconfSession = createNetconfSession(100);

        assertThrows(LoadException.class,
            () -> netconfSession.loadTextConfiguration("some config", "some type"));
    }

    @Test
    public void loadTextConfigurationFailsWithOkResponseButErrors() throws Exception {
        final String helloMessage = createHelloMessage();
        final RpcReply rpcReply = RpcReply.builder()
            .ok(true)
            .addError(RpcError.builder().errorSeverity(RpcError.ErrorSeverity.ERROR).build())
            .messageId("1")
            .build();

        final String combinedMessage = helloMessage + NetconfConstants.DEVICE_PROMPT +
            rpcReply.getXml() + NetconfConstants.DEVICE_PROMPT;

        final InputStream combinedStream = new ByteArrayInputStream(combinedMessage.getBytes(StandardCharsets.UTF_8));
        when(mockChannel.getInputStream()).thenReturn(combinedStream);

        final NetconfSession netconfSession = createNetconfSession(100);

        assertThatThrownBy(() -> netconfSession.loadTextConfiguration("some config", "some type"))
            .isInstanceOf(LoadException.class)
            .hasMessageContaining("Load operation returned 1 rpc-error")
            .satisfies(throwable -> {
                LoadException exception = (LoadException) throwable;
                assertThat(exception.getRpcReply()).isNotNull();
                assertThat(exception.getRpcErrors()).hasSize(1);
            });
    }

    @Test
    public void lockConfigFailsLocallyWhenServerLacksCandidateCapability() throws Exception {
        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        NetconfSession netconfSession = createNetconfSession(
            helloOnlyStream(createHelloMessageWithCapabilities(NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0)),
            capturedOutput,
            100
        );
        String helloWrite = capturedOutput.toString(StandardCharsets.UTF_8);

        assertThatThrownBy(netconfSession::lockConfig)
            .isInstanceOf(UnsupportedCapabilityException.class)
            .hasMessageContaining("candidate")
            .hasMessageContaining("lockConfig");

        assertThat(capturedOutput.toString(StandardCharsets.UTF_8)).isEqualTo(helloWrite);
    }

    @Test
    public void validateFailsLocallyWhenServerLacksValidateCapability() throws Exception {
        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        NetconfSession netconfSession = createNetconfSession(
            helloOnlyStream(createHelloMessageWithCapabilities(
                NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
                NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0 + "#candidate"
            )),
            capturedOutput,
            100
        );
        String helloWrite = capturedOutput.toString(StandardCharsets.UTF_8);

        assertThatThrownBy(netconfSession::validate)
            .isInstanceOf(UnsupportedCapabilityException.class)
            .hasMessageContaining("validate");

        assertThat(capturedOutput.toString(StandardCharsets.UTF_8)).isEqualTo(helloWrite);
    }

    @Test
    public void validateThrowsStructuredValidateExceptionOnRpcErrorReply() throws Exception {
        NetconfSession netconfSession = createNetconfSession(
            helloThenReplyStream(VALIDATE_ERROR_RPC_REPLY),
            new ByteArrayOutputStream(),
            100
        );

        assertThatThrownBy(netconfSession::validate)
            .isInstanceOf(ValidateException.class)
            .hasMessageContaining("Error while forking commitd process")
            .hasMessageContaining("could not run commit script: Resource temporarily unavailable")
            .satisfies(throwable -> {
                ValidateException exception = (ValidateException) throwable;
                assertThat(exception.getRpcReply()).isNotNull();
                assertThat(exception.getRpcErrors()).hasSize(2);
                assertThat(exception.getRpcErrors())
                    .allMatch(error -> error.errorSeverity() == RpcError.ErrorSeverity.ERROR);
            });
    }

    @Test
    public void validateThrowsStructuredValidateExceptionOnNamespacedRpcErrorReply() throws Exception {
        NetconfSession netconfSession = createNetconfSession(
            helloThenReplyStream(NAMESPACED_VALIDATE_ERROR_RPC_REPLY),
            new ByteArrayOutputStream(),
            100
        );

        assertThatThrownBy(netconfSession::validate)
            .isInstanceOf(ValidateException.class)
            .hasMessageContaining("Error while forking commitd process")
            .hasMessageContaining("translation script failure")
            .satisfies(throwable -> {
                ValidateException exception = (ValidateException) throwable;
                assertThat(exception.getRpcErrors()).hasSize(4);
                assertThat(exception.getRpcErrors())
                    .allMatch(error -> error.errorSeverity() == RpcError.ErrorSeverity.ERROR);
                assertThat(exception.getRpcErrors())
                    .extracting(RpcError::errorMessage)
                    .containsExactly(
                        "Error while forking commitd process",
                        "could not run commit script: Resource temporarily unavailable",
                        "could not start translation script handler",
                        "translation script failure"
                    );
            });
    }

    @Test
    public void validateReturnsFalseForWarningOnlyReply() throws Exception {
        NetconfSession netconfSession = createNetconfSession(
            helloThenReplyStream(WARNING_ONLY_RPC_REPLY),
            new ByteArrayOutputStream(),
            100
        );

        assertThat(netconfSession.validate()).isFalse();
        assertThat(netconfSession.getLastRpcReplyObject().hasWarnings()).isTrue();
        assertThat(netconfSession.getLastRpcReplyObject().hasErrors()).isFalse();
    }

    @Test
    public void loadXmlConfigurationSucceedsWithOkResponse() throws Exception {
        NetconfSession netconfSession = createNetconfSession(
            helloThenReplyStream(OK_RPC_REPLY),
            new ByteArrayOutputStream(),
            100
        );

        netconfSession.loadXMLConfiguration("some config", "merge");
    }

    @Test
    public void loadXmlConfigurationFailsWithNotOkResponse() throws Exception {
        final String helloMessage = createHelloMessage();
        final RpcReply rpcReply = RpcReply.builder()
            .ok(false)
            .messageId("1")
            .build();

        final String combinedMessage = helloMessage + NetconfConstants.DEVICE_PROMPT +
            rpcReply.getXml() + NetconfConstants.DEVICE_PROMPT;

        final InputStream combinedStream = new ByteArrayInputStream(combinedMessage.getBytes(StandardCharsets.UTF_8));
        when(mockChannel.getInputStream()).thenReturn(combinedStream);

        final NetconfSession netconfSession = createNetconfSession(100);

        assertThrows(LoadException.class,
            () -> netconfSession.loadXMLConfiguration("some config", "merge"));
    }

    @Test
    public void loadXmlConfigurationFailsWithOkResponseButErrors() throws Exception {
        final String helloMessage = createHelloMessage();
        final RpcReply rpcReply = RpcReply.builder()
            .ok(true)
            .addError(RpcError.builder().errorSeverity(RpcError.ErrorSeverity.ERROR).build())
            .messageId("1")
            .build();

        final String combinedMessage = helloMessage + NetconfConstants.DEVICE_PROMPT +
            rpcReply.getXml() + NetconfConstants.DEVICE_PROMPT;

        final InputStream combinedStream = new ByteArrayInputStream(combinedMessage.getBytes(StandardCharsets.UTF_8));
        when(mockChannel.getInputStream()).thenReturn(combinedStream);

        final NetconfSession netconfSession = createNetconfSession(100);

        assertThrows(LoadException.class,
            () -> netconfSession.loadXMLConfiguration("some config", "merge"));
    }

    /**
     * RFC 6241 §7.9 – a successful &lt;kill-session&gt; returns &lt;ok/&gt;.
     */
    @Test
    public void killSessionReturnsTrueOnOkReply() throws Exception {
        when(mockNetconfSession.killSession("42")).thenCallRealMethod();
        when(mockNetconfSession.getRpcReply(anyString())).thenReturn(OK_RPC_REPLY);
        when(mockNetconfSession.hasError()).thenReturn(false);
        when(mockNetconfSession.isOK()).thenReturn(true);

        assertThat(mockNetconfSession.killSession("42")).isTrue();
    }

    /**
     * RFC 6241 §7.9 – if the server returns &lt;rpc-error&gt;, killSession() should return false.
     */
    @Test
    public void killSessionReturnsFalseOnErrorReply() throws Exception {
        when(mockNetconfSession.killSession("99")).thenCallRealMethod();
        when(mockNetconfSession.getRpcReply(anyString())).thenReturn(ERROR_RPC_REPLY);
        when(mockNetconfSession.hasError()).thenReturn(true);
        when(mockNetconfSession.isOK()).thenReturn(false);

        assertThat(mockNetconfSession.killSession("99")).isFalse();
    }

    /* =========================================================
     * :confirmed-commit:1.1 tests
     * ========================================================= */

    @Test
    public void commitConfirmWithPersistCompletesSuccessfullyOnOkReply() throws Exception {
        NetconfSession netconfSession = createNetconfSession(
            helloThenReplyStream(
                createHelloMessageWithCapabilities(
                    NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
                    "urn:ietf:params:netconf:capability:candidate:1.0",
                    "urn:ietf:params:netconf:capability:confirmed-commit:1.1"
                ),
                OK_RPC_REPLY
            ),
            new ByteArrayOutputStream(),
            100
        );

        netconfSession.commitConfirm(600, "abc");
    }

    @Test
    public void commitConfirmWithPersistThrowsCommitExceptionOnErrorReply() throws Exception {
        NetconfSession netconfSession = createNetconfSession(
            helloThenReplyStream(
                createHelloMessageWithCapabilities(
                    NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
                    "urn:ietf:params:netconf:capability:candidate:1.0",
                    "urn:ietf:params:netconf:capability:confirmed-commit:1.1"
                ),
                ERROR_RPC_REPLY
            ),
            new ByteArrayOutputStream(),
            100
        );

        assertThatThrownBy(() -> netconfSession.commitConfirm(600, "xyz"))
            .isInstanceOf(CommitException.class)
            .hasMessageContaining("Confirmed-commit operation returned")
            .satisfies(throwable -> {
                CommitException exception = (CommitException) throwable;
                assertThat(exception.getRpcReply()).isNotNull();
                assertThat(exception.getRpcErrors()).hasSize(1);
            });
    }

    @Test
    public void commitConfirmWithPersistFailsLocallyWhenServerOnlyAdvertisesLegacyConfirmedCommit() throws Exception {
        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        NetconfSession netconfSession = createNetconfSession(
            helloOnlyStream(createHelloMessage()),
            capturedOutput,
            100
        );
        String helloWrite = capturedOutput.toString(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> netconfSession.commitConfirm(600, "persist-token"))
            .isInstanceOf(UnsupportedCapabilityException.class)
            .hasMessageContaining("confirmed-commit:1.1");

        assertThat(capturedOutput.toString(StandardCharsets.UTF_8)).isEqualTo(helloWrite);
    }

    /* ----- cancel-commit ----- */

    @Test
    public void cancelCommitReturnsTrueOnOkReply() throws Exception {
        NetconfSession netconfSession = createNetconfSession(
            helloThenReplyStream(
                createHelloMessageWithCapabilities(
                    NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
                    "urn:ietf:params:netconf:capability:candidate:1.0",
                    "urn:ietf:params:netconf:capability:confirmed-commit:1.1"
                ),
                OK_RPC_REPLY
            ),
            new ByteArrayOutputStream(),
            100
        );

        assertThat(netconfSession.cancelCommit("abc")).isTrue();
    }

    @Test
    public void cancelCommitReturnsFalseOnErrorReply() throws Exception {
        NetconfSession netconfSession = createNetconfSession(
            helloThenReplyStream(
                createHelloMessageWithCapabilities(
                    NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
                    "urn:ietf:params:netconf:capability:candidate:1.0",
                    "urn:ietf:params:netconf:capability:confirmed-commit:1.1"
                ),
                ERROR_RPC_REPLY
            ),
            new ByteArrayOutputStream(),
            100
        );

        assertThat(netconfSession.cancelCommit(null)).isFalse();
    }

    @Test
    public void commitThisConfigurationUnlocksCandidateWhenLoadFails() throws Exception {
        NetconfSession netconfSession = spy(createNetconfSession(
            helloOnlyStream(createHelloMessage()),
            new ByteArrayOutputStream(),
            100
        ));
        doReturn(true).when(netconfSession).lockConfig();
        doThrow(new LoadException("boom")).when(netconfSession).loadTextConfiguration(anyString(), anyString());
        doReturn(true).when(netconfSession).unlockConfig();

        Path configFile = Files.createTempFile("netconf-java-", ".conf");
        Files.writeString(configFile, "system { services { ftp; } }", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> netconfSession.commitThisConfiguration(configFile.toString(), "merge"))
            .isInstanceOf(LoadException.class);

        verify(netconfSession).unlockConfig();
    }

    // Helper methods to reduce code duplication and improve readability

    private NetconfSession createNetconfSession(int commandTimeout) throws IOException {
        return createNetconfSession(commandTimeout, DEFAULT_CLIENT_CAPABILITIES);
    }

    private NetconfSession createNetconfSession(int commandTimeout, List<String> clientCapabilities) throws IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new NetconfException(String.format("Error creating XML Parser: %s", e.getMessage()));
        }

        return new NetconfSession(mockChannel, CONNECTION_TIMEOUT, commandTimeout, clientCapabilities, FAKE_HELLO, builder);
    }

    private NetconfSession createNetconfSession(InputStream inputStream,
                                                java.io.OutputStream outputStream,
                                                int commandTimeout) throws IOException {
        return createNetconfSession(inputStream, outputStream, DEFAULT_CLIENT_CAPABILITIES, commandTimeout);
    }

    private NetconfSession createNetconfSession(InputStream inputStream,
                                                java.io.OutputStream outputStream,
                                                List<String> clientCapabilities,
                                                int commandTimeout) throws IOException {
        when(mockChannel.getInputStream()).thenReturn(inputStream);
        when(mockChannel.getOutputStream()).thenReturn(outputStream);
        return createNetconfSession(commandTimeout, clientCapabilities);
    }

    private InputStream helloThenReplyStream(String reply) {
        return helloThenReplyStream(createHelloMessage(), reply);
    }

    private InputStream helloThenReplyStream(String helloMessage, String reply) {
        String combinedMessage = helloMessage
            + NetconfConstants.DEVICE_PROMPT
            + reply
            + NetconfConstants.DEVICE_PROMPT;
        return new ByteArrayInputStream(combinedMessage.getBytes(StandardCharsets.UTF_8));
    }

    private InputStream helloOnlyStream(String helloMessage) {
        String combinedMessage = helloMessage + NetconfConstants.DEVICE_PROMPT;
        return new ByteArrayInputStream(combinedMessage.getBytes(StandardCharsets.UTF_8));
    }

    private String readAll(BufferedReader reader) throws IOException {
        StringBuilder content = new StringBuilder();
        char[] buffer = new char[128];
        int read;
        while ((read = reader.read(buffer)) != -1) {
            content.append(buffer, 0, read);
        }
        return content.toString();
    }

    private void writeDataWithDelay() throws IOException, InterruptedException {
        outPipe.write(FAKE_RPC_REPLY.getBytes(StandardCharsets.UTF_8));
        for (int i = 0; i < 7; i++) {
            outPipe.write(FAKE_RPC_REPLY.getBytes(StandardCharsets.UTF_8));
            Thread.sleep(200);
            outPipe.flush();
        }
        Thread.sleep(200);
        outPipe.close();
    }

    private void writeDataAndClose() throws IOException, InterruptedException {
        outPipe.write(FAKE_RPC_REPLY.getBytes(StandardCharsets.UTF_8));
        Thread.sleep(200);
        outPipe.flush();
        Thread.sleep(200);
        outPipe.close();
    }

    private void writeValidResponse() throws IOException, InterruptedException {
        outPipe.write(createHelloMessage().getBytes(StandardCharsets.UTF_8));
        outPipe.write(DEVICE_PROMPT_BYTE);
        Thread.sleep(200);
        outPipe.flush();
        Thread.sleep(200);
        outPipe.close();
    }

    private void writeLldpResponse(byte[] lldpResponse) throws IOException, InterruptedException {
        outPipe.write(createHelloMessage().getBytes(StandardCharsets.UTF_8));
        outPipe.write(DEVICE_PROMPT_BYTE);
        outPipe.flush();
        Thread.sleep(800);
        outPipe.write(lldpResponse);
        outPipe.flush();
        Thread.sleep(700);
        outPipe.write(DEVICE_PROMPT_BYTE);
        outPipe.flush();
        Thread.sleep(1900);
        outPipe.close();
    }

    private String createHelloMessage() {
        return createHelloMessageWithCapabilities(
            NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
            NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0 + "#candidate",
            NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0 + "#confirmed-commit",
            NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0 + "#validate",
            NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0 + "#url?protocol=http,ftp,file"
        );
    }

    private String createHelloMessageWithBase11() {
        return createHelloMessageWithCapabilities(
            NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
            NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1
        );
    }

    private String createHelloMessageWithCapabilities(String... capabilities) {
        StringBuilder helloBuilder = new StringBuilder();
        helloBuilder.append("<hello xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n");
        helloBuilder.append("  <capabilities>\n");
        for (String capability : capabilities) {
            helloBuilder.append("    <capability>").append(capability).append("</capability>\n");
        }
        helloBuilder.append("  </capabilities>\n");
        helloBuilder.append("  <session-id>27700</session-id>\n");
        helloBuilder.append("</hello>");
        return helloBuilder.toString();
    }

    private String toChunkedMessage(String payload) {
        return "\n#" + payload.getBytes(StandardCharsets.UTF_8).length + "\n"
            + payload
            + "\n##\n";
    }
}
