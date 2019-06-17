/*
 * Copyright (c) 2013 Juniper Networks, Inc.
 * All Rights Reserved
 *
 * Use is subject to license terms.
 *
 */

import net.juniper.netconf.Device;
import net.juniper.netconf.XML;
import org.xml.sax.SAXException;

import java.io.IOException;

public class ShowChassis {
    public static void main(String[] args) throws
            SAXException, IOException {

        Device device = CreateDevice.createDevice();
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
