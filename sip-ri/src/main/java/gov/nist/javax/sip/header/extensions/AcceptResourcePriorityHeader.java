package gov.nist.javax.sip.header.extensions;

import java.text.ParseException;
import javax.sip.header.Header;
import javax.sip.header.Parameters;

public interface AcceptResourcePriorityHeader extends Parameters, Header {
    
    /**
     * Sets the Namespace of the AcceptResourcePriorityHeader. The Namespace parameter 
     * specifies the namespace of the accepted priority value.
     *
     * @param namespace - the string value of the Namespace of this AcceptResourcePriorityHeader
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the namespace value.
     */
    public void setNamespace(String namespace) throws ParseException;

    /**
     * Returns the Namespace of AcceptResourcePriorityHeader. The Namespace parameter 
     * specifies the namespace of the accepted priority value.
     *
     * @return the String value of the Namespace of this AcceptResourcePriorityHeader
     */
    public String getNamespace();
    
    /**
     * Sets the Priority of the AcceptResourcePriorityHeader. The Priority parameter 
     * specifies the accepted priority value within the given namespace.
     *
     * @param priority - the string value of the Priority of this AcceptResourcePriorityHeader
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the priority value.
     */
    public void setPriority(String priority) throws ParseException;

    /**
     * Returns the Priority of AcceptResourcePriorityHeader. The Priority parameter 
     * specifies the accepted priority value within the given namespace.
     *
     * @return the String value of the Priority of this AcceptResourcePriorityHeader
     */
    public String getPriority();
    
    /**
     * Encode the header into a string.
     *
     * @return the encoded string representation of the header.
     */
    public String encode();
    
    /**
     * Name of AcceptResourcePriorityHeader
     */
    public final static String NAME = "Accept-Resource-Priority";
}

