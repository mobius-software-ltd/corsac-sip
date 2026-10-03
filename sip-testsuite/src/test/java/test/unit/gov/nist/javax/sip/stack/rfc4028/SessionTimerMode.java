package test.unit.gov.nist.javax.sip.stack.rfc4028;

import java.util.Properties;

/**
 * Who handles RFC 4028 on a test stack.
 */
public enum SessionTimerMode {

    /** gov.nist.javax.sip.RFC_4028_AUTO_SUPPORTED=true: the stack does it. */
    STACK,
    /** Flag off: the test UA puts the headers on by hand and never arms a timer. */
    MANUAL;

    /**
     * RFC 4028 flag + aggressive memory saving
     */
    public Properties stackProperties() {
        Properties properties = new Properties();
        properties.setProperty("gov.nist.javax.sip.RFC_4028_AUTO_SUPPORTED", Boolean.toString(this == STACK));
        properties.setProperty("gov.nist.javax.sip.RELEASE_REFERENCES_STRATEGY", "Aggressive");
        return properties;
    }
}
