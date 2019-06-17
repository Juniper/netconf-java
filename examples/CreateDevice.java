import net.juniper.netconf.Device;
import net.juniper.netconf.NetconfException;

class CreateDevice {

    private static final String HOSTNAME = "HOSTNAME";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "passwd!";
    private static final String PEM_KEY_FILE_PATH = "/tmp/pemFile";


    /**
     * Create a new Device using username and password authentication.
     *
     * @return an unconnected Device object.
     * @throws NetconfException if there are issues creating the Device.
     */
    public static Device createDevice() throws NetconfException {
        return Device.builder()
                .hostName(HOSTNAME)
                .userName(USERNAME)
                .password(PASSWORD)
                .strictHostKeyChecking(false)
                .build();
    }

    /**
     * Create a new Device using username and public key file.
     *
     * @param keyFile the path to a private key file used to authenticate to the Device.
     * @return an unconnected Device object.
     * @throws NetconfException if there are issues creating the Device.
     */
    public static Device createDeviceWithKeyAuth(String keyFile) throws NetconfException {
        return Device.builder()
                .hostName(HOSTNAME)
                .userName(USERNAME)
                .pemKeyFile(PEM_KEY_FILE_PATH)
                .strictHostKeyChecking(false)
                .build();
    }
}
