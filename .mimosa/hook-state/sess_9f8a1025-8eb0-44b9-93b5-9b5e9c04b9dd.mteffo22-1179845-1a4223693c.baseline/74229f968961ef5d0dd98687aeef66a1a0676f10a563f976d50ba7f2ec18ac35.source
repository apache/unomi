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

package org.apache.unomi.didvc.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Kafka publisher for manifest-verification results (FR-L3): each
 * per-manifest outcome is published as JSON to the
 * {@code didvc-manifest-verification} topic, keyed by manifest id so
 * downstream Single-Window consumers deduplicate and replay safely.
 */
public class KafkaManifestResultSink implements java.util.function.Consumer<ManifestRecord.Result>, AutoCloseable {

    public static final String DEFAULT_TOPIC = "didvc-manifest-verification";

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaManifestResultSink.class);

    private final KafkaProducer<String, String> producer;
    private final String topic;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Creates the sink.
     *
     * @param bootstrapServers the Kafka bootstrap servers
     * @param topic            the target topic
     */
    public KafkaManifestResultSink(String bootstrapServers, String topic) {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", bootstrapServers);
        properties.put("key.serializer", StringSerializer.class.getName());
        properties.put("value.serializer", StringSerializer.class.getName());
        properties.put("acks", "all");
        this.producer = new KafkaProducer<>(properties);
        this.topic = topic;
    }

    @Override
    public void accept(ManifestRecord.Result result) {
        String value;
        try {
            value = objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            LOGGER.warn("Failed to serialize manifest result {}", result.getManifestId(), e);
            return;
        }
        producer.send(new ProducerRecord<>(topic, result.getManifestId(), value), (metadata, exception) -> {
            if (exception != null) {
                LOGGER.warn("Failed to publish manifest result {} to {}", result.getManifestId(), topic, exception);
            }
        });
    }

    @Override
    public void close() {
        producer.close();
    }
}
