package gov.nist.javax.sip.header.extensions;

import java.text.ParseException;

import javax.sip.address.Address;
import javax.sip.header.ExtensionHeader;
import gov.nist.core.NameValue;
import gov.nist.core.NameValueList;
import gov.nist.javax.sip.address.AddressImpl;
import gov.nist.javax.sip.header.*;
/**
 * SIP Diversion header implementation.
 */

public final class Diversion 
    extends AddressParametersHeader 
    implements ExtensionHeader, DiversionHeader{
	
	/**
	*
	*@author ValeriiaMukha
	*
	*/

    private static final long serialVersionUID = 1L;
    public Address diversion;
    protected boolean wildCardFlag;
    public static final String REASON = ParameterNames.REASON;
    public static final String PRIVACY = ParameterNames.PRIVACY;
    public static final String SCREEN = ParameterNames.SCREEN;
    public static final String LIMIT = ParameterNames.LIMIT;
    public static final String COUNTER = ParameterNames.COUNTER;

    /** Default constructor given an address.
    *
    */

   public Diversion() {
       super(NAME);
   }
   
   public void setDiversion(String diversion) {
       this.diversion = address;
    }
   
   public Address getDiversion() {
       return address;
   }
   
   /**
    * Set the wildCardFlag member
    * @param w boolean to set
    */
   public void setWildCardFlag(boolean w) {
       this.address = new AddressImpl();
       this.address.setWildCardFlag();
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
   
    /** get the address field.
     * @return Address
     */
    public javax.sip.address.Address getAddress() {
        // JAIN-SIP stores the wild card as an address!
        return address;
    }
    
    /**
     * Set the address member
     *
     * @param address Address to set
     */
    public void setAddress(javax.sip.address.Address address) {
        // Canonical form must have <> around the address.
        if (address == null)
            throw new NullPointerException("null address");
        this.address = (AddressImpl) address;
    }

    /** get the parameters List
     * @return NameValueList
     */
    public NameValueList getDiversionParms() {
        return parameters;
    }
   
   /**
    * Gets the Reason parameter of the Diversion header.
    *
    * @return the parameter value
    */
   public String getReason() {
        if (parameters == null)
            return null;
        return getParameter(ParameterNames.REASON);
   }
   /**
    * Sets the Reason parameter of the Diversion header.
    *
    * @param diversion-reason parameter value to set
    * @throws ParseException if the provided parameter is invalid
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

   /** remove diversion-reason member
    */
   public void removeReason() {
       parameters.delete(ParameterNames.REASON);      
   }
   
   /**
    * Sets the diversion-limit parameter of the Diversion header.
    *
    * @param limit The diversion-limit value as a positive integer.
    * @throws IllegalArgumentException if the provided limit is not a positive integer.
 * @throws ParseException 
    */
   public void setLimit(int l) throws IllegalArgumentException, ParseException{
     if (l <= 0) {
       throw new IllegalArgumentException("Diversion limit must be a positive integer");
     }
     this.setParameter(ParameterNames.LIMIT,String.valueOf(l));
   }

   /**
    * Gets the diversion-limit parameter from the address param list as an integer.
    * 
    * @return The diversion-limit value as an integer, or null if not present.
    * @throws ParseException if the limit parameter is invalid.
    */
   public Integer getLimit() throws ParseException {
     String limitStr = getParameter(ParameterNames.LIMIT);
     if (limitStr == null) {
       return null;
     }
     try {
       int limit = Integer.parseInt(limitStr);
       if (limit <= 0) {
         throw new ParseException("Invalid diversion limit: must be positive", 0);
       }
       return limit;
     } catch (NumberFormatException e) {
       throw new ParseException("Invalid diversion limit format", 0);
     }
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
    * Sets the diversion-counter parameter of the Diversion header.
    *
    * @param counter The diversion-counter value as a positive integer.
    * @throws IllegalArgumentException if the provided counter is not a positive integer.
 * @throws ParseException 
    */
   public void setCounter(int c) throws IllegalArgumentException, ParseException {
     if (c <= 0) {
       throw new IllegalArgumentException("Diversion counter must be a positive integer");
     }
     this.setParameter(ParameterNames.COUNTER, String.valueOf(c));
   }

   /**
    * Gets the diversion-counter parameter from the address param list as an integer.
    * 
    * @return The diversion-counter value as an integer, or null if not present.
    * @throws ParseException if the counter parameter is invalid.
    */
   public Integer getCounter() throws ParseException {
     String counterStr = getParameter(ParameterNames.COUNTER);
     if (counterStr == null) {
       return null;
     }
     try {
       int counter = Integer.parseInt(counterStr);
       if (counter <= 0) {
         throw new ParseException("Invalid diversion counter: must be positive", 0);
       }
       return counter;
     } catch (NumberFormatException e) {
       throw new ParseException("Invalid diversion counter format", 0);
     }
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
    * @return true if the parameter exist
    */
   public boolean hasScreen() {
       return hasParameter(ParameterNames.SCREEN);
   }
   
   /** remove diversion-screen member
    */
   public void removeScreen() {
       parameters.delete(ParameterNames.SCREEN);
   }
   
   public String encodeBody() {
       return encodeBody(new StringBuilder()).toString();
   }

    /**
     * Encode the body part of this header (i.e. leave out the hdrName).
     * @return String encoded body part of the header.
     */
   public StringBuilder encodeBody(StringBuilder buffer) {
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

   @Override
   public void setValue(String value) throws ParseException {
       decodeBody(value);
   }
   /**
    * Parses the header string into Address and parameters.
    *
    * @param body the header string to parse
    * @throws ParseException if the header string cannot be parsed
    */
   public void decodeBody(String body) throws ParseException {
     try {
       // Split the header by semicolons to extract each parameter
       String[] params = body.split(";");

       // Extract the address from the first parameter
       String diversionParam = params[0].trim();
       int equalIndex = diversionParam.indexOf('=');
       if (equalIndex != -1) {
         setDiversion(diversionParam.substring(equalIndex + 1).trim());
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
           } else if (paramName.equalsIgnoreCase(ParameterNames.PRIVACY)) {
             setPrivacy(paramValue);
           } else if (paramName.equalsIgnoreCase(ParameterNames.LIMIT)) {
             try {
               setLimit(Integer.parseInt(paramValue)); // Parse limit as integer
             } catch (NumberFormatException e) {
               throw new ParseException("Invalid diversion limit format: Not a valid integer", 0);
             }
           } else if (paramName.equalsIgnoreCase(ParameterNames.COUNTER)) {
             try {
               setCounter(Integer.parseInt(paramValue)); // Parse counter as integer
             } catch (NumberFormatException e) {
               throw new ParseException("Invalid diversion counter format: Not a valid integer", 0);
             }
           } else if (paramName.equalsIgnoreCase(ParameterNames.SCREEN)) {
             setScreen(paramValue);
           } else {
             // Handle random parameters
             setParameter(paramName, paramValue);
           }
         }
       }
     } catch (ParseException e) {
       // Handle the exception gracefully, e.g., log an error message
       System.err.println("Error parsing Diversion header: " + e.getMessage());
     }
   }
}
   