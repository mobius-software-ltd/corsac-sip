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
            "Accept-Resource-Priority: dsn.flash, wps.3\n"
        };

        super.testParser(AcceptResourcePriorityParser.class, acceptResourcePriorityHeaders);
    }

    public void testSingle() {
        try {
            String acceptResourcePriorityHeader = "Accept-Resource-Priority: ns1.1\n";
            AcceptResourcePriorityParser arpParser = new AcceptResourcePriorityParser(acceptResourcePriorityHeader);
            AcceptResourcePriorityHeader arpHeader = (AcceptResourcePriorityHeader) arpParser.parse();
            assertNotNull(arpHeader);

            assertEquals(arpHeader.getResources().size(),1);
            assertEquals("ns1", arpHeader.getResources().get(0).getNamespace());
            assertEquals("1", arpHeader.getResources().get(0).getResource());

            // Test toString() method
            assertEquals(acceptResourcePriorityHeader.trim(), arpHeader.toString().trim());

            // Test encode() method
            assertEquals(acceptResourcePriorityHeader.trim().toLowerCase(), arpHeader.encode().trim().toLowerCase());
        } catch (ParseException e) {
            // Handle the exception gracefully, e.g., log an error message
            fail("Parsing exception occurred: " + e.getMessage());
        }
    }
    
    public void testMulti() {
        try {
            String acceptResourcePriorityHeader = "Accept-Resource-Priority: dsn.flash,wps.3 \n";
            AcceptResourcePriorityParser arpParser = new AcceptResourcePriorityParser(acceptResourcePriorityHeader);
            AcceptResourcePriorityHeader arpHeader = (AcceptResourcePriorityHeader) arpParser.parse();
            assertNotNull(arpHeader);

            assertEquals(arpHeader.getResources().size(),2);
            assertEquals("dsn", arpHeader.getResources().get(0).getNamespace());
            assertEquals("flash", arpHeader.getResources().get(0).getResource());
            assertEquals("wps", arpHeader.getResources().get(1).getNamespace());
            assertEquals("3", arpHeader.getResources().get(1).getResource());

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