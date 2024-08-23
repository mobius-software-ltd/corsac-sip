package gov.nist.javax.sip.header.extensions;

import java.text.ParseException;
import java.util.List;

import javax.sip.header.Header;

public interface ResourcePriorityHeader extends Header {
    
	/**
     * Sets the Resources of the ResourcePriorityHeader. The resources parameter 
     * specifies the resources of the accepted priority value.
     *
     * @param namespace - the list of the Resource of this ResourcePriorityHeader
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the resource value.
     */
    public void setResources(List<Resource> resources) throws ParseException;

    /**
     * Returns the Resources of ResourcePriorityHeader.
     *
     * @return the list of the Resources of this ResourcePriorityHeader
     */
    public List<Resource> getResources();
    
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

