package ru.yandex.practicum.payment.worker;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobWorker;
import ru.yandex.practicum.payment.domain.PaymentScenario;
import ru.yandex.practicum.payment.domain.PaymentState;
import ru.yandex.practicum.payment.repository.PaymentRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PaymentWorkers implements AutoCloseable {

    private final ZeebeClient zeebe;
    private final PaymentRepository repository;
    private final List<JobWorker> workers = new ArrayList<>();

    public PaymentWorkers(ZeebeClient zeebe, PaymentRepository repository) {
        this.zeebe = zeebe;
        this.repository = repository;
    }

    public void start() {
        openWorker("create-payment", job -> {
            state(job).mark("CREATED", "PAYMENT_CREATED");
            return Map.of();
        });

        openWorker("debit-customer-account", job -> {
            boolean succeeded = scenario(job) != PaymentScenario.DEBIT_FAILURE;
            state(job).mark(succeeded ? "FUNDS_DEBITED" : "FAILED",
                    succeeded ? "DEBIT_SUCCEEDED" : "DEBIT_FAILED");
            return Map.of("debitSucceeded", succeeded);
        });

        openWorker("check-fraud", job -> {
            String decision = switch (scenario(job)) {
                case FRAUD_DENY -> "DENY";
                case MANUAL_REVIEW, MANUAL_ALLOW, MANUAL_DENY, CUTOFF -> "MANUAL_REVIEW";
                default -> "ALLOW";
            };
            PaymentState payment = state(job);
            payment.add("FRAUD_" + decision);
            if (decision.equals("MANUAL_REVIEW")) {
                payment.mark("MANUAL_REVIEW_PENDING", "MANUAL_REVIEW_REQUIRED");
            }
            return Map.of(
                    "fraudDecision", decision,
                    "fraudDenied", decision.equals("DENY")
            );
        });

        openWorker("check-compliance", job -> {
            boolean denied = scenario(job) == PaymentScenario.COMPLIANCE_DENY;
            state(job).add(denied ? "COMPLIANCE_DENIED" : "COMPLIANCE_ALLOWED");
            return Map.of("complianceDenied", denied);
        });

        openWorker("check-limits", job -> {
            boolean denied = scenario(job) == PaymentScenario.LIMITS_DENY;
            state(job).add(denied ? "LIMITS_DENIED" : "LIMITS_ALLOWED");
            return Map.of("limitsDenied", denied);
        });

        openWorker("record-manual-review", job -> {
            boolean denied = booleanVariable(job, "fraudDenied");
            state(job).mark(denied ? "REJECTED" : "APPROVED",
                    denied ? "MANUAL_REVIEW_DENIED" : "MANUAL_REVIEW_ALLOWED");
            return Map.of();
        });

        openWorker("approve-by-cutoff", job -> {
            state(job).mark("APPROVED", "CUT_OFF_EXPIRED");
            return Map.of("fraudDecision", "ALLOW", "fraudDenied", false);
        });

        openWorker("credit-counterparty-account", job -> {
            boolean succeeded = scenario(job) != PaymentScenario.CREDIT_FAILURE;
            state(job).mark("CREDIT_PENDING", succeeded ? "CREDIT_SUCCEEDED" : "CREDIT_FAILED");
            return Map.of("creditSucceeded", succeeded);
        });

        openWorker("refund-customer-account", job -> {
            state(job).mark("REFUND_PENDING", "REFUND_SUCCEEDED");
            return Map.of();
        });

        openWorker("notify-security", job -> {
            state(job).add("SECURITY_NOTIFIED");
            return Map.of();
        });

        openWorker("notify-customer-success", job -> {
            state(job).mark("COMPLETED", "CUSTOMER_NOTIFIED_SUCCESS");
            return Map.of();
        });

        openWorker("notify-customer-refund", job -> {
            state(job).mark("REFUNDED", "CUSTOMER_NOTIFIED_REFUND");
            return Map.of();
        });

        openWorker("notify-customer-failure", job -> {
            state(job).mark("FAILED", "CUSTOMER_NOTIFIED_FAILURE");
            return Map.of();
        });
    }

    private void openWorker(String jobType, WorkerAction action) {
        JobWorker worker = zeebe.newWorker()
                .jobType(jobType)
                .handler((client, job) -> {
                    try {
                        Map<String, Object> variables = action.execute(job);
                        client.newCompleteCommand(job.getKey())
                                .variables(variables)
                                .send()
                                .join();
                        System.out.printf("Completed %-31s payment=%s%n", jobType, paymentId(job));
                    } catch (Exception error) {
                        System.err.printf("Worker %s failed: %s%n", jobType, error.getMessage());
                        client.newFailCommand(job.getKey())
                                .retries(Math.max(job.getRetries() - 1, 0))
                                .errorMessage(error.getMessage() == null
                                        ? error.getClass().getName()
                                        : error.getMessage())
                                .send();
                    }
                })
                .name("payment-orchestrator-" + jobType)
                .timeout(Duration.ofSeconds(15))
                .open();
        workers.add(worker);
    }

    private PaymentState state(ActivatedJob job) {
        return repository.require(paymentId(job));
    }

    private String paymentId(ActivatedJob job) {
        return stringVariable(job, "paymentId");
    }

    private PaymentScenario scenario(ActivatedJob job) {
        return PaymentScenario.from(stringVariable(job, "scenario"));
    }

    private boolean booleanVariable(ActivatedJob job, String name) {
        return Boolean.TRUE.equals(job.getVariablesAsMap().get(name));
    }

    private String stringVariable(ActivatedJob job, String name) {
        Object value = job.getVariablesAsMap().get(name);
        if (value == null) {
            throw new IllegalArgumentException("Required variable is missing: " + name);
        }
        return value.toString();
    }

    @Override
    public void close() {
        workers.forEach(JobWorker::close);
    }

    @FunctionalInterface
    private interface WorkerAction {
        Map<String, Object> execute(ActivatedJob job);
    }
}
