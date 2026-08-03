package io.metaloom.ai.genai.mockllm;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.metaloom.ai.genai.mockllm.MockResponse.ErrorResponse;
import io.metaloom.ai.genai.mockllm.MockResponse.StructuredResponse;
import io.metaloom.ai.genai.mockllm.MockResponse.SuccessResponse;
import io.metaloom.ai.genai.mockllm.MockResponse.ToolCallsResponse;
import io.metaloom.ai.genai.mockllm.MockResponse.TruncatedResponse;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * A lightweight Vert.x HTTP server that mimics the OpenAI-compatible chat completions API.
 *
 * <p>Responses are pre-programmed in a FIFO queue. Each request to
 * {@code POST /v1/chat/completions} consumes the next queued response.
 * If the queue is empty the server returns a 500 error.
 *
 * <p>Both non-streaming (single JSON body) and streaming (Server-Sent Events, when the
 * client sends {@code "stream": true}) chat completions are supported, tool calls included.
 *
 * <h3>Example usage</h3>
 * <pre>{@code
 * try (MockLLMServer server = MockLLMServer.create(0)
 *         .addResponse("Hello World")
 *         .addError(ErrorResponse.rateLimitError())
 *         .addResponse("Second answer")
 *         .start()) {
 *
 *     String baseUrl = server.baseUrl();
 *     // … configure your LLM client to use baseUrl …
 * }
 * }</pre>
 */
public final class MockLLMServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MockLLMServer.class);

    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    private static final String MODELS_PATH = "/v1/models";

    /** Approximate SSE chunk size (in characters) when streaming a queued response. */
    private static final int STREAM_CHUNK_SIZE = 8;

    /** Port requested at construction time. 0 means "pick a random free port". */
    private final int requestedPort;

    /** Resolved port after {@link #start()} has been called. */
    private int actualPort = -1;

    /** Pre-programmed responses consumed in FIFO order. */
    private final Deque<MockResponse> responseQueue = new ArrayDeque<>();

    private Vertx vertx;
    private HttpServer httpServer;

    private MockLLMServer(int port) {
        this.requestedPort = port;
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Creates a new, not-yet-started {@link MockLLMServer}.
     *
     * @param port the TCP port to bind to; pass {@code 0} for a random free port
     */
    public static MockLLMServer create(int port) {
        return new MockLLMServer(port);
    }

    // -------------------------------------------------------------------------
    // Configuration (fluent, must be called before start())
    // -------------------------------------------------------------------------

    /**
     * Queues a successful chat completion that will return the given text content.
     */
    public MockLLMServer addResponse(String text) {
        responseQueue.addLast(new SuccessResponse(text));
        return this;
    }

    /**
     * Queues multiple successful responses in order.
     */
    public MockLLMServer addResponses(String... texts) {
        for (String text : texts) {
            addResponse(text);
        }
        return this;
    }

    /**
     * Queues a successful chat completion with finish_reason="length", simulating
     * a truncated output due to the token limit being reached (soft error).
     */
    public MockLLMServer addTruncatedResponse(String text) {
        responseQueue.addLast(new TruncatedResponse(text));
        return this;
    }

    /**
     * Queues an error response.
     */
    public MockLLMServer addError(ErrorResponse error) {
        responseQueue.addLast(error);
        return this;
    }

    /**
     * Queues an error response with the given HTTP status code and a minimal error body.
     *
     * @param statusCode HTTP status code
     * @param code       OpenAI error code string
     * @param message    Human-readable error message
     */
    public MockLLMServer addError(int statusCode, String code, String message) {
        return addError(new ErrorResponse(statusCode, "server_error", code, message));
    }

    /**
     * Queues a structured-output response whose {@code content} will be the serialised form of the
     * given JSON object.
     */
    public MockLLMServer addStructuredResponse(JsonObject json) {
        responseQueue.addLast(new StructuredResponse(json));
        return this;
    }

    /**
     * Queues a tool-calls response.
     */
    public MockLLMServer addToolCallsResponse(ToolCallsResponse toolCallsResponse) {
        responseQueue.addLast(toolCallsResponse);
        return this;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Starts the server and blocks until it is ready to accept connections.
     *
     * @return {@code this} for chaining
     * @throws RuntimeException if the server fails to start within 10 seconds
     */
    public MockLLMServer start() {
        vertx = Vertx.vertx();
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.route().handler(ctx -> {
            log.info("Mock LLM received {} {}", ctx.request().method(), ctx.request().uri());
            // Force a fresh TCP connection per request. Some HTTP clients (notably
            // OkHttp used by the OpenAI Java SDK) keep idle connections in a pool
            // that survives across test cases; when the mock server is restarted
            // in @BeforeEach/@AfterEach the pooled socket becomes stale and the
            // next request fails with "unexpected end of stream".
            ctx.response().putHeader("Connection", "close");
            ctx.next();
        });

        io.vertx.core.Handler<io.vertx.ext.web.RoutingContext> chatHandler = ctx -> {
            MockResponse next = responseQueue.pollFirst();
            if (next == null) {
                log.warn("No more queued responses – returning 500");
                ctx.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end(buildErrorBody(500, "server_error", "no_response_queued",
                                "The mock LLM server has no more pre-programmed responses.", null));
                return;
            }

            boolean streaming = isStreamRequested(ctx);

            switch (next) {
                case SuccessResponse s -> respondContent(ctx, streaming, s.text(), "stop");
                case TruncatedResponse t -> respondContent(ctx, streaming, t.text(), "length");
                case StructuredResponse s -> respondContent(ctx, streaming, s.json().encode(), "stop");
                case ToolCallsResponse t -> {
                    if (streaming) {
                        streamToolCalls(ctx.response(), t);
                    } else {
                        String body = buildToolCallsBody(t);
                        ctx.response()
                                .setStatusCode(200)
                                .putHeader("Content-Type", "application/json")
                                .end(body);
                    }
                }
                case ErrorResponse e -> {
                    String body = buildErrorBody(e.statusCode(), e.type(), e.code(), e.message(), e.param());
                    ctx.response()
                            .setStatusCode(e.statusCode())
                            .putHeader("Content-Type", "application/json")
                            .end(body);
                }
            }
        };

        // Register both with and without /v1 prefix so the server works regardless
        // of whether the client's baseUrl already contains "/v1" or not.
        router.post(CHAT_COMPLETIONS_PATH).handler(chatHandler);
        router.post("/chat/completions").handler(chatHandler);
        // Azure OpenAI style path: /openai/deployments/{deployment}/chat/completions
        router.post("/openai/deployments/:deployment/chat/completions").handler(chatHandler);

        // Minimal models endpoint so testConnection() calls work.
        io.vertx.core.Handler<io.vertx.ext.web.RoutingContext> modelsHandler = ctx -> {
            JsonObject response = new JsonObject()
                    .put("object", "list")
                    .put("data", new JsonArray()
                            .add(new JsonObject()
                                    .put("id", "mock-model")
                                    .put("object", "model")
                                    .put("created", System.currentTimeMillis() / 1000)
                                    .put("owned_by", "mock")));
            ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(response.encode());
        };
        router.get(MODELS_PATH).handler(modelsHandler);
        router.get("/models").handler(modelsHandler);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> startError = new AtomicReference<>();

        // Disable HTTP/2 cleartext (h2c) upgrade so all clients communicate over
        // HTTP/1.1. The handler above sets a "Connection: close" response header
        // which is required to force fresh TCP connections for HTTP/1.1 clients
        // (e.g. OkHttp used by the OpenAI SDK), but that header is prohibited in
        // HTTP/2. Clients using the JDK HttpClient (newer OpenAI SDK)
        // default to HTTP/2 and would otherwise reject the response with
        // "malformed response: Prohibited header name 'connection'".
        httpServer = vertx.createHttpServer(new HttpServerOptions().setHttp2ClearTextEnabled(false));
        httpServer.requestHandler(router).listen(requestedPort).onComplete(result -> {
            if (result.succeeded()) {
                actualPort = result.result().actualPort();
                log.info("Mock LLM server started on port {}", actualPort);
            } else {
                startError.set(result.cause());
                log.error("Failed to start mock LLM server", result.cause());
            }
            latch.countDown();
        });

        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new RuntimeException("Mock LLM server did not start within 10 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for mock LLM server to start", e);
        }

        if (startError.get() != null) {
            throw new RuntimeException("Mock LLM server failed to start", startError.get());
        }

        return this;
    }

    /**
     * Stops the server and releases all Vert.x resources.
     */
    public void stop() {
        if (httpServer != null) {
            CountDownLatch latch = new CountDownLatch(1);
            httpServer.close().onComplete(v -> latch.countDown());
            try {
                latch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            httpServer = null;
        }
        if (vertx != null) {
            CountDownLatch latch = new CountDownLatch(1);
            vertx.close().onComplete(v -> latch.countDown());
            try {
                latch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            vertx = null;
        }
        log.info("Mock LLM server stopped");
    }

    @Override
    public void close() {
        stop();
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the port the server is actually listening on.
     * Only valid after {@link #start()} has been called.
     */
    public int port() {
        if (actualPort < 0) {
            throw new IllegalStateException("Server has not been started yet");
        }
        return actualPort;
    }

    /**
     * Returns the base URL of the server, e.g. {@code http://localhost:12345}.
     */
    public String baseUrl() {
        return "http://localhost:" + port();
    }

    /**
     * Returns the number of responses still waiting in the queue.
     */
    public int remainingResponses() {
        return responseQueue.size();
    }

    /**
     * Removes all queued responses. Useful between test cases when the server
     * instance is shared across multiple tests.
     */
    public void clearResponses() {
        responseQueue.clear();
    }

    // -------------------------------------------------------------------------
    // Response dispatch
    // -------------------------------------------------------------------------

    /** Returns {@code true} when the client requested a streamed response ({@code "stream": true}). */
    private static boolean isStreamRequested(io.vertx.ext.web.RoutingContext ctx) {
        try {
            JsonObject body = ctx.body() != null ? ctx.body().asJsonObject() : null;
            return body != null && Boolean.TRUE.equals(body.getBoolean("stream", false));
        } catch (Exception e) {
            return false;
        }
    }

    /** Sends the given content either as a single JSON completion or as an SSE stream. */
    private static void respondContent(io.vertx.ext.web.RoutingContext ctx, boolean streaming, String content,
            String finishReason) {
        if (streaming) {
            streamContent(ctx.response(), content, finishReason);
        } else {
            String body = buildCompletionBody(content, finishReason);
            ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(body);
        }
    }

    /**
     * Emits the content as a sequence of {@code chat.completion.chunk} SSE events, mirroring the
     * OpenAI streaming wire format, followed by a terminating {@code data: [DONE]} event.
     */
    private static void streamContent(HttpServerResponse response, String content, String finishReason) {
        long created = System.currentTimeMillis() / 1000;
        String id = "chatcmpl-mock-" + shortUuid();

        response.setStatusCode(200)
                .putHeader("Content-Type", "text/event-stream")
                .putHeader("Cache-Control", "no-cache")
                .setChunked(true);

        // First chunk announces the assistant role.
        response.write(sseEvent(streamChunk(id, created, new JsonObject().put("role", "assistant"), null)));

        // Content chunks split into fixed-size character segments so concatenation
        // reproduces the original content exactly, independent of whitespace.
        for (int i = 0; i < content.length(); i += STREAM_CHUNK_SIZE) {
            String piece = content.substring(i, Math.min(i + STREAM_CHUNK_SIZE, content.length()));
            response.write(sseEvent(streamChunk(id, created, new JsonObject().put("content", piece), null)));
        }

        // Final chunk carries the finish reason and an empty delta.
        response.write(sseEvent(streamChunk(id, created, new JsonObject(), finishReason)));
        response.write("data: [DONE]\n\n");
        response.end();
    }

    /**
     * Emits tool calls the way a real OpenAI-compatible server streams them: the first chunk of a
     * call carries its id and function name, later chunks carry successive slices of the argument
     * JSON. The slices are deliberately split mid-token so a client that simply concatenates them
     * per {@code index} is exercised properly.
     */
    private static void streamToolCalls(HttpServerResponse response, ToolCallsResponse toolCallsResponse) {
        long created = System.currentTimeMillis() / 1000;
        String id = "chatcmpl-mock-" + shortUuid();

        response.setStatusCode(200)
                .putHeader("Content-Type", "text/event-stream")
                .putHeader("Cache-Control", "no-cache")
                .setChunked(true);

        response.write(sseEvent(streamChunk(id, created, new JsonObject().put("role", "assistant"), null)));

        List<MockResponse.MockToolCall> calls = toolCallsResponse.toolCalls();
        for (int index = 0; index < calls.size(); index++) {
            MockResponse.MockToolCall call = calls.get(index);

            // Opening fragment: id + function name, no arguments yet.
            JsonObject head = new JsonObject()
                    .put("index", index)
                    .put("id", call.id())
                    .put("type", "function")
                    .put("function", new JsonObject().put("name", call.functionName()).put("arguments", ""));
            response.write(sseEvent(streamChunk(id, created,
                    new JsonObject().put("tool_calls", new JsonArray().add(head)), null)));

            // Argument fragments: only index + the argument slice, as on the wire.
            String arguments = call.argumentsJson();
            for (int i = 0; i < arguments.length(); i += STREAM_CHUNK_SIZE) {
                String piece = arguments.substring(i, Math.min(i + STREAM_CHUNK_SIZE, arguments.length()));
                JsonObject fragment = new JsonObject()
                        .put("index", index)
                        .put("function", new JsonObject().put("arguments", piece));
                response.write(sseEvent(streamChunk(id, created,
                        new JsonObject().put("tool_calls", new JsonArray().add(fragment)), null)));
            }
        }

        response.write(sseEvent(streamChunk(id, created, new JsonObject(), "tool_calls")));
        response.write("data: [DONE]\n\n");
        response.end();
    }

    private static String sseEvent(String json) {
        return "data: " + json + "\n\n";
    }

    private static String streamChunk(String id, long created, JsonObject delta, String finishReason) {
        JsonObject choice = new JsonObject()
                .put("index", 0)
                .put("delta", delta)
                .put("finish_reason", finishReason);
        return new JsonObject()
                .put("id", id)
                .put("object", "chat.completion.chunk")
                .put("created", created)
                .put("model", "mock-model")
                .put("choices", new JsonArray().add(choice))
                .encode();
    }

    // -------------------------------------------------------------------------
    // JSON builders
    // -------------------------------------------------------------------------

    private static String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String buildCompletionBody(String content, String finishReason) {
        long created = System.currentTimeMillis() / 1000;
        String id = "chatcmpl-mock-" + shortUuid();

        JsonObject message = new JsonObject()
                .put("role", "assistant")
                .put("content", content);

        JsonObject choice = new JsonObject()
                .put("index", 0)
                .put("message", message)
                .put("finish_reason", finishReason);

        JsonObject usage = new JsonObject()
                .put("prompt_tokens", 10)
                .put("completion_tokens", content.length() / 4 + 1)
                .put("total_tokens", 10 + content.length() / 4 + 1)
                .put("completion_tokens_details", new JsonObject()
                        .put("reasoning_tokens", 0));

        return new JsonObject()
                .put("id", id)
                .put("object", "chat.completion")
                .put("created", created)
                .put("model", "mock-model")
                .put("choices", new JsonArray().add(choice))
                .put("usage", usage)
                .encode();
    }

    private static String buildToolCallsBody(ToolCallsResponse toolCallsResponse) {
        long created = System.currentTimeMillis() / 1000;
        String id = "chatcmpl-mock-" + shortUuid();

        JsonArray toolCallsJson = new JsonArray();
        for (MockResponse.MockToolCall tc : toolCallsResponse.toolCalls()) {
            JsonObject function = new JsonObject()
                    .put("name", tc.functionName())
                    .put("arguments", tc.argumentsJson());
            toolCallsJson.add(new JsonObject()
                    .put("id", tc.id())
                    .put("type", "function")
                    .put("function", function));
        }

        JsonObject message = new JsonObject()
                .put("role", "assistant")
                .putNull("content")
                .put("tool_calls", toolCallsJson);

        JsonObject choice = new JsonObject()
                .put("index", 0)
                .put("message", message)
                .put("finish_reason", "tool_calls");

        JsonObject usage = new JsonObject()
                .put("prompt_tokens", 10)
                .put("completion_tokens", 0)
                .put("total_tokens", 10)
                .put("completion_tokens_details", new JsonObject()
                        .put("reasoning_tokens", 0));

        return new JsonObject()
                .put("id", id)
                .put("object", "chat.completion")
                .put("created", created)
                .put("model", "mock-model")
                .put("choices", new JsonArray().add(choice))
                .put("usage", usage)
                .encode();
    }

    private static String buildErrorBody(int statusCode, String type, String code, String message, String param) {
        JsonObject error = new JsonObject()
                .put("message", message)
                .put("type", type)
                .put("code", code);
        if (param != null) {
            error.put("param", param);
        }
        return new JsonObject().put("error", error).encode();
    }
}
