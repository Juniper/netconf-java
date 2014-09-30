/*
 * Copyright (c) 2013 Juniper Networks, Inc.
 * All Rights Reserved
 *
 * Use is subject to license terms.
 *
 */

import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import net.juniper.netconf.NetconfException;
import org.xml.sax.SAXException;

import net.juniper.netconf.XML;
import net.juniper.netconf.Device;

public class ShowChassis {
    public static void main(String args[]) throws NetconfException,
              ParserConfigurationException, SAXException, IOException {

        //Create device
        Device device = new Device("10.127.11.101","admin","Lab123!",null);
        device.connect();

        //Send RPC and receive RPC Reply as XML
        XML rpc_reply = device.executeRPC("get-chassis-inventory");
        /* OR
         * device.executeRPC("<get-chassis-inventory/>");
         * OR
         * device.executeRPC("<rpc><get-chassis-inventory/></rpc>");
         */

        //Print the RPC-Reply and close the device.
        System.out.println(rpc_reply.toString());
        device.close();
    }
}
