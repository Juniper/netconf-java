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
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

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
@Slf4j
@Getter
public class Device {

    private static final int DEFAULT_NETCONF_PORT = 830;
    private static final int DEFAULT_TIMEOUT = 5000;

    private String hostName;
    private int port;
    private int timeout;

    private String userName;
    private String password;

    private boolean keyBasedAuthentication;
    private String pemKeyFile;

    private boolean strictHostKeyChecking;
    private String hostKeysFileName;

    private JSch sshClient;
    private ChannelSubsystem sshChannel;
    private Session sshSession;

    private DocumentBuilder builder;
    private NetconfSession netconfSession;

    private List<String> netconfCapabilities;
    private String helloRpc;

    @Builder
    public Device(
            @NonNull String hostName,
            Integer port,
            Integer timeout,
            @NonNull String userName,
            String password,
            Boolean keyBasedAuthentication,
            String pemKeyFile,
            Boolean strictHostKeyChecking,
            String hostKeysFileName,
            List<String> netconfCapabilities
    ) throws NetconfException {
        this.hostName = hostName;
        this.port = (port != null) ? port : DEFAULT_NETCONF_PORT;
        this.timeout = (timeout != null) ? timeout : DEFAULT_TIMEOUT;

        this.userName = userName;
        this.password = password;

        if (this.password == null && pemKeyFile == null) {
            throw new NetconfException("Auth requires either setting the password or the pemKeyFile");
        }

        this.keyBasedAuthentication = (keyBasedAuthentication != null) ? keyBasedAuthentication : false;
        this.pemKeyFile = pemKeyFile;

        if (this.keyBasedAuthentication && pemKeyFile == null) {
            throw new NetconfException("key based authentication requires setting the pemKeyFile");
        }

        this.strictHostKeyChecking = (strictHostKeyChecking != null) ? strictHostKeyChecking : true;
        this.hostKeysFileName = hostKeysFileName;

        if (this.strictHostKeyChecking && hostKeysFileName == null) {
            throw new NetconfException("Strict Host Key checking requires setting the hostKeysFileName");
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new NetconfException(String.format("Error creating XML Parser: %s", e.getMessage()));
        }

        this.netconfCapabilities = (netconfCapabilities != null) ? netconfCapabilities : getDefaultClientCapabilities();
        this.helloRpc = createHelloRPC(this.netconfCapabilities);

        this.sshClient = new JSch();
    }

    /**
     * Get the client capabilities that are advertised to the Netconf server by default.
     * RFC 6241 describes the standard netconf capabilities.
     * https://tools.ietf.org/html/rfc6241#section-8
     *
     * @return List of default client capabilities.
     */
    private List<String> getDefaultClientCapabilities() {
        List<String> defaultCap = new ArrayList<>();
        defaultCap.add(NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0);
        defaultCap.add(NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0 + "#candidate");
        defaultCap.add(NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0 + "#confirmed-commit");
        defaultCap.add(NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0 + "#validate");
        defaultCap.add(NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0 + "#url?protocol=http,ftp,file");
        return defaultCap;
    }

    /**
     * Given a list of netconf capabilities, generate the netconf hello rpc message.
     * https://tools.ietf.org/html/rfc6241#section-8.1
     *
     * @param capabilities A list of netconf capabilities
     * @return the hello RPC that represents those capabilities.
     */
    private String createHelloRPC(List<String> capabilities) {
        StringBuilder helloRPC = new StringBuilder();
        helloRPC.append("<hello xmlns=\"" + NetconfConstants.URN_XML_NS_NETCONF_BASE_1_0 + "\">\n");
        helloRPC.append("<capabilities>\n");
        for (Object o : capabilities) {
            String capability = (String) o;
            helloRPC
                    .append("<capability>")
                    .append(capability)
                    .append("</capability>\n");
        }
        helloRPC.append("</capabilities>\n");
        helloRPC.append("</hello>\n");
        helloRPC.append(NetconfConstants.DEVICE_PROMPT);
        return helloRPC.toString();
    }

    /**
     * Create a new Netconf session.
     *
     * @return NetconfSession
     * @throws NetconfException if there are issues communicating with the Netconf server.
     */
    private NetconfSession createNetconfSession() throws NetconfException {
        if (!isConnected()) {
            sshClient = new JSch();

            try {
                if (strictHostKeyChecking) {
                    if (hostKeysFileName == null) {
                        throw new NetconfException("Cannot do strictHostKeyChecking if hostKeysFileName is null");
                    }
                    sshClient.setKnownHosts(hostKeysFileName);
                }
            } catch (JSchException e) {
                throw new NetconfException(String.format("Error loading known hosts file: %s", e.getMessage()));
            }
            sshClient.setHostKeyRepository(sshClient.getHostKeyRepository());
            log.info("Connecting to host {} on port {}.", hostName, port);
            if (keyBasedAuthentication) {
                sshSession = loginWithPrivateKey(timeout);
                loadPrivateKey();
            } else {
                sshSession = loginWithUserPass(timeout);
            }
            try {
                sshSession.setTimeout(timeout);
            } catch (JSchException e) {
                throw new NetconfException(String.format("Error setting session timeout: %s", e.getMessage()));
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
            return new NetconfSession(sshChannel, timeout, helloRpc, builder);
        } catch (JSchException | IOException e) {
            throw new NetconfException("Failed to create Netconf session:" +
                    e.getMessage());
        }
    }

    private Session loginWithUserPass(int timeoutMilliSeconds) throws NetconfException {
        try {
            Session session = sshClient.getSession(userName, hostName, port);
            session.setConfig("userauth", "password");
            session.setConfig("StrictHostKeyChecking", "no");
            session.setPassword(password);
            session.connect(timeoutMilliSeconds);
            return session;
        } catch (JSchException e) {
            throw new NetconfException(String.format("Error connecting to host: %s - Error: %s",
                    hostName, e.getMessage()));
        }
    }

    private Session loginWithPrivateKey(int timeoutMilliSeconds) throws NetconfException {
        try {
            Session session = sshClient.getSession(userName, hostName, port);
            session.setConfig("userauth", "publickey");
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(timeoutMilliSeconds);
            return session;
        } catch (JSchException e) {
            throw new NetconfException(String.format("Error using key pair file: %s to connect to host: %s - Error: %s",
                    pemKeyFile, hostName, e.getMessage()));
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
            throw new NetconfException(String.format("Error parsing the pemKeyFile: %s", e.getMessage()));
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
    public void close() {
        if (!isConnected()) {
            return;
        }
        sshChannel.disconnect();
        sshSession.disconnect();
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
            throw new NetconfException(String.format("Failed to open exec session: %s", e.getMessage()));
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
                    throw new NetconfException(e.getMessage());
                }
                if (line == null || line.equals(""))
                    break;
                reply.append(line).append("\n");
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
            throw new NetconfException(String.format("Failed to open exec session: %s", e.getMessage()));
        }
        InputStream stdout = channel.getInputStream();
        return new BufferedReader(new InputStreamReader(stdout, Charset.defaultCharset()));
    }

    /**
     * Send an RPC(as String object) over the default Netconf session and get
     * the response as an XML object.
     * <p>
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
        return this.netconfSession.executeRPC(rpcContent);
    }

    /**
     * Send an RPC(as XML object) over the Netconf session and get the response
     * as an XML object.
     * <p>
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
     * <p>
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
     * Send an RPC(as String object) over the default Netconf session and get
     * the response as a BufferedReader.
     * <p>
     *
     * @param rpcContent RPC content to be sent. For example, to send an rpc
     *                   &lt;rpc&gt;&lt;get-chassis-inventory/&gt;&lt;/rpc&gt;, the
     *                   String to be passed can be
     *                   "&lt;get-chassis-inventory/&gt;" OR
     *                   "get-chassis-inventory" OR
     *                   "&lt;rpc&gt;&lt;get-chassis-inventory/&gt;&lt;/rpc&gt;"
     * @return RPC reply sent by Netconf server as a BufferedReader. This is
     * useful if we want continuous stream of output, rather than wait
     * for whole output till rpc execution completes.
     * @throws java.io.IOException if there are errors communicating with the Netconf server.
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
     * <p>
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
     * Send an RPC(as Document object) over the Netconf session and get the
     * response as a BufferedReader.
     * <p>
     *
     * @param rpcDoc RPC content to be sent, as a org.w3c.dom.Document object.
     * @return RPC reply sent by Netconf server as a BufferedReader. This is
     * useful if we want continuous stream of output, rather than wait
     * for whole output till command execution completes.
     * @throws java.io.IOException If there are errors communicating with the Netconf server.
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
     *                      services {
     *                          ftp;
     *                      }
     *                    }"
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

}

