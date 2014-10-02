package net.juniper.netconf;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class XMLBuilderTest {

    XMLBuilder builder;
    final static String NEWLINE = System.getProperty("line.separator");

    @org.junit.Before
    public void setUp() throws Exception {
        builder = new XMLBuilder();
    }

    @Test
    public void testCreateNewFtpConfigWithFormatting() throws Exception {
        XML ftpConfig = builder.createNewConfig("system", "services", "ftp");
        assertNotNull(ftpConfig);
        String expectedOutput = "<configuration>" + NEWLINE +
                "    <system>" + NEWLINE +
                "        <services>" + NEWLINE +
                "            <ftp/>" + NEWLINE +
                "        </services>" + NEWLINE +
                "    </system>" + NEWLINE +
                "</configuration>";
        assertEquals(expectedOutput, ftpConfig.toString().trim());
    }


    @Test
    public void testCreateNewTrapGroupConfigWithoutFormatting() throws Exception {
        XML trapGroupConfig = builder.createNewConfig("snmp");
        XML trapGroup = trapGroupConfig.addPath("trap-group");
        trapGroup.append("group-name", "new-trap-receiver");
        XML categories = trapGroup.append("categories");
        categories.append("chassis");
        categories.append("link");
        trapGroup.append("destination-port", "162");
        XML targets = trapGroup.append("targets");
        targets.append("name", "10.0.0.1");


        String expectedOutput = "<configuration><snmp><trap-group><group-name>new-trap-receiver</group-name><categories><chassis/><link/></categories><destination-port>162</destination-port><targets><name>10.0.0.1</name></targets></trap-group></snmp></configuration>";
        assertEquals(expectedOutput, trapGroupConfig.toString().replaceAll(">\\s+<", "><").trim());
    }

}