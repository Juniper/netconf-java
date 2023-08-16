package net.juniper.netconf;

import lombok.extern.slf4j.Slf4j;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

@Slf4j
public class Main {
    public static void main(String args[]) throws IOException, SAXException {
        String mainTrackerIp = getIPByTrackerHost();
        if (args.length != 3)
            throw new IllegalArgumentException("Need 3 arguments: path to RSA key, hostname, username");
        Device device = Device.builder()
            .hostName(args[1])
            .userName(args[2])
            .keyBasedAuthentication(true)
            .pemKeyFile(args[0])
            .strictHostKeyChecking(false)
            .build();
        device.connect();
        String rpcRequestTemplate = "<rpc><get-route-information format='json'><destination>%s</destination><table>inet.0</table><protocol>rip</protocol></get-route-information></rpc>";
        String request = String.format(rpcRequestTemplate, mainTrackerIp);
        log.info("rpc-request is: \n{}", request);
        XML rpc_reply = device.executeRPC(request);
        System.out.println(rpc_reply);//Write to System.out stream for usage later in separate input stream
        device.close();
    }

    private static final String TRACKER_HOST = "ad.propellerads.com";

    private static String getIPByTrackerHost() throws UnknownHostException {
        InetAddress[] ipaddress = InetAddress.getAllByName(TRACKER_HOST);
        return ipaddress[0].getHostAddress();
    }
}
