package test.unit.gov.nist.javax.sip.stack;

import java.util.HashSet;
import java.util.Iterator;

import javax.sip.ObjectInUseException;
import javax.sip.SipProvider;
import javax.sip.SipStack;

import gov.nist.javax.sip.SipProviderImpl;

public class Utils {
	public static void stopSipStack(SipStack sipStack) {

        HashSet<SipProvider> hashSet = new HashSet<SipProvider>();

        for (Iterator<?> it = sipStack.getSipProviders(); it.hasNext();) {

            SipProvider sipProvider = (SipProvider) it.next();
            hashSet.add(sipProvider);
        }

        for (Iterator<SipProvider> it = hashSet.iterator(); it.hasNext();) {
            SipProvider sipProvider = it.next();

            Boolean succesfull=false;
            for (int j = 0; j < 5; j++) {
                try {
                	sipStack.deleteSipProvider(sipProvider);
                	succesfull=true;
                	break;
                } catch (ObjectInUseException ex) {
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e) {
                    }
                }
            }
            
            if(!succesfull) {
            	try {
            		((SipProviderImpl)sipProvider).removeListeningPoints();
            	}
            	catch(Exception ex) {
            	}
            }
        }

        sipStack.stop();
	}
}