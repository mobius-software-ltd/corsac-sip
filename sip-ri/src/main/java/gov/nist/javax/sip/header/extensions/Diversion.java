package gov.nist.javax.sip.header.extensions;

import java.text.ParseException;

import javax.sip.address.Address;
import javax.sip.header.ExtensionHeader;
import gov.nist.javax.sip.address.AddressImpl;
import gov.nist.javax.sip.header.*;

public class Diversion 
	extends AddressParametersHeader 
	implements ExtensionHeader, DiversionHeader{

    private static final long serialVersionUID = 1L;
    public String diversionAddress;
    
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
   /**
    * Set the address member
    *
    * @param address Address to set
    */
   public void setDiversion(javax.sip.address.Address address) {
       // Canonical form must have <> around the address.
       if (address == null)
           throw new NullPointerException("null address");
       this.address = (AddressImpl) address;
   }
   
   public void setDiversionAddress(String devAd) {
	   diversionAddress = devAd;
   }
   
   public Address getDiversion() {
	   return address;
   }
   
   public String getDiversionAddress() {
	   return diversionAddress;
   }
   
   /**
    * Gets the Reason parameter of the Diversion header.
    *
    * @return the remote tag value
    */
   public String getReason() {
   	 if (parameters == null)
            return null;
        return getParameter(ParameterNames.REASON);
   }
   /**
    * Sets the remote tag of the Target-Dialog header.
    *
    * @param remoteTag the remote tag value to set
    * @throws ParseException if the provided remoteTag is invalid
    */
   public void setReason(String r) throws ParseException {
   	  if (r == null)
             throw new NullPointerException("null param");
         else if (r.trim().equals(""))
             throw new ParseException("bad param", 0);
         this.setParameter(ParameterNames.REASON, r);
   }
   /** Boolean function
    * @return true if the parameter exist
    */
   public boolean hasReason() {
       return hasParameter(ParameterNames.REASON);
   }

   /** remove Tag member
    */
   public void removeReason() {
       parameters.delete(ParameterNames.REASON);      
   }
   
   /**
    * Gets the diversion-limit parameter from the address param list.
    * @return field
    */
   public String getLimit() {
       if (parameters == null)
           return null;
       return getParameter(ParameterNames.LIMIT);
   }
   
   /**
    * Sets the diversion-limit parameter of the Diversion header.
    *
    * @param reason diversion-limit parameter value to set
    * @throws ParseException if the provided parameter is invalid
    */
   public void setLimit(String l) throws ParseException {
   	  if (l == null)
             throw new NullPointerException("no limit param");
         else if (l.trim().equals(""))
             throw new ParseException("bad parameter", 0);
         this.setParameter(ParameterNames.LIMIT, l);
   }
   
   /** Boolean function
    * @return true if the parameter exist
    */
   public boolean hasLimit() {
       return hasParameter(ParameterNames.LIMIT);
   }
   
   /** remove diversion-limit member
    */
   public void removeLimit() {
       parameters.delete(ParameterNames.LIMIT);
   }
   
   
   /**
    * Gets the diversion-counter parameter from the address param list.
    * @return field
    */
   public String getCounter() {
       if (parameters == null)
           return null;
       return getParameter(ParameterNames.COUNTER);
   }
   
   /**
    * Sets the diversion-counter parameter of the Diversion header.
    *
    * @param reason diversion-counter parameter value to set
    * @throws ParseException if the provided parameter is invalid
    */
   public void setCounter(String c) throws ParseException {
   	  if (c == null)
             throw new NullPointerException("no counter param");
         else if (c.trim().equals(""))
             throw new ParseException("bad parameter", 0);
         this.setParameter(ParameterNames.COUNTER, c);
   }
   
   /** Boolean function
    * @return true if the parameter exist
    */
   public boolean hasCounter() {
       return hasParameter(ParameterNames.COUNTER);
   }
   
   /** remove diversion-counter member
    */
   public void removeCounter() {
       parameters.delete(ParameterNames.COUNTER);
   }
   

   /**
    * Gets the diversion-privacy parameter from the address param list.
    * @return field
    */
   public String getPrivacy() {
       if (parameters == null)
           return null;
       return getParameter(ParameterNames.PRIVACY);
   }
   
   /**
    * Sets the diversion-privacy parameter of the Diversion header.
    *
    * @param reason diversion-privacy parameter value to set
    * @throws ParseException if the provided parameter is invalid
    */
   public void setPrivacy(String p) throws ParseException {
   	  if (p == null)
             throw new NullPointerException("no privacy param");
         else if (p.trim().equals(""))
             throw new ParseException("bad parameter", 0);
         this.setParameter(ParameterNames.PRIVACY, p);
   }
   
   /** Boolean function
    * @return true if the parameter exist
    */
   public boolean hasPrivacy() {
       return hasParameter(ParameterNames.PRIVACY);
   }
   
   /** remove diversion-privacy member
    */
   public void removePrivacy() {
       parameters.delete(ParameterNames.PRIVACY);
   }
   
   
   
   /**
    * Gets the diversion-screen parameter from the address param list.
    * @return field
    */
   public String getScreen() {
       if (parameters == null)
           return null;
       return getParameter(ParameterNames.SCREEN);
   }

   /**
    * Sets the diversion-screen parameter of the Diversion header.
    *
    * @param reason diversion-privacy parameter value to set
    * @throws ParseException if the provided parameter is invalid
    */
   public void setScreen(String s) throws ParseException {
       if (s == null) {
           throw new NullPointerException("no screen param");
       } else if (s.trim().equals("")) {
           throw new ParseException("bad parameter", 0);
       }
       this.setParameter(ParameterNames.SCREEN, s);
   }
   
   /** Boolean function
    * @return true if the Tag exist
    */
   public boolean hasScreen() {
       return hasParameter(ParameterNames.SCREEN);
   }
   
   /** remove diversion-screen member
    */
   public void removeScreen() {
       parameters.delete(ParameterNames.SCREEN);
   }
   
   /**
    * Gets the diversion-extension parameter from the address parameter list.
    * @return field
    */
   public String getExtension() {
       if (parameters == null)
           return null;
       return getParameter(ParameterNames.EXTENSION);
   }
   
   /**
    * Sets the diversion-extension parameter of the Diversion header.
    *
    * @param reason diversion-extension parameter value to set
    * @throws ParseException if the provided parameter is invalid
    */
   public void setExtension(String ex) throws ParseException {
   	  if (ex == null)
             throw new NullPointerException("no extension param");
         else if (ex.trim().equals(""))
             throw new ParseException("bad parameter", 0);
         this.setParameter(ParameterNames.EXTENSION, ex);
   }
   
   /** Boolean function
    * @return true if the parameter exist
    */
   public boolean hasExtension() {
       return hasParameter(ParameterNames.EXTENSION);
   }
   
   /** remove diversion-extension member
    */
   public void removeExtension() {
       parameters.delete(ParameterNames.EXTENSION);
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
       decodeBody(value);
   }
   
   /**
    * Parses the header string into Call-Id, local tag, remote tag, and parameters.
 * @param <Address>
    *
    * @param body the header string to parse
    * @throws ParseException if the header string cannot be parsed
    */
   public void decodeBody(String body) {
       try {
           // Split the header by semicolons to extract each parameter
           String[] params = body.split(";");

           // Extract the address from the first parameter
           String diversionParam = params[0].trim();
           int equalIndex = diversionParam.indexOf('=');
           if (equalIndex != -1) {
               setDiversionAddress(diversionParam.substring(equalIndex + 1).trim());
           } else {
               throw new ParseException("Invalid Diversion header format: missing Call-Id", 0);
           }

           // Extract other parameters from subsequent parameters
           for (int i = 1; i < params.length; i++) {
               String param = params[i].trim();
               equalIndex = param.indexOf('=');
               if (equalIndex != -1) {
                   String paramName = param.substring(0, equalIndex).trim();
                   String paramValue = param.substring(equalIndex + 1).trim();
                   if (paramName.equalsIgnoreCase(ParameterNames.REASON)) {
                       setReason(paramValue);
                   } else if (paramName.equalsIgnoreCase(ParameterNames.LIMIT)) {
                       setLimit(paramValue);
                   }  else if (paramName.equalsIgnoreCase(ParameterNames.COUNTER)) {
                       setCounter(paramValue);
                   } else if (paramName.equalsIgnoreCase(ParameterNames.SCREEN)) {
                       setScreen(paramValue);
                   } else if (paramName.equalsIgnoreCase(ParameterNames.EXTENSION)) {
                       setExtension(paramValue);
                   }
               }
           }
       } catch (ParseException e) {
           // Handle the exception gracefully, e.g., log an error message
           System.err.println("Error parsing Diversion header: " + e.getMessage());
       }
	}

}
   