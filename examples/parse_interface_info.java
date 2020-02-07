/*
 * Copyright (c) 2013 Juniper Networks, Inc.
 * All Rights Reserved
 *
 * Use is subject to license terms.
 *
 */

//code to parse layered rpc reply

import net.juniper.netconf.Device;
import net.juniper.netconf.XML;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class parse_interface_info {
    public static void main(String[] args) throws IOException,
            SAXException {

        Device device = CreateDevice.createDevice();
        device.connect();

         XML rpc_reply = device.executeRPC("get-interface-information");
         System.out.println(rpc_reply.toString());
        // Obtain a list of list of ‘org.w3c.dom.Node’ objects
        List<String> list = Arrays.asList("interface-information","physical-interface"); 
        List physical_interfaces_list = rpc_reply.findNodes(list);
        // Print the value for each of the name elements:
        for (Object o : physical_interfaces_list) {
            Node node = (Node) o;
            NodeList child_nodes_of_phy_interface = node.getChildNodes();
            // child_nodes_of_phy_interface contains nodes like <name> and <admin-status>
            // Get each <name> node from the NodeList
            for (int i = 0; i < child_nodes_of_phy_interface.getLength(); i++) {
                Node child_node = child_nodes_of_phy_interface.item(i);
                if (child_node.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                if (child_node.getNodeName().equals("name")) { // Print the text value of the <name> node
                    System.out.println(child_node.getTextContent());
                }
                break;
            }
        }
  }
}
