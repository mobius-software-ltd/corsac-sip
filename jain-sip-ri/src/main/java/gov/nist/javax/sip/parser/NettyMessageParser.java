
package gov.nist.javax.sip.parser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.text.ParseException;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.header.ContentLength;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import io.netty.buffer.ByteBuf;

public class NettyMessageParser {
    private static StackLogger logger = CommonLogger.getLogger(NettyMessageParser.class);

	private static final String ENCODING = "UTF-8";	
	private static final String CONTENT_LENGTH_COMPACT_NAME = "l";

	private enum ParsingState {	
		INIT,
		CRLF,
		DOUBLE_CRLF,
		READING_HEADER_LINES,
		READING_EMPTY_LINE,
		READING_MESSAGE_BODY_CONTENTS,
		PARSING_COMPLETE
	}

	private	ParsingState parsingState;
    private final MessageParser messageParser;
	private ByteArrayOutputStream messageHeaders = new ByteArrayOutputStream();        
	private ByteArrayOutputStream messageBody = new ByteArrayOutputStream();        		
	private String partialLine = null;
	private int contentLength = -1;	
	private int maxMessageSize = -1;            
	private SIPMessage sipMessage = null;

    public NettyMessageParser(MessageParser messageParser, int maxMessageSize) {
        this.messageParser = messageParser;        
		this.maxMessageSize = maxMessageSize;
		sipMessage = null;
		parsingState = ParsingState.INIT;
    }

	public NettyMessageParser parseBytes(ByteBuf byteBuf) throws Exception {			
		int readableBytes = byteBuf.readableBytes();

		while (readableBytes > 0 && !isParsingComplete()) {
			switch (parsingState) {
				case INIT:
					readSIPMessageHeader(byteBuf, readableBytes);
					break;
				case CRLF:
					readSIPMessageHeader(byteBuf, readableBytes);
					break;
				case READING_HEADER_LINES:
					readSIPMessageHeader(byteBuf, readableBytes);
					break;
				case READING_EMPTY_LINE:
					readSIPMessageHeader(byteBuf, readableBytes);
					break;
				case READING_MESSAGE_BODY_CONTENTS:
					readMessageBody(byteBuf, readableBytes, sipMessage);
					break;
				case PARSING_COMPLETE:
					sipMessage = messageParser.parseSIPMessage(messageHeaders.toByteArray(), false, false, null);
					if(contentLength > 0) {
						sipMessage.setMessageContent(messageBody.toByteArray());
					}					
					break;
				default:
					break;
			}		
			readableBytes = byteBuf.readableBytes();
		}
		if(parsingState == ParsingState.DOUBLE_CRLF || parsingState == ParsingState.CRLF) {
			parsingState = ParsingState.DOUBLE_CRLF;
			sipMessage = createNullRequest(messageHeaders.size());				
		} else if(parsingState == ParsingState.PARSING_COMPLETE) {
			sipMessage = messageParser.parseSIPMessage(messageHeaders.toByteArray(), false, false, null);
			if(contentLength > 0) {
				sipMessage.setMessageContent(messageBody.toByteArray());
			}	
		}		
		
		return this;		
	}

	public void readSIPMessageHeader(ByteBuf byteBuf, int readableBytes) throws UnsupportedEncodingException, IOException, ParseException {
		// Read Message Headers
		int readerIndex = byteBuf.readerIndex();
		int crIndex = byteBuf.indexOf(readerIndex, readerIndex + readableBytes, (byte)'\r');
		int lfIndex = byteBuf.indexOf(readerIndex, readerIndex + readableBytes, (byte)'\n');
		// check if we have a full header line with \r\n at the end
		if(crIndex != -1 && lfIndex != -1 && lfIndex - crIndex == 1) { 			
			int length = lfIndex - readerIndex +1;
			String line = byteBuf.toString(readerIndex, length, Charset.forName(ENCODING));
			if(partialLine != null) {
				// if we had a split line we aggregate the previous line
				// not fully read with the current one
				line = partialLine + line;
				partialLine = null;	
			}
			// if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {   
            //     logger.logDebug("Read line:" + line);
            // }
			String lineIgnoreCase = line.toLowerCase();
			
			if(parsingState == ParsingState.CRLF && !lineIgnoreCase.equalsIgnoreCase(SIPMessage.SINGLE_CRLF)) {
				// if we are in a SINGLE CRLF case and the byte buffer contain a message after that
				// we need to return a null message
				parsingState = ParsingState.DOUBLE_CRLF;
				return;
			}
			byteBuf.skipBytes(length);			
			messageHeaders.write(line.getBytes(ENCODING));
			if(lineIgnoreCase.startsWith(ContentLength.NAME_LOWER)) { 
				// naive Content-Length header parsing to figure out how much bytes 
				// of message body must be read after the SIP headers
				contentLength = Integer.parseInt(line.substring(
						ContentLength.NAME_LOWER.length()+1).trim());
			} else if(lineIgnoreCase.startsWith(CONTENT_LENGTH_COMPACT_NAME)) { 
				// issue with compact header form
				contentLength = Integer.parseInt(line.substring(
						CONTENT_LENGTH_COMPACT_NAME.length()+1).trim());
			} else if(lineIgnoreCase.equalsIgnoreCase(SIPMessage.SINGLE_CRLF)) {
				if(parsingState == ParsingState.INIT) {
					// in case of single CLRF we continue the processing as we may be in a double CRLF Situation
					parsingState = ParsingState.CRLF;										
					return;
				}
				if(parsingState == ParsingState.CRLF) {
					// in case of double CLRF we stop the processing and will return a null request
					parsingState = ParsingState.DOUBLE_CRLF;										
					return;
				} 					
				if((parsingState == ParsingState.READING_HEADER_LINES || parsingState == ParsingState.READING_EMPTY_LINE) && 
					contentLength >= 0) {
					// if we saw a Content-Length header and we are in CRLF case we can stop processing headers
					// as we are in a split message case
					parsingState = ParsingState.READING_EMPTY_LINE;																										
				}
			}
			if(parsingState == ParsingState.INIT) {
				parsingState = ParsingState.READING_HEADER_LINES;
			}
			if(contentLength == 0) {
				if(parsingState == ParsingState.READING_EMPTY_LINE) {
					// if we saw a Content-Length header we can stop processing headers
					parsingState = ParsingState.PARSING_COMPLETE;																							
				}
				if(parsingState == ParsingState.READING_HEADER_LINES) {
					parsingState = ParsingState.READING_EMPTY_LINE;
				}
			}
				
			if(contentLength > 0) {
				if(parsingState == ParsingState.READING_EMPTY_LINE) {
					// if we saw a Content-Length header we can stop processing headers
					parsingState = ParsingState.READING_MESSAGE_BODY_CONTENTS;			
				}	
				if(parsingState == ParsingState.READING_HEADER_LINES) {
					// if we saw a Content-Length header we can stop processing headers
					parsingState = ParsingState.READING_EMPTY_LINE;			
				}
																									
			}
		} else {
			if(readableBytes > 0 && parsingState == ParsingState.READING_HEADER_LINES) {
				// case of split message in the middle of a SIP Message Header line
				String line = byteBuf.toString(readerIndex, readableBytes, Charset.forName(ENCODING));
				partialLine = line;
				// messageHeaders.write(line.getBytes(ENCODING));
				// if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {   
				// 	logger.logDebug("Read line:" + line);
				// }
				byteBuf.skipBytes(readableBytes);										
			}					
		}	
		//checking for max message size
		if(maxMessageSize > 0 && messageHeaders.size() > maxMessageSize) {
			throw new ParseException("Max size exceeded!", messageHeaders.size());
		}		
	}

	public void readMessageBody(ByteBuf byteBuf, int readableBytes, SIPMessage message) throws ParseException, IOException {
		if(readableBytes < contentLength) {
			// split body situation			
			byteBuf.readBytes(messageBody, readableBytes);					
		} else {
			// same buffer contains the whole body or 
			// buffer contains the body + additional messages
			// we read only what we need to complete current message
			byteBuf.readBytes(messageBody, contentLength);					
		}		
		if(messageBody.size() == contentLength) {			
			parsingState = ParsingState.PARSING_COMPLETE;
		}	
	}
	
	public SIPMessage createNullRequest(int size) {
		SIPRequest nullRequest = new SIPRequest();
		nullRequest.setSize(size);
		nullRequest.setNullRequest();				
		
		return nullRequest;
	}

	private void reset() {	
		parsingState = ParsingState.INIT;
		contentLength = -1;			
		messageHeaders = new ByteArrayOutputStream();        		
		messageBody = new ByteArrayOutputStream();        		
		partialLine = null;
	}	

    public SIPMessage consumeSIPMessage() {
		if(sipMessage == null) {
			return null;
		}
		SIPMessage retVal = this.sipMessage;
		this.sipMessage = null;
		reset();
        return retVal;
    }	

	public boolean isParsingComplete() {
		return parsingState == ParsingState.PARSING_COMPLETE || 
				parsingState == ParsingState.DOUBLE_CRLF;
	}
}
