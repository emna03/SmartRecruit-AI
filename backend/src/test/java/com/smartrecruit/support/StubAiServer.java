package com.smartrecruit.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Faux service IA (vrai serveur HTTP local) : réponse, statut et délai configurables, requêtes enregistrées. */
public final class StubAiServer implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService executor;
    private final AtomicInteger requestCount = new AtomicInteger();

    private volatile int status = 200;
    private volatile String body = "{}";
    private volatile long delayMs;
    private volatile HttpExchange lastExchange;
    private volatile byte[] lastBody;
    private final Map<String, Route> routes = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> pathCounts = new ConcurrentHashMap<>();

    private record Route(int status, String body) {
    }

    private StubAiServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "stub-ai-server");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    public static StubAiServer start() {
        return new StubAiServer();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void respond(int status, String body) {
        this.status = status;
        this.body = body;
    }

    /** Réponse propre à un chemin (prioritaire sur {@link #respond}). */
    public void respondTo(String path, int status, String body) {
        routes.put(path, new Route(status, body));
    }

    /** Requêtes reçues sur un chemin donné. */
    public int requestCount(String path) {
        return pathCounts.getOrDefault(path, new AtomicInteger()).get();
    }

    public void delay(long delayMs) {
        this.delayMs = delayMs;
    }

    public void reset() {
        respond(200, "{}");
        delay(0);
        routes.clear();
        pathCounts.clear();
        requestCount.set(0);
        lastExchange = null;
        lastBody = null;
    }

    public int requestCount() {
        return requestCount.get();
    }

    public String lastHeader(String name) {
        return lastExchange == null ? null : lastExchange.getRequestHeaders().getFirst(name);
    }

    public String lastMethod() {
        return lastExchange == null ? null : lastExchange.getRequestMethod();
    }

    public String lastPath() {
        return lastExchange == null ? null : lastExchange.getRequestURI().getPath();
    }

    public String lastBody() {
        return lastBody == null ? null : new String(lastBody, StandardCharsets.UTF_8);
    }

    public byte[] lastBodyBytes() {
        return lastBody;
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        String path = exchange.getRequestURI().getPath();
        pathCounts.computeIfAbsent(path, p -> new AtomicInteger()).incrementAndGet();
        lastBody = exchange.getRequestBody().readAllBytes();
        lastExchange = exchange;
        Route route = routes.getOrDefault(path, new Route(status, body));
        try {
            if (delayMs > 0) {
                Thread.sleep(delayMs);
            }
            byte[] bytes = route.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(route.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
            // le client a abandonné la requête (délai dépassé)
        } finally {
            exchange.close();
        }
    }
}
