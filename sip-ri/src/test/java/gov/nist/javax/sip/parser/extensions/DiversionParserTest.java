package gov.nist.javax.sip.parser.extensions;

import gov.nist.javax.sip.header.extensions.DiversionHeader;
import gov.nist.javax.sip.header.extensions.DiversionList;
import gov.nist.javax.sip.parser.ParserTestCase;


import java.text.ParseException;

public class DiversionParserTest extends ParserTestCase {

    public void testParser() {
        String[] diversions = {
            "Diversion: sip:user@example.com;reason=busy;limit=4;privacy=conditionally;counter=2;screen=yes;extension=token\n"
        };
        super.testParser(DiversionParser.class, diversions);
    }

    public void testParseDiversionHeaderParameters() throws ParseException {
        String diversionHeader = "Diversion: sip:user@example.com;reason=busy;limit=4;privacy=conditionally;counter=2;screen=yes;extension=token\n";
        try {
            DiversionParser parser = new DiversionParser(diversionHeader);
            DiversionList diversionList = (DiversionList) parser.parse();
            assertNotNull(diversionList);

            for (DiversionHeader diversion : diversionList) {
                System.out.println("Diversion Header Parameters:");
                System.out.println("Address: " + diversion.getAddress().toString());
                System.out.println("Reason: " + diversion.getReason());
                System.out.println("Limit: " + diversion.getLimit());
                System.out.println("Privacy: " + diversion.getPrivacy());
                System.out.println("Counter: " + diversion.getCounter());
                System.out.println("Screen: " + diversion.getScreen());
                System.out.println("Extension: " + diversion.getExtension());
            }
        } catch (Exception e) {
            fail("Exception occurred: " + e.getMessage());
        }
    }
}
