/*
 * Copyright (c) 2013 Juniper Networks, Inc.
 * All Rights Reserved
 *
 * Use is subject to license terms.
 *
 */


//code to load snmp configuration

import net.juniper.netconf.CommitException;
import net.juniper.netconf.Device;
import net.juniper.netconf.LoadException;
import net.juniper.netconf.XML;
import net.juniper.netconf.XMLBuilder;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;


/*Build the XML configuration. The XML configuration required is:

<configuration>
    <system>
        <services>
            <ftp/>
        </services>
    </system>
</configuration>
<edit-config>
    <target>
        <candidate></candidate>
    </target>
    <config>
        <configuration>
            <snmp>
                <trap-group>
                    <group-name>new-trap-receiver</group-name>
                    <categories>
                        <chassis></chassis>
                        <link></link>
                    </categories>
                    <destination-port>162</destination-port>
                    <targets>
                        <name>10.0.0.1</name>
                    </targets>
                </trap-group>
            </snmp>
        </configuration>
    </config>
</edit-config>


XMLBuilder builder = new XMLBuilder();
XML ftp_config = builder.createNewConfig("system", "services", "ftp");

*/

public class snmp_config {
    public static void main(String[] args) throws IOException,
            ParserConfigurationException, SAXException {

        XMLBuilder builder = new XMLBuilder();
        XML trapGroupConfig = builder.createNewConfig("snmp");
        XML trapGroup = trapGroupConfig.addPath("trap-group");
        trapGroup.append("group-name", "new-trap-receiver");
        XML categories = trapGroup.append("categories");
        categories.append("chassis");
        categories.append("link");
        trapGroup.append("destination-port", "162");
        XML targets = trapGroup.append("targets");
        targets.append("name", "10.0.0.1");

        Device device = CreateDevice.createDevice();
        device.connect();

        //Lock the configuration first
        boolean isLocked = device.lockConfig();
        if (!isLocked) {
            System.out.println("Could not lock configuration. Exit now.");
            return;
        }

        //Load and commit the configuration
        try {
            device.loadXMLConfiguration(trapGroupConfig.toString(), "merge");
            device.commit();
        } catch (LoadException | CommitException e) {
            System.out.println(e.getMessage());
            return;
        }

        //Unlock the configuration and close the device.
        device.unlockConfig();
        device.close();
    }
}
