package ru.yandex.practicum.payment.repository;

import ru.yandex.practicum.payment.domain.PaymentState;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PaymentRepository {

    private final Map<String, PaymentState> payments = new ConcurrentHashMap<>();

    public void save(PaymentState payment) {
        payments.put(payment.paymentId(), payment);
    }

    public PaymentState find(String paymentId) {
        return payments.get(paymentId);
    }

    public PaymentState require(String paymentId) {
        PaymentState payment = find(paymentId);
        if (payment == null) {
            throw new IllegalStateException("Unknown payment " + paymentId);
        }
        return payment;
    }

    public Collection<PaymentState> findAll() {
        return payments.values();
    }
}
