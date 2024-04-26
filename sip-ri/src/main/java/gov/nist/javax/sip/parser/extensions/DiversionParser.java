package gov.nist.javax.sip.parser.extensions;

import gov.nist.javax.sip.header.*;
import gov.nist.javax.sip.header.extensions.Diversion;
import gov.nist.javax.sip.header.extensions.DiversionHeader;
import gov.nist.javax.sip.header.extensions.DiversionList;
import gov.nist.javax.sip.parser.*;
import java.text.ParseException;

/**
 * Parser for Diversion header.
 * @author valeriiamukha
 * 
 */

public class DiversionParser extends AddressParametersParser {

    public DiversionParser(String address) {
        super(address);
    }

    protected DiversionParser(Lexer lexer) {
        super(lexer);
    }

    /** parse the String message and generate the Diversion List Object
     * @return SIPHeader the Diversion List object
     * @throws SIPParseException if errors occur during the parsing
     */
    public SIPHeader parse() throws ParseException {
        DiversionList retval = new DiversionList();
            this.lexer.match(TokenTypes.DIVERSION);
            this.lexer.SPorHT();
            this.lexer.match(':');
            this.lexer.SPorHT();
            while (true) {
                Diversion diversion = new Diversion();
                if (lexer.lookAhead(0) == '*') {
                    final char next = lexer.lookAhead(1);
                    if (next == ' ' || next == '\t' || next == '\r' || next == '\n') {
                        this.lexer.match('*');
                        diversion.setWildCardFlag(true);
                    } else {
                        super.parse(diversion);
                    }
                } else {
                    super.parse(diversion);
                }
                retval.add(diversion);
                this.lexer.SPorHT();
                char la = lexer.lookAhead(0);
                if (la == ',') {
                    this.lexer.match(',');
                    this.lexer.SPorHT();
                } else if (la == '\n' || la == '\0')
                    break;
                else
                    throw createParseException("unexpected char");
            }
            return retval;
        } 

    //testing case
    
    public static void main(String args[]) throws ParseException {
        String[] diversionStrings = {
                "Diversion: <sip:user@example.com>;reason=busy;limit=4;privacy=conditionally;counter=2;screen=yes\n"
        };

        for (int i = 0; i < diversionStrings.length; i++) {
            DiversionParser dp = new DiversionParser(diversionStrings[i]);
            DiversionList dList = (DiversionList) dp.parse();
            System.out.println("Parsing => " + diversionStrings[i]);
            
            // Iterate over the list and handle each DiversionHeader individually
            for (DiversionHeader d : dList) {
                System.out.print("encoded = " + d.encode() + "==> ");
                System.out.println("address: " + d.getAddress() + " reason="
                        + d.getReason() + " limit=" + d.getLimit()
                        + " privacy=" + d.getPrivacy() + " counter=" + d.getCounter()
                        + " screen=" + d.getScreen() );
            }
        }
    }
}


