package gov.nist.javax.sip.parser.extensions;


import gov.nist.javax.sip.parser.ParserTestCase;

public class DiversionParserTest extends ParserTestCase {

	@Override
	public void testParser() {
	
		String[] diversions = {
				 "Diversion: sip:user@example.com;reason=busy;limit=4;privacy=conditionally;counter=2;screen=yes;extension=token\n" };
		super.testParser(DiversionParser.class, diversions);
    }
}

