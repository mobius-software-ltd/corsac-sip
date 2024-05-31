package gov.nist.javax.sip.header.extensions;

import java.text.ParseException;
import javax.sip.header.ExtensionHeader;
import gov.nist.javax.sip.header.*;

/**
 * SIP Accept-Resource-Priority header implementation.
 */
public class AcceptResourcePriority extends ParametersHeader implements ExtensionHeader, AcceptResourcePriorityHeader {

    private static final long serialVersionUID = 1L;

    private String namespace;
    private String priority;

    public static final String NAME = "Accept-Resource-Priority"; // Ensure the correct header name

    /**
     * Default constructor.
     */
    public AcceptResourcePriority() {
        super(NAME); // Ensure the superclass is properly initialized
    }

    /**
     * Constructor with namespace and priority.
     *
     * @param namespace the namespace to set
     * @param priority the priority to set
     */
    public AcceptResourcePriority(String namespace, String priority) throws IllegalArgumentException {
        super(NAME); // Ensure the superclass is properly initialized
        this.namespace = namespace;
        this.priority = priority;
    }

    /**
     * Encode the body part of this header (i.e. leave out the hdrName).
     * @return String encoded body part of the header.
     */
    public StringBuilder encodeBody(StringBuilder retval) {
        if (namespace == null || priority == null)
            return retval;
        else {
            retval.append(namespace).append('.').append(priority);
            if (!parameters.isEmpty()) {
                retval.append(SEMICOLON);
                parameters.encode(retval);
            }
            return retval;
        }
    }

    /**
     * Get the namespace.
     *
     * @return the namespace
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Set the namespace.
     *
     * @param namespace the namespace to set
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * Get the priority.
     *
     * @return the priority
     */
    public String getPriority() {
        return priority;
    }

    /**
     * Set the priority.
     *
     * @param priority the priority to set
     */
    public void setPriority(String priority) {
        this.priority = priority;
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
            String[] parts = body.split(";");
            String[] acceptResourcePriority = parts[0].split("\\.");

            if (acceptResourcePriority.length != 2) {
                throw new ParseException("Invalid Accept-Resource-Priority header format", 0);
            }

            this.namespace = acceptResourcePriority[0].trim();
            this.priority = acceptResourcePriority[1].trim();

            // Parse additional parameters if they exist
            for (int i = 1; i < parts.length; i++) {
                String param = parts[i].trim();
                int equalIndex = param.indexOf('=');
                if (equalIndex != -1) {
                    String paramName = param.substring(0, equalIndex).trim();
                    String paramValue = param.substring(equalIndex + 1).trim();
                    this.setParameter(paramName, paramValue);
                }
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

