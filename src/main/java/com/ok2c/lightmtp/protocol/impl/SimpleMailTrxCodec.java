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
import java.util.LinkedList;

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
import com.ok2c.lightmtp.protocol.RcptResult;
import com.ok2c.lightmtp.protocol.DeliveryRequest;
import com.ok2c.lightnio.IOSession;
import com.ok2c.lightnio.SessionInputBuffer;
import com.ok2c.lightnio.SessionOutputBuffer;

public class SimpleMailTrxCodec implements ProtocolCodec<SessionState> {
    
    enum CodecState {
        
        MAIL_REQUEST_READY,
        MAIL_RESPONSE_EXPECTED,
        RCPT_REQUEST_READY,
        RCPT_RESPONSE_EXPECTED,
        DATA_REQUEST_READY,
        DATA_RESPONSE_EXPECTED,
        COMPLETED
        
    }
    
    private final SMTPMessageParser<SMTPReply> parser;
    private final SMTPMessageWriter<SMTPCommand> writer;
    private final LinkedList<String> recipients;
    
    private CodecState codecState;
    
    public SimpleMailTrxCodec(boolean enhancedCodes) {
        super();
        this.parser = new SMTPReplyParser(enhancedCodes);
        this.writer = new SMTPCommandWriter();
        this.recipients = new LinkedList<String>();
        this.codecState = CodecState.MAIL_REQUEST_READY; 
    }

    public void reset(
            final IOSession iosession, 
            final SessionState sessionState) throws IOException, SMTPProtocolException {
        if (iosession == null) {
            throw new IllegalArgumentException("IO session may not be null");
        }
        if (sessionState == null) {
            throw new IllegalArgumentException("Session state may not be null");
        }
        this.writer.reset();
        this.parser.reset();
        this.recipients.clear();
        this.codecState = CodecState.MAIL_REQUEST_READY; 
        
        if (sessionState.getRequest() != null) {
            iosession.setEventMask(SelectionKey.OP_WRITE);
        } else {
            iosession.setEventMask(SelectionKey.OP_READ);
        }
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
        if (sessionState.getRequest() == null) {
            throw new IllegalArgumentException("Delivery request may not be null");
        }

        SessionOutputBuffer buf = sessionState.getOutbuf();
        DeliveryRequest request = sessionState.getRequest();
        
        switch (this.codecState) {
        case MAIL_REQUEST_READY:
            SMTPCommand mailFrom = new SMTPCommand("MAIL", 
                    "FROM:<" + request.getSender() + ">");
            this.writer.write(mailFrom, buf);
            
            this.codecState = CodecState.MAIL_RESPONSE_EXPECTED;
            iosession.setEventMask(SelectionKey.OP_READ);
            break;
        case RCPT_REQUEST_READY:
            String recipient = this.recipients.getFirst(); 
            
            SMTPCommand rcptTo = new SMTPCommand("RCPT", "TO:<" + recipient + ">");
            this.writer.write(rcptTo, buf);
            
            this.codecState = CodecState.RCPT_RESPONSE_EXPECTED;
            break;
        case DATA_REQUEST_READY:
            SMTPCommand data = new SMTPCommand("DATA");
            this.writer.write(data, buf);
            this.codecState = CodecState.DATA_RESPONSE_EXPECTED;
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
        DeliveryRequest request = sessionState.getRequest();
        
        int bytesRead = buf.fill(iosession.channel());
        SMTPReply reply = this.parser.parse(buf, bytesRead == -1);

        if (reply != null) {
            switch (this.codecState) {
            case MAIL_RESPONSE_EXPECTED:
                if (reply.getCode() == SMTPCodes.OK) {
                    this.codecState = CodecState.RCPT_REQUEST_READY;
                    this.recipients.clear();
                    this.recipients.addAll(request.getRecipients());
                    iosession.setEventMask(SelectionKey.OP_WRITE);
                } else {
                    this.codecState = CodecState.COMPLETED;
                    sessionState.setReply(reply);
                }
                break;
            case RCPT_RESPONSE_EXPECTED:
                if (this.recipients.isEmpty()) {
                    throw new IllegalStateException("Unexpected state: " + this.codecState);
                }
                String recipient = this.recipients.removeFirst();

                if (reply.getCode() != SMTPCodes.OK) {
                    sessionState.getFailures().add(new RcptResult(reply, recipient));
                }
                
                if (this.recipients.isEmpty()) {
                    this.codecState = CodecState.DATA_REQUEST_READY;
                } else {
                    this.codecState = CodecState.RCPT_REQUEST_READY;
                }
                iosession.setEventMask(SelectionKey.OP_WRITE);
                break;
            case DATA_RESPONSE_EXPECTED:
                this.codecState = CodecState.COMPLETED;
                sessionState.setReply(reply);
                break;
            }
        }

        if (bytesRead == -1) {
            throw new UnexpectedEndOfStreamException();
        }
    }
    
    public boolean isIdle() {
        return this.codecState == CodecState.MAIL_REQUEST_READY; 
    }

    public boolean isCompleted() {
        return this.codecState == CodecState.COMPLETED; 
    }

    public String next(
            final ProtocolCodecs<SessionState> codecs, 
            final SessionState sessionState) {
        if (isCompleted()) {
            return ProtocolState.DATA.name();
        } else {
            return null;
        }
    }
        
}
