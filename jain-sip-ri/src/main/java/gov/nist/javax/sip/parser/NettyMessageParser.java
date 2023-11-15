
/*
 * Mobius Software LTD
 * Copyright 2023, Mobius Software LTD and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package gov.nist.javax.sip.parser;

import java.nio.charset.Charset;
import java.text.ParseException;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.SIPConstants;
import gov.nist.javax.sip.header.ContentLength;
import gov.nist.javax.sip.header.RequestLine;
import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.header.StatusLine;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import io.netty.buffer.ByteBuf;

/**
 * SIP Message Parser based on Netty ByteBuf
 *  
 * @author Jean Deruelle
 */
public class NettyMessageParser {
    // private static StackLogger logger = CommonLogger.getLogger(NettyMessageParser.class);

	private static final String ENCODING = "UTF-8";	
	private static final String CONTENT_LENGTH_COMPACT_NAME = "l";

	private static final byte CR = (byte)'\r';	
	private static final byte LF = (byte)'\n';	

	private enum ParsingState {	
		INIT,
		CRLF,
		DOUBLE_CRLF,
		READING_HEADER_LINES,
		READING_PARTIAL_HEADER_LINE,
		READING_EMPTY_LINE,
		READING_MESSAGE_BODY_CONTENTS,
		READING_PARTIAL_MESSAGE_BODY_CONTENTS,
		PARSING_COMPLETE		
	}

	private	ParsingState parsingState;    
	private ParseException parseException;

	private int contentLength = -1;	
	private int maxMessageSize = -1;        
	private boolean computeContentLengthFromMessage = false;   		
	private SIPMessage sipMessage = null;	

    public NettyMessageParser(int maxMessageSize, boolean computeContentLengthFromMessage) {
		this.maxMessageSize = maxMessageSize;
		this.computeContentLengthFromMessage = computeContentLengthFromMessage;
		sipMessage = null;
		parsingState = ParsingState.INIT;
    }

	public NettyMessageParser parseBytes(ByteBuf byteBuf) {			
		int readableBytes = byteBuf.readableBytes();		

		// if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {   
		// 	logger.logDebug("Readable Bytes: " + readableBytes + ", Parsing State:" + parsingState);
		// }

		while (readableBytes > 0 				
				&& !isParsingComplete()) {
					
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
				case READING_PARTIAL_HEADER_LINE:
					readSIPMessageHeader(byteBuf, readableBytes);
					break;
				case READING_EMPTY_LINE:
					readSIPMessageHeader(byteBuf, readableBytes);
					break;
				case READING_MESSAGE_BODY_CONTENTS:
					readMessageBody(byteBuf, readableBytes, sipMessage);
					break;
				case READING_PARTIAL_MESSAGE_BODY_CONTENTS:
					readMessageBody(byteBuf, readableBytes, sipMessage);
					break;
				case PARSING_COMPLETE:					
					break;
				default:
					break;
			}		
			readableBytes = byteBuf.readableBytes();
			// if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {   
			// 	logger.logDebug("Readable Bytes: " + readableBytes + ", Parsing State:" + parsingState);
			// }			
		}
		if(parsingState == ParsingState.DOUBLE_CRLF || parsingState == ParsingState.CRLF) {
			parsingState = ParsingState.DOUBLE_CRLF;
			sipMessage = createNullRequest(byteBuf.slice(0, byteBuf.readerIndex()).capacity());				
		} else if(parsingState == ParsingState.PARSING_COMPLETE) {
			// ByteBuf bufSlice = byteBuf.slice(0, byteBuf.readerIndex());
			// byte[] msg = new byte[bufSlice.capacity()];
			// bufSlice.getBytes(0, msg);
			// if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {   
			// 	String messageString = new String(msg, Charset.forName(ENCODING));
			// 	logger.logDebug("Msg to be parsed:" + messageString);
			// }
			// sipMessage = messageParser.parseSIPMessage(msg, true, false, null);				
			byteBuf.discardReadBytes();
		}		
		
		return this;		
	}

	public void readSIPMessageHeader(ByteBuf byteBuf, int readableBytes) {		
		// Read Message Headers
		int readerIndex = byteBuf.readerIndex();
		int lfIndex = byteBuf.indexOf(readerIndex, readerIndex + readableBytes, LF);				
		int crIndex = -1;
		if(lfIndex >= 1 && byteBuf.getByte(lfIndex-1) == CR)
			crIndex = lfIndex - 1;
		
		//byteBuf.indexOf(readerIndex, readerIndex + readableBytes, (byte)'\r');
		// check if we have a full header line with \r\n at the end
		if(crIndex != -1 && lfIndex != -1 && lfIndex - crIndex == 1) { 			
			int length = lfIndex - readerIndex +1;
			String line = byteBuf.toString(readerIndex, length, Charset.forName(ENCODING));
			
			// if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {   
			// 	logger.logDebug("Read temp Partial line:" + line);
			// }
			String lineIgnoreCase = line.toLowerCase();
			
			if(parsingState == ParsingState.CRLF && !lineIgnoreCase.equalsIgnoreCase(SIPMessage.SINGLE_CRLF)) {
				// if we are in a SINGLE CRLF case and the byte buffer contain a message after that
				// we need to return a null message
				parsingState = ParsingState.DOUBLE_CRLF;
				return;
			}
			byteBuf.skipBytes(length);			
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
					return ;
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
				processFirstLine(line);
				parsingState = ParsingState.READING_HEADER_LINES;
			} else if(parsingState == ParsingState.READING_HEADER_LINES) {
				processHeader(line);
			}
			
			checkContentLength();
		} else {
			if(readableBytes > 0 && parsingState == ParsingState.READING_HEADER_LINES) {
				// case of split message in the middle of a SIP Message Header line
				parsingState = ParsingState.READING_PARTIAL_HEADER_LINE;				
				// if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {   
				// 	String line = byteBuf.toString(readerIndex, readableBytes, Charset.forName(ENCODING));				
				// 	logger.logDebug("Read Partial line:" + line);
				// }
				return;
			}					
		}	
		//checking for max message size
		if(maxMessageSize > 0 && byteBuf.readerIndex() > maxMessageSize) {
			parseException = new ParseException("Max size exceeded!", byteBuf.readerIndex());
		}	
		return;	
	}

	private void checkContentLength() {
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
			// if(parsingState == ParsingState.READING_HEADER_LINES) {
			// 	// if we saw a Content-Length header we can stop processing headers
			// 	parsingState = ParsingState.READING_EMPTY_LINE;			
			// }
																								
		}
	}

	public void readMessageBody(ByteBuf byteBuf, int readableBytes, SIPMessage message) {				
		if(readableBytes >= contentLength) {	
			int readerIndex = byteBuf.readerIndex();
			ByteBuf bodySlice = byteBuf.slice(readerIndex, contentLength);	
			byte[] body = new byte[contentLength];
			bodySlice.getBytes(0, body);
			try {
				message.setMessageContent(body, false, computeContentLengthFromMessage,
                        contentLength);									
			} catch (ParseException e) {
				parseException = e;
			}
			byteBuf.skipBytes(contentLength);						
			parsingState = ParsingState.PARSING_COMPLETE;
		} else {
			parsingState = ParsingState.READING_PARTIAL_MESSAGE_BODY_CONTENTS;
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
		parseException = null;
		this.sipMessage = null;		
	}	

    public SIPMessage consumeSIPMessage() throws ParseException {
		
		if(parsingState == ParsingState.READING_PARTIAL_HEADER_LINE) {
			parsingState = ParsingState.READING_HEADER_LINES;
		}
		if(parsingState == ParsingState.READING_PARTIAL_MESSAGE_BODY_CONTENTS) {
			parsingState = ParsingState.READING_MESSAGE_BODY_CONTENTS;
		}
			
		if((parsingState != ParsingState.PARSING_COMPLETE && parsingState != ParsingState.DOUBLE_CRLF)) {
			return null;
		}
				
		if(parseException == null) {
			SIPMessage retVal = this.sipMessage;			
			reset();	
        	return retVal;
		} else {
			// if there was a problem parsing the message, 
			// we don't return a SIP Message and throw an exception
			ParseException retVal = this.parseException;			
			reset();			
			throw retVal;
		}
    }	

	public boolean isMessageComplete() {
		return parsingState == ParsingState.PARSING_COMPLETE 
				|| parsingState == ParsingState.DOUBLE_CRLF;
	}

	public boolean isParsingComplete() {
		return parsingState == ParsingState.PARSING_COMPLETE 
				|| parsingState == ParsingState.DOUBLE_CRLF
				|| parsingState == ParsingState.READING_PARTIAL_HEADER_LINE
				|| parsingState == ParsingState.READING_PARTIAL_MESSAGE_BODY_CONTENTS;
	}

	// SIP Message Parsing methods
	/**
	 * Processes the first line of a SIP message.
	 * @param firstLine the first line of the SIP message.
	 * @throws ParseException
	 */
	protected void processFirstLine(String firstLine) {        
		firstLine = StringMsgParser.trimEndOfLine(firstLine);
		try {
			if (!firstLine.startsWith(SIPConstants.SIP_VERSION_STRING)) {
				sipMessage = new SIPRequest();
				
				RequestLine requestLine = new RequestLineParser(firstLine + "\n")
						.parse();
				((SIPRequest) sipMessage).setRequestLine(requestLine);
			} else {
				sipMessage = new SIPResponse();
				StatusLine sl = new StatusLineParser(firstLine + "\n").parse();
				((SIPResponse) sipMessage).setStatusLine(sl);
			}   
		} catch (ParseException ex) {
			parseException = ex;
		}
    }

	/**
	 * Processes a SIP header.
	 * @param header the SIP header.
	 * @throws ParseException
	 */
	protected void processHeader(String header) {
		header = StringMsgParser.trimEndOfLine(header);
        HeaderParser headerParser = null;
        try {
            headerParser = ParserFactory.createParser(header + "\n");
			SIPHeader sipHeader = headerParser.parse();
            sipMessage.attachHeader(sipHeader, false);
        } catch (ParseException ex) {
			parseException = ex;
        }
    }

	public ParseException getParseException() {
		return parseException;
	}
}
