package gov.nist.javax.sip.parser.extensions;

import gov.nist.javax.sip.header.*;
import gov.nist.javax.sip.header.extensions.Diversion;
import gov.nist.javax.sip.header.extensions.DiversionList;
import gov.nist.javax.sip.parser.*;
import java.text.ParseException;

/**
 * Parser for Diversion header.
 * @author valeriiamukha
 * 
 */

public class DiversionParser extends AddressParametersParser {

    public DiversionParser(String diversion) {
        super(diversion);
    }

    protected DiversionParser(Lexer lexer) {
        super(lexer);
    }

    /** parse the String message and generate the Diversion List Object
     * @return SIPHeader the Diversion List object
     * @throws SIPParseException if errors occur during the parsing
     */
    public SIPHeader parse() throws ParseException {
        DiversionList diversionList = new DiversionList();
        if (debug)
            dbg_enter("parse");

        try {
            this.lexer.match(TokenTypes.DIVERSION);
            this.lexer.SPorHT();
            this.lexer.match(':');
            this.lexer.SPorHT();
            while (true) {
                Diversion diversion = new Diversion();
                super.parse(diversion);
                diversionList.add(diversion);
                this.lexer.SPorHT();
                char la = lexer.lookAhead(0);
                if (la == ',') {
                    this.lexer.match(',');
                    this.lexer.SPorHT();
                } else if (la == '\n')
                    break;
                else
                    throw createParseException("unexpected char");
            }
            return diversionList;
        } finally {
            if (debug)
                dbg_leave("parse");
        }

    }

    public static void main(String args[]) throws ParseException {
        String[] diversionStrings = {
                "Diversion: sip:user@example.com;reason=busy;limit=4;privacy=conditionally;counter=2;screen=yes;extension=token\n"
        };

        for (int i = 0; i < diversionStrings.length; i++) {
            DiversionParser dp = new DiversionParser(diversionStrings[i]);
            DiversionList diversionList = (DiversionList) dp.parse();
            System.out.println("encoded = " + diversionList.encode());
        }
    }
}



