
package gov.nist.javax.sip.parser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.ParseException;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogLevels;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.header.ContentLength;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import io.netty.buffer.ByteBuf;

public class NettyMessageParser {
    private static StackLogger logger = CommonLogger.getLogger(NettyMessageParser.class);

	private static final String ENCODING = "UTF-8";	
	private static final String CONTENT_LENGHT_COMPACT_NAME = "l";

    private final MessageParser messageParser;            
	private boolean currentStreamEnded = false;
	private boolean readingMessageBodyContents = false;
	private boolean readingHeaderLines = true;
	private boolean partialLineRead = false; // if we didn't receive enough bytes for a full line we expect the line to end in the next batch of bytes
	private String partialLine = "";
    private int maxMessageSize;
    private int sizeCounter;
    private int contentLength = 0;
	private int contentReadSoFar = 0;
    
	private ByteArrayOutputStream messageHeaders = new ByteArrayOutputStream();        
	private byte[] messageBody = null;
	
	private SIPMessage sipMessage = null;

    public NettyMessageParser(MessageParser messageParser, int maxMessageSize) {
        this.messageParser = messageParser;        
		this.maxMessageSize = maxMessageSize;
    }

    public SIPMessage addBytes(ByteBuf byteBuf)  throws Exception{
		currentStreamEnded = false;
		int readableBytes = byteBuf.readableBytes();
		// Dealing with RFC5626 keepalive mechanism
		if(readableBytes == 2 || readableBytes == 4) {
			byte[] msgBuffer = new byte[readableBytes];
			byteBuf.readBytes(msgBuffer);

			// Array contains only control char, return null.
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				if( readableBytes == 2) {
					logger.logDebug("handled CRLF so returning null request");
				} else {
					logger.logDebug("handled Double CRLF so returning null request");
				}
			}
			String currentLine = new String(msgBuffer);
			if (currentLine.equalsIgnoreCase(SIPMessage.DOUBLE_CRLF) || currentLine.equals(SIPMessage.SINGLE_CRLF)) {
				SIPMessage nullMessage = new SIPRequest();
				nullMessage.setSize(currentLine.length());
				nullMessage.setNullRequest();
				
				return nullMessage;
			}
		}	
		return readStream(byteBuf);
	}

	
    /*
	 *  This is where we receive the bytes from the stream and we analyze the through message structure.
	 *  For TCP the key things to identify are message lines for the headers, parse the Content-Length header
	 *  and then read the message body (aka message content). For TCP the Content-Length must be 100% accurate.
	 */
	public SIPMessage readStream(ByteBuf byteBuf) throws IOException {
		boolean isPreviousLineCRLF = false;
		while(true) { // We read continiously from the bytes we receive and only break where there are no more bytes in the inputStream passed to us
			if(currentStreamEnded) break; // The stream ends when we have read all bytes in the chunk NIO passed to us
			else {
				if(readingHeaderLines) {// We are in state to read header lines right now
					isPreviousLineCRLF = readMessageSipHeaderLines(byteBuf, isPreviousLineCRLF);															
				}
				if(readingMessageBodyContents) { // We've already read the headers an now we are reading the Contents of the SIP message (which doesn't generally have lines)
					return readMessageBody(byteBuf);
				}
			}
		}
        return null;
	}

    private boolean readMessageSipHeaderLines(ByteBuf byteBuf, boolean isPreviousLineCRLF) throws IOException {
		boolean crlfReceived = false;
		String line = readLine(byteBuf); // This gives us a full line or if it didn't fit in the byte check it may give us part of the line
        // System.out.println("line: " + line);
		if(partialLineRead) {
			partialLine = partialLine + line; // If we are reading partial line again we must concatenate it with the previous partial line to reconstruct the full line
		} else {
			line = partialLine + line; // If we reach the end of the line in this chunk we concatenate it with the partial line from the previous buffer to have a full line
			partialLine = ""; // Reset the partial line so next time we will concatenate empty string instead of the obsolete partial line that we just took care of
			if(!line.equals(SIPMessage.SINGLE_CRLF)) { // CRLF indicates END of message headers by RFC
				messageHeaders.write(line.getBytes(ENCODING)); // Collect the line so far in the message buffer (line by line)
                String lineIgnoreCase = line.toLowerCase();
                // contribution from Alexander Saveliev compare to lower case as RFC 3261 states (7.3.1 Header Field Format) states that header fields are case-insensitive
				if(lineIgnoreCase.startsWith(ContentLength.NAME_LOWER)) { // naive Content-Length header parsing to figure out how much bytes of message body must be read after the SIP headers
					contentLength = Integer.parseInt(line.substring(
							ContentLength.NAME_LOWER.length()+1).trim());
				} else if(lineIgnoreCase.startsWith(CONTENT_LENGHT_COMPACT_NAME)) { // issue with compact header form
					contentLength = Integer.parseInt(line.substring(
							CONTENT_LENGHT_COMPACT_NAME.length()+1).trim());
				} 
			} else {				
				if(isPreviousLineCRLF) {
            		// Handling keepalive ping (double CRLF) as defined per RFC 5626 Section 4.4.1
                	// sending pong (single CRLF)
                	if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                        logger.logDebug("KeepAlive Double CRLF received, sending single CRLF as defined per RFC 5626 Section 4.4.1");
                        logger.logDebug("~~~ setting isPreviousLineCRLF=false");
                    }
                	crlfReceived = false;	                    					
            	} else {
            		crlfReceived = true;
                	if (logger.isLoggingEnabled(LogLevels.TRACE_DEBUG)) {
                    	logger.logDebug("Received CRLF");
                    }								
            	}
				if(messageHeaders.size() > 0) { // if we havent read any headers yet we are between messages and ignore CRLFs
					readingMessageBodyContents = true;
					readingHeaderLines = false;
					partialLineRead = false;
					messageHeaders.write(SIPMessage.SINGLE_CRLF.getBytes(ENCODING)); // the parser needs CRLF at the end, otherwise fails TODO: Is that a bug?
					if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
						logger.logDebug("Content Length parsed is " + contentLength);
					}

					contentReadSoFar = 0;
					messageBody = new byte[contentLength];
				}
			}			
		}
		return crlfReceived;
	}

	// This method must be called repeatedly until the inputStream returns -1 or some error conditions is triggered
	private SIPMessage readMessageBody(ByteBuf byteBuf) throws IOException {
		int bytesRead = 0;
		if(contentLength>0) {
			bytesRead = readChunk(byteBuf, messageBody, contentReadSoFar, contentLength-contentReadSoFar);
			if(bytesRead == -1) {
				currentStreamEnded = true;
				bytesRead = 0; // avoid passing by a -1 for a one-off bug when contentReadSoFar gets wrong
			}
		}
		contentReadSoFar += bytesRead;
        // System.out.println("body read so far: " + new String(messageBody) + ", contentreadsofar: " + contentReadSoFar + ", contentlength: " + contentLength);
		if(contentReadSoFar == contentLength) { // We have read the full message headers + body
			sizeCounter = maxMessageSize;
			readingHeaderLines = true;
			readingMessageBodyContents = false;
            this.contentLength = 0;																		            	            
            
            try {
                // System.out.println("Parsing SIP Message " + msgLines + "\n" + new String(messageBody));                
                sipMessage = messageParser.parseSIPMessage(messageHeaders.toByteArray(), false, false, null);
                sipMessage.setMessageContent(messageBody);

                return sipMessage;
            } catch (ParseException e) {
                NettyMessageParser.logger.logDebug(
                        "Parsing issue !  " + new String(messageHeaders.toByteArray()) + " " + e.getMessage());
            } finally {
				messageHeaders.reset();				
				messageBody = null;
				currentStreamEnded = false;
				partialLineRead = false; // if we didn't receive enough bytes for a full line we expect the line to end in the next batch of bytes
				sipMessage = null;
				messageBody = null;
			}        
		}
        return null;
	}

    private int readChunk(ByteBuf byteBuf, byte[] where, int offset, int length) throws IOException {
        int read = byteBuf.readableBytes();
		byteBuf.readBytes(where, offset, length);
		sizeCounter -= read;
		checkLimits();
		return read;
	}
	
	private int readSingleByte(ByteBuf byteBuf) throws IOException {
		sizeCounter --;
		checkLimits();
		return byteBuf.readByte();
	}
	
	private void checkLimits() {
		if(maxMessageSize > 0 && sizeCounter < 0) throw new RuntimeException("Max Message Size Exceeded " + maxMessageSize);
	}

    /**
     * read a line of input. Note that we encode the result in UTF-8
     */
    private String readLine(ByteBuf byteBuf) throws IOException {
    	partialLineRead = false;
        int counter = 0;
        int increment = 1024;
        int bufferSize = increment;
        byte[] lineBuffer = new byte[bufferSize];
        // handles RFC 5626 CRLF keepalive mechanism
        byte[] crlfBuffer = new byte[2];
        int crlfCounter = 0;
        while (true) {
            char ch;
            if(byteBuf.readableBytes() == 0) {
                partialLineRead = true;
            	currentStreamEnded = true;
            	break;
            }
            int i = readSingleByte(byteBuf);            
            ch = (char) ( i & 0xFF);
            
            if (ch != '\r')
                lineBuffer[counter++] = (byte) (i&0xFF);
            else if (counter == 0)            	
            	crlfBuffer[crlfCounter++] = (byte) '\r';
                       
            if (ch == '\n') {
            	if(counter == 1 && crlfCounter > 0) {
            		crlfBuffer[crlfCounter++] = (byte) '\n';            		
            	} 
            	break;            	
            }
            
            if( counter == bufferSize ) {
                byte[] tempBuffer = new byte[bufferSize + increment];
                System.arraycopy((Object)lineBuffer,0, (Object)tempBuffer, 0, bufferSize);
                bufferSize = bufferSize + increment;
                lineBuffer = tempBuffer;
                
            }
        }
        if(counter == 1 && crlfCounter > 0) {			
        	return new String(crlfBuffer,0,crlfCounter,"UTF-8");
        } else {			
        	String lineRead = new String(lineBuffer,0,counter,"UTF-8");
			//In case \r\n are not in the same chunk, wait for the rest
			//fixes https://github.com/RestComm/jain-sip/issues/48
			if (crlfCounter == 1) {				
				lineRead = lineRead + "\r";
			}
			return lineRead;
        }
        
    }

    public SIPMessage getSIPMessage() {
        return sipMessage;
    }

	public String getMessage() {
		return messageHeaders.toString();
	}   
}
