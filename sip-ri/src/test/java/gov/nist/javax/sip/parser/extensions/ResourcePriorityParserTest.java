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
            "Resource-Priority: dsn.flash, wps.3\n"
        };

        super.testParser(ResourcePriorityParser.class, resourcePriorityHeaders);
    }

    public void testSingle() {
        try {
            String resourcePriorityHeader = "Resource-Priority: ns1.1\n";
            ResourcePriorityParser rpParser = new ResourcePriorityParser(resourcePriorityHeader);
            ResourcePriorityHeader rpHeader = (ResourcePriorityHeader) rpParser.parse();
            assertNotNull(rpHeader);

            assertEquals(rpHeader.getResources().size(),1);
            assertEquals("ns1", rpHeader.getResources().get(0).getNamespace());
            assertEquals("1", rpHeader.getResources().get(0).getResource());

            // Test toString() method
            assertEquals(resourcePriorityHeader.trim(), rpHeader.toString().trim());

            // Test encode() method
            assertEquals(resourcePriorityHeader.trim().toLowerCase(), rpHeader.encode().trim().toLowerCase());
        } catch (ParseException e) {
            // Handle the exception gracefully, e.g., log an error message
            fail("Parsing exception occurred: " + e.getMessage());
        }
    }
    
    public void testMulti() {
        try {
            String resourcePriorityHeader = "Resource-Priority: dsn.flash,wps.3 \n";
            ResourcePriorityParser rpParser = new ResourcePriorityParser(resourcePriorityHeader);
            ResourcePriorityHeader rpHeader = (ResourcePriorityHeader) rpParser.parse();
            assertNotNull(rpHeader);

            assertEquals(rpHeader.getResources().size(),2);
            assertEquals("dsn", rpHeader.getResources().get(0).getNamespace());
            assertEquals("flash", rpHeader.getResources().get(0).getResource());
            assertEquals("wps", rpHeader.getResources().get(1).getNamespace());
            assertEquals("3", rpHeader.getResources().get(1).getResource());

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