/**
* Copyright (c) 2013 Juniper Networks, Inc.
* All Rights Reserved
*
* Use is subject to license terms.
*
*/

package net.juniper.netconf;

import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A <code>NetconfSession</code> object is used to call the Netconf driver
 * methods.
 * This is derived by creating a Device first,
 * and calling createNetconfSession().
 * <p>
 * Typically, one
 * <ol>
 * <li>creates a Device object.</li>
 * <li>calls the createNetconfSession() method to get a NetconfSession
 * object.</li>
 * <li>perform operations on the NetconfSession object.</li>
 * <li>finally, one must close the NetconfSession and release resources with
 * the {@link #close() close()} method.</li>
 * </ol>
 */
class NetconfSession {

    private final Session netconfSession;
    private String serverCapability;
    private final BufferedReader bufferReader;
    private String lastRpcReply;
    private final DocumentBuilder builder;

    NetconfSession(Session netconfSession, String hello,
                   DocumentBuilder builder) throws IOException {

        this.netconfSession = netconfSession;
        this.builder = builder;
        InputStream stdout = new StreamGobbler(netconfSession.getStdout());
        bufferReader = new BufferedReader(new InputStreamReader(stdout));
        sendHello(hello);
    }

    private XML convertToXML(String xml) throws SAXException, IOException {
        Document doc = builder.parse(new InputSource(new StringReader(xml)));
        Element root = doc.getDocumentElement();
        return new XML(root);
    }

    private void sendHello(String hello) throws IOException {
        String reply = getRpcReply(hello);
        serverCapability = reply;
        lastRpcReply = reply;
    }

    private String getRpcReply(String rpc) throws IOException {
        byte b[]= rpc.getBytes();
        netconfSession.getStdin().write(b);
        String rpcReply = "";
        while (true) {
            String line;
            line = bufferReader.readLine();
            if (line == null || line.equals("]]>]]>"))
	        break;
	    rpcReply += line + "\n";
        }
        return rpcReply;
    }

    private BufferedReader getRpcReplyRunning(String rpc) throws IOException {
        byte b[]= rpc.getBytes();
        netconfSession.getStdin().write(b);
        return bufferReader;
    }

    private void loadXMLConfiguration(String target, String configuration,
            String loadType) throws IOException, SAXException {

        configuration = configuration.trim();
        if (!configuration.startsWith("<configuration")) {
            configuration = "<configuration>" + configuration
                    + "</configuration>";
        }
        lastRpcReply = getRpcReply("" + "<rpc>" + "<edit-config>" + "<target>" + "<" + target + "/>" + "</target>" + "<default-operation>" + loadType + "</default-operation>" + "<config>" + configuration + "</config>" + "</edit-config>" + "</rpc>" + "]]>]]>");
        if (hasError() || !isOK())
            throw new LoadException("Load operation returned error.");
    }

    private void loadTextConfiguration(String target, String configuration,
            String loadType) throws IOException, SAXException {
        lastRpcReply = getRpcReply("" + "<rpc>" + "<edit-config>" + "<target>" + "<" + target + "/>" + "</target>" + "<default-operation>" + loadType + "</default-operation>" + "<config-text>" + "<configuration-text>" + configuration + "</configuration-text>" + "</config-text>" + "</edit-config>" + "</rpc>" + "]]>]]>");
        if (hasError() || !isOK())
            throw new LoadException("Load operation returned error");
    }

    private String getConfig(String target, String configTree)
            throws IOException {

        lastRpcReply = getRpcReply("" + "<rpc>" + "<get-config>" + "<source>" + "<" + target + "/>" + "</source>" + "<filter type=\"subtree\">" + configTree + "</filter>" + "</get-config>" + "</rpc>" + "]]>]]>");
        return lastRpcReply;
    }

    private String readFile(String path) throws IOException {
        String strFileContent;
        File file = new File(path);
        FileInputStream fin = new FileInputStream(file);
        byte fileContent[] = new byte[(int)file.length()];
        fin.read(fileContent);
        strFileContent = new String(fileContent);
        return strFileContent;
    }

    /**
     * Get capability of the Netconf server.
     * @return server capability
     */
    public String getServerCapability() {
        return serverCapability;
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
        rpcContent += "]]>]]>";
        String rpcReply = getRpcReply(rpcContent);
        lastRpcReply = rpcReply;
        return convertToXML(rpcReply);
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
        return executeRPC(rpc.toString());
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
        Element root = rpcDoc.getDocumentElement();
        XML xml = new XML(root);
        return executeRPC(xml);
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
     *         for whole output till command execution completes.
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public BufferedReader executeRPCRunning(String rpcContent)
            throws IOException {
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
        rpcContent += "]]>]]>";
        return getRpcReplyRunning(rpcContent);
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
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public BufferedReader executeRPCRunning(XML rpc) throws IOException,
            SAXException {
        return executeRPCRunning(rpc.toString());
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
    public BufferedReader executeRPCRunning(Document rpcDoc) throws IOException,
            SAXException {
        Element root = rpcDoc.getDocumentElement();
        XML xml = new XML(root);
        return executeRPCRunning(xml);
    }

    /**
     * Get the session ID of the Netconf session.
     * @return Session ID as a string.
     */
    public String getSessionId() {
        String split[] = serverCapability.split("<session-id>");
        if (split.length != 2)
            return null;
        String idSplit[] = split[1].split("</session-id>");
        if (idSplit.length != 2)
            return null;
        return idSplit[0];
    }

    /**
     * Close the Netconf session. You should always call this once you don't
     * need the session anymore.
     */
    public void close() throws IOException {
        lastRpcReply = getRpcReply("" + "<rpc>" + "<close-session/>" + "</rpc>" + "]]>]]>");
        netconfSession.close();
    }

    /**
     * Check if the last RPC reply returned from Netconf server has any error.
     * @return true if any errors are found in last RPC reply.
     */
    public boolean hasError() throws SAXException, IOException {
        if (lastRpcReply == null || !(lastRpcReply.contains("<rpc-error>")))
            return false;
        XML xmlReply = convertToXML(lastRpcReply);
        List tagList = new ArrayList();
        tagList.add("rpc-error");
        tagList.add("error-severity");
        String errorSeverity = xmlReply.findValue(tagList);
        return errorSeverity != null && errorSeverity.equals("error");
    }

    /**
     * Check if the last RPC reply returned from Netconf server has any warning.
     * @return true if any errors are found in last RPC reply.
     */
    public boolean hasWarning() throws SAXException, IOException {
        if (lastRpcReply == null || !(lastRpcReply.contains("<rpc-error>")))
            return false;
        XML xmlReply = convertToXML(lastRpcReply);
        List tagList = new ArrayList();
        tagList.add("rpc-error");
        tagList.add("error-severity");
        String errorSeverity = xmlReply.findValue(tagList);
        return errorSeverity != null && errorSeverity.equals("warning");
    }

    /**
     * Check if the last RPC reply returned from Netconf server,
     * contains &lt;ok/&gt; tag.
     * @return true if &lt;ok/&gt; tag is found in last RPC reply.
     */
    public boolean isOK() {
        return lastRpcReply != null && lastRpcReply.contains("<ok/>");
    }

    /**
     * Locks the candidate configuration.
     * @return true if successful.
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public boolean lockConfig() throws IOException, SAXException {
        lastRpcReply = getRpcReply("" + "<rpc>" + "<lock>" + "<target>" + "<candidate/>" + "</target>" + "</lock>" + "</rpc>" + "]]>]]>");
        return !(hasError() || !isOK());
    }

    /**
     * Unlocks the candidate configuration.
     * @return true if successful.
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public boolean unlockConfig() throws IOException, SAXException {
        lastRpcReply = getRpcReply("" + "<rpc>" + "<unlock>" + "<target>" + "<candidate/>" + "</target>" + "</unlock>" + "</rpc>" + "]]>]]>");
        return !(hasError() || !isOK());
    }

    /**
     * Loads the candidate configuration, Configuration should be in XML format.
     * @param configuration
     *            Configuration,in XML format, to be loaded. For example,
     * "&lt;configuration&gt;&lt;system&gt;&lt;services&gt;&lt;ftp/&gt;&lt;
     * services/&gt;&lt;/system&gt;&lt;/configuration/&gt;"
     * will load 'ftp' under the 'systems services' hierarchy.
     * @param loadType
     *           You can choose "merge" or "replace" as the loadType.
     * @throws net.juniper.netconf.LoadException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public void loadXMLConfiguration(String configuration, String loadType)
            throws IOException, SAXException {
        if (loadType == null || (!loadType.equals("merge") &&
                !loadType.equals("replace")))
            throw new IllegalArgumentException("'loadType' argument must be " +
                    "merge|replace");
        loadXMLConfiguration("candidate",configuration,loadType);
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
        if (loadType == null || (!loadType.equals("merge") &&
                !loadType.equals("replace")))
            throw new IllegalArgumentException("'loadType' argument must be " +
                    "merge|replace");
        loadTextConfiguration("candidate",configuration,loadType);
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
        lastRpcReply = getRpcReply("" + "<rpc>" + "<load-configuration action=\"set\">" + "<configuration-set>" + configuration + "</configuration-set>" + "</load-configuration>" + "</rpc>");
        if (hasError() || !isOK())
            throw new LoadException("Load operation returned error");
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
        String configuration;
        try {
            configuration = readFile(configFile);
        } catch (FileNotFoundException e) {
            throw new FileNotFoundException("The system cannot find the " +
                    "configuration file specified: " + configFile);
        }
        if (loadType == null || (!loadType.equals("merge") &&
                !loadType.equals("replace")))
            throw new IllegalArgumentException("'loadType' argument must be " +
                    "merge|replace");
        loadXMLConfiguration(configuration,loadType);
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
        String configuration;
        try {
            configuration = readFile(configFile);
        } catch (FileNotFoundException e) {
            throw new FileNotFoundException("The system cannot find the " +
                    "configuration file specified: " + configFile);
        }
        if (loadType == null || (!loadType.equals("merge") &&
                !loadType.equals("replace")))
            throw new IllegalArgumentException("'loadType' argument must be " +
                    "merge|replace");
        loadTextConfiguration(configuration,loadType);
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
        String configuration;
        try {
            configuration = readFile(configFile);
        } catch (FileNotFoundException e) {
            throw new FileNotFoundException("The system cannot find the " +
                    "configuration file specified: " + configFile);
        }
        loadSetConfiguration(configuration);
    }

    /**
     * Loads and commits the candidate configuration, Configuration can be in
     * text/xml/set format.
     * @param configFile
     *            Path name of file containing configuration,in text/xml/set format,
     *            to be loaded. For example,
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
     * OR
     * "set system services ftp"
     * will load 'ftp' under the 'systems services' hierarchy.
     * @param loadType
     *           You can choose "merge" or "replace" as the loadType.
     * NOTE: This parameter's value is redundant in case the file contains
     * configuration in 'set' format.
     * @throws net.juniper.netconf.LoadException
     * @throws net.juniper.netconf.CommitException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public void commitThisConfiguration(String configFile, String loadType)
            throws IOException, SAXException {
        String configuration;
        try {
            configuration = readFile(configFile);
        } catch (FileNotFoundException e) {
            throw new FileNotFoundException("The system cannot find the " +
                    "configuration file specified: " + configFile);
        }
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
     * @throws net.juniper.netconf.CommitException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public void commit() throws IOException, SAXException {
        lastRpcReply = getRpcReply("" + "<rpc>" + "<commit/>" + "</rpc>" + "]]>]]>");
        if (hasError() || !isOK())
            throw new CommitException("Commit operation returned error.");
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
        lastRpcReply = getRpcReply("" + "<rpc>" + "<commit>" + "<confirmed/>" + "<confirm-timeout>" + seconds + "</confirm-timeout>" + "</commit>" + "</rpc>" + "]]>]]>");
        if (hasError() || !isOK())
            throw new CommitException("Commit operation returned " +
                    "error.");
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
        return convertToXML(getConfig("candidate", configTree));
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
        return convertToXML(getConfig("running", configTree));
    }

    /**
     * Retrieve the whole candidate configuration.
     * @return configuration data as XML object.
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public XML getCandidateConfig() throws SAXException, IOException {
        return convertToXML(getConfig("candidate",
                "<configuration></configuration>"));
    }

    /**
     * Retrieve the whole running configuration.
     * @return configuration data as XML object.
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public XML getRunningConfig() throws SAXException, IOException {
        return convertToXML(getConfig("running",
                "<configuration></configuration>"));
    }

    /**
     * Validate the candidate configuration.
     * @return true if validation successful.
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public boolean validate() throws IOException, SAXException {

        lastRpcReply = getRpcReply("" + "<rpc>" + "<validate>" + "<source>" + "<candidate/>" + "</source>" + "</validate>" + "</rpc>" + "]]>]]>");
        return !(hasError() || !isOK());
    }

    /**
     * Reboot the device corresponding to the Netconf Session.
     * @return RPC reply sent by Netconf server.
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public String reboot() throws IOException {
        return getRpcReply("" + "<rpc>" + "<request-reboot/>" + "</rpc>" + "]]>]]>");
    }

    /**
     * Run a cli command.
     * NOTE: The text output is supported for JUNOS 11.4 and later.
     * @param command
     *       the cli command to be executed.
     * @return result of the command, as a String.
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public String runCliCommand(String command) throws IOException, SAXException  {

        String rpcReply = getRpcReply("" + "<rpc>" + "<command format=\"text\">" + command + "</command>" + "</rpc>" + "]]>]]>");
        lastRpcReply = rpcReply;
        XML xmlReply = convertToXML(rpcReply);
        ArrayList tags = new ArrayList();
        tags.add("output");
        String output = xmlReply.findValue(tags);
        if (output != null)
            return output;
        else
            return rpcReply;
    }

    /**
     * Run a cli command.
     * @param command
     *       the cli command to be executed.
     * @return result of the command, as a BufferedReader. This is
     *         useful if we want continuous stream of output, rather than wait
     *         for whole output till command execution completes.
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public BufferedReader runCliCommandRunning(String command) throws
            SAXException, IOException {

        return executeRPCRunning("" + "<command format=\"text\">" + command + "</command>");
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

        lastRpcReply = getRpcReply("" + "<rpc>" + "<open-configuration>" + mode + "</open-configuration>" + "</rpc>" + "]]>]]>");
    }

    /**
     * This method should be called to close a private session, in case its
     * started.
     * @throws java.io.IOException
     */
    public void closeConfiguration() throws IOException {
        lastRpcReply = getRpcReply("" + "<rpc>" + "<close-configuration/>" + "</rpc>" + "]]>]]>");
    }

    /**
     * Returns the last RPC reply sent by Netconf server.
     * @return Last RPC reply, as a string.
     */
    public String getLastRPCReply() {
        return this.lastRpcReply;
    }

}
