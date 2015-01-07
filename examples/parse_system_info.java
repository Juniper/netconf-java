/*
 * Copyright (c) 2013 Juniper Networks, Inc.
 * All Rights Reserved
 *
 * Use is subject to license terms.
 *
 */

// Code to parse following rpc-reply
/* <rpc-reply xmlns="urn:ietf:params:xml:ns:netconf:base:1.0" xmlns:junos="http://xml.juniper.net/junos/14.2I0/junos">
 * <system-information>
 * <hardware-model>abc</hardware-model>
 * <os-name>junos</os-name>
 * <os-version>14.2I20</os-version>
 * <host-name>server</host-name>
 * </system-information>
 * </rpc-reply>
 */



import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import net.juniper.netconf.Device;
import net.juniper.netconf.NetconfException;
import net.juniper.netconf.XML;
import org.xml.sax.SAXException;

public class parse_system_info {
     public static void main(String args[]) throws NetconfException, 
              ParserConfigurationException, SAXException, IOException {
        
        //Create device
        Device device = new Device("router","username","passwd",null);
        device.connect();
        
        //Send RPC and receive RPC Reply as XML
        XML rpc_reply = device.executeRPC("get-system-information");
        List<String> list1 = Arrays.asList("system-information","hardware-model");
        List<String> list2 = Arrays.asList("system-information","os-name");
        List<String> list3 = Arrays.asList("system-information","os-version");
        List<String> list4 = Arrays.asList("system-information","host-name");
        
        String val1= rpc_reply.findValue(list1);
        String val2= rpc_reply.findValue(list2);
        String val3= rpc_reply.findValue(list3);
        String val4= rpc_reply.findValue(list4);
        
        System.out.println(val1);
        System.out.println(val2);
        System.out.println(val3);
        System.out.println(val4);
     
     device.close();
     }
}
