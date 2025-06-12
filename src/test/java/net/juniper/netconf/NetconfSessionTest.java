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
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
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
    private static final String NETCONF_SYNTAX_ERROR_MSG_FROM_DEVICE = "netconf error: syntax error";

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
        when(mockNetconfSession.getCandidateConfig()).thenCallRealMethod();
        when(mockNetconfSession.getRpcReply(anyString())).thenReturn(NETCONF_SYNTAX_ERROR_MSG_FROM_DEVICE);

        assertThatThrownBy(mockNetconfSession::getCandidateConfig)
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
    public void loadTextConfigurationSucceedsWithOkResponse() throws Exception {
        doCallRealMethod().when(mockNetconfSession)
                          .loadTextConfiguration(anyString(), anyString());
        when(mockNetconfSession.getRpcReply(anyString())).thenReturn(OK_RPC_REPLY);
        when(mockNetconfSession.hasError()).thenReturn(false);
        when(mockNetconfSession.isOK()).thenReturn(true);

        // should complete without throwing
        mockNetconfSession.loadTextConfiguration("some config", "some type");
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

        assertThrows(LoadException.class,
            () -> netconfSession.loadTextConfiguration("some config", "some type"));
    }

    @Test
    public void loadXmlConfigurationSucceedsWithOkResponse() throws Exception {
        doCallRealMethod().when(mockNetconfSession)
                          .loadXMLConfiguration(anyString(), anyString());
        when(mockNetconfSession.getRpcReply(anyString())).thenReturn(OK_RPC_REPLY);
        when(mockNetconfSession.hasError()).thenReturn(false);
        when(mockNetconfSession.isOK()).thenReturn(true);

        // should complete without throwing
        mockNetconfSession.loadXMLConfiguration("some config", "merge");
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
        doCallRealMethod().when(mockNetconfSession).commitConfirm(600, "abc");
        when(mockNetconfSession.getRpcReply(anyString())).thenReturn(OK_RPC_REPLY);
        when(mockNetconfSession.hasError()).thenReturn(false);
        when(mockNetconfSession.isOK()).thenReturn(true);

        // should complete without throwing CommitException
        mockNetconfSession.commitConfirm(600, "abc");
    }

    @Test
    public void commitConfirmWithPersistThrowsCommitExceptionOnErrorReply() throws Exception {
        doCallRealMethod().when(mockNetconfSession).commitConfirm(600, "xyz");
        when(mockNetconfSession.getRpcReply(anyString())).thenReturn(ERROR_RPC_REPLY);
        when(mockNetconfSession.hasError()).thenReturn(true);
        when(mockNetconfSession.isOK()).thenReturn(false);

        assertThatThrownBy(() -> mockNetconfSession.commitConfirm(600, "xyz"))
            .isInstanceOf(CommitException.class)
            .hasMessage("Confirmed-commit operation returned error.");
    }

    /* ----- cancel-commit ----- */

    @Test
    public void cancelCommitReturnsTrueOnOkReply() throws Exception {
        when(mockNetconfSession.cancelCommit("abc")).thenCallRealMethod();
        when(mockNetconfSession.getRpcReply(anyString())).thenReturn(OK_RPC_REPLY);
        when(mockNetconfSession.hasError()).thenReturn(false);
        when(mockNetconfSession.isOK()).thenReturn(true);

        assertThat(mockNetconfSession.cancelCommit("abc")).isTrue();
    }

    @Test
    public void cancelCommitReturnsFalseOnErrorReply() throws Exception {
        when(mockNetconfSession.cancelCommit(null)).thenCallRealMethod();
        when(mockNetconfSession.getRpcReply(anyString())).thenReturn(ERROR_RPC_REPLY);
        when(mockNetconfSession.hasError()).thenReturn(true);
        when(mockNetconfSession.isOK()).thenReturn(false);

        assertThat(mockNetconfSession.cancelCommit(null)).isFalse();
    }

    // Helper methods to reduce code duplication and improve readability

    private NetconfSession createNetconfSession(int commandTimeout) throws IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new NetconfException(String.format("Error creating XML Parser: %s", e.getMessage()));
        }

        return new NetconfSession(mockChannel, CONNECTION_TIMEOUT, commandTimeout, FAKE_HELLO, builder);
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
        outPipe.write(FAKE_RPC_REPLY.getBytes(StandardCharsets.UTF_8));
        outPipe.write(DEVICE_PROMPT_BYTE);
        Thread.sleep(200);
        outPipe.flush();
        Thread.sleep(200);
        outPipe.close();
    }

    private void writeLldpResponse(byte[] lldpResponse) throws IOException, InterruptedException {
        outPipe.write(FAKE_RPC_REPLY.getBytes(StandardCharsets.UTF_8));
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
        return "<hello xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "  <capabilities>\n"
            + "    <capability>urn:ietf:params:netconf:base:1.0</capability>\n"
            + "    <capability>urn:ietf:params:netconf:base:1.0#candidate</capability>\n"
            + "    <capability>urn:ietf:params:netconf:base:1.0#confirmed-commit</capability>\n"
            + "    <capability>urn:ietf:params:netconf:base:1.0#validate</capability>\n"
            + "    <capability>urn:ietf:params:netconf:base:1.0#url?protocol=http,ftp,file</capability>\n"
            + "  </capabilities>\n"
            + "  <session-id>27700</session-id>\n"
            + "</hello>";
    }
}