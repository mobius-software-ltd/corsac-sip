package gov.nist.javax.sip.header.extensions;

import java.text.ParseException;
import javax.sip.header.Header;
import javax.sip.header.Parameters;

public interface ResourcePriorityHeader extends Parameters, Header {
    
    /**
     * Sets the Namespace of the ResourcePriorityHeader. The Namespace parameter 
     * specifies the namespace of the priority value.
     *
     * @param namespace - the string value of the Namespace of this ResourcePriorityHeader
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the namespace value.
     */
    public void setNamespace(String namespace) throws ParseException;

    /**
     * Returns the Namespace of ResourcePriorityHeader. The Namespace parameter 
     * specifies the namespace of the priority value.
     *
     * @return the String value of the Namespace of this ResourcePriorityHeader
     */
    public String getNamespace();
    
    /**
     * Sets the Priority of the ResourcePriorityHeader. The Priority parameter 
     * specifies the priority value within the given namespace.
     *
     * @param priority - the string value of the Priority of this ResourcePriorityHeader
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the priority value.
     */
    public void setPriority(String priority) throws ParseException;

    /**
     * Returns the Priority of ResourcePriorityHeader. The Priority parameter 
     * specifies the priority value within the given namespace.
     *
     * @return the String value of the Priority of this ResourcePriorityHeader
     */
    public String getPriority();
    
    /**
     * Encode the header into a string.
     *
     * @return the encoded string representation of the header.
     */
    public String encode();
    
    /**
     * Name of ResourcePriorityHeader
     */
    public final static String NAME = "Resource-Priority";
}

