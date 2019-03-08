/*
 * Copyright 2008-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Original Work: Apache License, Version 2.0, Copyright 2018 Confluent Inc.
 */

package com.mongodb.kafka.connect.embedded;

import java.net.URI;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.common.utils.Time;
import org.apache.kafka.connect.errors.NotFoundException;
import org.apache.kafka.connect.runtime.Connect;
import org.apache.kafka.connect.runtime.Herder;
import org.apache.kafka.connect.runtime.Worker;
import org.apache.kafka.connect.runtime.WorkerInfo;
import org.apache.kafka.connect.runtime.isolation.Plugins;
import org.apache.kafka.connect.runtime.rest.RestServer;
import org.apache.kafka.connect.runtime.rest.entities.ConnectorInfo;
import org.apache.kafka.connect.runtime.standalone.StandaloneConfig;
import org.apache.kafka.connect.runtime.standalone.StandaloneHerder;
import org.apache.kafka.connect.storage.FileOffsetBackingStore;
import org.apache.kafka.connect.util.ConnectUtils;
import org.apache.kafka.connect.util.FutureCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ConnectStandalone {

    private static final Logger log = LoggerFactory.getLogger(ConnectStandalone.class);

    private final String connectionString;
    private final Herder herder;
    private final Connect connect;

    @SuppressWarnings("unchecked")
    ConnectStandalone(final Properties workerProperties) {
        Time time = Time.SYSTEM;
        log.info("Kafka Connect standalone worker initializing ...");
        long initStart = time.hiResClockMs();
        WorkerInfo initInfo = new WorkerInfo();
        initInfo.logAll();

        Map<String, String> workerProps = (Map) workerProperties;

        log.info("Scanning for plugin classes. This might take a moment ...");
        Plugins plugins = new Plugins(workerProps);
        plugins.compareAndSwapWithDelegatingLoader();
        StandaloneConfig config = new StandaloneConfig(workerProps);

        String kafkaClusterId = ConnectUtils.lookupKafkaClusterId(config);
        log.debug("Kafka cluster ID: {}", kafkaClusterId);

        RestServer rest = new RestServer(config);
        URI advertisedUrl = rest.advertisedUrl();
        String workerId = advertisedUrl.getHost() + ":" + advertisedUrl.getPort();

        Worker worker = new Worker(workerId, time, plugins, config, new FileOffsetBackingStore());
        this.herder = new StandaloneHerder(worker, kafkaClusterId);
        connectionString = advertisedUrl.toString() + herder.kafkaClusterId();

        this.connect = new Connect(herder, rest);
        log.info("Kafka Connect standalone worker initialization took {}ms", time.hiResClockMs() - initStart);
    }

    String getConnectionString() {
        return connectionString;
    }

    void start() {
        connect.start();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    void addConnector(final String name, final Properties properties) {
        FutureCallback<Herder.Created<ConnectorInfo>> cb = new FutureCallback<>((error, info) -> {
            if (error != null) {
                log.error("Failed to create job for {}", properties);
            } else {
                log.info("Created connector {}", info.result().name());
            }
        });
        try {
            herder.putConnectorConfig(name, (Map) properties, true, cb);
            cb.get();
        } catch (Exception e) {
            log.error("Failed to add connector for {}", properties);
            throw new ConnectorConfigurationException(e);
        }
    }

    void deleteConnector(final String name) {
        FutureCallback<Herder.Created<ConnectorInfo>> cb = new FutureCallback<>((error, info) -> {
            if (error != null) {
                log.error("Failed to delete connector: {}", error);
            } else {
                log.info("Deleted connector {}", name);
            }
        });
        try {
            herder.deleteConnectorConfig(name, cb);
            cb.get();
        } catch (Exception e) {
            if (!(e.getCause() instanceof NotFoundException)) {
                log.error("Failed to delete connector for", name);
                throw new ConnectorConfigurationException(e);
            }
        }
    }

    void stop() {
        connect.stop();
        connect.awaitStop();
    }

    class ConnectorConfigurationException extends RuntimeException {
        ConnectorConfigurationException(final Throwable cause) {
            super(cause);
        }
    }

}