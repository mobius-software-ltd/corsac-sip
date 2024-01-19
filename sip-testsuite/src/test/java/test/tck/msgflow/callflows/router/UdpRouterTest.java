/*
* Conditions Of Use
*
* This software was developed by employees of the National Institute of
* Standards and Technology (NIST), and others.
* This software is has been contributed to the public domain.
* As a result, a formal license is not needed to use the software.
*
* This software is provided "AS IS."
* NIST MAKES NO WARRANTY OF ANY KIND, EXPRESS, IMPLIED
* OR STATUTORY, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTY OF
* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NON-INFRINGEMENT
* AND DATA ACCURACY.  NIST does not warrant or make any representations
* regarding the use of the software or the results thereof, including but
* not limited to the correctness, accuracy, reliability or usefulness of
* the software.
*
*
*/
package test.tck.msgflow.callflows.router;

/**
 * Shootist sends INVITE
 * Shootme receives INVITE and sends 100 Trying then 200 OK
 * Shootist receives 200 OK and sends ACK.
 * Shootme receives ACK and sends BYE.
 * Shootist receives BYE and sends OK.
 */
public class UdpRouterTest extends AbstractRouterTestCase {
    //boolean myFlag;
    public void setUp() throws Exception {
        testedImplFlag = false;
        super.transport = "udp";
        init();
    }
    
    public void init() throws Exception {
    	super.setUp();
    }

    public void testTelUriInvite() {
        this.shootist.sendInvite();

    }

    /*public void testTelUriInvite2() {
        this.shootist.sendInvite();
    }*/
}
