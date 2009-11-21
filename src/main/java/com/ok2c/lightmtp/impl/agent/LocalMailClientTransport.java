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
package com.ok2c.lightmtp.impl.agent;

import java.io.IOException;

import com.ok2c.lightmtp.impl.protocol.LocalClientSessionFactory;
import com.ok2c.lightmtp.protocol.DeliveryRequestHandler;
import com.ok2c.lightnio.impl.IOReactorConfig;

public class LocalMailClientTransport extends DefaultMailClientTransport {

    public LocalMailClientTransport(
            final IOSessionRegistry sessionRegistry,
            final IOReactorConfig config) throws IOException {
        super(sessionRegistry, config);
    }

    public LocalMailClientTransport(
            final IOReactorConfig config) throws IOException {
        this(new IOSessionRegistry(), config);
    }

    public void start(final DeliveryRequestHandler deliveryRequestHandler) {
        LocalClientSessionFactory sessionFactory = new LocalClientSessionFactory(
                deliveryRequestHandler);
        start(sessionFactory);
    }

}
