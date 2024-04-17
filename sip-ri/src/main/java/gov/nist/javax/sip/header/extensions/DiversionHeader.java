package gov.nist.javax.sip.header.extensions;

import java.text.ParseException;

import javax.sip.address.Address;
import javax.sip.header.*;


public interface DiversionHeader extends HeaderAddress,Parameters,Header {
	/**
	*
	*@author ValeriiaMukha
	*
	*/
	
	 
    /**
     * Name of the DiversionHeader
     */
	public static final String NAME = "Diversion";

	public String encode();
	 public void setDiversion(Address address);
	 public Address getAddress();
	 
	 public void setDiversionAddress(String devAd);
	 public String getDiversionAddress();
	 
	 public void  setReason(String r) throws ParseException;
	 public String getReason();
	 
	 public void setLimit(String l) throws ParseException;
	 public String getLimit();
	 
	 public void setPrivacy(String p) throws ParseException;
	 public String getPrivacy();
	 
	 public void setCounter(String c) throws ParseException;
	 public String getCounter();
	 
	 public void setScreen(String s) throws ParseException;
	 public String getScreen();
	 
	 public void setExtension(String ex) throws ParseException;
	 public String getExtension();

}
