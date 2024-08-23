package gov.nist.javax.sip.header.extensions;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import javax.sip.header.ExtensionHeader;

import gov.nist.javax.sip.header.SIPHeader;

/**
 * SIP Accept-Resource-Priority header implementation.
 */
public class AcceptResourcePriority extends SIPHeader implements ExtensionHeader, AcceptResourcePriorityHeader {

    private static final long serialVersionUID = 1L;

    private List<Resource> resources;

    public static final String NAME = "Accept-Resource-Priority"; // Ensure the correct header name

    /**
     * Default constructor.
     */
    public AcceptResourcePriority() {
        super(NAME); // Ensure the superclass is properly initialized
    }

    /**
     * Constructor.
     *
     * @param resources the resources to set
     */
    public AcceptResourcePriority(List<Resource> resources) throws IllegalArgumentException {
        super(NAME); // Ensure the superclass is properly initialized
        this.resources = resources;
    }

    /**
     * Encode the body part of this header (i.e. leave out the hdrName).
     * @return String encoded body part of the header.
     */
    public StringBuilder encodeBody(StringBuilder retval) {
        if (resources == null || resources.size()==0)
            return retval;
        else {
        	Boolean isFirst=true;
        	for(Resource currResource:resources) {
        		if(!isFirst)
        			retval.append(COMMA);
        			
        		retval.append(currResource.encode()); 
        		isFirst = false;
        	}
            return retval;
        }
    }

    /**
     * Get the resources.
     *
     * @return the resources
     */
    public List<Resource> getResources() {
        return resources;
    }

    /**
     * Set the resources.
     *
     * @param resources to set
     */
    public void setResources(List<Resource> resources) {
        this.resources = resources;
    }

    @Override
    public void setValue(String value) throws ParseException {
        decodeBody(value);
    }

    /**
     * Parses the header string into namespace and priority.
     *
     * @param body the header string to parse
     * @throws ParseException if the header string cannot be parsed
     */
    public void decodeBody(String body) throws ParseException {
        try {
            // Split the body by semicolon to extract parameters
            String[] parts = body.split(",");
            for(String currPart:parts) {
            	String[] acceptResourcePriority = currPart.split("\\.");

            	if (acceptResourcePriority.length != 2) {
            		throw new ParseException("Invalid Accept-Resource-Priority header format", 0);
            	}

            	if(resources==null)
            		resources = new ArrayList<Resource>();
            	
            	this.resources.add(new Resource(acceptResourcePriority[0].trim(), acceptResourcePriority[1].trim()));
            }
        } catch (Exception e) {
            throw new ParseException("Error parsing Accept-Resource-Priority header: " + e.getMessage(), 0);
        }
    }

    @Override
    public String encode() {
        StringBuilder retval = new StringBuilder();
        retval.append(NAME).append(": ");
        encodeBody(retval);
        return retval.toString();
    }
}

