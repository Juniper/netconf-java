/**
* Copyright (c) 2013 Juniper Networks, Inc.
* All Rights Reserved
*
* Use is subject to license terms.
*
*/

package net.juniper.netconf;

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.ArrayList;

/**
 * A <code>Device</code> is used to define a Netconf server.
 * <p>
 * Typically, one
 * <ol>
 * <li>creates a {@link #Device(String,String,String,String) Device}
 * object.</li>
 * <li>perform netconf operations on the Device object.</li>
 * <li>If needed, call the method createNetconfSession() to create another
 * NetconfSession.</li>
 * <li>Finally, one must close the Device and release resources with the
 * {@link #close() close()} method.</li>
 * </ol>
 */
public class Device {

    private String hostName;
    private String userName;
    private String password;
    private String helloRpc;
    private String pemKeyFile;
    private boolean connectionOpen;
    private boolean keyBasedAuthentication;
    private Connection NetconfConn;
    private int port;
    private int timeout;
    private DocumentBuilder builder;
    private NetconfSession defaultSession;
    private static final String NEWLINE = System.getProperty("line.separator");

    /**
     * Prepares a new <code>Device</code> object, with default client
     * capabilities and default port 830, which can then be used to perform
     * netconf operations.
     * <p>
     * @throws javax.xml.parsers.ParserConfigurationException
     */
    public Device() throws ParserConfigurationException {
        keyBasedAuthentication = false;
        connectionOpen = false;
        helloRpc = defaultHelloRPC();
        port = 830;
        timeout = 5000;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance() ;
        builder = factory.newDocumentBuilder() ;
    }


    /**
     * Prepares a new <code>Device</code> object, with default client
     * capabilities and default port 830, which can then be used to perform
     * netconf operations.
     * <p>
     * @param hostName
     *            the hostname of the Netconf server.
     * @param userName
     *            the login username of the Netconf server.
     * @param password
     *            the login password of the Netconf server.
     * @param pemKeyFile
     *            path of the file containing RSA/DSA private key, in PEM
     *            format. For user-password based authentication, let this be
     *            null.
     * @throws net.juniper.netconf.NetconfException
     * @throws javax.xml.parsers.ParserConfigurationException
     */
    public Device(String hostName, String userName, String password,
            String pemKeyFile) throws NetconfException,
            ParserConfigurationException {
        this.hostName = hostName;
        this.userName = userName;
        this.password = password;
        this.pemKeyFile = pemKeyFile;
        keyBasedAuthentication = pemKeyFile != null;
        connectionOpen = false;
        helloRpc = defaultHelloRPC();
        port = 830;
        timeout = 5000;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance() ;
        builder = factory.newDocumentBuilder() ;
    }

    /**
     * Prepares a new <code>Device</code> object, with default client
     * capabilities and user-defined port which can then be used to perform
     * netconf operations.
     * <p>
     * @param hostName
     *            the hostname of the Netconf server.
     * @param userName
     *            the login username of the Netconf server.
     * @param password
     *            the login password of the Netconf server.
     * @param pemKeyFile
     *            path of the file containing RSA/DSA private key, in PEM
     *            format. For user-password based authentication, let this be
     *            null.
     * @param port
     *            port number to establish Netconf session over SSH-2.
     * @throws net.juniper.netconf.NetconfException
     * @throws javax.xml.parsers.ParserConfigurationException
     */
    public Device(String hostName, String userName, String password,
            String pemKeyFile, int port)
            throws NetconfException, ParserConfigurationException {
        this.hostName = hostName;
        this.userName = userName;
        this.password = password;
        this.pemKeyFile = pemKeyFile;
        keyBasedAuthentication = pemKeyFile != null;
        connectionOpen = false;
        helloRpc = defaultHelloRPC();
        this.port = port;
        timeout = 5000;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance() ;
        builder = factory.newDocumentBuilder() ;
    }

    /**
     * Prepares a new <code>Device</code> object, with user-defined client
     * capabilities and default port 830 which can then be used to perform
     * netconf operations.
     * <p>
     * @param hostName
     *            the hostname of the Netconf server.
     * @param userName
     *            the login username of the Netconf server.
     * @param password
     *            the login password of the Netconf server.
     * @param pemKeyFile
     *            path of the file containing RSA/DSA private key, in PEM
     *            format. For user-password based authentication, let this be
     *            null.
     * @param capabilities
     *            the client capabilities to be advertised to Netconf server.
     * @throws net.juniper.netconf.NetconfException
     * @throws javax.xml.parsers.ParserConfigurationException
     */
    public Device(String hostName, String userName, String password,
            String pemKeyFile, ArrayList capabilities) throws
            NetconfException, ParserConfigurationException {
        this.hostName = hostName;
        this.userName = userName;
        this.password = password;
        this.pemKeyFile = pemKeyFile;
        keyBasedAuthentication = pemKeyFile != null;
        connectionOpen = false;
        helloRpc = createHelloRPC(capabilities);
        port = 830;
        timeout = 5000;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance() ;
        builder = factory.newDocumentBuilder() ;
    }

    /**
     * Prepares a new <code>Device</code> object, with user-defined client
     * capabilities and user-defined port which can then be used to perform
     * netconf operations.
     * <p>
     * @param hostName
     *            the hostname of the Netconf server.
     * @param userName
     *            the login username of the Netconf server.
     * @param password
     *            the login password of the Netconf server.
     * @param pemKeyFile
     *            path of the file containing RSA/DSA private key, in PEM
     *            format. For user-password based authentication, let this be
     *            null.
     * @param port
     *            port number to establish Netconf session over SSH-2.
     * @param capabilities
     *            the client capabilities to be advertised to Netconf server.
     * @throws net.juniper.netconf.NetconfException
     * @throws javax.xml.parsers.ParserConfigurationException
     */
    public Device(String hostName, String userName, String password,
            String pemKeyFile, int port, ArrayList capabilities) throws
            NetconfException, ParserConfigurationException {
        this.hostName = hostName;
        this.userName = userName;
        this.password = password;
        this.pemKeyFile = pemKeyFile;
        keyBasedAuthentication = pemKeyFile != null;
        connectionOpen = false;
        helloRpc = createHelloRPC(capabilities);
        this.port = port;
        timeout = 5000;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance() ;
        builder = factory.newDocumentBuilder() ;
    }

    private String defaultHelloRPC() {
        ArrayList defaultCap = getDefaultClientCapabilities();
        return createHelloRPC(defaultCap);
    }

    private String createHelloRPC(ArrayList capabilities) {
        StringBuilder helloRPC = new StringBuilder();
        helloRPC.append("<hello>").append(NEWLINE);
        helloRPC.append("<capabilities>").append(NEWLINE);
        for (Object capability1 : capabilities) {
            String capability = (String) capability1;
            helloRPC.append("<capability>").append(capability).append("</capability>").append(NEWLINE);
        }
        helloRPC.append("</capabilities>").append(NEWLINE);
        helloRPC.append("</hello>").append(NEWLINE);
        helloRPC.append("]]>]]>").append(NEWLINE);
        return helloRPC.toString();
    }

    /**
     * Connect to the Device, and establish a default NETCONF session.
     * @throws net.juniper.netconf.NetconfException
     */
    public void connect() throws NetconfException {
        if (hostName == null || userName == null || (password == null &&
                pemKeyFile == null)) {
            throw new NetconfException("Login parameters of Device can't be " +
                    "null.");
        }
        defaultSession = this.createNetconfSession();
    }

    /**
     * Set the timeout value for connecting to the Device.
     * @param timeout
     *           timeout in milliseconds.
     */
    public void setTimeOut(int timeout) throws NetconfException {
        if (connectionOpen) {
            throw new NetconfException("Can't change timeout on a live device."
                    + "Close the device first.");
        }
        this.timeout = timeout;
    }

    /**
     * Set the hostname of the Netconf server.
     * @param hostName
     *           hostname of the Netconf server, to be set.
     */
    public void setHostname(String hostName) throws NetconfException {
        if (connectionOpen) {
            throw new NetconfException("Can't change hostname on a live device."
                    + "Close the device first.");
        }
        this.hostName = hostName;
    }

    /**
     * Set the username of the Netconf server.
     * @param userName
     *           username of the Netconf server, to be set.
     */
    public void setUserName(String userName) throws NetconfException {
        if (connectionOpen) {
            throw new NetconfException("Can't change username on a live device."
                    + "Close the device first.");
        }
        this.userName = userName;
    }

    /**
     * Set the password of the Netconf server.
     * @param password
     *           password of the Netconf server, to be set.
     */
    public void setPassword(String password) throws NetconfException {
        if (connectionOpen) {
            throw new NetconfException("Can't change password on a live device."
                    + "Close the device first.");
        }
        this.password = password;
    }

    /**
     * Set path of the RSA/DSA private key.
     * @param pemKeyFile
     *            Path of the file containing RSA/DSA private key.
     */
    public void setPemKeyFile(String pemKeyFile) throws NetconfException {
        if (connectionOpen) {
            throw new NetconfException("Can't change private key on a live " +
                    "device.Close the device first.");
        }
        this.pemKeyFile = pemKeyFile;
        keyBasedAuthentication = true;
    }

    /**
     * Set the client capabilities to be advertised to the Netconf server.
     * @param capabilities
     *           Client capabilities to be advertised to the Netconf server.
     */
    public void setCapabilities(ArrayList capabilities) throws NetconfException {
        if (capabilities == null) {
            throw new IllegalArgumentException("Client capabilities cannot be "
                    + "null");
        }
        if (connectionOpen) {
            throw new NetconfException("Can't change client capabilities on a "
                    + "live device.Close the device first.");
        }
        helloRpc = createHelloRPC(capabilities);
    }

    /**
     * Set the port number to establish Netconf session over SSH-2.
     * @param port
     *           Port number.
     */
    public void setPort(int port) throws NetconfException {
        if (connectionOpen) {
            throw new NetconfException("Can't change port number on a live " +
                    "device.Close the device first.");
        }
        this.port = port;
    }

    /**
     * Get hostname of the Netconf server.
     * @return Hostname of the device.
     */
    public String gethostName() {
        return this.hostName;
    }

    /**
     * Create a new Netconf session.
     * @return NetconfSession
     * @throws net.juniper.netconf.NetconfException
     */
    NetconfSession createNetconfSession() throws NetconfException {
        Session normalSession;
        NetconfSession netconfSess;
        if (!connectionOpen) {
            try {
                NetconfConn = new Connection(hostName, port);
                NetconfConn.connect(null,timeout,0);
            } catch(Exception e) {
                throw new NetconfException(e.getMessage());
            }
            boolean isAuthenticated;
            try {
                if (keyBasedAuthentication) {
                    File keyFile = new File(pemKeyFile);
                    isAuthenticated = NetconfConn.authenticateWithPublicKey
                            (userName, keyFile, password);
                } else {
                    isAuthenticated = NetconfConn.authenticateWithPassword
                            (userName, password);
                }
            } catch (IOException e) {
                throw new NetconfException("Authentication failed:" +
                        e.getMessage());
            }
            if (!isAuthenticated)
                throw new NetconfException("Authentication failed.");
            connectionOpen = true;
        }
        try {
            normalSession = NetconfConn.openSession();
            normalSession.startSubSystem("netconf");
            netconfSess = new NetconfSession(normalSession, helloRpc, builder);
        } catch (IOException e) {
            throw new NetconfException("Failed to create Netconf session:" +
                    e.getMessage());
        }
        return netconfSess;
    }

    /**
     * Reboot the device.
     * @return RPC reply sent by Netconf server.
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public String reboot() throws SAXException, IOException {
        if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        return this.defaultSession.reboot();
    }

    /**
     * Close the connection to the Netconf server. All associated Netconf
     * sessions will be closed, too. Can be called at any time. Don't forget to
     * call this once you don't need the device anymore.
     */
    public void close() {
        if (!connectionOpen) {
            return;
        }
        NetconfConn.close();
        connectionOpen = false;
    }

    /**
     * Execute a command in shell mode.
     * @param command
     *          The command to be executed in shell mode.
     * @return Result of the command execution, as a String.
     * @throws java.io.IOException
     */
    public String runShellCommand(String command) throws IOException {
        if (!connectionOpen) {
            return "Could not find open connection.";
        }
        Session session = NetconfConn.openSession();
        session.execCommand(command);
        InputStream stdout;
        BufferedReader bufferReader;
        stdout = new StreamGobbler(session.getStdout());
        bufferReader = new BufferedReader(new InputStreamReader(stdout));

       String reply = "";
       while (true) {
            String line;
            try {
                line = bufferReader.readLine();
            } catch ( Exception e) {
                throw new NetconfException(e.getMessage());
            }
            if (line == null || line.equals(""))
	        break;
	    reply += line + "\n";
        }
        return reply;
    }

    /**
     * Execute a command in shell mode.
     * @param command
     *          The command to be executed in shell mode.
     * @return Result of the command execution, as a BufferedReader. This is
     *         useful if we want continuous stream of output, rather than wait
     *         for whole output till command execution completes.
     * @throws java.io.IOException
     */
    public BufferedReader runShellCommandRunning(String command)
            throws IOException {
        if (!connectionOpen) {
            throw new IOException("Could not find open connection");
        }
        Session session = NetconfConn.openSession();
        session.execCommand(command);
        InputStream stdout;
        BufferedReader bufferReader;
        stdout = new StreamGobbler(session.getStdout());
        bufferReader = new BufferedReader(new InputStreamReader(stdout));
        return bufferReader;
    }

    /**
     * Get the client capabilities that are advertised to the Netconf server
     * by default.
     * @return Arraylist of default client capabilities.
     */
    ArrayList getDefaultClientCapabilities() {
        ArrayList defaultCap = new ArrayList();
        defaultCap.add("urn:ietf:params:xml:ns:netconf:base:1.0");
        defaultCap.add("urn:ietf:params:xml:ns:netconf:base:1.0#candidate");
        defaultCap.add("urn:ietf:params:xml:ns:netconf:base:1.0#confirmed-commit");
        defaultCap.add("urn:ietf:params:xml:ns:netconf:base:1.0#validate");
        defaultCap.add("urn:ietf:params:xml:ns:netconf:base:1.0#url?protocol=http,ftp,file");
        return defaultCap;
    }

    /**
     * Send an RPC(as String object) over the default Netconf session and get
     * the response as an XML object.
     * <p>
     * @param rpcContent
     *          RPC content to be sent. For example, to send an rpc
     *          &lt;rpc&gt;&lt;get-chassis-inventory/&gt;&lt;/rpc&gt;, the
     *          String to be passed can be
     *                 "&lt;get-chassis-inventory/&gt;" OR
     *                 "get-chassis-inventory" OR
     *                 "&lt;rpc&gt;&lt;get-chassis-inventory/&gt;&lt;/rpc&gt;"
     * @return RPC reply sent by Netconf server
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public XML executeRPC(String rpcContent) throws SAXException, IOException {
        if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        return this.defaultSession.executeRPC(rpcContent);
    }

    /**
     * Send an RPC(as XML object) over the Netconf session and get the response
     * as an XML object.
     * <p>
     * @param rpc
     *          RPC to be sent. Use the XMLBuilder to create RPC as an
     *          XML object.
     * @return RPC reply sent by Netconf server
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public XML executeRPC(XML rpc) throws SAXException, IOException {
        if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        return this.defaultSession.executeRPC(rpc);
    }

    /**
     * Send an RPC(as Document object) over the Netconf session and get the
     * response as an XML object.
     * <p>
     * @param rpcDoc
     *          RPC content to be sent, as a org.w3c.dom.Document object.
     * @return RPC reply sent by Netconf server
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public XML executeRPC(Document rpcDoc) throws SAXException, IOException {
        if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        return this.defaultSession.executeRPC(rpcDoc);
    }

    /**
     * Send an RPC(as String object) over the default Netconf session and get
     * the response as a BufferedReader.
     * <p>
     * @param rpcContent
     *          RPC content to be sent. For example, to send an rpc
     *          &lt;rpc&gt;&lt;get-chassis-inventory/&gt;&lt;/rpc&gt;, the
     *          String to be passed can be
     *                 "&lt;get-chassis-inventory/&gt;" OR
     *                 "get-chassis-inventory" OR
     *                 "&lt;rpc&gt;&lt;get-chassis-inventory/&gt;&lt;/rpc&gt;"
     * @return RPC reply sent by Netconf server as a BufferedReader. This is
     *         useful if we want continuous stream of output, rather than wait
     *         for whole output till rpc execution completes.
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public BufferedReader executeRPCRunning(String rpcContent)
            throws SAXException, IOException {
        if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        return this.defaultSession.executeRPCRunning(rpcContent);
    }

    /**
     * Send an RPC(as XML object) over the Netconf session and get the response
     * as a BufferedReader.
     * <p>
     * @param rpc
     *          RPC to be sent. Use the XMLBuilder to create RPC as an
     *          XML object.
     * @return RPC reply sent by Netconf server as a BufferedReader. This is
     *         useful if we want continuous stream of output, rather than wait
     *         for whole output till command execution completes.
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public BufferedReader executeRPCRunning(XML rpc) throws SAXException,
            IOException {
        if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        return this.defaultSession.executeRPCRunning(rpc);
    }

    /**
     * Send an RPC(as Document object) over the Netconf session and get the
     * response as a BufferedReader.
     * <p>
     * @param rpcDoc
     *          RPC content to be sent, as a org.w3c.dom.Document object.
     * @return RPC reply sent by Netconf server as a BufferedReader. This is
     *         useful if we want continuous stream of output, rather than wait
     *         for whole output till command execution completes.
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public BufferedReader executeRPCRunning(Document rpcDoc)
            throws SAXException, IOException {
        if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        return this.defaultSession.executeRPCRunning(rpcDoc);
    }

    /**
     * Get the session ID of the Netconf session.
     * @return Session ID
     */
    public String getSessionId() {
        if (defaultSession == null) {
            throw new IllegalStateException("Cannot get session ID, you need " +
                    "to establish a connection first.");
        }
        return this.defaultSession.getSessionId();
    }

    /**
     * Check if the last RPC reply returned from Netconf server has any error.
     * @return true if any errors are found in last RPC reply.
     */
    public boolean hasError() throws SAXException, IOException {
        if (defaultSession == null) {
            throw new IllegalStateException("No RPC executed yet, you need to" +
                    " establish a connection first.");
        }
        return this.defaultSession.hasError();
    }

    /**
     * Check if the last RPC reply returned from Netconf server has any warning.
     * @return true if any errors are found in last RPC reply.
     */
    public boolean hasWarning() throws SAXException, IOException {
        if (defaultSession == null) {
            throw new IllegalStateException("No RPC executed yet, you need to " +
                    "establish a connection first.");
        }
        return this.defaultSession.hasWarning();
    }

    /**
     * Check if the last RPC reply returned from Netconf server, contains
     * &lt;ok/&gt; tag.
     * @return true if &lt;ok/&gt; tag is found in last RPC reply.
     */
    public boolean isOK() {
        if (defaultSession == null) {
            throw new IllegalStateException("No RPC executed yet, you need to " +
                    "establish a connection first.");
        }
        return this.defaultSession.isOK();
    }

    /**
     * Locks the candidate configuration.
     * @return true if successful.
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public boolean lockConfig() throws IOException, SAXException {
        if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        return this.defaultSession.lockConfig();
    }

    /**
     * Unlocks the candidate configuration.
     * @return true if successful.
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public boolean unlockConfig() throws IOException, SAXException {
        if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        return this.defaultSession.unlockConfig();
    }

    /**
     * Loads the candidate configuration, Configuration should be in XML format.
     * @param configuration
     *            Configuration,in XML format, to be loaded. For example,
     * "&lt;configuration&gt;&lt;system&gt;&lt;services&gt;&lt;ftp/&gt;
     * &lt;services/&gt;&lt;/system&gt;&lt;/configuration/&gt;"
     * will load 'ftp' under the 'systems services' hierarchy.
     * @param loadType
     *           You can choose "merge" or "replace" as the loadType.
     * @throws net.juniper.netconf.LoadException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public void loadXMLConfiguration(String configuration, String loadType)
            throws IOException, SAXException {
        if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        this.defaultSession.loadXMLConfiguration(configuration, loadType);
    }

    /**
     * Loads the candidate configuration, Configuration should be in text/tree
     * format.
     * @param configuration
     *            Configuration,in text/tree format, to be loaded. For example,
     * " system {
     *     services {
     *         ftp;
     *     }
     *   }"
     * will load 'ftp' under the 'systems services' hierarchy.
     * @param loadType
     *           You can choose "merge" or "replace" as the loadType.
     * @throws net.juniper.netconf.LoadException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public void loadTextConfiguration(String configuration, String loadType)
            throws IOException, SAXException {
        if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        this.defaultSession.loadTextConfiguration(configuration, loadType);
    }

    /**
     * Loads the candidate configuration, Configuration should be in set
     * format.
     * NOTE: This method is applicable only for JUNOS release 11.4 and above.
     * @param configuration
     *            Configuration,in set format, to be loaded. For example,
     * "set system services ftp"
     * will load 'ftp' under the 'systems services' hierarchy.
     * To load multiple set statements, separate them by '\n' character.
     * @throws net.juniper.netconf.LoadException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public void loadSetConfiguration(String configuration) throws
            IOException,
            SAXException {
        if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        this.defaultSession.loadSetConfiguration(configuration);
    }

    /**
     * Loads the candidate configuration from file,
     * configuration should be in XML format.
     * @param configFile
     *            Path name of file containing configuration,in xml format,
     *            to be loaded.
     * @param loadType
     *           You can choose "merge" or "replace" as the loadType.
     * @throws net.juniper.netconf.LoadException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public void loadXMLFile(String configFile, String loadType)
            throws IOException, SAXException {
        if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        this.defaultSession.loadXMLFile(configFile, loadType);
    }

    /**
     * Loads the candidate configuration from file,
     * configuration should be in text/tree format.
     * @param configFile
     *            Path name of file containing configuration,in xml format,
     *            to be loaded.
     * @param loadType
     *           You can choose "merge" or "replace" as the loadType.
     * @throws net.juniper.netconf.LoadException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public void loadTextFile(String configFile, String loadType)
            throws IOException, SAXException {
        if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        this.defaultSession.loadTextFile(configFile, loadType);
    }

    /**
     * Loads the candidate configuration from file,
     * configuration should be in set format.
     * NOTE: This method is applicable only for JUNOS release 11.4 and above.
     * @param configFile
     *            Path name of file containing configuration,in set format,
     *            to be loaded.
     * @throws net.juniper.netconf.LoadException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public void loadSetFile(String configFile) throws
            IOException, SAXException {
        if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        this.defaultSession.loadSetFile(configFile);
    }

    /**
     * Commit the candidate configuration.
     * @throws net.juniper.netconf.CommitException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public void commit() throws IOException, SAXException {
        if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        this.defaultSession.commit();
    }

    /**
     * Commit the candidate configuration, temporarily. This is equivalent of
     * 'commit confirm'
     * @param seconds
     *           Time in seconds, after which the previous active configuration
     *           is reverted back to.
     * @throws net.juniper.netconf.CommitException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public void commitConfirm(long seconds) throws IOException,
            SAXException {
        if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        this.defaultSession.commitConfirm(seconds);
    }

    /**
     * Loads and commits the candidate configuration, Configuration can be in
     * text/xml format.
     * @param configFile
     *            Path name of file containing configuration,in text/xml format,
     * to be loaded. For example,
     * " system {
     *     services {
     *         ftp;
     *     }
     *   }"
     * will load 'ftp' under the 'systems services' hierarchy.
     * OR
     * "&lt;configuration&gt;&lt;system&gt;&lt;services&gt;&lt;ftp/&gt;&lt;
     * services/&gt;&lt;/system&gt;&lt;/configuration/&gt;"
     * will load 'ftp' under the 'systems services' hierarchy.
     * @param loadType
     *           You can choose "merge" or "replace" as the loadType.
     * @throws net.juniper.netconf.LoadException
     * @throws net.juniper.netconf.CommitException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public void commitThisConfiguration(String configFile, String loadType)
            throws IOException, SAXException {
        if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        this.defaultSession.commitThisConfiguration(configFile, loadType);
    }

    /**
     * Retrieve the candidate configuration, or part of the configuration.
     * @param configTree
     *           configuration hierarchy to be retrieved as the argument.
     * For example, to get the whole configuration, argument should be
     * &lt;configuration&gt;&lt;/configuration&gt;
     * @return configuration data as XML object.
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public XML getCandidateConfig(String configTree) throws SAXException,
            IOException {
        if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        return this.defaultSession.getCandidateConfig(configTree);
    }

    /**
     * Retrieve the running configuration, or part of the configuration.
     * @param configTree
     *           configuration hierarchy to be retrieved as the argument.
     * For example, to get the whole configuration, argument should be
     * &lt;configuration&gt;&lt;/configuration&gt;
     * @return configuration data as XML object.
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public XML getRunningConfig(String configTree) throws SAXException,
            IOException {
        if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        return this.defaultSession.getRunningConfig(configTree);
    }

    /**
     * Retrieve the whole candidate configuration.
     * @return configuration data as XML object.
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public XML getCandidateConfig() throws SAXException, IOException {
        if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        return this.defaultSession.getCandidateConfig();
    }

    /**
     * Retrieve the whole running configuration.
     * @return configuration data as XML object.
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public XML getRunningConfig() throws SAXException, IOException  {
        if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        return this.defaultSession.getRunningConfig();
    }

    /**
     * Validate the candidate configuration.
     * @return true if validation successful.
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public boolean validate() throws IOException, SAXException  {
         if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        return this.defaultSession.validate();
    }

    /**
     * Run a cli command, and get the corresponding output.
     * NOTE: The text output is supported for JUNOS 11.4 and later.
     * @param command
     *       the cli command to be executed.
     * @return result of the command.
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public String runCliCommand(String command) throws IOException, SAXException {
         if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        return this.defaultSession.runCliCommand(command);
    }

     /**
     * Run a cli command.
     * @param command
     *       the cli command to be executed.
     * @return result of the command, as a Bufferedreader. This is
     *         useful if we want continuous stream of output, rather than wait
     *         for whole output till command execution completes.
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public BufferedReader runCliCommandRunning(String command)
            throws  SAXException, IOException {
         if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        return this.defaultSession.runCliCommandRunning(command);
    }

    /**
     * This method should be called for load operations to happen in 'private'
     * mode.
     * @param mode
     *       Mode in which to open the configuration.
     *       Permissible mode(s): "private"
     * @throws java.io.IOException
     */
    public void openConfiguration(String mode) throws IOException {
        if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        defaultSession.openConfiguration(mode);
    }

    /**
     * This method should be called to close a private session, in case its
     * started.
     * @throws java.io.IOException
     */
    public void closeConfiguration() throws IOException {
        if (defaultSession == null) {
            throw new IllegalStateException("Cannot execute RPC, you need to " +
                    "establish a connection first.");
        }
        defaultSession.closeConfiguration();
    }

    /**
     * Returns the last RPC reply sent by Netconf server.
     * @return Last RPC reply, as a string
     */
    public String getLastRPCReply() {
        return this.defaultSession.getLastRPCReply();
    }

}

