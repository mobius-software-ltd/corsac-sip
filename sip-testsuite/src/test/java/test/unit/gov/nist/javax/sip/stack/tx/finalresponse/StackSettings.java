package test.unit.gov.nist.javax.sip.stack.tx.finalresponse;

/**
 * How the UAC stack is set up.
 */
public enum StackSettings {

    /**  automatic dialog support on, non reentrant listener, no aggressive cleanup. */
    DEFAULT,
    /** javax.sip.AUTOMATIC_DIALOG_SUPPORT=off, otherwise defaults. */
    NO_AUTOMATIC_DIALOG,
    /**
     * automatic dialog support off,
     * REENTRANT_LISTENER=true, AGGRESSIVE_CLEANUP=true.
     */
    SIP_RA;

    public boolean automaticDialog() {
        return this == DEFAULT;
    }

    public boolean reentrantListener() {
        return this == SIP_RA;
    }

    public boolean aggressiveCleanup() {
        return this == SIP_RA;
    }
}
