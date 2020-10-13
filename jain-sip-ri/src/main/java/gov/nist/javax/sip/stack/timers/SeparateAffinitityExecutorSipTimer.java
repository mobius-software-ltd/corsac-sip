package gov.nist.javax.sip.stack.timers;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.ThreadAffinityExecutor;

import java.util.Properties;

public class SeparateAffinitityExecutorSipTimer extends AffinitityExecutorSipTimer {
	
	private static StackLogger logger = CommonLogger.getLogger(SeparateAffinitityExecutorSipTimer.class);

	@Override
	public void start(SipStackImpl sipStack, Properties configurationProperties) {
		sipStackImpl = sipStack;
        threadPoolExecutor = new ThreadAffinityExecutor(sipStackImpl.getSeparateAffinitityExecutorSipTimerThreadPool());

		if(logger.isLoggingEnabled(StackLogger.TRACE_INFO)) {
			logger.logInfo("the sip stack timer " + this.getClass().getName() + " has been started, with thread number : " + sipStackImpl.getSeparateAffinitityExecutorSipTimerThreadPool());
		}
	}

}