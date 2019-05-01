package gov.nist.javax.sip.stack;

public interface SocketAuditorMBean {

    void setMaxIterations(Integer maxIterations);

    Integer getMaxIterations();

    Integer getRemovedSockets();

    Integer getChannelSize();

}
