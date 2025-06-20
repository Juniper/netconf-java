package net.juniper.netconf;

import com.jcraft.jsch.ChannelSubsystem;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmlunit.assertj.XmlAssert;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class DeviceTest {

    private static final String TEST_HOSTNAME = "hostname";
    private static final String TEST_USERNAME = "username";
    private static final String TEST_PASSWORD = "password";
    private static final int DEFAULT_NETCONF_PORT = 830;
    private static final int OTHER_NETCONF_PORT = 990;
    private static final int DEFAULT_TIMEOUT = 5000;
    private static final int OTHER_TIMEOUT = 1000;
    private static final String TEST_FILENAME = "TEST_FILENAME";
    private static final String SUBSYSTEM = "subsystem";
    private static final String HELLO_WITH_DEFAULT_CAPABILITIES = """
        <?xml version="1.0" encoding="utf-8"?>\
        <hello xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
        <capabilities>
        <capability>urn:ietf:params:netconf:base:1.0</capability>
        <capability>urn:ietf:params:netconf:base:1.0#candidate</capability>
        <capability>urn:ietf:params:netconf:base:1.0#confirmed-commit</capability>
        <capability>urn:ietf:params:netconf:base:1.0#validate</capability>
        <capability>urn:ietf:params:netconf:base:1.0#url?protocol=http,ftp,file</capability>
        <capability>urn:ietf:params:netconf:base:1.1</capability>
        </capabilities>
        </hello>""";
    private static final String HELLO_WITH_BASE_CAPABILITIES = """
        <?xml version="1.0" encoding="utf-8"?>\
        <hello xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
        <capabilities>
        <capability>urn:ietf:params:netconf:base:1.0</capability>
        <capability>urn:ietf:params:netconf:base:1.1</capability>
        </capabilities>
        </hello>""";

    private ByteArrayOutputStream outputStream;

    private Device createTestDevice() throws NetconfException {
        return Device.builder()
                .hostName(TEST_HOSTNAME)
                .userName(TEST_USERNAME)
                .password(TEST_PASSWORD)
                .strictHostKeyChecking(false)
                .build();
    }

    @BeforeEach
    public void setUp() {
        outputStream = new ByteArrayOutputStream();
    }

    @Test
    public void GIVEN_requiredParameters_THEN_createDevice() throws NetconfException {
        Device device = createTestDevice();
        assertThat(device.getHostName()).isEqualTo(TEST_HOSTNAME);
        assertThat(device.getUserName()).isEqualTo(TEST_USERNAME);
        assertThat(device.getPassword()).isEqualTo(TEST_PASSWORD);
        assertThat(device.getPort()).isEqualTo(DEFAULT_NETCONF_PORT);
        assertThat(device.getConnectionTimeout()).isEqualTo(DEFAULT_TIMEOUT);
        assertThat(device.getCommandTimeout()).isEqualTo(DEFAULT_TIMEOUT);
        assertThat(device.isKeyBasedAuthentication()).isFalse();
        assertThat(device.getPemKeyFile()).isNull();
        assertThat(device.getHostKeysFileName()).isNull();
    }

    @Test
    public void GIVEN_deviceBuilder_THEN_buildDevice() throws NetconfException {
        Device device = Device.builder()
            .hostName(TEST_HOSTNAME)
            .userName(TEST_USERNAME)
            .password(TEST_PASSWORD)
            .port(OTHER_NETCONF_PORT)
            .pemKeyFile(TEST_FILENAME)
            .connectionTimeout(OTHER_TIMEOUT)
            .commandTimeout(OTHER_TIMEOUT)
            .keyBasedAuth(TEST_FILENAME)
            .hostKeysFileName(TEST_FILENAME)
            .build();
        assertThat(device.getHostName()).isEqualTo(TEST_HOSTNAME);
        assertThat(device.getUserName()).isEqualTo(TEST_USERNAME);
        assertThat(device.getPassword()).isEqualTo(TEST_PASSWORD);
        assertThat(device.getPort()).isEqualTo(OTHER_NETCONF_PORT);
        assertThat(device.getConnectionTimeout()).isEqualTo(OTHER_TIMEOUT);
        assertThat(device.getCommandTimeout()).isEqualTo(OTHER_TIMEOUT);
        assertThat(device.isKeyBasedAuthentication()).isTrue();
        assertThat(device.getPemKeyFile()).isEqualTo(TEST_FILENAME);
        assertThat(device.getHostKeysFileName()).isEqualTo(TEST_FILENAME);
        List<String> caps = device.getDefaultClientCapabilities();
        assertThat(caps).isNotNull();
        assertThat(caps).contains("urn:ietf:params:netconf:base:1.0");
    }

    @Test
    public void GIVEN_sshAvailableNetconfNot_THEN_closeDevice() throws Exception {
        JSch sshClient = mock(JSch.class);
        Session session = mock(Session.class);
        HostKeyRepository hostKeyRepository = mock(HostKeyRepository.class);
        ChannelSubsystem channel = mock(ChannelSubsystem.class);
        when(channel.isConnected()).thenReturn(false);

        when(session.isConnected()).thenReturn(true);
        when(session.openChannel(eq(SUBSYSTEM))).thenReturn(channel);
        doThrow(new JSchException("failed to send channel request")).when(channel).connect(eq(DEFAULT_TIMEOUT));

        when(sshClient.getSession(eq(TEST_USERNAME), eq(TEST_HOSTNAME), eq(DEFAULT_NETCONF_PORT))).thenReturn(session);
        when(sshClient.getHostKeyRepository()).thenReturn(hostKeyRepository);

        try (Device device = Device.builder()
                .sshClient(sshClient)
                .hostName(TEST_HOSTNAME)
                .userName(TEST_USERNAME)
                .password(TEST_PASSWORD)
                .strictHostKeyChecking(false)
                .build()) {
            device.connect();
        } catch (NetconfException e) {
            // Do nothing
        }

        verify(channel).connect(eq(DEFAULT_TIMEOUT));
        verify(channel).setSubsystem(anyString());
        verify(channel).getInputStream();
        verify(channel).getOutputStream();
        verify(channel).isConnected();

        verify(session).disconnect();
        verify(session).openChannel(eq(SUBSYSTEM));
        verify(session, times(2)).isConnected();
        verify(session).getTimeout();
        verify(session).connect(eq(DEFAULT_TIMEOUT));
        verify(session).setTimeout(eq(DEFAULT_TIMEOUT));
        verify(session, times(2)).setConfig(anyString(), anyString());
        verify(session).setPassword(anyString());

        verify(sshClient).getSession(eq(TEST_USERNAME), eq(TEST_HOSTNAME), eq(DEFAULT_NETCONF_PORT));
        verify(sshClient).getHostKeyRepository();
        verify(sshClient).setHostKeyRepository(hostKeyRepository);

        verifyNoMoreInteractions(channel);
        verifyNoMoreInteractions(session);
        verifyNoMoreInteractions(sshClient);
    }

    @Test
    public void GIVEN_newDevice_WHEN_withNullUserName_THEN_throwsException() {
        assertThatThrownBy(() -> Device.builder().hostName("foo").build())
                .isInstanceOf(NetconfException.class)
                .hasMessage("userName is required");
    }

    @Test
    public void GIVEN_newDevice_WHEN_withHostName_THEN_throwsException() {
        assertThatThrownBy(() -> Device.builder().userName("foo").build())
                .isInstanceOf(NetconfException.class)
                .hasMessage("hostName is required");
    }

    @Test
    public void GIVEN_newDevice_WHEN_checkIfConnected_THEN_returnFalse() throws NetconfException {
        Device device = createTestDevice();
        assertThat(device.isConnected()).isFalse();
    }

    @Test
    public void GIVEN_newDevice_WHEN_connect_THEN_sendHelloWithDefaultCapabilities() throws Exception {

        final JSch sshClient = givenConnectingSshClient();

        final Device device = Device.builder()
            .sshClient(sshClient)
            .hostName(TEST_HOSTNAME)
            .userName(TEST_USERNAME)
            .password(TEST_PASSWORD)
            .strictHostKeyChecking(false)
            .build();
        device.connect();

        final String message = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(message).endsWith(NetconfConstants.DEVICE_PROMPT);
        final String hello = message.substring(0, message.length() - NetconfConstants.DEVICE_PROMPT.length());
        XmlAssert.assertThat(hello)
            .and(HELLO_WITH_DEFAULT_CAPABILITIES)
            .ignoreWhitespace()
            .areIdentical();
    }

    @Test
    public void GIVEN_newDevice_WHEN_connect_THEN_sendHelloWithCustomCapabilities() throws Exception {

        final JSch sshClient = givenConnectingSshClient();

        final Device device = Device.builder()
            .sshClient(sshClient)
            .netconfCapabilities(Collections.singletonList(NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0))
            .hostName(TEST_HOSTNAME)
            .userName(TEST_USERNAME)
            .password(TEST_PASSWORD)
            .strictHostKeyChecking(false)
            .build();
        device.connect();

        final String message = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(message).endsWith(NetconfConstants.DEVICE_PROMPT);
        final String hello = message.substring(0, message.length() - NetconfConstants.DEVICE_PROMPT.length());
        XmlAssert.assertThat(hello)
            .and(HELLO_WITH_BASE_CAPABILITIES)
            .ignoreWhitespace()
            .areIdentical();
    }

    private JSch givenConnectingSshClient() throws IOException, JSchException {
        final Session sshSession = mock(Session.class);
        when(sshSession.isConnected())
            .thenReturn(true);
        final ChannelSubsystem sshChannel = mock(ChannelSubsystem.class);
        when(sshChannel.getOutputStream())
            .thenReturn(outputStream);
        final ByteArrayInputStream is = new ByteArrayInputStream(
            ("<hello/>" + NetconfConstants.DEVICE_PROMPT)
                .getBytes(StandardCharsets.UTF_8));
        when(sshChannel.getInputStream())
            .thenReturn(is);
        when(sshSession.openChannel(eq(SUBSYSTEM)))
            .thenReturn(sshChannel);
        final JSch sshClient = mock(JSch.class);
        when(sshClient.getSession(eq(TEST_USERNAME), eq(TEST_HOSTNAME), eq(DEFAULT_NETCONF_PORT)))
            .thenReturn(sshSession);
        return sshClient;
    }
}
