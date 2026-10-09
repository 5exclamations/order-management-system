package com.acme.oms.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    @Bean
    NewTopic eventsTopic(AppProperties props) {
        return TopicBuilder.name(props.kafka().eventsTopic()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic eventsDeadLetterTopic(AppProperties props) {
        return TopicBuilder.name(props.kafka().eventsTopic() + ".DLT").partitions(3).replicas(1).build();
    }

    /** Retry a failing record a few times, then park it on the dead-letter topic instead of blocking the partition. */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), new FixedBackOff(500L, 4));
    }
}
