package io.github.majiajustar.codex;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** Cooperative cancellation signal that can interrupt one or more running Codex turns. */
public final class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final CopyOnWriteArrayList<Runnable> callbacks = new CopyOnWriteArrayList<>();

    /** Request cancellation once. Repeated calls have no effect. */
    public boolean cancel() {
        if (!cancelled.compareAndSet(false, true)) return false;
        callbacks.forEach(Runnable::run);
        callbacks.clear();
        return true;
    }

    /** Return whether cancellation has been requested. */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /** Throw when cancellation has already been requested. */
    public void throwIfCancelled() {
        if (isCancelled()) throw new CancellationException("Codex operation was cancelled");
    }

    Registration onCancel(Runnable callback) {
        if (isCancelled()) {
            callback.run();
            return () -> {};
        }
        callbacks.add(callback);
        if (isCancelled() && callbacks.remove(callback)) callback.run();
        return () -> callbacks.remove(callback);
    }

    @FunctionalInterface
    interface Registration extends AutoCloseable {
        @Override
        void close();
    }
}
