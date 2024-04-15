package gov.nist.javax.sip.header.extensions;

import java.text.ParseException;

import javax.sip.header.ExtensionHeader;

import gov.nist.core.NameValue;
import gov.nist.javax.sip.address.AddressImpl;
import gov.nist.javax.sip.header.*;

public class Diversion 
	extends AddressParametersHeader 
	implements ExtensionHeader, DiversionHeader{

    private static final long serialVersionUID = 1L;

	/**
     * Default constructor.
     */
    public Diversion() {
        super(NAME);
    }
    
    /** Default constructor given an address.
    *
    *@param address -- address of this header.
    *
    */

   public Diversion(AddressImpl address) {
       super(NAME);
       this.address = address;
   }
   
   public String encodeBody() {
       return encodeBody(new StringBuilder()).toString();
   }
   
   /** Set a parameter.
    */
    public void setParameter(String name, String value) throws ParseException {
        NameValue nv = parameters.getNameValue(name);
        if (nv != null) {
            nv.setValueAsObject(value);
        } else {
            nv = new NameValue(name, value);
            if (name.equalsIgnoreCase("methods"))
                nv.setQuotedValue();
            this.parameters.set(nv);
        }
    }
    
    /**
     * Encode the body part of this header (i.e. leave out the hdrName).
     * @return String encoded body part of the header.
     */
   protected StringBuilder encodeBody(StringBuilder buffer) {
       boolean addrFlag = address.getAddressType() == AddressImpl.NAME_ADDR;
       if (!addrFlag) {
           buffer.append('<');
           address.encode(buffer);
           buffer.append('>');
       } else {
           address.encode(buffer);
       }
       if (!parameters.isEmpty()) {
           buffer.append(SEMICOLON);
           parameters.encode(buffer);
       }
       return buffer;
   }

   public boolean equals(Object other) {
       return (other instanceof DiversionHeader) && super.equals(other);
   }

@Override
public void setValue(String value) throws ParseException {
	throw new ParseException(value,0);
	
}
	}