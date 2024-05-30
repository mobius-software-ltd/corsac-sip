package gov.nist.javax.sip.parser.extensions;

import java.text.ParseException;

import gov.nist.javax.sip.header.extensions.AcceptResourcePriorityHeader;
import gov.nist.javax.sip.parser.ParserTestCase;

public class AcceptResourcePriorityParserTest extends ParserTestCase {
    /**
    * Test for the Accept-Resource-Priority parser
    */
    public void testParser() {
        String[] acceptResourcePriorityHeaders = {
            "Accept-Resource-Priority: ets.0\n",
            "Accept-Resource-Priority: wps.2\n",
            "Accept-Resource-Priority: ns1.1;param1=value1;param2=value2\n",
            "Accept-Resource-Priority: ns2.3;param3=value3\n"
        };

        super.testParser(AcceptResourcePriorityParser.class, acceptResourcePriorityHeaders);
    }

    public void testParameters() {
        try {
            String acceptResourcePriorityHeader = "Accept-Resource-Priority: ns1.1;param1=value1;param2=value2\n";
            AcceptResourcePriorityParser arpParser = new AcceptResourcePriorityParser(acceptResourcePriorityHeader);
            AcceptResourcePriorityHeader arpHeader = (AcceptResourcePriorityHeader) arpParser.parse();
            assertNotNull(arpHeader);

            assertEquals("ns1", arpHeader.getNamespace());
            assertEquals("1", arpHeader.getPriority());

            // Test for additional parameters
            assertEquals("value1", arpHeader.getParameter("param1"));
            assertEquals("value2", arpHeader.getParameter("param2"));

            // Test toString() method
            assertEquals(acceptResourcePriorityHeader.trim(), arpHeader.toString().trim());

            // Test encode() method
            assertEquals(acceptResourcePriorityHeader.trim().toLowerCase(), arpHeader.encode().trim().toLowerCase());
        } catch (ParseException e) {
            // Handle the exception gracefully, e.g., log an error message
            fail("Parsing exception occurred: " + e.getMessage());
        }
    }
}