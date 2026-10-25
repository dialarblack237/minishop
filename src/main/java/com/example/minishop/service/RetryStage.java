package com.example.minishop.service;

public enum RetryStage {

    MAIN("orders", "orders.retry.5s"),
    RETRY_5S("orders.retry.5s", "orders.retry.30s"),
    RETRY_30S("orders.retry.30s", "orders.retry.5m"),
    RETRY_5M("orders.retry.5m", "orders.DLQ");

    private final String topic;
    private final String nextTopic;

    RetryStage(String topic, String nextTopic) {
        this.topic = topic;
        this.nextTopic = nextTopic;
    }

    public String topic() {
        return topic;
    }

    public String nextTopic() {
        return nextTopic;
    }

    public static RetryStage fromTopic(String topic) {
        for (RetryStage s : values()) {
            if (s.topic.equals(topic)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown retry topic: " + topic);
    }
}