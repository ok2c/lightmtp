/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.ok2c.lightmtp.message;

import java.nio.charset.CharacterCodingException;
import java.util.LinkedList;

import org.apache.http.nio.reactor.SessionInputBuffer;
import org.apache.http.util.Args;
import org.apache.http.util.CharArrayBuffer;

import com.ok2c.lightmtp.SMTPCommand;
import com.ok2c.lightmtp.SMTPConsts;
import com.ok2c.lightmtp.SMTPProtocolException;

public class SMTPCommandParser implements SMTPMessageParser<SMTPCommand> {

    private final CharArrayBuffer lineBuf;
    private final int maxLineLen;

    public SMTPCommandParser(final int maxLineLen) {
        super();
        this.lineBuf = new CharArrayBuffer(1024);
        this.maxLineLen = maxLineLen;
    }

    public SMTPCommandParser() {
        this(SMTPConsts.MAX_COMMAND_LEN);
    }

    @Override
    public void reset() {
        this.lineBuf.clear();
    }

    @Override
    public SMTPCommand parse(
            final SessionInputBuffer buf, final boolean endOfStream) throws SMTPProtocolException {
        Args.notNull(buf, "Session input buffer");
        if (readLine(buf, endOfStream)) {
            LinkedList<String> lines = new LinkedList<String>();
            int i = 0;
            int len = this.lineBuf.length();
            while (i < len) {
                if (this.lineBuf.charAt(i) != ' ') {
                    break;
                }
                i++;
            }
            while (i < len) {
                int idx = this.lineBuf.indexOf(' ', i, len);
                if (idx == -1) {
                    idx = len;
                }
                String s = this.lineBuf.substringTrimmed(i, idx);
                if (s.length() == 0) {
                    throw new SMTPProtocolException("Malformed command line: "
                            + this.lineBuf.toString());
                }
                i += s.length() + 1;
                lines.add(s);
            }
            if (lines.isEmpty()) {
                throw new SMTPProtocolException("Empty command line");
            }
            String code = lines.removeFirst();
            String argument = null;
            if (!lines.isEmpty()) {
                argument = lines.removeFirst();
            }
            this.lineBuf.clear();
            return new SMTPCommand(code, argument, lines);
        } else {
            return null;
        }
    }

    private boolean readLine(
            final SessionInputBuffer buf, final boolean endOfStream) throws SMTPProtocolException {
        try {
            boolean lineComplete = buf.readLine(this.lineBuf, endOfStream);
            if (this.maxLineLen > 0 &&
                    (this.lineBuf.length() > this.maxLineLen ||
                            (!lineComplete && buf.length() > this.maxLineLen))) {
                throw new SMTPProtocolException("Maximum command length limit exceeded");
            }
            return lineComplete;
        } catch (CharacterCodingException ex) {
            throw new SMTPProtocolException("Invalid character coding", ex);
        }
    }

}
