package io.github.majiajustar.codex.example.sse.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Sinks;

/** A bounded replay channel used to bridge Codex events to browser SSE clients. */
final class SseChannel implements AutoCloseable {
    private static final int HISTORY_LIMIT = 500;

    private final ObjectMapper mapper;
    private final String sessionId;
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Sinks.One<Void> closeSignal = Sinks.one();
    private final ReentrantLock lock = new ReentrantLock();
    private final ArrayDeque<String> history = new ArrayDeque<>();
    private final List<FluxSink<String>> subscribers = new ArrayList<>();

    SseChannel(ObjectMapper mapper, String sessionId) {
        this.mapper = mapper;
        this.sessionId = sessionId;
    }

    Flux<String> flux() {
        Flux<String> events = Flux.<String>create(sink -> {
            lock.lock();
            try {
                if (closed.get()) {
                    sink.complete();
                    return;
                }
                history.forEach(sink::next);
                subscribers.add(sink);
            } finally {
                lock.unlock();
            }
            sink.onDispose(() -> remove(sink));
        });
        Flux<String> heartbeat = Flux.interval(Duration.ofSeconds(15))
                .map(ignored -> encode("heartbeat", Map.of()));
        return Flux.merge(events, heartbeat).takeUntilOther(closeSignal.asMono());
    }

    void publish(String type, Object data) {
        String json = encode(type, data);
        lock.lock();
        try {
            if (closed.get()) return;
            history.addLast(json);
            while (history.size() > HISTORY_LIMIT) history.removeFirst();
            subscribers.removeIf(sink -> {
                if (sink.isCancelled()) return true;
                sink.next(json);
                return false;
            });
        } finally {
            lock.unlock();
        }
    }

    private String encode(String type, Object data) {
        LinkedHashMap<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("sequence", sequence.incrementAndGet());
        envelope.put("type", type);
        envelope.put("sessionId", sessionId);
        envelope.put("time", Instant.now().toString());
        envelope.put("data", data);
        try {
            return mapper.writeValueAsString(envelope);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot encode SSE event", error);
        }
    }

    private void remove(FluxSink<String> sink) {
        lock.lock();
        try {
            subscribers.remove(sink);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        closeSignal.tryEmitEmpty();
        lock.lock();
        try {
            subscribers.forEach(FluxSink::complete);
            subscribers.clear();
            history.clear();
        } finally {
            lock.unlock();
        }
    }
}
