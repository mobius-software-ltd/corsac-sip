package gov.nist.javax.sip.parser.extensions;

import gov.nist.javax.sip.header.*;
import gov.nist.javax.sip.header.extensions.ResourcePriority;
import gov.nist.javax.sip.header.extensions.ResourcePriorityHeader;
import gov.nist.javax.sip.parser.*;
import java.text.ParseException;

/**
 * Parser for Resource-Priority header.
 */
public class ResourcePriorityParser extends ParametersParser {

    public ResourcePriorityParser(String resourcePriority) {
        super(resourcePriority);
    }

    protected ResourcePriorityParser(Lexer lexer) {
        super(lexer);
    }

    /**
     * parse the String message and generate the ResourcePriority Object
     * @return SIPHeader the ResourcePriority object
     * @throws SIPParseException if errors occur during the parsing
     */
    public SIPHeader parse() throws ParseException {
        ResourcePriority resourcePriority = new ResourcePriority();
        this.lexer.match(TokenTypes.RESOURCE_PRIORITY);
        this.lexer.SPorHT();
        this.lexer.match(':');
        this.lexer.SPorHT();

        String rest = this.lexer.getRest().trim();
        resourcePriority.decodeBody(rest);

        return resourcePriority;
    }

    // testing case
    public static void main(String args[]) throws ParseException {
        String[] resourcePriorityStrings = {
                "Resource-Priority: ets.0",
                "Resource-Priority: wps.2"
        };

        for (int i = 0; i < resourcePriorityStrings.length; i++) {
            ResourcePriorityParser rpParser = new ResourcePriorityParser(resourcePriorityStrings[i]);
            ResourcePriorityHeader rpHeader = (ResourcePriorityHeader) rpParser.parse();
            System.out.println("Parsing => " + resourcePriorityStrings[i]);
            
            System.out.print("encoded = " + rpHeader.encode() + "==> ");
            System.out.println("namespace: " + rpHeader.getNamespace() + " priority="
                    + rpHeader.getPriority());
        }
    }
}


