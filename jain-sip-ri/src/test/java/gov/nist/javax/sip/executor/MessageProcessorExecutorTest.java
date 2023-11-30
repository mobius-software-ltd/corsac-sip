package gov.nist.javax.sip.executor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import gov.nist.core.executor.MessageProcessorExecutor;
import gov.nist.javax.sip.Utils;
import junit.framework.Assert;

public class MessageProcessorExecutorTest {
    private MessageProcessorExecutor executor;

    private static final String CALLID = "16505551212@192.168.1.100";

    @Before
    public void setUp() {
        executor = new MessageProcessorExecutor();
        executor.start(8);
    }

    @Test
    public void testHashing() {
        int staticIndex = executor.findQueueIndex(CALLID);
        System.out.println("Index: " + staticIndex + " for callID: " + CALLID);
        int uniqueStaticIndex = executor.findQueueIndex(CALLID);
        System.out.println("New Index: " + uniqueStaticIndex + " for callID: " + CALLID);
        Assert.assertEquals(staticIndex, uniqueStaticIndex);
        int index = -1;
        do {
            String callId = Utils.getInstance().generateCallIdentifier("127.0.0.1");
            index = executor.findQueueIndex(callId);
            System.out.println("Index: " + index + " callId: " + callId);
            uniqueStaticIndex = executor.findQueueIndex(callId);
            //System.out.println("New Index: " + uniqueStaticIndex + " for callID: " + callId);
            Assert.assertEquals(index, uniqueStaticIndex);
        } while (index != staticIndex);
    }

    @After
    public void teardown() {
        executor.stop();
    }
}