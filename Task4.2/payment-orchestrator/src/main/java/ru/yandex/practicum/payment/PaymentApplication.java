package ru.yandex.practicum.payment;

import io.camunda.zeebe.client.ZeebeClient;
import ru.yandex.practicum.payment.api.PaymentHttpServer;
import ru.yandex.practicum.payment.infrastructure.ZeebeConnector;
import ru.yandex.practicum.payment.repository.PaymentRepository;
import ru.yandex.practicum.payment.service.PaymentProcessService;
import ru.yandex.practicum.payment.worker.PaymentWorkers;

import java.util.concurrent.CountDownLatch;

public final class PaymentApplication {

    private PaymentApplication() {
    }

    public static void main(String[] args) throws Exception {
        String zeebeAddress = env("ZEEBE_ADDRESS", "localhost:26500");
        int httpPort = Integer.parseInt(env("HTTP_PORT", "8080"));

        ZeebeClient zeebe = ZeebeConnector.connect(zeebeAddress);
        PaymentRepository repository = new PaymentRepository();
        PaymentProcessService processService = new PaymentProcessService(zeebe, repository);
        PaymentWorkers workers = new PaymentWorkers(zeebe, repository);
        PaymentHttpServer httpServer = new PaymentHttpServer(httpPort, repository, processService);

        workers.start();
        httpServer.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            httpServer.close();
            workers.close();
            processService.close();
            zeebe.close();
        }));

        System.out.printf("Payment orchestrator is ready: http://localhost:%d%n", httpPort);
        new CountDownLatch(1).await();
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
