package gov.nist.javax.sip.header.extensions;

import gov.nist.javax.sip.header.*;

/**
* Diversion SIPHeader - there can be several  Diversion headers.
*
*@author ValeriiaMukha
*
*
*
*/

public class DiversionList extends SIPHeaderList<Diversion>{
	
    private static final long serialVersionUID = 1L;


        public Object clone() {
        	DiversionList retval = new DiversionList();
            retval.clonehlist(this.hlist);
            return retval;
        }
        public String encode() {
            if ( super.hlist.isEmpty()) return "";
            else return super.encode();
        }



            /** default constructor
             */
        public DiversionList() {
            super( Diversion.class, DiversionHeader.NAME);
            
        }
}
