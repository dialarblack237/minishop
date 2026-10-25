package com.example.minishop.service;

import com.example.minishop.domain.DlqMessage;
import com.example.minishop.repository.DlqMessageRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class DlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(DlqConsumer.class);

    private final DlqMessageRepository dlqRepo;

    public DlqConsumer(DlqMessageRepository dlqRepo) {
        this.dlqRepo = dlqRepo;
    }

    @KafkaListener(topics = "orders.DLQ", groupId = "dlq-audit")
    public void onDlq(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String reason = readReason(record);
        DlqMessage saved = dlqRepo.save(new DlqMessage(
                record.topic(), record.key(), reason, record.value()));

        log.warn("DLQ message stored: id={} reason={} key={}",
                saved.getId(), reason, record.key());

        ack.acknowledge();
    }

    private String readReason(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader("reason");
        if (header == null) {
            return DlqReason.UNKNOWN.value();
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}