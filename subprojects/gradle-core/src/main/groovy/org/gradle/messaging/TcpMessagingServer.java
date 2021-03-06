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

import org.gradle.messaging.dispatch.*;

import java.net.URI;

/**
 * A {@link org.gradle.messaging.MessagingServer} implementation which uses a single incoming TCP port for all peers.
 */
public class TcpMessagingServer implements MessagingServer {
    private final TcpIncomingConnector incomingConnector;
    private final DefaultMultiChannelConnector connector;
    private final DefaultMessagingServer server;

    public TcpMessagingServer(ClassLoader messageClassLoader) {
        incomingConnector = new TcpIncomingConnector(messageClassLoader);
        connector = new DefaultMultiChannelConnector(new NoOpOutgoingConnector(), incomingConnector);
        server = new DefaultMessagingServer(connector, messageClassLoader);
    }

    public ObjectConnection createUnicastConnection() {
        return server.createUnicastConnection();
    }

    public void stop() {
        incomingConnector.requestStop();
        server.stop();
        connector.stop();
        incomingConnector.stop();
    }

    private static class NoOpOutgoingConnector implements OutgoingConnector {
        public Connection<Message> connect(URI destinationUri) {
            throw new UnsupportedOperationException();
        }
    }
}
