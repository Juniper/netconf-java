/*
 Copyright (c) 2013 Juniper Networks, Inc.
 All Rights Reserved

 Use is subject to license terms.

*/

package net.juniper.netconf;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.JSchException;
import net.juniper.netconf.element.Datastore;
import net.juniper.netconf.element.Hello;
import net.juniper.netconf.element.RpcReply;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A {@code NetconfSession} is obtained by first building a
 * {@link Device} and then calling {@link Device#connect()}.
 * <p>
 * Typically, one
 * <ol>
 *   <li>Build a {@link Device} using its fluent {@code builder()}.</li>
 *   <li>Invoke {@link Device#connect()} to establish transport and receive a
 *       {@code NetconfSession}.</li>
 *   <li>Perform RPC operations via the session
 *       (e.g.&nbsp;{@link #executeRPC(String)}, {@link #killSession(String)},
 *       {@link #commitConfirm(long, String)}, {@link #cancelCommit(String)}).</li>
 *   <li>Call {@link #close()} when finished to free resources.</li>
 * </ol>
 */
public class NetconfSession {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NetconfSession.class);

    private final Channel netconfChannel;
    private String serverCapability;
    private Hello serverHello;

    private final InputStream stdInStreamFromDevice;
    private final OutputStream stdOutStreamToDevice;

    private String lastRpcReply;
    private RpcReply lastRpcReplyObject;
    private final DocumentBuilder builder;
    private final int commandTimeout;

    private final Map<String, String> rpcAttrMap = new HashMap<>();
    private String rpcAttributes;

    private int messageId = 0;
    /**
     * Size (in characters) of the temporary read buffer used when collecting
     * RPC replies from the device.  Set larger than the internal buffer in
     * {@link java.io.BufferedReader} so that large replies are less likely to
     * require multiple passes.
     */
    public static final int BUFFER_SIZE = 9 * 1024;

    private static final String CANDIDATE_CONFIG = "candidate";
    private static final String EMPTY_CONFIGURATION_TAG = "<configuration></configuration>";
    private static final String RUNNING_CONFIG = "running";
    private static final String NETCONF_SYNTAX_ERROR_MSG_FROM_DEVICE = "netconf error: syntax error";

    NetconfSession(Channel netconfChannel, int timeout, String hello,
                   DocumentBuilder builder) throws IOException {
        this(netconfChannel, timeout, timeout, hello, builder);
    }

    NetconfSession(Channel netconfChannel, int connectionTimeout, int commandTimeout,
                   String hello,
                   DocumentBuilder builder) throws IOException {

        stdInStreamFromDevice = netconfChannel.getInputStream();
        stdOutStreamToDevice = netconfChannel.getOutputStream();
        try {
            netconfChannel.connect(connectionTimeout);
        } catch (JSchException e) {
            throw new NetconfException("Failed to create Netconf session:" +
                    e.getMessage(), e);
        }
        this.netconfChannel = netconfChannel;
        this.commandTimeout = commandTimeout;
        this.builder = builder;

        sendHello(hello);
    }

    private XML convertToXML(String xml) throws SAXException, IOException {
        if (xml.contains(NETCONF_SYNTAX_ERROR_MSG_FROM_DEVICE)) {
            throw new NetconfException(String.format("Netconf server detected an error: %s", xml));
        }
        Document doc = builder.parse(new InputSource(new StringReader(xml)));
        Element root = doc.getDocumentElement();
        return new XML(root);
    }

    private void sendHello(String hello) throws IOException {
        setHelloReply(getRpcReply(hello));
    }

    @VisibleForTesting
    String getRpcReply(String rpc) throws IOException {
        // write the rpc to the device
        sendRpcRequest(rpc);

        final char[] buffer = new char[BUFFER_SIZE];
        final StringBuilder rpcReply = new StringBuilder();
        final long startTime = System.nanoTime();
        final Reader in = new InputStreamReader(stdInStreamFromDevice, Charsets.UTF_8);
        boolean timeoutNotExceeded = true;
        int promptPosition;
        while ((promptPosition = rpcReply.indexOf(NetconfConstants.DEVICE_PROMPT)) < 0 &&
                (timeoutNotExceeded = (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime) < commandTimeout))) {
            int charsRead = in.read(buffer, 0, buffer.length);
            if (charsRead < 0) throw new NetconfException("Input Stream has been closed during reading.");
            rpcReply.append(buffer, 0, charsRead);
        }

        if (!timeoutNotExceeded)
            throw new SocketTimeoutException("Command timeout limit was exceeded: " + commandTimeout);
        // fixing the rpc reply by removing device prompt
        log.debug("Received Netconf RPC-Reply\n{}", rpcReply);
        rpcReply.setLength(promptPosition);

        return rpcReply.toString();
    }

    private BufferedReader getRpcReplyRunning(String rpc) throws IOException {
        sendRpcRequest(rpc);
        return new BufferedReader(
                new InputStreamReader(stdInStreamFromDevice, Charsets.UTF_8));
    }

    private void sendRpcRequest(String rpc) throws IOException {
        // RFC conformance for XML type, namespaces and message ids for RPCs
        messageId++;
        rpc = rpc.replace("<rpc>", "<rpc" + getRpcAttributes() + " message-id=\"" + messageId + "\">").trim();
        rpc = rpc.replace("<datastore>", "<datastore xmlns:ds=\"urn:ietf:params:xml:ns:yang:ietf-datastores\">");
        rpc = rpc.replace("<get-data>", "<get-data xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-nmda\">");
        if (!rpc.contains(NetconfConstants.XML_VERSION)) {
            rpc = NetconfConstants.XML_VERSION + rpc;
        }
        // writing the rpc to the device
        log.debug("Sending Netconf RPC\n{}", rpc);
        stdOutStreamToDevice.write(rpc.getBytes(Charsets.UTF_8));
        stdOutStreamToDevice.flush();
    }

    /**
     * Gets the current rpc attribute string. If the rpc attribute string is not yet generated or has been reset then
     * we generate rpc attributes from the RPC Attribute Map.
     *
     * @return The attribute set XML formatted into a string.
     */
    public String getRpcAttributes() {
        if(rpcAttributes == null) {
            StringBuilder attributes = new StringBuilder();
            boolean useDefaultNamespace = true;
            for (Map.Entry<String, String> attribute : rpcAttrMap.entrySet()) {
                attributes.append(String.format(" %1s=\"%2s\"", attribute.getKey(), attribute.getValue()));
                if ("xmlns".equals(attribute.getKey()))
                    useDefaultNamespace = false;
            }
            if (useDefaultNamespace)
                attributes.append(" xmlns=\"" + NetconfConstants.URN_XML_NS_NETCONF_BASE_1_0 + "\"");
            rpcAttributes = attributes.toString();
        }
        return rpcAttributes;
    }


    /**
     * Loads the candidate configuration, Configuration should be in XML format.
     *
     * @param configuration Configuration,in XML format, to be loaded. For example,
     *                      "&lt;configuration&gt;&lt;system&gt;&lt;services&gt;&lt;ftp/&gt;&lt;
     *                      services/&gt;&lt;/system&gt;&lt;/configuration/&gt;"
     *                      will load 'ftp' under the 'systems services' hierarchy.
     * @param loadType      You can choose "merge" or "replace" as the loadType.
     * @throws org.xml.sax.SAXException If there are issues parsing the config file.
     * @throws java.io.IOException      If there are issues reading the config file.
     */
    public void loadXMLConfiguration(String configuration, String loadType) throws IOException, SAXException {
        validateLoadType(loadType);
        configuration = configuration.trim();
        if (!configuration.startsWith("<configuration")) {
            configuration = "<configuration>" + configuration
                    + "</configuration>";
        }
        String rpc = "<rpc>" +
                "<edit-config>" +
                "<target>" +
                "<" + CANDIDATE_CONFIG + "/>" +
                "</target>" +
                "<default-operation>" +
                loadType +
                "</default-operation>" +
                "<config>" +
                configuration +
                "</config>" +
                "</edit-config>" +
                "</rpc>" +
                NetconfConstants.DEVICE_PROMPT;
        try {
            setLastRpcReply(getRpcReply(rpc));
        } catch (NetconfException e) {
            // Propagate as a LoadException so the caller knows this happened
            throw new LoadException("Load operation returned error.", e);
        }

        if (hasError() || !isOK()) {
            throw new LoadException("Load operation returned error.");
        }
    }

    private void setHelloReply(final String reply) throws IOException {
        this.serverCapability = reply;
        this.lastRpcReply = reply;
        try {
            this.serverHello = Hello.from(reply);
        } catch (final ParserConfigurationException | SAXException | XPathExpressionException e) {
            throw new NetconfException("Invalid <hello> message from server: " + reply, e);
        }
    }

    private void setLastRpcReply(final String reply) throws IOException {
        this.lastRpcReply = reply;
        try {
            this.lastRpcReplyObject = RpcReply.from(reply);
        } catch (final ParserConfigurationException | SAXException | XPathExpressionException e) {
            throw new NetconfException("Invalid <rpc-reply> message from server: " + lastRpcReply, e);
        }
    }

    /**
     * Loads the candidate configuration, Configuration should be in text/tree
     * format.
     *
     * @param configuration Configuration,in text/tree format, to be loaded. For example,
     *                      "system {
     *                          services {
     *                              ftp;
     *                          }
     *                       }"
     *                      will load 'ftp' under the 'systems services' hierarchy.
     * @param loadType      You can choose "merge" or "replace" as the loadType.
     * @throws org.xml.sax.SAXException If there are issues parsing the config file.
     * @throws java.io.IOException      If there are issues reading the config file.
     */
    public void loadTextConfiguration(String configuration, String loadType) throws IOException, SAXException {
        String rpc = "<rpc>" +
                "<edit-config>" +
                "<target>" +
                "<" + CANDIDATE_CONFIG + "/>" +
                "</target>" +
                "<default-operation>" +
                loadType +
                "</default-operation>" +
                "<config-text>" +
                "<configuration-text>" +
                configuration +
                "</configuration-text>" +
                "</config-text>" +
                "</edit-config>" +
                "</rpc>" +
                NetconfConstants.DEVICE_PROMPT;
        try {
            setLastRpcReply(getRpcReply(rpc));
        } catch (NetconfException e) {
            throw new LoadException("Load operation returned error.", e);
        }

        if (hasError() || !isOK()) {
            throw new LoadException("Load operation returned error");
        }
    }

    private String getConfig(String configTree) throws IOException {

        String rpc = "<rpc>" +
                "<get-config>" +
                "<source>" +
                "<" + CANDIDATE_CONFIG + "/>" +
                "</source>" +
                "<filter type=\"subtree\">" +
                configTree +
                "</filter>" +
                "</get-config>" +
                "</rpc>" +
                NetconfConstants.DEVICE_PROMPT;
        setLastRpcReply(getRpcReply(rpc));
        return lastRpcReply;
    }

    /**
     * Executes a NETCONF {@code &lt;get&gt;} operation against the device’s running
     * datastore and returns the configuration **and** operational state data.
     *
     * @param xpathFilter optional XPath filter to limit the returned subtree;
     *                    pass {@code null} to retrieve the entire running state
     * @return an {@link XML} object representing the server’s {@code &lt;rpc-reply&gt;}
     * @throws IOException  if communication with the device fails
     * @throws SAXException if the reply cannot be parsed into valid XML
     */
    public XML getRunningConfigAndState(String xpathFilter) throws IOException, SAXException {
        String rpc = "<rpc>" +
                "<get>" +
                (xpathFilter == null ? "" : xpathFilter) +
                "</get>" +
                "</rpc>" +
                NetconfConstants.DEVICE_PROMPT;
        setLastRpcReply(getRpcReply(rpc));
        return convertToXML(lastRpcReply);
    }

    /**
     * Executes the YANG NMDA {@code &lt;get-data&gt;} operation against the specified
     * {@link Datastore}.
     *
     * @param xpathFilter optional XPath filter that narrows the returned data;
     *                    may be {@code null} for an unfiltered request
     * @param datastore   the target datastore (e.g., {@code operational}, {@code running});
     *                    must not be {@code null}
     * @return an {@link XML} object containing the server’s reply
     * @throws IllegalArgumentException if {@code datastore} is {@code null}
     * @throws IOException              if communication with the device fails
     * @throws SAXException             if the reply XML is malformed
     */
    public XML getData(String xpathFilter, Datastore datastore)
            throws IOException, SAXException {
        if (datastore == null) {
            throw new IllegalArgumentException("Datastore argument must not be null");
        }
        String rpc = "<rpc>" +
                "<get-data>" +
                "<datastore>ds:" + datastore + "</datastore>" +
                (xpathFilter == null ? "" : xpathFilter) +
                "</get-data>" +
                "</rpc>" +
                NetconfConstants.DEVICE_PROMPT;
        lastRpcReply = getRpcReply(rpc);
        return convertToXML(lastRpcReply);
    }

    private String getConfig(String target, String configTree)
            throws IOException {

        String rpc = "<rpc>" +
                "<get-config>" +
                "<source>" +
                "<" + target + "/>" +
                "</source>" +
                (configTree == null ? "" : "<filter type=\"subtree\">" + configTree + "</filter>") +
                "</get-config>" +
                "</rpc>" +
                NetconfConstants.DEVICE_PROMPT;
        setLastRpcReply(getRpcReply(rpc));
        return lastRpcReply;
    }

    /**
     * Get capability of the Netconf server.
     *
     * @return server capability
     */
    public String getServerCapability() {
        return serverCapability;
    }

    /**
     * Returns the &lt;hello&gt; message received from the server.
     * See <a href="https://datatracker.ietf.org/doc/html/rfc6241#section-8.1">...</a>
     * @return the &lt;hello&gt; message received from the server.
     */
    public Hello getServerHello() {
        return serverHello;
    }

    /**
     * Sends a raw RPC string, waits for the {@code &lt;rpc-reply&gt;}, and converts the
     * response to an {@link XML} object.
     * <p>
     * If the server includes any {@code &lt;rpc-error&gt;} elements, the call
     * now throws a {@link NetconfException}.  This makes error handling
     * symmetrical with other high‑level helpers (e.g.&nbsp;{@code load*()},
     * {@code commit()}).
     *
     * @param rpcContent the RPC payload (with or without &lt;rpc&gt; wrapper)
     * @return parsed {@link XML} representation of the reply
     * @throws NetconfException        if the reply contains one or more
     *                                 {@code &lt;rpc-error&gt;} elements
     * @throws SAXException            if the reply cannot be parsed as XML
     * @throws IOException             on transport errors
     */
    public XML executeRPC(String rpcContent) throws SAXException, IOException {
        String rpcReply = getRpcReply(fixupRpc(rpcContent));
        setLastRpcReply(rpcReply);

        if (hasError()) {
            throw new NetconfException("RPC returned error: " + rpcReply);
        }
        return convertToXML(rpcReply);
    }

    /**
     * Send an RPC (as XML object) over the Netconf session and get the response
     * as an XML object.
     *
     * @param rpc RPC to be sent. Use the XMLBuilder to create RPC as an
     *            XML object.
     * @return RPC reply sent by Netconf server
     * @throws org.xml.sax.SAXException If the XML Reply cannot be parsed.
     * @throws java.io.IOException      If there are issues communicating with the netconf server.
     */
    public XML executeRPC(XML rpc) throws SAXException, IOException {
        return executeRPC(rpc.toString());
    }

    /**
     * Send an RPC (as Document object) over the Netconf session and get the
     * response as an XML object.
     *
     * @param rpcDoc RPC content to be sent, as a org.w3c.dom.Document object.
     * @return RPC reply sent by Netconf server
     * @throws org.xml.sax.SAXException If the XML Reply cannot be parsed.
     * @throws java.io.IOException      If there are issues communicating with the netconf server.
     */
    public XML executeRPC(Document rpcDoc) throws SAXException, IOException {
        Element root = rpcDoc.getDocumentElement();
        XML xml = new XML(root);
        return executeRPC(xml);
    }


    /**
     * Given an RPC command, wrap it in RPC tags.
     * <a href="https://tools.ietf.org/html/rfc6241#section-4.1">...</a>
     *
     * @param rpcContent an RPC command that may or may not be wrapped in  &lt; or &gt;
     * @return a string of the RPC command wrapped in &lt;rpc&gt;&lt; &gt;&lt;/rpc&gt;
     * @throws IllegalArgumentException if null is passed in as the rpcContent.
     */
    @VisibleForTesting
    static String fixupRpc(String rpcContent) throws IllegalArgumentException {
        if (rpcContent == null) {
            throw new IllegalArgumentException("Null RPC");
        }
        rpcContent = rpcContent.trim();
        if (!rpcContent.startsWith("<rpc>") && !rpcContent.equals("<rpc/>")) {
            if (rpcContent.startsWith("<"))
                rpcContent = "<rpc>" + rpcContent + "</rpc>";
            else
                rpcContent = "<rpc>" + "<" + rpcContent + "/>" + "</rpc>";
        }
        return rpcContent + NetconfConstants.DEVICE_PROMPT;
    }


    /**
     * Send an RPC (as String object) over the default Netconf session and get
     * the response as a BufferedReader.
     *
     * @param rpcContent RPC content to be sent. For example, to send an rpc
     *                   &lt;rpc&gt;&lt;get-chassis-inventory/&gt;&lt;/rpc&gt;, the
     *                   String to be passed can be
     *                      "&lt;get-chassis-inventory/&gt;" OR
     *                      "get-chassis-inventory" OR
     *                      "&lt;rpc&gt;&lt;get-chassis-inventory/&gt;&lt;/rpc&gt;"
     * @return RPC reply sent by Netconf server as a BufferedReader. This is
     * useful if we want continuous stream of output, rather than wait
     * for whole output till command execution completes.
     * @throws java.io.IOException If there are issues communicating with the netconf server.
     */
    public BufferedReader executeRPCRunning(String rpcContent) throws IOException {
        return getRpcReplyRunning(fixupRpc(rpcContent));
    }

    /**
     * Send an RPC (as XML object) over the Netconf session and get the response
     * as a BufferedReader.
     *
     * @param rpc RPC to be sent. Use the XMLBuilder to create RPC as an
     *            XML object.
     * @return RPC reply sent by Netconf server as a BufferedReader. This is
     * useful if we want continuous stream of output, rather than wait
     * for whole output till command execution completes.
     * @throws java.io.IOException If there are issues communicating with the netconf server.
     */
    public BufferedReader executeRPCRunning(XML rpc) throws IOException {
        return executeRPCRunning(rpc.toString());
    }

    /**
     * Send an RPC (as Document object) over the Netconf session and get the
     * response as a BufferedReader.
     *
     * @param rpcDoc RPC content to be sent, as a org.w3c.dom.Document object.
     * @return RPC reply sent by Netconf server as a BufferedReader. This is
     * useful if we want continuous stream of output, rather than wait
     * for whole output till command execution completes.
     * @throws java.io.IOException If there are issues communicating with the netconf server.
     */
    public BufferedReader executeRPCRunning(Document rpcDoc) throws IOException {
        Element root = rpcDoc.getDocumentElement();
        XML xml = new XML(root);
        return executeRPCRunning(xml);
    }

    /**
     * Get the session ID of the Netconf session.
     *
     * @return Session ID as a string.
     */
    public String getSessionId() {
        return serverHello.getSessionId();
    }

    /**
     * Close the Netconf session. You should always call this once you don't
     * need the session anymore.
     *
     * @throws IOException if there are errors communicating with the Device
     */
    public void close() throws IOException {
        String rpc = "<rpc>" +
                "<close-session/>" +
                "</rpc>" +
                NetconfConstants.DEVICE_PROMPT;
        setLastRpcReply(getRpcReply(rpc));
        netconfChannel.disconnect();
    }

    /**
     * Check if the last RPC reply returned from Netconf server has any error.
     *
     * @return true if any errors are found in last RPC reply.
     */
    public boolean hasError() {
        return lastRpcReplyObject.hasErrors();
    }

    /**
     * Check if the last RPC reply returned from Netconf server has any warning.
     *
     * @return true if any errors are found in last RPC reply.
     */
    public boolean hasWarning() {
        return lastRpcReplyObject.hasWarnings();
    }

    /**
     * Check if the last RPC reply returned from Netconf server,
     * contains &lt;ok/&gt; tag.
     *
     * @return true if &lt;ok/&gt; tag is found in last RPC reply.
     */
    public boolean isOK() {
        return lastRpcReplyObject.isOK();
    }

    /**
     * Locks the candidate configuration.
     *
     * @return true if successful.
     * @throws org.xml.sax.SAXException If the XML Reply cannot be parsed.
     * @throws java.io.IOException      If there are issues communicating with the netconf server.
     */
    public boolean lockConfig() throws IOException, SAXException {
        String rpc = "<rpc>" +
                "<lock>" +
                "<target>" +
                "<candidate/>" +
                "</target>" +
                "</lock>" +
                "</rpc>" +
                NetconfConstants.DEVICE_PROMPT;
        setLastRpcReply(getRpcReply(rpc));
        return !hasError() && isOK();
    }

    /**
     * Unlocks the candidate configuration.
     *
     * @return true if successful.
     * @throws java.io.IOException      If there are issues communicating with the netconf server.
     */
    public boolean unlockConfig() throws IOException {
        String rpc = "<rpc>" +
                "<unlock>" +
                "<target>" +
                "<candidate/>" +
                "</target>" +
                "</unlock>" +
                "</rpc>" +
                NetconfConstants.DEVICE_PROMPT;
        setLastRpcReply(getRpcReply(rpc));
        return !hasError() && isOK();
    }

    /**
     * Terminates another active NETCONF session on the server
     * (<a href="https://datatracker.ietf.org/doc/html/rfc6241#section-7.9">RFC&nbsp;6241&nbsp;§7.9</a>).
     * <p>
     * The server will abort any operations in progress for that session,
     * release resources and locks, and close the connection.
     *
     * @param sessionId the session identifier to terminate; must not be {@code null} or empty
     * @return {@code true} if the operation succeeded (no &lt;rpc-error&gt; and an &lt;ok/&gt; was returned)
     * @throws IllegalArgumentException if {@code sessionId} is {@code null} or empty
     * @throws IOException              if communication with the device fails
     * @throws SAXException             if the reply cannot be parsed
     */
    public boolean killSession(String sessionId) throws IOException, SAXException {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new IllegalArgumentException("sessionId must not be null or empty");
        }

        String rpc = "<rpc>" +
                "<kill-session>" +
                "<session-id>" + sessionId + "</session-id>" +
                "</kill-session>" +
                "</rpc>" +
                NetconfConstants.DEVICE_PROMPT;

        setLastRpcReply(getRpcReply(rpc));
        return !hasError() && isOK();
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
     * @throws java.io.IOException      If there are issues reading the config file.
     */
    public void loadSetConfiguration(String configuration) throws IOException {
        String rpc = "<rpc>" +
                "<load-configuration action=\"set\">" +
                "<configuration-set>" +
                configuration +
                "</configuration-set>" +
                "</load-configuration>" +
                "</rpc>";
        setLastRpcReply(getRpcReply(rpc));
        if (hasError() || !isOK())
            throw new LoadException("Load operation returned error");
    }

    /**
     * Loads the candidate configuration from file,
     * configuration should be in XML format.
     *
     * @param configFile Path name of file containing configuration,in xml format,
     *                   to be loaded.
     * @param loadType   You can choose "merge" or "replace" as the loadType.
     * @throws org.xml.sax.SAXException If there are issues parsing the config file.
     * @throws java.io.IOException      If there are issues reading the config file.
     */
    public void loadXMLFile(String configFile, String loadType) throws IOException, SAXException {
        validateLoadType(loadType);
        loadXMLConfiguration(readConfigFile(configFile), loadType);
    }

    /**
     * Validate that the load type is either merge or replace.
     *
     * @param loadType how to load the config, merge or replace.
     * @throws IllegalArgumentException if the load type is not merge or replace.
     */
    private void validateLoadType(String loadType) throws IllegalArgumentException {
        if (loadType == null || (!loadType.equals("merge") &&
                !loadType.equals("replace")))
            throw new IllegalArgumentException("'loadType' argument must be " +
                    "merge|replace");
    }

    /**
     * Read the config file and return as a string.
     *
     * @param configFile The name of the configuration file
     * @return a string of the config file.
     * @throws java.io.IOException If there are issues reading the config file.
     */
    private String readConfigFile(String configFile) throws IOException {
        try {
            return Files.readString(Paths.get(configFile), Charset.defaultCharset());
        } catch (FileNotFoundException e) {
            throw new FileNotFoundException("The system cannot find the configuration file specified: " + configFile);
        }
    }


    /**
     * Loads the candidate configuration from file,
     * configuration should be in text/tree format.
     *
     * @param configFile Path name of file containing configuration,in xml format,
     *                   to be loaded.
     * @param loadType   You can choose "merge" or "replace" as the loadType.
     * @throws org.xml.sax.SAXException If there are issues parsing the config file.
     * @throws java.io.IOException      If there are issues reading the config file.
     */
    public void loadTextFile(String configFile, String loadType) throws IOException, SAXException {
        validateLoadType(loadType);
        loadTextConfiguration(readConfigFile(configFile), loadType);
    }

    /**
     * Loads the candidate configuration from file,
     * configuration should be in set format.
     * NOTE: This method is applicable only for JUNOS release 11.4 and above.
     *
     * @param configFile Path name of file containing configuration,in set format,
     *                   to be loaded.
     * @throws java.io.IOException      If there are issues reading the config file.
     */
    public void loadSetFile(String configFile) throws
            IOException {
        loadSetConfiguration(readConfigFile(configFile));
    }

    /**
     * Loads and commits the candidate configuration, Configuration can be in
     * text/xml/set format.
     *
     * @param configFile Path name of file containing configuration,in text/xml/set format,
     *                   to be loaded. For example,
     *                   "system {
     *                      services {
     *                          ftp;
     *                      }
     *                   }"
     *                   will load 'ftp' under the 'systems services' hierarchy.
     *                   OR
     *                   "&lt;configuration&gt;&lt;system&gt;&lt;services&gt;&lt;ftp/&gt;&lt;
     *                   services/&gt;&lt;/system&gt;&lt;/configuration/&gt;"
     *                   will load 'ftp' under the 'systems services' hierarchy.
     *                   OR
     *                   "set system services ftp"
     *                   will load 'ftp' under the 'systems services' hierarchy.
     * @param loadType   You can choose "merge" or "replace" as the loadType.
     *                   NOTE: This parameter's value is redundant in case the file contains
     *                   configuration in 'set' format.
     * @throws java.io.IOException      if there are errors communication with the netconf server.
     * @throws org.xml.sax.SAXException if there are errors parsing the XML reply.
     */
    public void commitThisConfiguration(String configFile, String loadType) throws IOException, SAXException {
        String configuration = readConfigFile(configFile);
        configuration = configuration.trim();
        if (this.lockConfig()) {
            if (configuration.startsWith("<")) {
                this.loadXMLConfiguration(configuration, loadType);
            } else if (configuration.startsWith("set")) {
                this.loadSetConfiguration(configuration);
            } else {
                this.loadTextConfiguration(configuration, loadType);
            }
            this.commit();
        } else {
            throw new IOException("Unclean lock operation. Cannot proceed " +
                    "further.");
        }
        this.unlockConfig();
    }

    /**
     * Commit the candidate configuration.
     *
     * @throws java.io.IOException      If there are errors communicating with the netconf server.
     * @throws org.xml.sax.SAXException If there are errors parsing the XML reply.
     */
    public void commit() throws IOException, SAXException {
        String rpc = "<rpc>" +
                "<commit/>" +
                "</rpc>" +
                NetconfConstants.DEVICE_PROMPT;
        setLastRpcReply(getRpcReply(rpc));
        if (hasError() || !isOK())
            throw new CommitException("Commit operation returned error.");
    }

    /**
     * Sends a *confirmed* &lt;commit&gt; request that follows the original
     * RFC&nbsp;6241 §8.4 semantics (confirmed‑commit&nbsp;**1.0**).
     * <p>
     * The server will roll back the candidate configuration unless a
     * non‑confirmed &lt;commit&gt; is issued from **the same session**
     * within the given timeout period.
     * </p>
     *
     * @param seconds the &lt;confirm-timeout&gt; in seconds; if {@code seconds
     *                &lt;= 0} the device’s default (600 s) is used
     * @throws IOException if communication with the device fails
     *
     * @deprecated Prefer {@link #commitConfirm(long, String)} which adds
     * the <code>&lt;persist&gt;</code> / <code>&lt;persist-id&gt;</code>
     * parameters required by the
     * <em>:confirmed-commit:1.1</em> capability.
     */
    @Deprecated
    public void commitConfirm(long seconds) throws IOException {
        commitConfirm(seconds, null);
    }

    /**
     * Issues a <em>confirmed</em> commit as defined by the
     * <a href="https://datatracker.ietf.org/doc/html/rfc6241#section-8.4">
     * :confirmed-commit:1.1 capability</a>.
     *
     * @param seconds        confirm‑timeout (600 s default).  If {@code seconds &lt;= 0}
     *                       the timeout element is omitted and the server default
     *                       is used.
     * @param persistToken   optional token for cross‑session confirmation; if
     *                       {@code null} the commit can only be confirmed from
     *                       the same session.
     * @throws IOException   if communication with the device fails
     */
    public void commitConfirm(long seconds, String persistToken) throws IOException {
        StringBuilder rpc = new StringBuilder();
        rpc.append("<rpc><commit><confirmed/>");
        if (seconds > 0) {
            rpc.append("<confirm-timeout>").append(seconds).append("</confirm-timeout>");
        }
        if (persistToken != null) {
            rpc.append("<persist>").append(persistToken).append("</persist>");
        }
        rpc.append("</commit></rpc>").append(NetconfConstants.DEVICE_PROMPT);

        setLastRpcReply(getRpcReply(rpc.toString()));
        if (hasError() || !isOK()) {
            throw new CommitException("Confirmed-commit operation returned error.");
        }
    }

    /**
     * Cancels a pending confirmed commit.
     *
     * @param persistId optional token that matches the &lt;persist&gt; used
     *                  in the original confirmed commit; may be {@code null}
     *                  if no token was supplied.
     * @return {@code true} if the server replied &lt;ok/&gt;
     * @throws IOException  if communication with the device fails
     * @throws SAXException if the reply cannot be parsed
     */
    public boolean cancelCommit(String persistId) throws IOException, SAXException {
        StringBuilder rpc = new StringBuilder();
        rpc.append("<rpc><cancel-commit/>");
        if (persistId != null) {
            rpc.insert(rpc.length() - "</cancel-commit/>".length(),
                       "<persist-id>" + persistId + "</persist-id>");
        }
        rpc.append("</rpc>").append(NetconfConstants.DEVICE_PROMPT);

        setLastRpcReply(getRpcReply(rpc.toString()));
        return !hasError() && isOK();
    }

    /**
     * Commit the candidate configuration and rebuild the config database.
     *
     * @throws net.juniper.netconf.CommitException if there is an error committing the config.
     * @throws java.io.IOException                 If there are errors communicating with the netconf server.
     */
    public void commitFull() throws CommitException, IOException {
        String rpc = "<rpc>" +
                "<commit-configuration>" +
                "<full/>" +
                "</commit-configuration>" +
                "</rpc>" +
                NetconfConstants.DEVICE_PROMPT;
        setLastRpcReply(getRpcReply(rpc));
        if (hasError() || !isOK())
            throw new CommitException("Commit operation returned error.");
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
        return convertToXML(getConfig(configTree));
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
        return convertToXML(getConfig(RUNNING_CONFIG, configTree));
    }

    /**
     * Retrieve the whole candidate configuration.
     *
     * @return configuration data as XML object.
     * @throws java.io.IOException      If there are errors communicating with the netconf server.
     * @throws org.xml.sax.SAXException If there are errors parsing the XML reply.
     */
    public XML getCandidateConfig() throws SAXException, IOException {
        return convertToXML(getConfig(EMPTY_CONFIGURATION_TAG));
    }

    /**
     * Retrieve the whole running configuration.
     *
     * @return configuration data as XML object.
     * @throws java.io.IOException      If there are errors communicating with the netconf server.
     * @throws org.xml.sax.SAXException If there are errors parsing the XML reply.
     */
    public XML getRunningConfig() throws SAXException, IOException {
        return convertToXML(getConfig(RUNNING_CONFIG, EMPTY_CONFIGURATION_TAG));
    }

    /**
     * Validate the candidate configuration.
     *
     * @return true if validation successful.
     * @throws java.io.IOException      If there are errors communicating with the netconf server.
     */
    public boolean validate() throws IOException {

        String rpc = "<rpc>" +
                "<validate>" +
                "<source>" +
                "<candidate/>" +
                "</source>" +
                "</validate>" +
                "</rpc>" +
                NetconfConstants.DEVICE_PROMPT;
        setLastRpcReply(getRpcReply(rpc));
        return !hasError() && isOK();
    }

    /**
     * Reboot the device corresponding to the Netconf Session.
     *
     * @return RPC reply sent by Netconf server.
     * @throws java.io.IOException If there are errors communicating with the netconf server.
     */
    public String reboot() throws IOException {
        String rpc = "<rpc>" +
                "<request-reboot/>" +
                "</rpc>" +
                NetconfConstants.DEVICE_PROMPT;
        return getRpcReply(rpc);
    }

    /**
     * Run a cli command.
     * NOTE: The text output is supported for JUNOS 11.4 and later.
     *
     * @param command the cli command to be executed.
     * @return result of the command, as a String.
     * @throws java.io.IOException      If there are errors communicating with the netconf server.
     * @throws org.xml.sax.SAXException If there are errors parsing the XML reply.
     */
    public String runCliCommand(String command) throws IOException, SAXException {

        String rpc = "<rpc>" +
                "<command format=\"text\">" +
                command +
                "</command>" +
                "</rpc>" +
                NetconfConstants.DEVICE_PROMPT;
        String rpcReply = getRpcReply(rpc);
        setLastRpcReply(rpcReply);
        XML xmlReply = convertToXML(rpcReply);
        List<String> tags = new ArrayList<>();
        tags.add("output");
        String output = xmlReply.findValue(tags);
        if (output != null)
            return output;
        else
            return rpcReply;
    }

    /**
     * Run a cli command.
     *
     * @param command the cli command to be executed.
     * @return result of the command, as a BufferedReader. This is
     * useful if we want continuous stream of output, rather than wait
     * for whole output till command execution completes.
     * @throws java.io.IOException If there are errors communicating with the netconf server.
     */
    public BufferedReader runCliCommandRunning(String command) throws
            IOException {

        String rpc = "<command format=\"text\">" +
                command +
                "</command>";
        return executeRPCRunning(rpc);
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

        StringBuilder rpc = new StringBuilder();
        rpc.append("<rpc>");
        rpc.append("<open-configuration>");
        if (mode.startsWith("<"))
            rpc.append(mode);
        else
            rpc.append("<").append(mode).append("/>");
        rpc.append("</open-configuration>");
        rpc.append("</rpc>");
        rpc.append(NetconfConstants.DEVICE_PROMPT);
        setLastRpcReply(getRpcReply(rpc.toString()));
    }

    /**
     * This method should be called to close a private session, in case its
     * started.
     *
     * @throws java.io.IOException If there are errors communicating with the netconf server.
     */
    public void closeConfiguration() throws IOException {
        String rpc = "<rpc>" +
                "<close-configuration/>" +
                "</rpc>" +
                NetconfConstants.DEVICE_PROMPT;
        setLastRpcReply(getRpcReply(rpc));
    }

    /**
     * Returns the last RPC reply sent by Netconf server.
     *
     * @return Last RPC reply, as a string.
     */
    public String getLastRPCReply() {
        return this.lastRpcReply;
    }

    /**
     * Returns the last RPC reply sent by Netconf server.
     *
     * @return Last RPC reply, as a RpcReply object.
     */
    public RpcReply getLastRpcReplyObject() {
        return lastRpcReplyObject;
    }

    /**
     * Adds an Attribute to the set of RPC attributes used in the RPC XML envelope. Resets the rpcAttributes value
     * to null for generation on the next request.
     *
     * @param name The attribute name for the new attribute.
     * @param value The attribute value for the new attribute.
     */
    public void addRPCAttribute(String name, String value) {
        rpcAttrMap.put(name, value);
        rpcAttributes = null;
    }

    /**
     * Removes an attribute from the set of RPC attributes used in the RPC XML envelope. Resets the rpcAttributes value
     * to null for generation on the next request.
     *
     * @param name The attribute name to be removed.
     *
     * @return The value of the removed attribute.
     */
    public String removeRPCAttribute (String name) {
        rpcAttributes = null;
        return rpcAttrMap.remove(name);
    }

    /**
     * Clears all the RPC attributes from the set of RPC attributes used in the RPC XML envelope. The set will be empty
     * after this call returns. Resets the rpcAttributes value to null for generation on the next request.
     */
    public void removeAllRPCAttributes() {
        rpcAttrMap.clear();
        rpcAttributes = null;
    }
}
