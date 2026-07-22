package ru.yandex.practicum.payment.domain;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum PaymentScenario {
    SUCCESS("success"),
    DEBIT_FAILURE("debit-failure"),
    FRAUD_DENY("fraud-deny"),
    MANUAL_REVIEW("manual-review"),
    MANUAL_ALLOW("manual-allow"),
    MANUAL_DENY("manual-deny"),
    CUTOFF("cutoff"),
    COMPLIANCE_DENY("compliance-deny"),
    LIMITS_DENY("limits-deny"),
    CREDIT_FAILURE("credit-failure");

    private final String value;

    PaymentScenario(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static PaymentScenario from(String value) {
        return Arrays.stream(values())
                .filter(scenario -> scenario.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown scenario. Allowed: " + allowedValues()));
    }

    private static String allowedValues() {
        return Arrays.stream(values())
                .map(PaymentScenario::value)
                .collect(Collectors.joining(", "));
    }
}
