package net.juniper.netconf;

public class TestConstants {

    public static final String CORRECT_HELLO = "<hello xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n" +
            "<capabilities>\n" +
            "<capability>urn:ietf:params:netconf:base:1.0</capability>\n" +
            "<capability>urn:ietf:params:netconf:base:1.0#candidate</capability>\n" +
            "<capability>urn:ietf:params:netconf:base:1.0#confirmed-commit</capability>\n" +
            "<capability>urn:ietf:params:netconf:base:1.0#validate</capability>\n" +
            "<capability>urn:ietf:params:netconf:base:1.0#url?protocol=http,ftp,file</capability>\n" +
            "</capabilities>\n" +
            "</hello>";
    public static final String LLDP_REQUEST = "<rpc><get-lldp-neighbors-information></get-lldp-neighbors-information></rpc>";
}
