/*
 Copyright (c) 2013 Juniper Networks, Inc.
 All Rights Reserved

 Use is subject to license terms.
*/

package net.juniper.netconf;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSubsystem;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import net.juniper.netconf.element.Datastore;
import net.juniper.netconf.element.Hello;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A <code>Device</code> is used to define a Netconf server.
 * <p>
 * A new device is created using the Device.Builder.build()
 * <p>
 * Example:
 * <pre>
 * {@code}
 * Device device = Device.builder().hostName("hostname")
 *     .userName("username")
 *     .password("password")
 *     .hostKeysFileName("hostKeysFileName")
 *     .build();
 * </pre>
 * <ol>
 * <li>creates a {@link Device} object.</li>
 * <li>perform netconf operations on the Device object.</li>
 * <li>If needed, call the method createNetconfSession() to create another
 * NetconfSession.</li>
 * <li>Finally, one must close the Device and release resources with the
 * {@link #close() close()} method.</li>
 * </ol>
 */
public class Device implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Device.class);

    private static final int DEFAULT_NETCONF_PORT = 830;
    private static final int DEFAULT_TIMEOUT = 5000;
    private static final List<String> DEFAULT_CLIENT_CAPABILITIES = Arrays.asList(
        NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
        NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0 + "#candidate",
        NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0 + "#confirmed-commit",
        NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0 + "#validate",
        NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0 + "#url?protocol=http,ftp,file"
    );

    private final JSch sshClient;
    private final String hostName;
    private final int port;
    private final int connectionTimeout;
    private final int commandTimeout;

    private final String userName;
    private final String password;

    private final boolean keyBasedAuthentication;
    private final String pemKeyFile;

    private final boolean strictHostKeyChecking;
    private final String hostKeysFileName;

    private final DocumentBuilder xmlBuilder;
    private final List<String> netconfCapabilities;
    private final String helloRpc;

    private ChannelSubsystem sshChannel;
    private Session sshSession;
    private NetconfSession netconfSession;

    /**
     * Returns a new {@link Builder} for constructing {@link Device} instances.
     *
     * @return fresh {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link Device}.  Configure desired fields and call
     * {@link #build()} to obtain an immutable instance.
     */
    public static final class Builder {
        /**
         * Creates an empty {@code Builder}.
         */
        private Builder() {
        }

        private JSch sshClient = new JSch();
        private String hostName;
        private int port = DEFAULT_NETCONF_PORT;
        private int timeout = DEFAULT_TIMEOUT;
        private Integer connectionTimeout;
        private Integer commandTimeout;
        private String userName;
        private String password;
        private boolean keyAuth = false;
        private String pemKeyFile;
        private boolean strictHostKeyChecking = true;
        private String hostKeysFileName;
        private List<String> netconfCapabilities = DEFAULT_CLIENT_CAPABILITIES;


        /**
         * Replaces the default {@link JSch} instance with a caller‑supplied one.
         * <p>
         * Supplying your own {@code JSch} lets you pre‑configure global settings
         * like proxies or identity repositories before a {@link Device} is built.
         *
         * @param sshClient pre‑configured {@link JSch} instance (must not be {@code null})
         * @return this {@code Builder} for fluent chaining
         * @throws NullPointerException if {@code sshClient} is {@code null}
         */
        public Builder sshClient(JSch sshClient) {
            this.sshClient = Objects.requireNonNull(sshClient);
            return this;
        }

        /**
         * Sets the DNS host name or IP address of the Netconf server.
         *
         * @param hostName server host name or IP
         * @return this {@code Builder} for fluent chaining
         */
        public Builder hostName(String hostName) {
            this.hostName = hostName;
            return this;
        }

        /**
         * Specifies the TCP port on which the Netconf SSH subsystem listens.
         * <p>
         * Defaults to {@code 830} if not set.
         *
         * @param port TCP port number
         * @return this {@code Builder} for fluent chaining
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * Sets a default timeout (in milliseconds) that is used when a more
         * specific {@link #connectionTimeout(int)} or {@link #commandTimeout(int)}
         * value has not been provided.
         *
         * @param ms timeout in milliseconds
         * @return this {@code Builder} for fluent chaining
         */
        public Builder timeout(int ms) {
            this.timeout = ms;
            return this;
        }

        /**
         * Overrides the default SSH connection timeout.
         *
         * @param ms timeout in milliseconds for establishing the SSH session
         * @return this {@code Builder} for fluent chaining
         */
        public Builder connectionTimeout(int ms) {
            this.connectionTimeout = ms;
            return this;
        }

        /**
         * Sets the per‑command timeout that applies to individual NETCONF RPCs
         * (distinct from the SSH connection timeout).
         *
         * @param ms timeout in milliseconds
         * @return this {@code Builder} for fluent chaining
         */
        public Builder commandTimeout(int ms) {
            this.commandTimeout = ms;
            return this;
        }

        /**
         * Specifies the login user name for the SSH/NETCONF session.
         *
         * @param userName user name string
         * @return this {@code Builder} for fluent chaining
         */
        public Builder userName(String userName) {
            this.userName = userName;
            return this;
        }

        /**
         * Sets the password used for password‑based SSH authentication.
         * <p>
         * Ignored if {@link #keyBasedAuth(String)} is used instead.
         *
         * @param password login password
         * @return this {@code Builder} for fluent chaining
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * Enables key‑based SSH authentication and sets the path to the PEM
         * private‑key file.
         *
         * @param pem absolute or relative path to the PEM‑formatted private key
         * @return this {@code Builder} for fluent chaining
         */
        public Builder keyBasedAuth(String pem) {
            this.keyAuth = true;
            this.pemKeyFile = pem;
            return this;
        }

        /**
         * Specifies the path to the PEM‑formatted private key that will be used
         * for key‑based SSH authentication.
         * <p>
         * Note: calling this method alone does <em>not</em> switch the builder
         * to key‑authentication mode; be sure to also invoke
         * {@link #keyBasedAuth(String)} or set {@link #keyAuth} explicitly.
         *
         * @param pemKeyFile absolute or relative path to the PEM key file
         * @return this {@code Builder} for fluent chaining
         */
        public Builder pemKeyFile(String pemKeyFile) {
            this.pemKeyFile = pemKeyFile;
            return this;
        }

        /**
         * Disables strict host‑key checking so every host key is trusted
         * (equivalent to {@code StrictHostKeyChecking=no} in OpenSSH).
         * <p>
         * <strong>Security note:</strong> Accepting all host keys makes the
         * connection vulnerable to man‑in‑the‑middle attacks.  Use this only in
         * development or other low‑risk environments.
         *
         * @return this {@code Builder} for fluent chaining
         */
        public Builder trustAllHostKeys() {
            this.strictHostKeyChecking = false;
            return this;
        }

        /**
         * Enables or disables strict host‑key checking for the SSH session.
         * <p>
         * When set to {@code true} the underlying JSch session will verify the
         * server's host key against the known‑hosts file and refuse the
         * connection if it is unknown.  When {@code false} the connection will
         * proceed even if the server's host key is not listed.
         *
         * @param strictHostKeyChecking whether to enforce host‑key checking
         * @return this {@code Builder} for fluent chaining
         */
        public Builder strictHostKeyChecking(boolean strictHostKeyChecking) {
            this.strictHostKeyChecking = strictHostKeyChecking;
            return this;
        }

        /**
         * Specifies the path to the SSH known‑hosts file used when
         * {@link #strictHostKeyChecking(boolean)} is enabled.
         *
         * @param hostKeysFileName absolute or relative path to the known‑hosts file
         * @return this {@code Builder} for fluent chaining
         */
        public Builder hostKeysFileName(String hostKeysFileName) {
            this.hostKeysFileName = hostKeysFileName;
            return this;
        }

        /**
         * Replaces the default list of client‑side Netconf capabilities that
         * will be advertised in the initial {@code &lt;hello&gt;} message.
         * <p>
         * The supplied list is defensively copied and wrapped in an
         * unmodifiable view, so subsequent modifications to the original list
         * do not affect the builder.
         *
         * @param caps list of capability URIs; must not be {@code null}
         * @return this {@code Builder} for fluent chaining
         * @throws NullPointerException if {@code caps} is {@code null}
         */
        public Builder netconfCapabilities(List<String> caps) {
            this.netconfCapabilities = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(caps));
            return this;
        }

        /**
         * Validates all required fields and constructs an immutable {@link Device}.
         * <p>
         * Mandatory parameters include host name, user credentials (or key), and
         * host‑key settings when strict checking is enabled.  If any of these are
         * missing or inconsistent, a {@link NetconfException} is thrown.
         *
         * @return a fully‑configured {@link Device} instance
         * @throws NetconfException if validation fails or if an internal error
         *                          occurs while initialising auxiliary resources
         */
        public Device build() throws NetconfException {
            // Validation logic moved from Device constructor
            if (hostName == null) throw new NetconfException("hostName is required");
            if (userName == null) throw new NetconfException("userName is required");
            if (!keyAuth && password == null)
                throw new NetconfException("Password is required for password auth");
            if (strictHostKeyChecking && hostKeysFileName == null)
                throw new NetconfException("hostKeysFileName required when strictHostKeyChecking=true");
            if (keyAuth && pemKeyFile == null)
                throw new NetconfException("pemKeyFile required when keyAuth=true");
            try {
                return new Device(this);
            } catch (NetconfException e) {
                throw e;
            }
        }
    }

    /* ------------------------------------------------------------------
     * Private constructor used by Builder
     * ------------------------------------------------------------------ */
    private Device(Builder b) throws NetconfException {
        this.sshClient = b.sshClient;
        this.hostName = b.hostName;
        this.port = b.port;
        this.connectionTimeout = b.connectionTimeout != null ? b.connectionTimeout : b.timeout;
        this.commandTimeout = b.commandTimeout != null ? b.commandTimeout : b.timeout;

        this.userName = b.userName;
        this.password = b.password;
        this.keyBasedAuthentication = b.keyAuth;
        this.pemKeyFile = b.pemKeyFile;
        this.strictHostKeyChecking = b.strictHostKeyChecking;
        this.hostKeysFileName = b.hostKeysFileName;

        this.netconfCapabilities = b.netconfCapabilities;

        try {
            this.xmlBuilder = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (javax.xml.parsers.ParserConfigurationException e) {
            throw new NetconfException("Cannot create XML Parser", e);
        }

        this.helloRpc = createHelloRPC(this.netconfCapabilities);
    }


    /**
     * Get the client capabilities that are advertised to the Netconf server by default.
     * RFC 6241 describes the standard netconf capabilities.
     * <a href="https://tools.ietf.org/html/rfc6241#section-8">...</a>
     *
     * @return List of default client capabilities.
     */
    protected List<String> getDefaultClientCapabilities() {
        return DEFAULT_CLIENT_CAPABILITIES;
    }

    /**
     * Given a list of netconf capabilities, generate the netconf hello rpc message.
     * <a href="https://tools.ietf.org/html/rfc6241#section-8.1">...</a>
     *
     * @param capabilities A list of netconf capabilities
     * @return the hello RPC that represents those capabilities.
     */
    private String createHelloRPC(List<String> capabilities) {
        return Hello.builder()
            .capabilities(capabilities)
            .build()
            .getXml()
            + NetconfConstants.DEVICE_PROMPT;
    }

    /**
     * Create a new Netconf session.
     *
     * @return NetconfSession
     * @throws NetconfException if there are issues communicating with the Netconf server.
     */
    private NetconfSession createNetconfSession() throws NetconfException {
        if (!isConnected()) {
            try {
                if (strictHostKeyChecking) {
                    if (hostKeysFileName == null) {
                        throw new NetconfException("Cannot do strictHostKeyChecking if hostKeysFileName is null");
                    }
                    sshClient.setKnownHosts(hostKeysFileName);
                }
            } catch (JSchException e) {
                throw new NetconfException(String.format("Error loading known hosts file: %s", e.getMessage()), e);
            }
            sshClient.setHostKeyRepository(sshClient.getHostKeyRepository());
            log.info("Connecting to host {} on port {}.", hostName, port);
            if (keyBasedAuthentication) {
                loadPrivateKey();
                sshSession = loginWithPrivateKey(connectionTimeout);
            } else {
                sshSession = loginWithUserPass(connectionTimeout);
            }
            try {
                sshSession.setTimeout(connectionTimeout);
            } catch (JSchException e) {
                throw new NetconfException(String.format("Error setting session timeout: %s", e.getMessage()), e);
            }
            if (sshSession.isConnected()) {
                log.info("Connected to host {} - Timeout set to {} msecs.", hostName, sshSession.getTimeout());
            } else {
                throw new NetconfException("Failed to connect to host. Unknown reason");
            }
        }
        try {
            sshChannel = (ChannelSubsystem) sshSession.openChannel("subsystem");
            sshChannel.setSubsystem("netconf");
            return new NetconfSession(sshChannel, connectionTimeout, commandTimeout, helloRpc, xmlBuilder);
        } catch (JSchException | IOException e) {
            throw new NetconfException("Failed to create Netconf session:" +
                e.getMessage(), e);
        }
    }

    private Session loginWithUserPass(int timeoutMilliSeconds) throws NetconfException {
        try {
            Session session = sshClient.getSession(userName, hostName, port);
            session.setConfig("userauth", "password");
            session.setConfig("StrictHostKeyChecking", isStrictHostKeyChecking() ? "yes" : "no");
            session.setPassword(password);
            session.connect(timeoutMilliSeconds);
            return session;
        } catch (JSchException e) {
            throw new NetconfException(String.format("Error connecting to host: %s - Error: %s",
                hostName, e.getMessage()), e);
        }
    }

    private Session loginWithPrivateKey(int timeoutMilliSeconds) throws NetconfException {
        try {
            Session session = sshClient.getSession(userName, hostName, port);
            session.setConfig("userauth", "publickey");
            session.setConfig("StrictHostKeyChecking", isStrictHostKeyChecking() ? "yes" : "no");
            session.connect(timeoutMilliSeconds);
            return session;
        } catch (JSchException e) {
            throw new NetconfException(String.format("Error using key pair file: %s to connect to host: %s - Error: %s",
                pemKeyFile, hostName, e.getMessage()), e);
        }
    }

    /**
     * Connect to the Device, and establish a default NETCONF session.
     *
     * @throws NetconfException if there are issues communicating with the Netconf server.
     */
    public void connect() throws NetconfException {
        if (hostName == null || userName == null || (password == null &&
            pemKeyFile == null)) {
            throw new NetconfException("Login parameters of Device can't be " +
                "null.");
        }
        netconfSession = this.createNetconfSession();
    }

    private void loadPrivateKey() throws NetconfException {
        try {
            sshClient.addIdentity(pemKeyFile);
        } catch (JSchException e) {
            throw new NetconfException(String.format("Error parsing the pemKeyFile: %s", e.getMessage()), e);
        }
    }

    /**
     * Reboot the device.
     *
     * @return RPC reply sent by Netconf server.
     * @throws java.io.IOException If there are issues communicating with the Netconf server.
     */
    public String reboot() throws IOException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        return this.netconfSession.reboot();
    }

    /**
     * Indicates whether both the SSH {@link Session} and {@link ChannelSubsystem}
     * are currently connected.
     *
     * @return {@code true} if the device is connected
     */
    public boolean isConnected() {
        return (isChannelConnected() && isSessionConnected());
    }

    private boolean isChannelConnected() {
        if (sshChannel == null) {
            return false;
        }
        return sshChannel.isConnected();
    }

    private boolean isSessionConnected() {
        if (sshSession == null) {
            return false;
        }
        return sshSession.isConnected();
    }

    /**
     * Close the connection to the Netconf server. All associated Netconf
     * sessions will be closed, too. Can be called at any time. Don't forget to
     * call this once you don't need the device anymore.
     */
    @Override
    public void close() {
        if (isChannelConnected()) {
            sshChannel.disconnect();
        }
        if (isSessionConnected()) {
            sshSession.disconnect();
        }
    }

    /**
     * Execute a command in shell mode.
     *
     * @param command The command to be executed in shell mode.
     * @return Result of the command execution, as a String.
     * @throws IOException if there are issues communicating with the Netconf server.
     */
    public String runShellCommand(String command) throws IOException {
        if (!isConnected()) {
            return "Could not find open connection.";
        }
        ChannelExec channel;
        try {
            channel = (ChannelExec) sshSession.openChannel("exec");
        } catch (JSchException e) {
            throw new NetconfException(String.format("Failed to open exec session: %s", e.getMessage()), e);
        }
        channel.setCommand(command);
        InputStream stdout;
        BufferedReader bufferReader;
        stdout = channel.getInputStream();

        bufferReader = new BufferedReader(new InputStreamReader(stdout, Charset.defaultCharset()));
        try {
            StringBuilder reply = new StringBuilder();
            while (true) {
                String line;
                try {
                    line = bufferReader.readLine();
                } catch (Exception e) {
                    throw new NetconfException(e.getMessage(), e);
                }
                if (line == null || line.equals(NetconfConstants.EMPTY_LINE))
                    break;
                reply.append(line).append(NetconfConstants.LF);
            }
            return reply.toString();
        } finally {
            bufferReader.close();
        }
    }

    /**
     * Execute a command in shell mode.
     *
     * @param command The command to be executed in shell mode.
     * @return Result of the command execution, as a BufferedReader. This is
     * useful if we want continuous stream of output, rather than wait
     * for whole output till command execution completes.
     * @throws IOException if there are issues communicating with the Netconf server.
     */
    public BufferedReader runShellCommandRunning(String command)
        throws IOException {
        if (!isConnected()) {
            throw new IOException("Could not find open connection");
        }
        ChannelExec channel;
        try {
            channel = (ChannelExec) sshSession.openChannel("exec");
        } catch (JSchException e) {
            throw new NetconfException(String.format("Failed to open exec session: %s", e.getMessage()), e);
        }
        InputStream stdout = channel.getInputStream();
        return new BufferedReader(new InputStreamReader(stdout, Charset.defaultCharset()));
    }

    /**
     * Send an RPC(as String object) over the default Netconf session and get the
     * response as an XML object.
     * <p>Convenience overload for raw‑string payloads.</p>
     *
     * @param rpcContent RPC content to be sent. For example, to send an rpc
     *                   &lt;rpc&gt;&lt;get-chassis-inventory/&gt;&lt;/rpc&gt;, the
     *                   String to be passed can be
     *                   "&lt;get-chassis-inventory/&gt;" OR
     *                   "get-chassis-inventory" OR
     *                   "&lt;rpc&gt;&lt;get-chassis-inventory/&gt;&lt;/rpc&gt;"
     * @return RPC reply sent by Netconf server
     * @throws java.io.IOException      If there are errors communicating with the netconf server.
     * @throws org.xml.sax.SAXException If there are errors parsing the XML reply.
     */
    public XML executeRPC(String rpcContent) throws SAXException, IOException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        return netconfSession.executeRPC(rpcContent);
    }

    /**
     * Send an RPC(as XML object) over the Netconf session and get the response
     * as an XML object.
     * <p>Use when the payload is already assembled as an {@link XML} helper object.</p>
     *
     * @param rpc RPC to be sent. Use the XMLBuilder to create RPC as an
     *            XML object.
     * @return RPC reply sent by Netconf server
     * @throws java.io.IOException      If there are errors communicating with the netconf server.
     * @throws org.xml.sax.SAXException If there are errors parsing the XML reply.
     */
    public XML executeRPC(XML rpc) throws SAXException, IOException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        return this.netconfSession.executeRPC(rpc);
    }

    /**
     * Send an RPC(as Document object) over the Netconf session and get the
     * response as an XML object.
     * <p>Accepts a DOM {@link org.w3c.dom.Document} that represents the full
     * &lt;rpc&gt; element.</p>
     *
     * @param rpcDoc RPC content to be sent, as a org.w3c.dom.Document object.
     * @return RPC reply sent by Netconf server
     * @throws java.io.IOException      If there are errors communicating with the netconf server.
     * @throws org.xml.sax.SAXException If there are errors parsing the XML reply.
     */
    public XML executeRPC(Document rpcDoc) throws SAXException, IOException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        return this.netconfSession.executeRPC(rpcDoc);
    }

    /**
     * Sends an RPC (as a raw XML string) over the default Netconf session and
     * returns a {@link BufferedReader} for streaming the reply.
     *
     * @param rpcContent XML payload to send (content of the &lt;rpc&gt; element)
     * @return RPC reply as a {@link BufferedReader}
     * @throws IOException           if communication with the server fails
     * @throws IllegalStateException if no Netconf connection exists
     */
    public BufferedReader executeRPCRunning(String rpcContent) throws IOException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        return this.netconfSession.executeRPCRunning(rpcContent);
    }

    /**
     * Send an RPC(as XML object) over the Netconf session and get the response
     * as a BufferedReader.
     * <p>Streams the reply incrementally, suitable for large responses.</p>
     *
     * @param rpc RPC to be sent. Use the XMLBuilder to create RPC as an
     *            XML object.
     * @return RPC reply sent by Netconf server as a BufferedReader. This is
     * useful if we want continuous stream of output, rather than wait
     * for whole output till command execution completes.
     * @throws java.io.IOException if there are errors communicating with the Netconf server.
     */
    public BufferedReader executeRPCRunning(XML rpc) throws IOException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        return this.netconfSession.executeRPCRunning(rpc);
    }

    /**
     * Sends an RPC (as a DOM {@link Document}) over the active NETCONF session
     * and returns a {@link BufferedReader} for streaming the reply.
     * <p>
     * Use this variant when you need to consume the server response
     * <em>incrementally</em>&nbsp;&mdash; for example, when the RPC produces a
     * large dataset or when you want to start processing output before the
     * device finishes sending the final <code>]]&gt;]]&gt;</code> prompt.
     * </p>
     *
     * @param rpcDoc the complete &lt;rpc&gt; element encoded as a DOM
     *               {@link Document}; must not be {@code null}
     *
     * @return a {@link BufferedReader} connected to the server’s reply stream
     *
     * @throws IOException           if an I/O error occurs while sending the
     *                               request or reading the reply
     * @throws IllegalStateException if no NETCONF connection is established
     */
    public BufferedReader executeRPCRunning(Document rpcDoc) throws IOException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        return this.netconfSession.executeRPCRunning(rpcDoc);
    }

    /**
     * Get the session ID of the Netconf session.
     *
     * @return Session ID
     * @throws IllegalStateException if the connection is not established
     */
    public String getSessionId() {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot get session ID, you need " +
                "to establish a connection first.");
        }
        return this.netconfSession.getSessionId();
    }

    /**
     * Check if the last RPC reply returned from Netconf server has any error.
     *
     * @return true if any errors are found in last RPC reply.
     * @throws SAXException          if there are issues parsing XML from the device.
     * @throws IOException           if there are issues communicating with the device.
     * @throws IllegalStateException if the connection is not established
     */
    public boolean hasError() throws SAXException, IOException {
        if (netconfSession == null) {
            throw new IllegalStateException("No RPC executed yet, you need to" +
                " establish a connection first.");
        }
        return this.netconfSession.hasError();
    }

    /**
     * Check if the last RPC reply returned from Netconf server has any warning.
     *
     * @return true if any errors are found in last RPC reply.
     * @throws SAXException if there are issues parsing XML from the device.
     * @throws IOException  if there are issues communicating with the device.
     */
    public boolean hasWarning() throws SAXException, IOException {
        if (netconfSession == null) {
            throw new IllegalStateException("No RPC executed yet, you need to " +
                "establish a connection first.");
        }
        return this.netconfSession.hasWarning();
    }

    /**
     * Check if the last RPC reply returned from Netconf server, contains
     * &lt;ok/&gt; tag.
     *
     * @return true if &lt;ok/&gt; tag is found in last RPC reply.
     * @throws IllegalStateException if the connection is not established
     */
    public boolean isOK() {
        if (netconfSession == null) {
            throw new IllegalStateException("No RPC executed yet, you need to " +
                "establish a connection first.");
        }
        return this.netconfSession.isOK();
    }

    /**
     * Locks the candidate configuration.
     *
     * @return true if successful.
     * @throws java.io.IOException      If there are errors communicating with the netconf server.
     * @throws org.xml.sax.SAXException If there are errors parsing the XML reply.
     * @throws IllegalStateException    if the connection is not established
     */
    public boolean lockConfig() throws IOException, SAXException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        return this.netconfSession.lockConfig();
    }

    /**
     * Unlocks the candidate configuration.
     *
     * @return true if successful.
     * @throws java.io.IOException      If there are errors communicating with the netconf server.
     * @throws org.xml.sax.SAXException If there are errors parsing the XML reply.
     */
    public boolean unlockConfig() throws IOException, SAXException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        return this.netconfSession.unlockConfig();
    }

    /**
     * Loads the candidate configuration, Configuration should be in XML format.
     *
     * @param configuration Configuration,in XML format, to be loaded. For example,
     *                      "&lt;configuration&gt;&lt;system&gt;&lt;services&gt;&lt;ftp/&gt;
     *                      &lt;services/&gt;&lt;/system&gt;&lt;/configuration/&gt;"
     *                      will load 'ftp' under the 'systems services' hierarchy.
     * @param loadType      You can choose "merge" or "replace" as the loadType.
     * @throws java.io.IOException      If there are errors communicating with the netconf server.
     * @throws org.xml.sax.SAXException If there are errors parsing the XML reply.
     */
    public void loadXMLConfiguration(String configuration, String loadType)
        throws IOException, SAXException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        this.netconfSession.loadXMLConfiguration(configuration, loadType);
    }

    /**
     * Loads the candidate configuration, Configuration should be in text/tree
     * format.
     *
     * @param configuration Configuration,in text/tree format, to be loaded. For example,
     *                      " system {
     *                      services {
     *                      ftp;
     *                      }
     *                      }"
     *                      will load 'ftp' under the 'systems services' hierarchy.
     * @param loadType      You can choose "merge" or "replace" as the loadType.
     * @throws java.io.IOException      If there are errors communicating with the netconf server.
     * @throws org.xml.sax.SAXException If there are errors parsing the XML reply.
     */
    public void loadTextConfiguration(String configuration, String loadType)
        throws IOException, SAXException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        this.netconfSession.loadTextConfiguration(configuration, loadType);
    }

    /**
     * Loads the candidate configuration, Configuration should be in set
     * format.
     * NOTE: This method is applicable only for JUNOS release 11.4 and above.
     *
     * @param configuration Configuration,in set format, to be loaded. For example,
     *                      "set system services ftp"
     *                      will load 'ftp' under the 'systems services' hierarchy.
     *                      To load multiple set statements, separate them by '\n' character.
     * @throws java.io.IOException      If there are errors communicating with the netconf server.
     * @throws org.xml.sax.SAXException If there are errors parsing the XML reply.
     */
    public void loadSetConfiguration(String configuration) throws
        IOException,
        SAXException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        this.netconfSession.loadSetConfiguration(configuration);
    }

    /**
     * Loads the candidate configuration from file,
     * configuration should be in XML format.
     *
     * @param configFile Path name of file containing configuration,in xml format,
     *                   to be loaded.
     * @param loadType   You can choose "merge" or "replace" as the loadType.
     * @throws java.io.IOException      If there are errors communicating with the netconf server.
     * @throws org.xml.sax.SAXException If there are errors parsing the XML reply.
     */
    public void loadXMLFile(String configFile, String loadType)
        throws IOException, SAXException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        this.netconfSession.loadXMLFile(configFile, loadType);
    }

    /**
     * Loads the candidate configuration from file,
     * configuration should be in text/tree format.
     *
     * @param configFile Path name of file containing configuration,in xml format,
     *                   to be loaded.
     * @param loadType   You can choose "merge" or "replace" as the loadType.
     * @throws java.io.IOException      If there are errors communicating with the netconf server.
     * @throws org.xml.sax.SAXException If there are errors parsing the XML reply.
     */
    public void loadTextFile(String configFile, String loadType)
        throws IOException, SAXException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        this.netconfSession.loadTextFile(configFile, loadType);
    }

    /**
     * Loads the candidate configuration from file,
     * configuration should be in set format.
     * NOTE: This method is applicable only for JUNOS release 11.4 and above.
     *
     * @param configFile Path name of file containing configuration,in set format,
     *                   to be loaded.
     * @throws java.io.IOException      If there are errors communicating with the netconf server.
     * @throws org.xml.sax.SAXException If there are errors parsing the XML reply.
     */
    public void loadSetFile(String configFile) throws
        IOException, SAXException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        this.netconfSession.loadSetFile(configFile);
    }

    /**
     * Commit the candidate configuration.
     *
     * @throws net.juniper.netconf.CommitException if there was an error committing the configuration.
     * @throws java.io.IOException                 If there are errors communicating with the netconf server.
     * @throws org.xml.sax.SAXException            If there are errors parsing the XML reply.
     */
    public void commit() throws CommitException, IOException, SAXException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        this.netconfSession.commit();
    }

    /**
     * Commit the candidate configuration, temporarily. This is equivalent of
     * 'commit confirm'
     *
     * @param seconds Time in seconds, after which the previous active configuration
     *                is reverted back to.
     * @throws net.juniper.netconf.CommitException if there was an error committing the configuration.
     * @throws java.io.IOException                 If there are errors communicating with the netconf server.
     * @throws org.xml.sax.SAXException            If there are errors parsing the XML reply.
     */
    public void commitConfirm(long seconds) throws CommitException, IOException,
        SAXException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        this.netconfSession.commitConfirm(seconds);
    }

    /**
     * Commit full is an unsupported Juniper command that will commit the config and then signal all processes to
     * check the configuration for changes. A normal commit only signals processes where there data has been modified.
     *
     * @throws CommitException if there is an error committing the config.
     * @throws IOException     if there is an error communicating with the Netconf server.
     * @throws SAXException    if there is an error parsing the XML Netconf response.
     */
    public void commitFull() throws CommitException, IOException, SAXException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        this.netconfSession.commitFull();
    }

    /**
     * Loads and commits the candidate configuration, Configuration can be in
     * text/xml format.
     *
     * @param configFile Path name of file containing configuration,in text/xml format,
     *                   to be loaded. For example,
     *                   "system {
     *                   services {
     *                   ftp;
     *                   }
     *                   }"
     *                   will load 'ftp' under the 'systems services' hierarchy.
     *                   OR
     *                   "&lt;configuration&gt;&lt;system&gt;&lt;services&gt;&lt;ftp/&gt;&lt;
     *                   services/&gt;&lt;/system&gt;&lt;/configuration/&gt;"
     *                   will load 'ftp' under the 'systems services' hierarchy.
     * @param loadType   You can choose "merge" or "replace" as the loadType.
     * @throws net.juniper.netconf.CommitException if there was an error committing the configuration.
     * @throws java.io.IOException                 If there are errors communicating with the netconf server.
     * @throws org.xml.sax.SAXException            If there are errors parsing the XML reply.
     */
    public void commitThisConfiguration(String configFile, String loadType)
        throws CommitException, IOException, SAXException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        this.netconfSession.commitThisConfiguration(configFile, loadType);
    }

    /**
     * Retrieve the candidate configuration, or part of the configuration.
     *
     * @param configTree configuration hierarchy to be retrieved as the argument.
     *                   For example, to get the whole configuration, argument should be
     *                   &lt;configuration&gt;&lt;/configuration&gt;
     * @return configuration data as XML object.
     * @throws java.io.IOException      If there are errors communicating with the netconf server.
     * @throws org.xml.sax.SAXException If there are errors parsing the XML reply.
     */
    public XML getCandidateConfig(String configTree) throws SAXException,
        IOException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        return this.netconfSession.getCandidateConfig(configTree);
    }

    /**
     * Retrieve the running configuration, or part of the configuration.
     *
     * @param configTree configuration hierarchy to be retrieved as the argument.
     *                   For example, to get the whole configuration, argument should be
     *                   &lt;configuration&gt;&lt;/configuration&gt;
     * @return configuration data as XML object.
     * @throws java.io.IOException      If there are errors communicating with the netconf server.
     * @throws org.xml.sax.SAXException If there are errors parsing the XML reply.
     */
    public XML getRunningConfig(String configTree) throws SAXException,
        IOException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        return this.netconfSession.getRunningConfig(configTree);
    }

    /**
     * Retrieve the running configuration, or part of the configuration.
     *
     * @param xpathFilter example {@code &lt;filter xmlns:model='urn:path:for:my:model' select='/model:*' /&gt;}
     * @return configuration data as XML object.
     * @throws java.io.IOException      If there are errors communicating with the netconf server.
     * @throws org.xml.sax.SAXException If there are errors parsing the XML reply.
     */
    public XML getRunningConfigAndState(String xpathFilter) throws IOException, SAXException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        return this.netconfSession.getRunningConfigAndState(xpathFilter);
    }


    /**
     * Run the {@code &lt;get-data&gt;} call to netconf server and retrieve data as an XML.
     *
     * @param xpathFilter example {@code &lt;filter xmlns:model='urn:path:for:my:model' select='/model:*' /&gt;}
     * @param datastore   running, startup, candidate, or operational
     * @return configuration data as XML object.
     * @throws java.io.IOException      If there are errors communicating with the netconf server.
     * @throws org.xml.sax.SAXException If there are errors parsing the XML reply.
     */
    public XML getData(String xpathFilter, Datastore datastore) throws IOException, SAXException {
        if (datastore == null) throw new NullPointerException("datastore must not be null");
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        return this.netconfSession.getData(xpathFilter, datastore);
    }


    /**
     * Retrieve the whole candidate configuration.
     *
     * @return configuration data as XML object.
     * @throws java.io.IOException      If there are errors communicating with the netconf server.
     * @throws org.xml.sax.SAXException If there are errors parsing the XML reply.
     */
    public XML getCandidateConfig() throws SAXException, IOException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        return this.netconfSession.getCandidateConfig();
    }

    /**
     * Retrieve the whole running configuration.
     *
     * @return configuration data as XML object.
     * @throws java.io.IOException      If there are errors communicating with the netconf server.
     * @throws org.xml.sax.SAXException If there are errors parsing the XML reply.
     */
    public XML getRunningConfig() throws SAXException, IOException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        return this.netconfSession.getRunningConfig();
    }

    /**
     * Validate the candidate configuration.
     *
     * @return true if validation successful.
     * @throws java.io.IOException      If there are errors communicating with the netconf server.
     * @throws org.xml.sax.SAXException If there are errors parsing the XML reply.
     */
    public boolean validate() throws IOException, SAXException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        return this.netconfSession.validate();
    }

    /**
     * Run a cli command, and get the corresponding output.
     * NOTE: The text output is supported for JUNOS 11.4 and later.
     *
     * @param command the cli command to be executed.
     * @return result of the command.
     * @throws java.io.IOException      If there are errors communicating with the netconf server.
     * @throws org.xml.sax.SAXException If there are errors parsing the XML reply.
     */
    public String runCliCommand(String command) throws IOException, SAXException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        return this.netconfSession.runCliCommand(command);
    }

    /**
     * Run a cli command.
     *
     * @param command the cli command to be executed.
     * @return result of the command, as a BufferedReader. This is
     * useful if we want continuous stream of output, rather than wait
     * for whole output till command execution completes.
     * @throws java.io.IOException If there are errors communicating with the Netconf server.
     */
    public BufferedReader runCliCommandRunning(String command) throws IOException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        return this.netconfSession.runCliCommandRunning(command);
    }

    /**
     * This method should be called for load operations to happen in 'private'
     * mode.
     *
     * @param mode Mode in which to open the configuration.
     *             Permissible mode(s): "private"
     * @throws java.io.IOException If there are errors communicating with the netconf server.
     */
    public void openConfiguration(String mode) throws IOException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        netconfSession.openConfiguration(mode);
    }

    /**
     * This method should be called to close a private session, in case its
     * started.
     *
     * @throws java.io.IOException If there are errors communicating with the netconf server.
     */
    public void closeConfiguration() throws IOException {
        if (netconfSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                "establish a connection first.");
        }
        netconfSession.closeConfiguration();
    }

    /**
     * Returns the last RPC reply sent by Netconf server.
     *
     * @return Last RPC reply, as a string
     */
    public String getLastRPCReply() {
        return this.netconfSession.getLastRPCReply();
    }

    /**
     * Creates a new RPC attribute for use in the default rpc xml envelope used in all rpc executions. Setting
     * the "xmlns" attribute will override the default namespace attribute, otherwise the value in
     * NetconfConstants.URN_XML_NS_NETCONF_BASE_1_0 will be used as the default namespace.
     *
     * @param name  The name of the new RPC attribute.
     * @param value The value of the new RPC attribute.
     * @throws NullPointerException If the device connection has not been made yet.
     */
    public void createRPCAttribute(String name, String value) {
        this.netconfSession.addRPCAttribute(name, value);
    }

    /**
     * Removes an RPC attribute from the default rpc xml envelope used in all rpc executions.
     *
     * @param name the attribute name to remove
     * @return The value of the removed attribute.
     * @throws NullPointerException If the device connection has not been made yet.
     */
    public String removeRPCAttribute(String name) {
        return this.netconfSession.removeRPCAttribute(name);
    }

    /**
     * Clears all the RPC attributes from the default rpc xml envelope used in all rpc executions. The default namespace
     * value NetconfConstants.URN_XML_NS_NETCONF_BASE_1_0 will still be present in the xml envelope.
     */
    public void clearRPCAttributes() {
        if (netconfSession != null)
            netconfSession.removeAllRPCAttributes();
    }

    /**
     * Convenience alias for {@link #getStrictHostKeyChecking()}.
     *
     * @return {@code true} if strict host-key checking is enabled
     */
    public boolean isStrictHostKeyChecking() {
        return this.strictHostKeyChecking;
    }
    // Getters for fields

    /**
     * Returns the {@link JSch} instance that backs this {@code Device}.
     * <p>
     * <strong>Note&nbsp;–</strong> the returned object is the live instance
     * used for all SSH operations; creating a defensive copy is not feasible,
     * so callers <em>must not</em> modify its global state (e.g.&nbsp;changing
     * the identity repository or host‑key repository) once the {@code Device}
     * has been built.
     *
     * @return the underlying {@link JSch} SSH client
     */
    @SuppressWarnings("EI_EXPOSE_REP") // Defensive copy not feasible for JSch
    public JSch getSshClient() {
        // Defensive copy not possible; document that caller must not modify
        return sshClient;
    }

    /**
     * Returns the DNS host name or IP address of the NETCONF server to which
     * this {@code Device} instance will attempt to connect.
     *
     * @return host name or IP string
     */
    public String getHostName() {
        return hostName;
    }

    /**
     * Returns the TCP port on which the remote device’s NETCONF SSH subsystem
     * is listening.  The default is {@code 830} unless explicitly overridden
     * in the builder.
     *
     * @return NETCONF port number
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the maximum time, in milliseconds, to wait while establishing
     * the underlying SSH transport connection before giving up.
     *
     * @return SSH connection timeout (milliseconds)
     */
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Returns the per‑command timeout configured for individual NETCONF RPCs.
     *
     * @return timeout in milliseconds
     */
    public int getCommandTimeout() {
        return commandTimeout;
    }

    /**
     * Returns the user name that will be used to authenticate the SSH session.
     *
     * @return login user name
     */
    public String getUserName() {
        return userName;
    }

    /**
     * Returns the password associated with {@link #getUserName()}, or
     * {@code null} if key‑based authentication is configured.
     *
     * @return password string, or {@code null}
     */
    public String getPassword() {
        return password;
    }

    /**
     * Indicates whether key‑based SSH authentication is configured instead
     * of password‑based authentication.
     *
     * @return {@code true} if key‑based authentication is configured
     */
    public boolean isKeyBasedAuthentication() {
        return keyBasedAuthentication;
    }

    /**
     * Returns the path to the PEM‑formatted private‑key file that will be
     * used for key‑based authentication.
     *
     * @return PEM key file path, or {@code null} if password auth is used
     */
    public String getPemKeyFile() {
        return pemKeyFile;
    }

    /**
     * Returns whether strict host-key checking is enabled for this device.
     *
     * @return {@code true} if strict host-key checking is on
     */
    public boolean getStrictHostKeyChecking() {
        return strictHostKeyChecking;
    }

    /**
     * Returns the path to the SSH known-hosts file used for host-key checking.
     *
     * @return known-hosts file path, or {@code null} if none was provided
     */
    public String getHostKeysFileName() {
        return hostKeysFileName;
    }

    /**
     * Returns the DocumentBuilder used for XML parsing.
     * <p>
     * Defensive copy not possible; caller must not modify the returned instance.
     *
     * @return the {@link DocumentBuilder} in use
     */
    @SuppressWarnings("EI_EXPOSE_REP") // Defensive copy not feasible for DocumentBuilder
    public DocumentBuilder getXmlBuilder() {
        // Defensive copy not possible; document that caller must not modify
        return xmlBuilder;
    }

    /**
     * Returns an immutable copy of the capability URIs that this client
     * advertises in its initial {@code &lt;hello&gt;} exchange.
     *
     * @return list of Netconf capability URIs
     */
    public List<String> getNetconfCapabilities() {
        return new java.util.ArrayList<>(netconfCapabilities);
    }

    /**
     * Returns the pre‑built NETCONF {@code &lt;hello&gt;} RPC payload that
     * will be sent when a session is established.
     *
     * @return initial NETCONF {@code &lt;hello&gt;} RPC string
     */
    public String getHelloRpc() {
        return helloRpc;
    }

    /**
     * Returns the active SSH {@link ChannelSubsystem} used for NETCONF
     * communication, or {@code null} if not yet connected.
     *
     * @return active SSH subsystem channel, or {@code null}
     */
    @SuppressWarnings("EI_EXPOSE_REP") // Defensive copy not feasible for active channel
    public ChannelSubsystem getSshChannel() {
        return sshChannel; // Defensive copy not feasible; caller must not modify
    }

    /**
     * Returns the underlying SSH {@link Session}, or {@code null} if not
     * connected.
     *
     * @return SSH session, or {@code null}
     */
    @SuppressWarnings("EI_EXPOSE_REP") // Defensive copy not feasible for active session
    public Session getSshSession() {
        return sshSession; // Defensive copy not feasible; caller must not modify
    }

    /**
     * Returns the current {@link NetconfSession}, or {@code null} if no
     * session has been established.
     *
     * @return active Netconf session, or {@code null}
     */
    @SuppressWarnings("EI_EXPOSE_REP") // Defensive copy not feasible for active session
    public NetconfSession getNetconfSession() {
        return netconfSession; // Defensive copy not feasible; caller must not modify
    }

    // Setters for mutable fields (if needed)

    /**
     * Sets the SSH channel subsystem.
     * <p>
     * Defensive copy not feasible; ensures non-null reference.
     * The caller must not reuse or externally share the provided object after setting.
     *
     * @param sshChannel the SSH channel subsystem (must not be null)
     * @throws NullPointerException if sshChannel is null
     */
    @SuppressWarnings("EI_EXPOSE_REP2") // Defensive copy not feasible for active channel
    public void setSshChannel(ChannelSubsystem sshChannel) {
        this.sshChannel = (ChannelSubsystem) Objects.requireNonNull(sshChannel);
    }

    /**
     * Sets the SSH session.
     * <p>
     * Defensive copy not feasible; ensures non-null reference.
     * The caller must not reuse or externally share the provided object after setting.
     *
     * @param sshSession the SSH session (must not be null)
     * @throws NullPointerException if sshSession is null
     */
    @SuppressWarnings("EI_EXPOSE_REP2") // Defensive copy not feasible for active session
    public void setSshSession(Session sshSession) {
        this.sshSession = Objects.requireNonNull(sshSession);
    }

    /**
     * Sets the Netconf session.
     * <p>
     * Defensive copy not feasible; ensures non-null reference.
     * The caller must not reuse or externally share the provided object after setting.
     *
     * @param netconfSession the Netconf session (must not be null)
     * @throws NullPointerException if netconfSession is null
     */
    @SuppressWarnings("EI_EXPOSE_REP2") // Defensive copy not feasible for active session
    public void setNetconfSession(NetconfSession netconfSession) {
        this.netconfSession = Objects.requireNonNull(netconfSession);
    }

    // equals(), hashCode(), toString()
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Device device = (Device) o;
        return port == device.port &&
            connectionTimeout == device.connectionTimeout &&
            commandTimeout == device.commandTimeout &&
            keyBasedAuthentication == device.keyBasedAuthentication &&
            strictHostKeyChecking == device.strictHostKeyChecking &&
            java.util.Objects.equals(sshClient, device.sshClient) &&
            java.util.Objects.equals(hostName, device.hostName) &&
            java.util.Objects.equals(userName, device.userName) &&
            java.util.Objects.equals(password, device.password) &&
            java.util.Objects.equals(pemKeyFile, device.pemKeyFile) &&
            java.util.Objects.equals(hostKeysFileName, device.hostKeysFileName) &&
            java.util.Objects.equals(xmlBuilder, device.xmlBuilder) &&
            java.util.Objects.equals(netconfCapabilities, device.netconfCapabilities) &&
            java.util.Objects.equals(helloRpc, device.helloRpc);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(
            sshClient, hostName, port, connectionTimeout, commandTimeout,
            userName, password, keyBasedAuthentication, pemKeyFile,
            strictHostKeyChecking, hostKeysFileName, xmlBuilder,
            netconfCapabilities, helloRpc
        );
    }

    @Override
    public String toString() {
        return "Device{" +
            "sshClient=" + sshClient +
            ", hostName='" + hostName + '\'' +
            ", port=" + port +
            ", connectionTimeout=" + connectionTimeout +
            ", commandTimeout=" + commandTimeout +
            ", userName='" + userName + '\'' +
            ", password='" + (password != null ? "***" : null) + '\'' +
            ", keyBasedAuthentication=" + keyBasedAuthentication +
            ", pemKeyFile='" + pemKeyFile + '\'' +
            ", strictHostKeyChecking=" + strictHostKeyChecking +
            ", hostKeysFileName='" + hostKeysFileName + '\'' +
            ", xmlBuilder=" + xmlBuilder +
            ", netconfCapabilities=" + netconfCapabilities +
            ", helloRpc='" + helloRpc + '\'' +
            '}';
    }
}