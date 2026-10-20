package test.unit.gov.nist.javax.sip.stack.malformed;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import gov.nist.core.LogWriter;

/** gov.nist.javax.sip.STACK_LOGGER that keeps the exceptions logged as errors, logs as usual otherwise. */
public class RecordingStackLogger extends LogWriter {

    private final List<Throwable> errors = new CopyOnWriteArrayList<Throwable>();

    @Override
    public void logError(String message, Throwable t) {
        errors.add(t);
        super.logError(message, t);
    }

    List<Throwable> getErrors() {
        return errors;
    }

    /** e.g. ParseException thrown in PPreferredIdentityParser.parse:73 */
    static String describe(Throwable t) {
        return t.getClass().getSimpleName() + " thrown in " + origin(t);
    }

    /** header parser that threw, frame right below processHeader, as Class.method:line */
    private static String origin(Throwable t) {
        StackTraceElement[] frames = t.getStackTrace();
        for (int i = 1; i < frames.length; i++) {
            if (frames[i].getMethodName().equals("processHeader")) {
                return shortName(frames[i - 1]);
            }
        }
        return frames.length > 0 ? shortName(frames[0]) : "?";
    }

    private static String shortName(StackTraceElement frame) {
        String className = frame.getClassName();
        return className.substring(className.lastIndexOf('.') + 1) + "." + frame.getMethodName() + ":"
                + frame.getLineNumber();
    }
}
