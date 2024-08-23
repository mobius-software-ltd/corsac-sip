package gov.nist.javax.sip.parser.extensions;

import java.text.ParseException;

import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.header.extensions.Resource;
import gov.nist.javax.sip.header.extensions.ResourcePriority;
import gov.nist.javax.sip.header.extensions.ResourcePriorityHeader;
import gov.nist.javax.sip.parser.Lexer;
import gov.nist.javax.sip.parser.ParametersParser;
import gov.nist.javax.sip.parser.TokenTypes;

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
                "Resource-Priority: ets.0, wps.2"
        };

        for (int i = 0; i < resourcePriorityStrings.length; i++) {
            ResourcePriorityParser rpParser = new ResourcePriorityParser(resourcePriorityStrings[i]);
            ResourcePriorityHeader rpHeader = (ResourcePriorityHeader) rpParser.parse();
            System.out.println("Parsing => " + resourcePriorityStrings[i]);
            
            System.out.print("encoded = " + rpHeader.encode() + "==> ");
            for(Resource resource : rpHeader.getResources())
            	System.out.println("namespace: " + resource.getNamespace() + " priority=" + resource.getResource());
        }
    }
}


