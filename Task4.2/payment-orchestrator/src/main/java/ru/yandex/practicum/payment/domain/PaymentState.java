package ru.yandex.practicum.payment.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PaymentState {

    private final String paymentId;
    private final String operationId;
    private final PaymentScenario scenario;
    private final Instant createdAt = Instant.now();
    private final List<String> history = new ArrayList<>();
    private long processInstanceKey;
    private String status = "PROCESS_STARTING";

    public PaymentState(String paymentId, String operationId, PaymentScenario scenario) {
        this.paymentId = paymentId;
        this.operationId = operationId;
        this.scenario = scenario;
        history.add("PROCESS_START_REQUESTED");
    }

    public String paymentId() {
        return paymentId;
    }

    public PaymentScenario scenario() {
        return scenario;
    }

    public synchronized void started(long key) {
        processInstanceKey = key;
        if (status.equals("PROCESS_STARTING")) {
            status = "PROCESS_STARTED";
        }
        history.add("PROCESS_INSTANCE_CREATED");
    }

    public synchronized void mark(String newStatus, String event) {
        status = newStatus;
        history.add(event);
    }

    public synchronized void add(String event) {
        history.add(event);
    }

    public synchronized Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("paymentId", paymentId);
        result.put("operationId", operationId);
        result.put("processInstanceKey", processInstanceKey);
        result.put("scenario", scenario.value());
        result.put("status", status);
        result.put("createdAt", createdAt.toString());
        result.put("history", List.copyOf(history));
        return result;
    }
}
