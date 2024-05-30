/*
* Conditions Of Use
*
* This software was developed by employees of the National Institute of
* Standards and Technology (NIST), an agency of the Federal Government.
* Pursuant to title 15 United States Code Section 105, works of NIST
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
package gov.nist.javax.sip.parser;

import gov.nist.core.Debug;
import gov.nist.core.LexerCore;
import gov.nist.core.ParserCore;
import gov.nist.core.Token;
import java.text.ParseException;

/**
 * Base parser class.
 *
 * @version 1.2 $Revision: 1.10 $ $Date: 2009-07-17 18:58:01 $
 *
 * Author: M. Ranganathan
 */
public abstract class Parser extends ParserCore implements TokenTypes {

    protected ParseException createParseException(String exceptionString) {
        return new ParseException(
            lexer.getBuffer() + ":" + exceptionString,
            lexer.getPtr());
    }

    protected Lexer getLexer() {
        return (Lexer) this.lexer;
    }

    protected String sipVersion() throws ParseException {
        if (debug)
            dbg_enter("sipVersion");
        try {
            Token tok = lexer.match(SIP);
            if (!tok.getTokenValue().equalsIgnoreCase("SIP"))
                createParseException("Expecting SIP");
            lexer.match('/');
            tok = lexer.match(ID);
            if (!tok.getTokenValue().equals("2.0"))
                createParseException("Expecting SIP/2.0");

            return "SIP/2.0";
        } finally {
            if (debug)
                dbg_leave("sipVersion");
        }
    }

    /**
     * parses a method. Consumes if a valid method has been found.
     */
    protected String method() throws ParseException {
        try {
            if (debug)
                dbg_enter("method");
            Token[] tokens = this.lexer.peekNextToken(1);
            Token token = (Token) tokens[0];
            if (token.getTokenType() == INVITE
                || token.getTokenType() == ACK
                || token.getTokenType() == OPTIONS
                || token.getTokenType() == BYE
                || token.getTokenType() == REGISTER
                || token.getTokenType() == CANCEL
                || token.getTokenType() == SUBSCRIBE
                || token.getTokenType() == NOTIFY
                || token.getTokenType() == PUBLISH
                || token.getTokenType() == MESSAGE
                || token.getTokenType() == ID) {
                lexer.consume();
                return token.getTokenValue();
            } else {
                throw createParseException("Invalid Method");
            }
        } finally {
            if (Debug.debug)
                dbg_leave("method");
        }
    }

    /**
     * Verifies that a given string matches the 'token' production in RFC3261
     *
     * @param token
     * @throws ParseException - if there are invalid characters
     *
     * Author: JvB
     */
    public static final void checkToken(String token) throws ParseException {
        if (token == null || token.length() == 0) {
            throw new ParseException("null or empty token", -1);
        } else {
            // Check that it is a valid token
            for (int i = 0; i < token.length(); ++i) {
                if (!LexerCore.isTokenChar(token.charAt(i))) {
                    throw new ParseException("Invalid character(s) in string (not allowed in 'token')", i);
                }
            }
        }
    }
}

