package test.tck.msgflow.callflows.prack;


public class TcpPrackTest extends AbstractPrackTestCase {
    boolean myFlag;

    public void setUp() throws Exception {
        super.testedImplFlag = !myFlag;
        myFlag = !super.testedImplFlag;
        super.transport = "tcp";
        super.setUp();
    }
    
    public void testPrack() {
        this.shootist.sendInvite();

    }

    public void testPrack2() {
        this.shootist.sendInvite();
    }
}
