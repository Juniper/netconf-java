package net.juniper.netconf;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;


@Category(Test.class)
public class DeviceTest {

    private static final String TEST_HOSTNAME = "hostname";
    private static final String TEST_USERNAME = "username";
    private static final String TEST_PASSWORD = "password";
    private static final int DEFAULT_NETCONF_PORT = 830;
    private static final int DEFAULT_TIMEOUT = 5000;

    private Device createTestDevice() throws NetconfException {
        return Device.builder()
                .hostName(TEST_HOSTNAME)
                .userName(TEST_USERNAME)
                .password(TEST_PASSWORD)
                .strictHostKeyChecking(false)
                .build();
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
        assertFalse(device.isKeyBasedAuthentication());
        assertNull(device.getPemKeyFile());
        assertNull(device.getHostKeysFileName());
    }

    @Test
    public void GIVEN_newDevice_WHEN_withNullUserName_THEN_throwsException() {
        assertThatThrownBy(() -> Device.builder().hostName("foo").build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("userName is marked @NonNull but is null");
    }

    @Test
    public void GIVEN_newDevice_WHEN_withHostName_THEN_throwsException() {
        assertThatThrownBy(() -> Device.builder().userName("foo").build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("hostName is marked @NonNull but is null");
    }

    @Test
    public void GIVEN_newDevice_WHEN_checkIfConnected_THEN_returnFalse() throws NetconfException {
        Device device = createTestDevice();
        assertFalse(device.isConnected());
    }
}
