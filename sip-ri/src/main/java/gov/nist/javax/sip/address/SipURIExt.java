package gov.nist.javax.sip.address;

import java.text.ParseException;
import javax.sip.address.SipURI;

/**
 * URI Interface extensions that will be added to version 2.0 of the JSR 32 spec.
 *
 * @author mranga
 *
 * @since 2.0
 *
 */
public interface SipURIExt extends SipURI {

    /**
     * Strip the headers that are tacked to the URI.
     *
     * @since 2.0
     */
    public void removeHeaders();

    /**
     * Strip a specific header tacked to the URI.
     *
     * @param headerName -- the name of the header.
     *
     * @since 2.0
     */
    public void removeHeader(String headerName);

    /**
     * Returns whether the <code>gr</code> parameter is set.
     *
     * @since 2.0
     */
    public boolean hasGrParam();

    /**
     * Sets the <code>gr</code> parameter.
     *
     * @param value -- the GRUU param value.
     *
     * @since 2.0
     */
    public void setGrParam(String value);
    
    /**
     * Returns whether the <code>lr</code> parameter is set.
     *
     * @since 2.0
     */
    public boolean hasLrParam();

    /**
     * Sets the <code>lr</code> parameter.
     *
     *
     * @since 2.0
     */
    public void setLrParam();
    
    /**
     * Both name and value will be encoded before saving this internally.
     * Following rules form RFC3261 will be used
     * pname             =  1*paramchar
     * pvalue            =  1*paramchar
     * paramchar         =  param-unreserved / unreserved / escaped
     * param-unreserved  =  "[" / "]" / "/" / ":" / "&amp;" / "+" / "$"
     * @param name
     * @param value contains chars not escaped following RFC3261 rules
     */
    public void setUnencodedParam(String name, String value)  throws ParseException;
    
    /**
     * Name will be encoded before looking for this parameter so it matches
     * the value in the network. Value will be URL decoded, un-escaping %XX chars.
     * @param name
     * @return decoded
     */
    public String getDecodedParam(String name);    

}
