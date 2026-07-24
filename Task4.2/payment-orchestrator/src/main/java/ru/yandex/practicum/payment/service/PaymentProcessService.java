package ru.yandex.practicum.payment.service;

import io.camunda.zeebe.client.ZeebeClient;
import ru.yandex.practicum.payment.domain.PaymentScenario;
import ru.yandex.practicum.payment.domain.PaymentState;
import ru.yandex.practicum.payment.repository.PaymentRepository;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class PaymentProcessService implements AutoCloseable {

    private final ZeebeClient zeebe;
    private final PaymentRepository repository;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public PaymentProcessService(ZeebeClient zeebe, PaymentRepository repository) {
        this.zeebe = zeebe;
        this.repository = repository;
    }

    public PaymentState start(String scenarioValue, String cutOffDuration) {
        PaymentScenario scenario = PaymentScenario.from(scenarioValue);
        Duration.parse(cutOffDuration);

        String paymentId = UUID.randomUUID().toString();
        String operationId = UUID.randomUUID().toString();
        PaymentState payment = new PaymentState(paymentId, operationId, scenario);
        repository.save(payment);

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("paymentId", paymentId);
        variables.put("operationId", operationId);
        variables.put("scenario", scenario.value());
        variables.put("cutOffDuration", cutOffDuration);
        variables.put("debitSucceeded", false);
        variables.put("fraudDenied", false);
        variables.put("complianceDenied", false);
        variables.put("limitsDenied", false);
        variables.put("creditSucceeded", false);

        long processInstanceKey = zeebe.newCreateInstanceCommand()
                .bpmnProcessId("payment-process")
                .latestVersion()
                .variables(variables)
                .send()
                .join()
                .getProcessInstanceKey();
        payment.started(processInstanceKey);

        if (scenario == PaymentScenario.MANUAL_ALLOW || scenario == PaymentScenario.MANUAL_DENY) {
            boolean denied = scenario == PaymentScenario.MANUAL_DENY;
            scheduler.schedule(() -> publishManualReview(payment, denied), 1, TimeUnit.SECONDS);
        }

        return payment;
    }

    public void completeManualReview(PaymentState payment, String decisionValue) {
        String decision = decisionValue.toUpperCase();
        if (!decision.equals("ALLOW") && !decision.equals("DENY")) {
            throw new IllegalArgumentException("decision must be ALLOW or DENY");
        }
        publishManualReview(payment, decision.equals("DENY"));
    }

    private void publishManualReview(PaymentState payment, boolean denied) {
        try {
            zeebe.newPublishMessageCommand()
                    .messageName("manual-review-result")
                    .correlationKey(payment.paymentId())
                    .timeToLive(Duration.ofMinutes(5))
                    .variables(Map.of(
                            "fraudDecision", denied ? "DENY" : "ALLOW",
                            "fraudDenied", denied
                    ))
                    .send()
                    .join();
            payment.add(denied ? "MANUAL_DECISION_DENY_SENT" : "MANUAL_DECISION_ALLOW_SENT");
        } catch (Exception error) {
            payment.add("MANUAL_DECISION_SEND_FAILED");
            System.err.printf("Cannot publish manual decision for %s: %s%n",
                    payment.paymentId(), error.getMessage());
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
