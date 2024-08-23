package gov.nist.javax.sip.header.extensions;

import java.text.ParseException;
import java.util.List;

import javax.sip.header.Header;

public interface AcceptResourcePriorityHeader extends Header {
    
    /**
     * Sets the Resources of the AcceptResourcePriorityHeader. The resources parameter 
     * specifies the resources of the accepted priority value.
     *
     * @param namespace - the list of the Resource of this AcceptResourcePriorityHeader
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the resource value.
     */
    public void setResources(List<Resource> resources) throws ParseException;

    /**
     * Returns the Resources of AcceptResourcePriorityHeader.
     *
     * @return the list of the Resources of this AcceptResourcePriorityHeader
     */
    public List<Resource> getResources();
    
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

