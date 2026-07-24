package ru.yandex.practicum.payment.infrastructure;

import io.camunda.zeebe.client.ZeebeClient;

public final class ZeebeConnector {

    private static final int MAX_ATTEMPTS = 60;

    private ZeebeConnector() {
    }

    public static ZeebeClient connect(String address) throws InterruptedException {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            ZeebeClient candidate = ZeebeClient.newClientBuilder()
                    .gatewayAddress(address)
                    .usePlaintext()
                    .build();
            try {
                candidate.newTopologyRequest().send().join();
                System.out.printf("Connected to Zeebe at %s%n", address);
                return candidate;
            } catch (RuntimeException error) {
                candidate.close();
                lastError = error;
                System.out.printf("Zeebe is not ready, attempt %d/%d%n", attempt, MAX_ATTEMPTS);
                Thread.sleep(2_000);
            }
        }
        throw new IllegalStateException("Cannot connect to Zeebe at " + address, lastError);
    }
}
