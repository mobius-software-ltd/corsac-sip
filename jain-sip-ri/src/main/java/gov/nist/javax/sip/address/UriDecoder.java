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
package gov.nist.javax.sip.address;

import gov.nist.javax.sip.parser.URLParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Replacement for previous RFC2396UrlDecoder class removed for licensing restrictions, hacked from scratch this time
 * might be less performant though
 * @author <A HREF="mailto:jean.deruelle@gmail.com">Jean Deruelle</A> 
 *
 */
public class UriDecoder {
        public static final char UTF8_ESCAPE_CHAR = '%';
        
	static Charset utf8CharSet = null;
	static {
		try {
			utf8CharSet = Charset.forName("UTF8");
		} catch (UnsupportedCharsetException e) {
			//the situation that UTF-8 is not supported should never happen
			throw new RuntimeException("Problem in decodePath: UTF-8 charset not supported.", e);
		}
	}
    /**
     * Decode a uri.
     *
     * Replace %XX (where XX is hexadecimal number) by its corresponding UTF-8 encoded bytes.
     * 
     * @param uri the uri to decode
     * @return the decoded uri
     */
    public static String decode(String uri) {
    	// if there is no % we just return the same uri
    	String uriToWorkOn = uri;
        int indexOfNextPercent = uriToWorkOn.indexOf(UTF8_ESCAPE_CHAR);
        StringBuilder decodedUri = new StringBuilder();
             
        while(indexOfNextPercent != -1) {
        	decodedUri.append(uriToWorkOn.substring(0, indexOfNextPercent));
            if(indexOfNextPercent + 2 < uriToWorkOn.length()) {
            	String hexadecimalString = uriToWorkOn.substring(indexOfNextPercent + 1, indexOfNextPercent + 3);
            	try {
                    byte hexadecimalNumber = (byte) Integer.parseInt(hexadecimalString, 16);
                    String correspondingCharacter = utf8CharSet.decode(ByteBuffer.wrap(new byte[] {hexadecimalNumber})).toString();
                    decodedUri.append(correspondingCharacter);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Illegal hex characters in pattern %" + hexadecimalString);
                }
            }        
            uriToWorkOn = uriToWorkOn.substring(indexOfNextPercent + 3);
            indexOfNextPercent = uriToWorkOn.indexOf(UTF8_ESCAPE_CHAR);
        }               
        decodedUri.append(uriToWorkOn);
        return decodedUri.toString();
    }
    
    static final Set<Character> CHARACHTERS_DONT_NEED_ENCODING = new HashSet<Character>(Arrays.asList('-', '_','.', '*','"'));
    
    static final int CHARACTER_CASE_DIFF = ('a' - 'A');


    public static String encode(String s) {
        return encode(s, CHARACHTERS_DONT_NEED_ENCODING);
    }

    public static String encodeUriParam(String s) {
        return encode(s, URLParser.PARAM_CHAR_SET);
    }    
	    
    /**
     * Translates a string into <code>x-www-form-urlencoded</code> format.
     *
     * @param   s   <code>String</code> to be translated.
     * @param noEncodingChars set of chars where encoding is skipped
     * @return  the translated <code>String</code>.
     */
    public static String encode(String s, Set<Character> noEncodingChars) {
		final StringBuffer out = new StringBuffer(s.length());
		final ByteArrayOutputStream buf = new ByteArrayOutputStream(32);
		final OutputStreamWriter writer = new OutputStreamWriter(buf);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (Character.isAlphabetic(c) || 
                                Character.isDigit(c) || noEncodingChars.contains(c)) {
				out.append((char) c);
			} else {
				try {
					writer.write(c);
					writer.flush();
				} catch (IOException e) {
					buf.reset();
					continue;
				}
				byte[] ba = buf.toByteArray();
				for (int j = 0; j < ba.length; j++) {
					out.append(UTF8_ESCAPE_CHAR);
					char ch = Character.forDigit((ba[j] >> 4) & 0xF, 16);
					// converting to use uppercase letter as part of
					// the hex value if ch is a letter.
					if (Character.isLetter(ch)) {
						ch -= CHARACTER_CASE_DIFF;
					}
					out.append(ch);
					ch = Character.forDigit(ba[j] & 0xF, 16);
					if (Character.isLetter(ch)) {
						ch -= CHARACTER_CASE_DIFF;
					}
					out.append(ch);
				}
				buf.reset();
			}
		}

		return out.toString();
	}    
}