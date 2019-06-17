package net.juniper.netconf;


import com.jcraft.jsch.Channel;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import javax.xml.parsers.DocumentBuilder;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@Category(Test.class)
public class NetconfSessionTest {

    private final Channel mockChannel = mock(Channel.class);
    private final DocumentBuilder mockBuilder = mock(DocumentBuilder.class);

    private static final String FAKE_HELLO = "fake hello";
    private static final String MERGE_LOAD_TYPE = "merge";
    private static final String REPLACE_LOAD_TYPE = "replace";
    private static final String BAD_LOAD_TYPE = "other";
    private static final String FAKE_TEXT_FILE_PATH = "fakepath";

    private static final String DEVICE_PROMPT = "]]>]]>";
    private static final String FAKE_RPC_REPLY = "<rpc>fakedata</rpc>";
    private static final String NETCONF_SYNTAX_ERROR_MSG_FROM_DEVICE = "netconf error: syntax error";


    private NetconfSession createNetconfSession() throws IOException {
        return new NetconfSession(mockChannel, 10, null, null);
    }

    @Test
    public void GIVEN_getCandidateConfig_WHEN_syntaxError_THEN_throwNetconfException() throws Exception {
        NetconfSession mockNetconfSession = mock(NetconfSession.class);
        when(mockNetconfSession.getCandidateConfig()).thenCallRealMethod();
        when(mockNetconfSession.getRpcReply(anyString())).thenReturn(NETCONF_SYNTAX_ERROR_MSG_FROM_DEVICE);

        assertThatThrownBy(mockNetconfSession::getCandidateConfig)
                .isInstanceOf(NetconfException.class)
                .hasMessage("Netconf server detected an error: netconf error: syntax error");
    }

    @Test
    public void GIVEN_getRunningConfig_WHEN_syntaxError_THEN_throwNetconfException() throws Exception {
        NetconfSession mockNetconfSession = mock(NetconfSession.class);
        when(mockNetconfSession.getRunningConfig()).thenCallRealMethod();
        when(mockNetconfSession.getRpcReply(anyString())).thenReturn(NETCONF_SYNTAX_ERROR_MSG_FROM_DEVICE);

        assertThatThrownBy(mockNetconfSession::getRunningConfig)
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
}
