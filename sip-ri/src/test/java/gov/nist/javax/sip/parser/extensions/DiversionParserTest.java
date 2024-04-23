package gov.nist.javax.sip.parser.extensions;

import java.text.ParseException;

import gov.nist.javax.sip.header.extensions.DiversionHeader;
import gov.nist.javax.sip.header.extensions.DiversionList;
import gov.nist.javax.sip.parser.ParserTestCase;

public class DiversionParserTest extends ParserTestCase {
	/**
	*@author ValeriiaMukha
	*/
	
   public void testParser() {
       String[] diversionHeaders = {
    		   "Diversion: <sip:user@example.com>;reason=busy;limit=4;privacy=conditionally;counter=2;screen=yes\n",
               "Diversion: <sip:alice@example.com>;reason=unavailable;limit=3;privacy=urgent;counter=1;screen=no\n",
               "Diversion: <sip:bob@example.com>;reason=no-answer;limit=5;privacy=emergency;counter=3;screen=yes\n",
               "Diversion: <sip:carol@example.com>;reason=offline;limit=2;privacy=normal;counter=4;screen=no\n",
               "Diversion: <sip:dave@example.com>;reason=available;limit=1;privacy=confidential;counter=5;screen=yes\n"
       };

       super.testParser(DiversionParser.class, diversionHeaders);
   }

   public void testParameters() {
	    try {
	        String diversionHeader = "Diversion: <sip:user@example.com>;reason=busy;limit=4;privacy=conditionally;counter=2;screen=yes;randomParam=randomValue\n";
	        DiversionParser dp = new DiversionParser(diversionHeader);
	        DiversionList dList = (DiversionList) dp.parse();
	        assertNotNull(dList);
	        assertFalse(dList.isEmpty());

	        DiversionHeader diversion = (DiversionHeader) dList.getFirst();
	        assertNotNull(diversion);

	        assertEquals("busy", diversion.getReason());
	        assertEquals("4", diversion.getLimit());
	        assertEquals("conditionally", diversion.getPrivacy());
	        assertEquals("2", diversion.getCounter());
	        assertEquals("yes", diversion.getScreen());

	        // Test for a random parameter
	        assertEquals("randomValue", diversion.getParameter("randomParam"));

	        // Test toString() method
	        assertEquals(diversionHeader.trim(), diversion.toString().trim());

	        // Test encode() method
	        assertEquals(diversionHeader.trim().toLowerCase(), diversion.encode().trim().toLowerCase());
	    } catch (ParseException e) {
	        // Handle the exception gracefully, e.g., log an error message
	        fail("Parsing exception occurred: " + e.getMessage());
	    }
	}
}

