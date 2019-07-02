package net.juniper.netconf;

import org.junit.Test;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static net.juniper.netconf.TestHelper.getSampleFile;
import static org.assertj.core.api.Assertions.assertThat;

public class XMLTest {

    private final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance() ;

    private void testFindValue(String sampleFileName, List<String> findValueList, String expectedValue)
            throws Exception {
        DocumentBuilder builder = factory.newDocumentBuilder();
        File sampleRPCFile = getSampleFile(sampleFileName);
        XML testXml = new XML(builder.parse(sampleRPCFile).getDocumentElement());

        assertThat(testXml.findValue(findValueList)).isEqualTo(expectedValue);
    }

    @Test
    public void GIVEN_sampleXML_WHEN_findValueOfSample_THEN_returnValue() throws Exception {
        String sampleFileName = "sampleFPCTempRPCReply.xml";
        List<String> findValueList = Arrays.asList(
                "environment-component-information",
                "environment-component-item",
                "name~Routing Engine 0",
                "temperature"
        );
        String expectedValue = "41 degrees C / 105 degrees F";
        testFindValue(sampleFileName,findValueList, expectedValue);
    }

    @Test
    public void GIVEN_sampleCliOutputRpc_WHEN_findValueOfSample_THEN_returnValue() throws Exception {
        String sampleFileName = "sampleCliOutputReply.xml";
        List<String> findValueList = Collections.singletonList("output");
        String expectedValue = "operational-response";
        testFindValue(sampleFileName,findValueList, expectedValue);
    }
}
