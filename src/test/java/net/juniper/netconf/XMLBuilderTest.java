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
    public void testCreateNewFtpConfig() throws Exception {
        XML ftp_config = builder.createNewConfig("system", "services", "ftp");
        assertNotNull(ftp_config);
        String expectedOutput = "<configuration>" + NEWLINE +
                "    <system>" + NEWLINE +
                "        <services>" + NEWLINE +
                "            <ftp/>" + NEWLINE +
                "        </services>" + NEWLINE +
                "    </system>" + NEWLINE +
                "</configuration>";
        assertEquals(expectedOutput, ftp_config.toString().trim());
    }

}