/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.messaging;

import org.gradle.api.Action;
import org.gradle.messaging.dispatch.*;

import java.net.URI;

/**
 * A {@link org.gradle.messaging.MessagingClient} which uses a single TCP connection with a server.
 */
public class TcpMessagingClient implements MessagingClient {
    private final DefaultMultiChannelConnector connector;
    private final DefaultMessagingClient client;

    public TcpMessagingClient(ClassLoader messagingClassLoader, URI serverAddress) {
        connector = new DefaultMultiChannelConnector(new TcpOutgoingConnector(messagingClassLoader), new NoOpIncomingConnector());
        client = new DefaultMessagingClient(connector, messagingClassLoader, serverAddress);
    }

    public ObjectConnection getConnection() {
        return client.getConnection();
    }

    public void stop() {
        client.stop();
        connector.stop();
    }

    private static class NoOpIncomingConnector implements IncomingConnector {
        public URI getLocalAddress() {
            throw new UnsupportedOperationException();
        }

        public void accept(Action<Connection<Message>> action) {
        }
    }
}
