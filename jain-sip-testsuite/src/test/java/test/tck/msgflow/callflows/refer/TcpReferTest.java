package test.tck.msgflow.callflows.refer;

import javax.sip.ListeningPoint;

public class TcpReferTest extends AbstractReferTestCase {
    boolean myFlag;

    public void setUp() throws Exception {
        // these flags determine
        // which SIP Stack (RI vs TI) is
        // Shootist and which one is Shootme
        // the following setup code flips the roles before each test is run
        testedImplFlag = !myFlag;
        myFlag = !testedImplFlag;
        System.out.println("testedImplFlag = " + testedImplFlag);
        super.transport = ListeningPoint.TCP;
        super.setUp();
    }
    public void testTCPRefer() {
        super.transport = ListeningPoint.TCP;
        this.referee.setTransport(ListeningPoint.TCP);
        this.referrer.setTransport(ListeningPoint.TCP);
        this.referrer.sendRefer();
    }

    public void testTCPRefer2() {
        super.transport = ListeningPoint.TCP;
        this.referee.setTransport(ListeningPoint.TCP);
        this.referrer.setTransport(ListeningPoint.TCP);        
        this.referrer.sendRefer();
    }
}
