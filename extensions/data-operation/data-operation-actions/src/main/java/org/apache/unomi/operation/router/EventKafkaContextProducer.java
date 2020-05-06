package org.apache.unomi.operation.router;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaEndpoint;
import org.apache.camel.core.osgi.OsgiDefaultCamelContext;
import org.apache.unomi.api.Event;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class EventKafkaContextProducer implements SynchronousBundleListener, EventProducer {
    private OsgiDefaultCamelContext camelContext;
    private BundleContext bundleContext;
    protected String kafkaTopic = "eventify-event";
    private ProducerTemplate producer;
    private JacksonDataFormat objectMapper;
    private static Logger logger = LoggerFactory.getLogger(EventKafkaContextProducer.class);

    private Map<String, String> kafkaProps;

    public void initCamelContext() throws Exception {
        camelContext = new OsgiDefaultCamelContext(bundleContext);
        logger.info("Start Camel Context");
        StringBuilder uriBuilder = new StringBuilder("kafka:");
        StringBuilder kafkaOptions = new StringBuilder();
        KafkaConfiguration kafkaConfiguration = new KafkaConfiguration();

        for (Map.Entry<String, String> entry : kafkaProps.entrySet()) {
            if (entry.getKey().equals("topic")) {
                kafkaConfiguration.setTopic(entry.getValue());
                uriBuilder.append(entry.getValue());
                continue;
            }
            if (entry.getKey().equals("brokers")) {
                kafkaConfiguration.setBrokers(entry.getValue());
            }
            kafkaOptions.append(entry.getKey()).append("=").append(entry.getValue());
        }
        uriBuilder.append("?").append(kafkaOptions.toString());
        logger.info("KAFKA Config");
        try {
            camelContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    KafkaComponent kafka = new KafkaComponent();
                    kafka.setBrokers(uriBuilder.toString());
                    kafka.setConfiguration(kafkaConfiguration);
                    camelContext.addComponent("kafka", kafka);
                    KafkaEndpoint endpoint = new KafkaEndpoint(uriBuilder.toString(), new KafkaComponent(this.getContext()));
                    endpoint.setConfiguration(kafkaConfiguration);
                    from("direct:kafkaRoute").marshal(objectMapper).log("Send to DataOperation: ${body}").to(endpoint);
                }
            });
            camelContext.start();
            logger.info("KAFKA start context");
        } catch (Exception e) {
            logger.error("KAFKA error", e);
            e.printStackTrace();
        }
    }

    public void preDestroy() throws Exception {
        bundleContext.removeBundleListener(this);
        //This is to shutdown Camel context
        //(will stop all routes/components/endpoints etc and clear internal state/cache)
        this.camelContext.stop();
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    @Override
    public void bundleChanged(BundleEvent bundleEvent) {

    }

    public void setKafkaProps(Map<String, String> kafkaProps) {
        this.kafkaProps = kafkaProps;
    }

    @Override
    public void send(Event e) {
        this.getProducer().sendBody("direct:kafkaRoute", e);
    }

    private ProducerTemplate getProducer() {
        if (producer == null) {
            producer = camelContext.createProducerTemplate();
        }
        return producer;
    }

    public void setObjectMapper(JacksonDataFormat objectMapper) {
        this.objectMapper = objectMapper;
    }
}
