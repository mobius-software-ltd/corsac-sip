package gov.nist.javax.sip.parser.extensions;

import java.text.ParseException;

import gov.nist.javax.sip.header.extensions.ResourcePriorityHeader;
import gov.nist.javax.sip.parser.ParserTestCase;

public class ResourcePriorityParserTest extends ParserTestCase {
    /**
    * Test for the Resource-Priority parser
    */
    public void testParser() {
        String[] resourcePriorityHeaders = {
            "Resource-Priority: ets.0\n",
            "Resource-Priority: wps.2\n",
            "Resource-Priority: ns1.1;param1=value1;param2=value2\n",
            "Resource-Priority: ns2.3;param3=value3\n"
        };

        super.testParser(ResourcePriorityParser.class, resourcePriorityHeaders);
    }

    public void testParameters() {
        try {
            String resourcePriorityHeader = "Resource-Priority: ns1.1;param1=value1;param2=value2\n";
            ResourcePriorityParser rpParser = new ResourcePriorityParser(resourcePriorityHeader);
            ResourcePriorityHeader rpHeader = (ResourcePriorityHeader) rpParser.parse();
            assertNotNull(rpHeader);

            assertEquals("ns1", rpHeader.getNamespace());
            assertEquals("1", rpHeader.getPriority());

            // Test for additional parameters
            assertEquals("value1", rpHeader.getParameter("param1"));
            assertEquals("value2", rpHeader.getParameter("param2"));

            // Test toString() method
            assertEquals(resourcePriorityHeader.trim(), rpHeader.toString().trim());

            // Test encode() method
            assertEquals(resourcePriorityHeader.trim().toLowerCase(), rpHeader.encode().trim().toLowerCase());
        } catch (ParseException e) {
            // Handle the exception gracefully, e.g., log an error message
            fail("Parsing exception occurred: " + e.getMessage());
        }
    }
}

