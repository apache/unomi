/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.unomi.kafka;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.services.EventService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;

@Component(
        name = "org.apache.unomi.kafka",
        immediate = true
)
public class KafkaInjector implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaInjector.class);

    private Dictionary<String, Object> properties;
    private KafkaConsumer<String, String> consumer;
    private String topic;
    private String messageType;
    private boolean consuming = false;
    private ObjectMapper objectMapper;

    @Reference
    private EventService eventService;

    @Activate
    public void activate(ComponentContext componentContext) {
        objectMapper = new ObjectMapper();

        properties = componentContext.getProperties();

        topic = getValue(properties, "topic", "unomi");
        messageType = getValue(properties, "message.type", "text");

        Properties config = new Properties();

        String bootstrapServers = getValue(properties, "bootstrap.servers", "localhost:9092");
        config.put("bootstrap.servers", bootstrapServers);

        String groupId = getValue(properties, "group.id", "unomi");
        config.put("group.id", groupId);

        String enableAutoCommit = getValue(properties, "enable.auto.commit", "true");
        config.put("enable.auto.commit", enableAutoCommit);

        String autoCommitIntervalMs = getValue(properties, "auto.commit.interval.ms", "1000");
        config.put("auto.commit.interval.ms", autoCommitIntervalMs);

        String sessionTimeoutMs = getValue(properties,"session.timeout.ms", "30000");
        config.put("session.timeout.ms", sessionTimeoutMs);

        String keyDeserializer = getValue(properties, "key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        config.put("key.deserializer", keyDeserializer);

        String valueDeserializer = getValue(properties, "value.deserializer", "org.apache.kafka.common.serialization.ValueDeserializer");
        config.put("value.deserializer", valueDeserializer);

        String securityProtocol = getValue(properties, "security.protocol", null);
        if (securityProtocol != null) {
            config.put("security.protocol", securityProtocol);
        }

        String sslTruststoreLocation = getValue(properties, "ssl.truststore.location", null);
        if (sslTruststoreLocation != null)
            config.put("ssl.truststore.location", sslTruststoreLocation);

        String sslTruststorePassword = getValue(properties, "ssl.truststore.password", null);
        if (sslTruststorePassword != null)
            config.put("ssl.truststore.password", sslTruststorePassword);

        String sslKeystoreLocation = getValue(properties, "ssl.keystore.location", null);
        if (sslKeystoreLocation != null)
            config.put("ssl.keystore.location", sslKeystoreLocation);

        String sslKeystorePassword = getValue(properties, "ssl.keystore.password", null);
        if (sslKeystorePassword != null)
            config.put("ssl.keystore.password", sslKeystorePassword);

        String sslKeyPassword = getValue(properties, "ssl.key.password", null);
        if (sslKeyPassword != null)
            config.put("ssl.key.password", sslKeyPassword);

        String sslProvider = getValue(properties, "ssl.provider", null);
        if (sslProvider != null)
            config.put("ssl.provider", sslProvider);

        String sslCipherSuites = getValue(properties, "ssl.cipher.suites", null);
        if (sslCipherSuites != null)
            config.put("ssl.cipher.suites", sslCipherSuites);

        String sslEnabledProtocols = getValue(properties, "ssl.enabled.protocols", null);
        if (sslEnabledProtocols != null)
            config.put("ssl.enabled.protocols", sslEnabledProtocols);

        String sslTruststoreType = getValue(properties, "ssl.truststore.type", null);
        if (sslTruststoreType != null)
            config.put("ssl.truststore.type", sslTruststoreType);

        String sslKeystoreType = getValue(properties, "ssl.keystore.type", null);
        if (sslKeystoreType != null)
            config.put("ssl.keystore.type", sslKeystoreType);

        ClassLoader originClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            consumer = new KafkaConsumer<String, String>(config);
            consumer.subscribe(Arrays.asList(topic));
        } finally {
            Thread.currentThread().setContextClassLoader(originClassLoader);
        }
        consuming = true;
        Executors.newSingleThreadExecutor().execute(this);
    }

    @Override
    public void run() {
        while (consuming) {
            try {
                consume();
            } catch (Exception e) {
                LOGGER.warn(e.getMessage(), e);
            }
        }
    }

    private void consume() throws UnsupportedEncodingException, IOException, JsonMappingException {
        ConsumerRecords<String, String> records = consumer.poll(10000);
        if (records.isEmpty()) {
            return;
        }

        Event event = null;

        for (ConsumerRecord<String, String> record : records) {
            String value = record.value();
            if (messageType.equalsIgnoreCase("text")) {
                event = objectMapper.readValue(value, Event.class);
            }
        }

        if (event != null) {
            eventService.send(event);
        }
    }

    private String getValue(Dictionary<String, Object> config, String key, String defaultValue) {
        String value = (String) config.get(key);
        return (value != null) ? value : defaultValue;
    }

}
