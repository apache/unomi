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

package org.apache.unomi.didvc.metering;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Kafka metering sink: publishes billable records as JSON to the
 * {@code didvc-metering} topic, keyed by event id so log compaction and
 * consumer deduplication make billing idempotent.
 */
public class KafkaMeteringSink implements MeteringSink, AutoCloseable {

    public static final String DEFAULT_TOPIC = "didvc-metering";

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaMeteringSink.class);

    private final KafkaProducer<String, String> producer;
    private final String topic;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Creates a Kafka metering sink with the given bootstrap servers.
     *
     * @param bootstrapServers the Kafka bootstrap servers
     * @param topic            the target topic
     */
    public KafkaMeteringSink(String bootstrapServers, String topic) {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", bootstrapServers);
        properties.put("key.serializer", StringSerializer.class.getName());
        properties.put("value.serializer", StringSerializer.class.getName());
        properties.put("acks", "all");
        this.producer = new KafkaProducer<>(properties);
        this.topic = topic;
    }

    @Override
    public void publish(VerificationMeteringRecord record) {
        String value;
        try {
            value = objectMapper.writeValueAsString(record);
        } catch (Exception e) {
            LOGGER.warn("Failed to serialize metering record {}", record.getEventId(), e);
            return;
        }
        producer.send(new ProducerRecord<>(topic, record.getEventId(), value), (metadata, exception) -> {
            if (exception != null) {
                LOGGER.warn("Failed to publish metering record {} to {}", record.getEventId(), topic, exception);
            }
        });
    }

    @Override
    public void close() {
        producer.close();
    }
}
