package gov.nist.core;

public class SystemClock implements Clock {

    @Override
    public long millis() {
        return System.currentTimeMillis();
    }

}
