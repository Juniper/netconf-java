package net.juniper.netconf;


import com.jcraft.jsch.Channel;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.SocketTimeoutException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

@Slf4j
@Category(Test.class)
public class NetconfSessionTest {

    public static final int CONNECTION_TIMEOUT = 2000;
    public static final int COMMAND_TIMEOUT = 5000;

    private static final String FAKE_HELLO = "fake hello";
    private static final String MERGE_LOAD_TYPE = "merge";
    private static final String REPLACE_LOAD_TYPE = "replace";
    private static final String BAD_LOAD_TYPE = "other";
    private static final String FAKE_TEXT_FILE_PATH = "fakepath";

    private static final String DEVICE_PROMPT = "]]>]]>";
    private static final byte[] DEVICE_PROMPT_BYTE = DEVICE_PROMPT.getBytes();
    private static final String FAKE_RPC_REPLY = "<rpc>fakedata</rpc>";
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

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);

        inPipe = new PipedInputStream(8096);
        outPipe = new PipedOutputStream(inPipe);
        PipedInputStream pipeInput = new PipedInputStream(1024);
        out = new BufferedOutputStream(new PipedOutputStream(pipeInput));

        when(mockChannel.getInputStream()).thenReturn(inPipe);
        when(mockChannel.getOutputStream()).thenReturn(out);
    }

    @Test
    public void GIVEN_getCandidateConfig_WHEN_syntaxError_THEN_throwNetconfException() throws Exception {
        when(mockNetconfSession.getCandidateConfig()).thenCallRealMethod();
        when(mockNetconfSession.getRpcReply(anyString())).thenReturn(NETCONF_SYNTAX_ERROR_MSG_FROM_DEVICE);

        assertThatThrownBy(mockNetconfSession::getCandidateConfig)
                .isInstanceOf(NetconfException.class)
                .hasMessage("Netconf server detected an error: netconf error: syntax error");
    }

    @Test
    public void GIVEN_getRunningConfig_WHEN_syntaxError_THEN_throwNetconfException() throws Exception {
        when(mockNetconfSession.getRunningConfig()).thenCallRealMethod();
        when(mockNetconfSession.getRpcReply(anyString())).thenReturn(NETCONF_SYNTAX_ERROR_MSG_FROM_DEVICE);

        assertThatThrownBy(mockNetconfSession::getRunningConfig)
                .isInstanceOf(NetconfException.class)
                .hasMessage("Netconf server detected an error: netconf error: syntax error");
    }

    @Test
    public void GIVEN_createSession_WHEN_timeoutExceeded_THEN_throwSocketTimeoutException() throws Exception {
        Thread thread = new Thread(() -> {
            try {
                outPipe.write(FAKE_RPC_REPLY.getBytes());
                Thread.sleep(200);
                outPipe.flush();
                Thread.sleep(200);
                outPipe.close();
            } catch (IOException | InterruptedException e) {
                log.error("error =", e);
            }
        });
        thread.start();

        assertThatThrownBy(() -> createNetconfSession())
                .isInstanceOf(SocketTimeoutException.class)
                .hasMessage("Command timeout limit was exceeded: 5000");
    }

    @Test
    public void GIVEN_createSession_WHEN_devicePromptWithoutLF_THEN_correctResponse() throws Exception {
        when(mockChannel.getInputStream()).thenReturn(inPipe);
        when(mockChannel.getOutputStream()).thenReturn(out);

        Thread thread = new Thread(() -> {
            try {
                outPipe.write(FAKE_RPC_REPLY.getBytes());
                outPipe.write(DEVICE_PROMPT_BYTE);
                Thread.sleep(200);
                outPipe.flush();
                Thread.sleep(200);
                outPipe.close();
            } catch (IOException | InterruptedException e) {
                log.error("error =", e);
            }
        });
        thread.start();

        createNetconfSession();
    }

    @Test
    public void GIVEN_executeRPC_WHEN_lldpRequest_THEN_correctResponse() throws Exception {
        byte[] lldpResponse = Files.readAllBytes(TestHelper.getSampleFile("responses/lldpResponse.xml").toPath());

        Thread thread = new Thread(() -> {
            try {
                outPipe.write("Go to HELL".getBytes());
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
            } catch (IOException | InterruptedException e) {
                log.error("error =", e);
            }
        });
        thread.start();

        NetconfSession netconfSession = createNetconfSession();
        Thread.sleep(200);
        String deviceResponse = netconfSession.executeRPC(TestConstants.LLDP_REQUEST).toString();

        assertEquals(new String(lldpResponse) + NetconfConstants.LF, deviceResponse);
    }

    @Test
    public void GIVEN_executeRPC_WHEN_syntaxError_THEN_throwNetconfException() throws Exception {
        when(mockNetconfSession.executeRPC(eq(TestConstants.LLDP_REQUEST))).thenCallRealMethod();
        when(mockNetconfSession.getRpcReply(anyString())).thenReturn(NETCONF_SYNTAX_ERROR_MSG_FROM_DEVICE);

        assertThatThrownBy(() -> mockNetconfSession.executeRPC(TestConstants.LLDP_REQUEST).toString())
                .isInstanceOf(NetconfException.class)
                .hasMessage("Netconf server detected an error: netconf error: syntax error");
    }

    @Test
    public void GIVEN_stringWithoutRPC_fixupRPC_THEN_returnStringWrappedWithRPCTags() {
        assertThat(NetconfSession.fixupRpc("fake string"))
                .isEqualTo("<rpc><fake string/></rpc>" + DEVICE_PROMPT);
    }

    @Test
    public void GIVEN_stringWithRPCTags_fixupRPC_THEN_returnWrappedString() {
        assertThat(NetconfSession.fixupRpc("<rpc>fake string</rpc>"))
                .isEqualTo("<rpc>fake string</rpc>" + DEVICE_PROMPT);
    }

    @Test
    public void GIVEN_stringWithTag_fixupRPC_THEN_returnWrappedString() {
        assertThat(NetconfSession.fixupRpc("<fake string/>"))
                .isEqualTo("<rpc><fake string/></rpc>" + DEVICE_PROMPT);
    }

    @Test
    public void GIVEN_nullString_WHEN_fixupRPC_THEN_throwException() {
        //noinspection ConstantConditions
        assertThatThrownBy(() -> NetconfSession.fixupRpc(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Null RPC");
    }

    private NetconfSession createNetconfSession() throws IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new NetconfException(String.format("Error creating XML Parser: %s", e.getMessage()));
        }

        return new NetconfSession(mockChannel, CONNECTION_TIMEOUT, COMMAND_TIMEOUT, FAKE_HELLO, builder);
    }
}
