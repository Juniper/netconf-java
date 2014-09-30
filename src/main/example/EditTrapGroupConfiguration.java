/*
 * Copyright (c) 2013 Juniper Networks, Inc.
 * All Rights Reserved
 *
 * Use is subject to license terms.
 *
 */

import net.juniper.netconf.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;


public class EditTrapGroupConfiguration {
    public static void main(String[] args) throws IOException,
            ParserConfigurationException, SAXException {

        /*Build the XML configuration
         *The XML configuration required is:
         *
         * <configuration>
         *     <system>
         *         <services>
         *             <ftp/>
         *         </services>
         *     </system>
         * </configuration>
         */
        XMLBuilder builder = new XMLBuilder();
        XML ftp_config = builder.createNewConfig("system", "services", "ftp");

        //Create the device
        Device device = new Device("10.209.12.13", "user", "PaSsWoRd", null);
        device.connect();

        //Lock the configuration first
        boolean isLocked = device.lockConfig();
        if (!isLocked) {
            System.out.println("Could not lock configuration. Exit now.");
            return;
        }

        //Load and commit the configuration
        try {
            device.loadXMLConfiguration(ftp_config.toString(), "merge");
            device.commit();
        } catch (LoadException e) {
            System.out.println(e.getMessage());
            return;
        } catch (CommitException e) {
            System.out.println(e.getMessage());
            return;
        }

        //Unlock the configuration and close the device.
        device.unlockConfig();
        device.close();
    }
}
