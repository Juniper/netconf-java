/*
 * Copyright (c) 2013 Juniper Networks, Inc.
 * All Rights Reserved
 *
 * Use is subject to license terms.
 *
 */

import net.juniper.netconf.CommitException;
import net.juniper.netconf.Device;
import net.juniper.netconf.LoadException;
import net.juniper.netconf.XML;
import net.juniper.netconf.XMLBuilder;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;


public class EditConfiguration {
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

        Device device = CreateDevice.createDevice();
        device.connect();

         //Lock the configuration first
         boolean isLocked = device.lockConfig();
         if(!isLocked) {
             System.out.println("Could not lock configuration. Exit now.");
             return;
         }

         //Load and commit the configuration
         try {
             device.loadXMLConfiguration(ftp_config.toString(), "merge");
             device.commit();
         } catch(LoadException | CommitException e) {
             System.out.println(e.getMessage());
             return;
         }

        //Unlock the configuration and close the device.
         device.unlockConfig();
         device.close();
    }
}
