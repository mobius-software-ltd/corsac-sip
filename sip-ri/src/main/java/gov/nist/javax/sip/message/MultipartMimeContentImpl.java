/*
 * Conditions Of Use 
 * 
 * This software was developed by employees of the National Institute of
 * Standards and Technology (NIST), an agency of the Federal Government.
 * Pursuant to title 15 Untied States Code Section 105, works of NIST
 * employees are not subject to copyright protection in the United States
 * and are considered to be in the public domain.  As a result, a formal
 * license is not needed to use the software.
 * 
 * This software is provided by NIST as a service and is expressly
 * provided "AS IS."  NIST MAKES NO WARRANTY OF ANY KIND, EXPRESS, IMPLIED
 * OR STATUTORY, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTY OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NON-INFRINGEMENT
 * AND DATA ACCURACY.  NIST does not warrant or make any representations
 * regarding the use of the software or the results thereof, including but
 * not limited to the correctness, accuracy, reliability or usefulness of
 * the software.
 * 
 * Permission to use this software is contingent upon your acceptance
 * of the terms of this agreement
 *  
 * .
 * 
 */
/*******************************************************************************
 * Product of NIST/ITL Advanced Networking Technologies Division (ANTD)         *
 *******************************************************************************/
package gov.nist.javax.sip.message;

import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.sip.header.ContentDispositionHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.Header;

import gov.nist.core.Separators;
import gov.nist.javax.sip.header.HeaderFactoryExt;
import gov.nist.javax.sip.header.HeaderFactoryImpl;

/**
 * Content list for multipart mime content type. <b> WARNING -- do not directly
 * cast to this this class. Use the methods of the interface that it implements.
 * Reimplementing it according to rfc1341 </b>
 * 
 * @author M. Ranganathan
 * @author yulianoifa
 * 
 */
public class MultipartMimeContentImpl implements MultipartMimeContent {
	private static byte[] LINE_FULL = new byte[] { '\r', '\n' };
	private static byte[] N_LINE = new byte[] { '\n' };
	private static byte[] HEADER_SEPARTOR = new byte[] { ':' };

	public static final String BOUNDARY = "boundary";
	private List<Content> contentList = new LinkedList<Content>();
	private HeaderFactoryExt headerFactory = new HeaderFactoryImpl();
	private ContentTypeHeader multipartMimeContentTypeHeader;
	private String boundary;

	private byte[] boundaryBytes;
	private byte[] delimiterBytes;

	private String charset;
	/**
	 * Creates a default content list.
	 * @throws UnsupportedEncodingException 
	 */
	public MultipartMimeContentImpl(ContentTypeHeader contentTypeHeader, String charset) throws UnsupportedEncodingException {
		this.multipartMimeContentTypeHeader = contentTypeHeader;
		this.boundary = contentTypeHeader.getParameter(BOUNDARY);
		this.charset = charset;
		
		if (boundary != null) {
			boundaryBytes = this.boundary.getBytes(charset);
			delimiterBytes = new byte[boundaryBytes.length + 2];
			delimiterBytes[0] = '-';
			delimiterBytes[1] = '-';
			System.arraycopy(boundaryBytes, 0, delimiterBytes, 2, boundaryBytes.length);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * gov.nist.javax.sip.message.MultipartMimeContentExt#add(gov.nist.javax.sip.
	 * message.Content)
	 */
	public boolean add(Content content) {
		return contentList.add((ContentImpl) content);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * gov.nist.javax.sip.message.MultipartMimeContentExt#getContentTypeHeader()
	 */
	public ContentTypeHeader getContentTypeHeader() {
		return multipartMimeContentTypeHeader;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gov.nist.javax.sip.message.MultipartMimeContentExt#toString()
	 */
	@Override
	public String toString() {
		StringBuilder result = new StringBuilder();

		for (Content content : this.contentList) {
			result.append("--" + boundary + Separators.NEWLINE);
			result.append(content.toString());
			result.append(Separators.NEWLINE);
		}

		if (!contentList.isEmpty()) {
			result.append("--" + boundary + "--");
		}

		return result.toString();

	}

	/**
	 * unpack a multipart mime packet and set a list of content packets.
	 * @throws UnsupportedEncodingException 
	 * 
	 * 
	 */
	public void createContentList(byte[] body) throws ParseException, UnsupportedEncodingException {
		if (boundary != null) {
			List<Integer> boundaryIndexes = findMatches(body, delimiterBytes);
			// we have now all indexes of --boundary, there should be at least 2 of them
			if (boundaryIndexes.size() > 1) {
				// lets validate that the end of body is matching --- in some cases it may be that we dont have -- in the end
				//int endIndex = boundaryIndexes.get(boundaryIndexes.size() - 1) + delimiterBytes.length;
				//if (endIndex <= body.length - 2 && body[endIndex] == '-' && body[endIndex + 1] == '-') {
					// we have n-1 contents
					for (int i = 0; i < boundaryIndexes.size() -1; i++) {
						// skip the boundary itself
						int currentStart = boundaryIndexes.get(i) + delimiterBytes.length;
						// skip new line after it
						if (startsWith(body, LINE_FULL, currentStart))
							currentStart += 2;
						else if (startsWith(body, N_LINE, currentStart))
							currentStart += 1;

						int currentEnd = boundaryIndexes.get(i + 1);

						// skip new line before end
						if (startsWith(body, LINE_FULL, currentEnd - 2))
							currentEnd -= 2;
						else if (startsWith(body, N_LINE, currentEnd - 1))
							currentEnd -= 1;

						// lets parse this part without copying it
						Content partContent = parseBodyPart(body, currentStart, currentEnd);
						contentList.add(partContent);
					}
				//}
			}
		}

		// we did not found anything yet, lets store single content
		if (contentList.size() == 0) {
			// No boundary had been set, we will consider the body as a single part
			ContentImpl content = parseBodyPart(body, 0, body.length);
			content.setContentTypeHeader(this.getContentTypeHeader());
			this.contentList.add(content);
		}
	}

	private ContentImpl parseBodyPart(byte[] body, int startIndex, int endIndex) throws ParseException, UnsupportedEncodingException {
		String headers[] = null;
		byte[] bodyContent = null;

		// if a empty line starts the body it means no headers are present
		if (startsWith(body, N_LINE, startIndex) || startsWith(body, LINE_FULL, startIndex)) {
			if(body.length==endIndex-startIndex)				
				bodyContent = body;
			else {
				bodyContent = new byte[endIndex-startIndex];
				System.arraycopy(body, startIndex, bodyContent, 0, endIndex - startIndex);
			}
		} else {
			Integer doubleLineIndex = getDoubleLineIndex(body, startIndex, endIndex);
			boolean hadHeaders = false;
			if (doubleLineIndex!= null) {
				// since we aren't completely sure the data is a header let's test the first one
				if (matches(body, HEADER_SEPARTOR, startIndex, doubleLineIndex)) {
					//first part are headers , second part is body
					headers = new String(body, startIndex, doubleLineIndex - startIndex, charset).split("\r?\n");

					//we have double line , lets skip it
					if (startsWith(body, LINE_FULL, doubleLineIndex))
						doubleLineIndex += 2;
					else if (startsWith(body, N_LINE, doubleLineIndex))
						doubleLineIndex += 1;
					
					if (startsWith(body, LINE_FULL, doubleLineIndex))
						doubleLineIndex += 2;
					else if (startsWith(body, N_LINE, doubleLineIndex))
						doubleLineIndex += 1;
					
					bodyContent = new byte[endIndex-doubleLineIndex];
					System.arraycopy(body, doubleLineIndex, bodyContent, 0, endIndex - doubleLineIndex);
					
					hadHeaders = true;
				}
			}
			
			if(!hadHeaders) {
				if(body.length==endIndex-startIndex)				
					bodyContent = body;
				else {
					bodyContent = new byte[endIndex-startIndex];
					System.arraycopy(body, startIndex, bodyContent, 0, endIndex - startIndex);
				}
			}
		}

		ContentImpl content = new ContentImpl(bodyContent);
		if (headers != null) {
			for (String partHeader : headers) {
				Header header = headerFactory.createHeader(partHeader);
				if (header instanceof ContentTypeHeader) {
					content.setContentTypeHeader((ContentTypeHeader) header);
				} else if (header instanceof ContentDispositionHeader) {
					content.setContentDispositionHeader((ContentDispositionHeader) header);
				} else {
					content.addExtensionHeader(header);
				}
			}
		}
		return content;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gov.nist.javax.sip.message.MultipartMimeContentExt#setContent(java.lang.
	 * String, java.lang.String, gov.nist.javax.sip.message.Content)
	 */
	public void addContent(Content content) {
		this.add(content);
	}

	public Iterator<Content> getContents() {
		return this.contentList.iterator();
	}

	public int getContentCount() {
		return this.contentList.size();
	}

	private static boolean startsWith(byte[] data, byte[] query, int startIndex) {
		if (query.length > data.length - startIndex)
			return false;

		for (int i = startIndex, j=0; j < query.length; i++, j++)
			if (data[i] != query[j])
				return false;

		return true;
	}

	private static List<Integer> findMatches(byte[] data, byte[] query) {
		List<Integer> results = new ArrayList<Integer>();
		for (int i = 0; i < data.length - query.length; i++) {
			if (data[i] == query[0]) {
				if (startsWith(data, query, i)) {
					results.add(i);
					i += query.length;
				}
			}
		}

		return results;
	}
	
	private static Integer getDoubleLineIndex(byte[] data, int startIndex, int endIndex) {
		boolean isCurrentNewLine = false;
		int charsCount = 0;
		for (int i = startIndex; i < endIndex; i++) {
			if(data[i] == '\n') {
				if(isCurrentNewLine) 
					return i - charsCount;
							
				charsCount ++;
				isCurrentNewLine = true;				
			}
			else if (data[i] == '\r')
				charsCount ++;
			else {
				isCurrentNewLine = false;
				charsCount = 0;
			}
		}
			
		return null;
	}
	
	private static Boolean matches(byte[] data, byte[] query, int startIndex, int endIndex) {
		for (int i = startIndex; i < endIndex - query.length; i++) {
			if (data[i] == query[0]) {
				if (startsWith(data, query, i)) {
					return true;
				}
			}
		}
		
		return false;
	}

	@Override
	public byte[] getEncodedValue() {
		Integer totalBytes = 0;	
		List<byte[]> encodedHeaders = new ArrayList<byte[]>();
		
		for (Content content : this.contentList) {
			totalBytes += delimiterBytes.length;
			totalBytes += LINE_FULL.length;
			byte[] currHeaders = content.getEncodedHeaders();
			totalBytes += currHeaders.length;
			encodedHeaders.add(currHeaders);
			totalBytes += content.getContent().length;
			totalBytes += LINE_FULL.length;
		}
		
		if (!contentList.isEmpty())
			totalBytes += delimiterBytes.length + 2;
		
		byte[] output = new byte[totalBytes];
		int currIndex = 0;
		for (Content content : this.contentList) {
			System.arraycopy(delimiterBytes, 0, output, currIndex, delimiterBytes.length);
			currIndex+=delimiterBytes.length;
			System.arraycopy(LINE_FULL, 0, output, currIndex, LINE_FULL.length);
			currIndex+=LINE_FULL.length;
			byte[] currHeaders = encodedHeaders.remove(0);
			System.arraycopy(currHeaders, 0, output, currIndex, currHeaders.length);
			currIndex+=currHeaders.length;
			System.arraycopy(content.getContent(), 0, output, currIndex, content.getContent().length);
			currIndex+=content.getContent().length;
			System.arraycopy(LINE_FULL, 0, output, currIndex, LINE_FULL.length);
			currIndex+=LINE_FULL.length;
		}

		if (!contentList.isEmpty()) {
			System.arraycopy(delimiterBytes, 0, output, currIndex, delimiterBytes.length);
			currIndex+=delimiterBytes.length;
			output[currIndex] = '-';
			output[currIndex+1] = '-';
		}

		return output;
	}

	@Override
	public int getEncodedLength() {
		Integer totalBytes = 0;	
		List<byte[]> encodedHeaders = new ArrayList<byte[]>();
		
		for (Content content : this.contentList) {
			totalBytes += delimiterBytes.length;
			totalBytes += LINE_FULL.length;
			byte[] currHeaders = content.getEncodedHeaders();
			totalBytes += currHeaders.length;
			encodedHeaders.add(currHeaders);
			totalBytes += content.getContent().length;
			totalBytes += LINE_FULL.length;
		}
		
		if (!contentList.isEmpty())
			totalBytes += delimiterBytes.length + 2;
		
		return totalBytes;
	}
}