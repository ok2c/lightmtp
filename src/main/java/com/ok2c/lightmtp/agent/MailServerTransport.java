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
package com.ok2c.lightmtp.agent;

import java.net.SocketAddress;
import java.util.Set;

import org.apache.http.nio.reactor.ListenerEndpoint;

import com.ok2c.lightmtp.protocol.DeliveryHandler;
import com.ok2c.lightmtp.protocol.EnvelopValidator;
import com.ok2c.lightmtp.protocol.RemoteAddressValidator;
import com.ok2c.lightmtp.protocol.UniqueIdGenerator;

public interface MailServerTransport extends MailTransport {

    ListenerEndpoint listen(SocketAddress address);

    Set<ListenerEndpoint> getEndpoints();

    void start(
            UniqueIdGenerator idgenerator,
            RemoteAddressValidator addressValidator,
            EnvelopValidator envelopValidator,
            DeliveryHandler deliveryHandler);

}
