package gov.nist.javax.sip.parser.extensions;

import java.text.ParseException;

import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.header.extensions.AcceptResourcePriority;
import gov.nist.javax.sip.header.extensions.AcceptResourcePriorityHeader;
import gov.nist.javax.sip.header.extensions.Resource;
import gov.nist.javax.sip.parser.Lexer;
import gov.nist.javax.sip.parser.ParametersParser;
import gov.nist.javax.sip.parser.TokenTypes;

/**
 * Parser for Accept-Resource-Priority header.
 */
public class AcceptResourcePriorityParser extends ParametersParser {

    public AcceptResourcePriorityParser(String acceptResourcePriority) {
        super(acceptResourcePriority);
    }

    protected AcceptResourcePriorityParser(Lexer lexer) {
        super(lexer);
    }

    /**
     * parse the String message and generate the AcceptResourcePriority Object
     * @return SIPHeader the AcceptResourcePriority object
     * @throws SIPParseException if errors occur during the parsing
     */
    public SIPHeader parse() throws ParseException {
        AcceptResourcePriority acceptResourcePriority = new AcceptResourcePriority();
        this.lexer.match(TokenTypes.ACCEPT_RESOURCE_PRIORITY);
        this.lexer.SPorHT();
        this.lexer.match(':');
        this.lexer.SPorHT();

        String rest = this.lexer.getRest().trim();
        acceptResourcePriority.decodeBody(rest);

        return acceptResourcePriority;
    }

    // testing case
    public static void main(String args[]) throws ParseException {
        String[] acceptResourcePriorityStrings = {
                "Accept-Resource-Priority: ets.0 , wps.2"                
        };

        for (int i = 0; i < acceptResourcePriorityStrings.length; i++) {
            AcceptResourcePriorityParser arpParser = new AcceptResourcePriorityParser(acceptResourcePriorityStrings[i]);
            AcceptResourcePriorityHeader arpHeader = (AcceptResourcePriorityHeader) arpParser.parse();
            System.out.println("Parsing => " + acceptResourcePriorityStrings[i]);
            
            System.out.print("encoded = " + arpHeader.encode() + "==> ");
            for(Resource resource : arpHeader.getResources())
            	System.out.println("namespace: " + resource.getNamespace() + " priority=" + resource.getResource());
        }
    }
}

