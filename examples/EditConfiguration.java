/*
 * Copyright (c) 2013 Juniper Networks, Inc.
 * All Rights Reserved
 *
 * Use is subject to license terms.
 *
 */

import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import net.juniper.netconf.CommitException;
import net.juniper.netconf.LoadException;
import net.juniper.netconf.NetconfException;
import org.xml.sax.SAXException;

import net.juniper.netconf.Device;
import net.juniper.netconf.XML;
import net.juniper.netconf.XMLBuilder;


public class EditConfiguration {
    public static void main(String[] args) throws LoadException, IOException, 
            NetconfException, ParserConfigurationException, SAXException {
        
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
         Device device = new Device("10.209.12.13","user","PaSsWoRd",null);
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
         } catch(LoadException e) {
             System.out.println(e.getMessage());
             return;
         } catch(CommitException e) {
             System.out.println(e.getMessage());
             return;
         }

         //Unlock the configuration and close the device.
         device.unlockConfig();
         device.close();
    }
}
