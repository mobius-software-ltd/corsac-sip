package gov.nist.javax.sip.stack.transports.processors.netty;

/*
 * Netty receive buffer allocator, see gov.nist.javax.sip.NETTY_RCVBUF_ALLOCATOR
 */
public enum RecvByteBufAllocatorType {
    //same buffer size for every read
    FIXED,
    //starts at the configured size, then follows what was read before
    ADAPTIVE
}
