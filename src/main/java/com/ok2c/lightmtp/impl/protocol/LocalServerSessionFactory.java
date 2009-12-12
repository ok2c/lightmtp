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
package com.ok2c.lightmtp.impl.protocol;

import java.io.File;

import com.ok2c.lightmtp.impl.protocol.cmd.DataHandler;
import com.ok2c.lightmtp.impl.protocol.cmd.DefaultProtocolHandler;
import com.ok2c.lightmtp.impl.protocol.cmd.EhloHandler;
import com.ok2c.lightmtp.impl.protocol.cmd.MailFromHandler;
import com.ok2c.lightmtp.impl.protocol.cmd.NoopHandler;
import com.ok2c.lightmtp.impl.protocol.cmd.QuitHandler;
import com.ok2c.lightmtp.impl.protocol.cmd.RcptToHandler;
import com.ok2c.lightmtp.impl.protocol.cmd.RsetHandler;
import com.ok2c.lightmtp.impl.protocol.cmd.VrfyHandler;
import com.ok2c.lightmtp.protocol.DeliveryHandler;
import com.ok2c.lightmtp.protocol.EnvelopValidator;
import com.ok2c.lightmtp.protocol.ProtocolCodecs;
import com.ok2c.lightmtp.protocol.ProtocolHandler;
import com.ok2c.lightmtp.protocol.RemoteAddressValidator;
import com.ok2c.lightmtp.protocol.SessionFactory;
import com.ok2c.lightnio.IOSession;

public class LocalServerSessionFactory implements SessionFactory<ServerSession> {

    private final File workingDir;
    private final RemoteAddressValidator addressValidator;
    private final EnvelopValidator validator;
    private final DeliveryHandler deliveryHandler;
    
    public LocalServerSessionFactory(
            final File workingDir,
            final RemoteAddressValidator addressValidator,
            final EnvelopValidator validator,
            final DeliveryHandler deliveryHandler) {
        super();
        if (workingDir == null) {
            throw new IllegalArgumentException("Working dir may not be null");
        }
        if (validator == null) {
            throw new IllegalArgumentException("Envelop validator may not be null");
        }
        if (deliveryHandler == null) {
            throw new IllegalArgumentException("Delivery handler may not be null");
        }
        this.workingDir = workingDir;
        this.addressValidator = addressValidator;
        this.validator = validator;
        this.deliveryHandler = deliveryHandler;
    }
    
    public ServerSession create(final IOSession iosession) {
        SMTPBuffers iobuffers = new SMTPBuffers();
        ProtocolCodecs<ServerState> codecs = new ProtocolCodecRegistry<ServerState>();
        codecs.register(ProtocolState.INIT.name(),
                new ServiceReadyCodec(iobuffers, this.addressValidator));
        codecs.register(ProtocolState.MAIL.name(),
                new PipeliningReceiveEnvelopCodec(iobuffers, 
                        createProtocolHandler(this.validator)));
        codecs.register(ProtocolState.DATA.name(),
                new ReceiveDataCodec(iobuffers, this.workingDir, this.deliveryHandler, 
                        DataAckMode.PER_RECIPIENT));
        codecs.register(ProtocolState.QUIT.name(),
                new ServiceShutdownCodec(iobuffers));
        return new ServerSession(iosession, iobuffers, codecs);
    }

    protected ProtocolHandler<ServerState> createProtocolHandler(
            final EnvelopValidator validator) {
        DefaultProtocolHandler handler = new DefaultProtocolHandler();
        handler.register("LHLO", new EhloHandler());
        handler.register("RSET", new RsetHandler());
        handler.register("NOOP", new NoopHandler());
        handler.register("QUIT", new QuitHandler());
        handler.register("VRFY", new VrfyHandler(validator));
        handler.register("MAIL", new MailFromHandler(validator));
        handler.register("RCPT", new RcptToHandler(validator));
        handler.register("DATA", new DataHandler());
        return handler;
    }

}
