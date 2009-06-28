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
package com.ok2c.lightmtp.protocol.impl;

import java.io.IOException;
import java.nio.channels.SelectionKey;

import com.ok2c.lightmtp.SMTPCodes;
import com.ok2c.lightmtp.SMTPCommand;
import com.ok2c.lightmtp.SMTPProtocolException;
import com.ok2c.lightmtp.SMTPReply;
import com.ok2c.lightmtp.message.SMTPCommandWriter;
import com.ok2c.lightmtp.message.SMTPMessageParser;
import com.ok2c.lightmtp.message.SMTPMessageWriter;
import com.ok2c.lightmtp.message.SMTPReplyParser;
import com.ok2c.lightmtp.protocol.ProtocolCodec;
import com.ok2c.lightmtp.protocol.ProtocolCodecs;
import com.ok2c.lightnio.IOSession;
import com.ok2c.lightnio.SessionInputBuffer;
import com.ok2c.lightnio.SessionOutputBuffer;

public class SimpleHeloCodec implements ProtocolCodec<SessionState> {
    
    enum CodecState {
        
        SERVICE_READY_EXPECTED,
        HELO_READY,
        HELO_RESPONSE_EXPECTED,
        COMPLETED
        
    }
    
    private final SMTPMessageParser<SMTPReply> parser;
    private final SMTPMessageWriter<SMTPCommand> writer;
    
    private CodecState codecState;
    
    public SimpleHeloCodec() {
        super();
        this.parser = new SMTPReplyParser();
        this.writer = new SMTPCommandWriter();
        this.codecState = CodecState.SERVICE_READY_EXPECTED; 
    }

    public void reset(
            final IOSession iosession, 
            final SessionState sessionState) throws IOException, SMTPProtocolException {
        this.parser.reset();
        this.writer.reset();
        this.codecState = CodecState.SERVICE_READY_EXPECTED; 
    }

    public void produceData(
            final IOSession iosession, 
            final SessionState sessionState) throws IOException, SMTPProtocolException {
        if (iosession == null) {
            throw new IllegalArgumentException("IO session may not be null");
        }
        if (sessionState == null) {
            throw new IllegalArgumentException("Session state may not be null");
        }

        SessionOutputBuffer buf = sessionState.getOutbuf();

        switch (this.codecState) {
        case HELO_READY:
            SMTPCommand helo = new SMTPCommand("HELO");
            this.writer.write(helo, buf);
            break;
        }
        
        if (buf.hasData()) {
            buf.flush(iosession.channel());
        }
        if (!buf.hasData()) {
            iosession.clearEvent(SelectionKey.OP_WRITE);
        }
    }

    public void consumeData(
            final IOSession iosession, 
            final SessionState sessionState) throws IOException, SMTPProtocolException {
        if (iosession == null) {
            throw new IllegalArgumentException("IO session may not be null");
        }
        if (sessionState == null) {
            throw new IllegalArgumentException("Session state may not be null");
        }

        SessionInputBuffer buf = sessionState.getInbuf();
        
        int bytesRead = buf.fill(iosession.channel());
        SMTPReply reply = this.parser.parse(buf, bytesRead == -1);

        if (reply != null) {
            switch (this.codecState) {
            case SERVICE_READY_EXPECTED:
                if (reply.getCode() == SMTPCodes.SERVICE_READY) {
                    this.codecState = CodecState.HELO_READY;
                    iosession.setEventMask(SelectionKey.OP_WRITE);
                } else {
                    this.codecState = CodecState.COMPLETED;
                    sessionState.setReply(reply);
                }
                break;
            case HELO_RESPONSE_EXPECTED:
                this.codecState = CodecState.COMPLETED;
                sessionState.setReply(reply);
                break;
            default:
                throw new SMTPProtocolException("Unexpected reply: " + reply);
            }
        }

        if (bytesRead == -1) {
            throw new UnexpectedEndOfStreamException();
        }
    }
    
    public boolean isCompleted() {
        return this.codecState == CodecState.COMPLETED; 
    }

    public String next(
            final ProtocolCodecs<SessionState> codecs, 
            final SessionState sessionState) {
        if (isCompleted()) {
            return ProtocolState.MAIL.name();
        } else {
            return null;
        }
    }
        
}