netconf-java
============

Java library for NETCONF

SUPPORT
=======

This software is not officially supported by Juniper Networks, but by a team dedicated to helping customers,
partners, and the development community.  To report bug-fixes, issues, suggestions, please contact netconf-automation-hackers@juniper.net

SYNOPSIS
========

    import java.io.IOException;
    import javax.xml.parsers.ParserConfigurationException;
    import net.juniper.netconf.NetconfException;
    import org.xml.sax.SAXException;

    import net.juniper.netconf.XML;
    import net.juniper.netconf.Device;

    public class ShowInterfaces {
        public static void main(String args[]) throws NetconfException,
                  ParserConfigurationException, SAXException, IOException {

            //Create device
            Device device = new Device("hostname","user","password",null);
            device.connect();

            //Send RPC and receive RPC Reply as XML
            XML rpc_reply = device.executeRPC("get-interface-information");
            /* OR
             * device.executeRPC("<get-interface-information/>");
             * OR
             * device.executeRPC("<rpc><get-interface-information/></rpc>");
             */

            //Print the RPC-Reply and close the device.
            System.out.println(rpc_reply);
            device.close();
        }
    }

AUTHOR
======

[Ankit Jain, Juniper Networks](ankitj@juniper.net)
