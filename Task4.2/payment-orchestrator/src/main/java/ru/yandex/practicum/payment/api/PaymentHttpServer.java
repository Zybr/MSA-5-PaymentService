package ru.yandex.practicum.payment.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ru.yandex.practicum.payment.domain.PaymentState;
import ru.yandex.practicum.payment.repository.PaymentRepository;
import ru.yandex.practicum.payment.service.PaymentProcessService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PaymentHttpServer implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final PaymentRepository repository;
    private final PaymentProcessService processService;
    private final ExecutorService executor = Executors.newFixedThreadPool(8);
    private final HttpServer server;

    public PaymentHttpServer(
            int port,
            PaymentRepository repository,
            PaymentProcessService processService
    ) throws IOException {
        this.repository = repository;
        this.processService = processService;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", this::handleHealth);
        server.createContext("/payments", this::handlePayments);
        server.setExecutor(executor);
    }

    public void start() {
        server.start();
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }
        sendJson(exchange, 200, Map.of("status", "UP"));
    }

    private void handlePayments(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            String[] segments = path.split("/");

            if (path.equals("/payments") && method.equals("POST")) {
                startPayment(exchange);
                return;
            }
            if (path.equals("/payments") && method.equals("GET")) {
                List<Map<String, Object>> result = repository.findAll().stream()
                        .map(PaymentState::snapshot)
                        .toList();
                sendJson(exchange, 200, result);
                return;
            }
            if (segments.length == 3 && method.equals("GET")) {
                getPayment(exchange, segments[2]);
                return;
            }
            if (segments.length == 4 && segments[3].equals("review") && method.equals("POST")) {
                completeManualReview(exchange, segments[2]);
                return;
            }
            sendError(exchange, 404, "Endpoint not found");
        } catch (IllegalArgumentException error) {
            sendError(exchange, 400, error.getMessage());
        } catch (Exception error) {
            error.printStackTrace(System.err);
            sendError(exchange, 500, error.getMessage() == null ? "Internal error" : error.getMessage());
        }
    }

    private void startPayment(HttpExchange exchange) throws IOException {
        Map<String, Object> request = readBody(exchange);
        String scenario = stringValue(request.getOrDefault("scenario", "success"));
        String cutOffDuration = stringValue(request.getOrDefault("cutOffDuration", "PT20M"));
        PaymentState payment = processService.start(scenario, cutOffDuration);
        sendJson(exchange, 202, payment.snapshot());
    }

    private void getPayment(HttpExchange exchange, String paymentId) throws IOException {
        PaymentState payment = repository.find(paymentId);
        if (payment == null) {
            sendError(exchange, 404, "Payment not found");
            return;
        }
        sendJson(exchange, 200, payment.snapshot());
    }

    private void completeManualReview(HttpExchange exchange, String paymentId) throws IOException {
        PaymentState payment = repository.find(paymentId);
        if (payment == null) {
            sendError(exchange, 404, "Payment not found");
            return;
        }

        Map<String, Object> request = readBody(exchange);
        processService.completeManualReview(payment, stringValue(request.get("decision")));
        sendJson(exchange, 202, payment.snapshot());
    }

    private static String stringValue(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Required string value is missing");
        }
        return value.toString();
    }

    private static Map<String, Object> readBody(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        return body.length == 0 ? Map.of() : JSON.readValue(body, MAP_TYPE);
    }

    private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, Map.of("error", message));
    }

    private static void sendJson(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] body = JSON.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(1);
        executor.shutdownNow();
    }
}
