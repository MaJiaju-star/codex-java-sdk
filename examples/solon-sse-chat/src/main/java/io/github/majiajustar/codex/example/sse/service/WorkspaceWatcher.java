package io.github.majiajustar.codex.example.sse.service;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/** Recursively reports workspace changes, including changes made by Codex shell commands. */
final class WorkspaceWatcher implements AutoCloseable {
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            ".git", ".idea", ".vscode", "node_modules", "target", "build", "dist");

    private final Path workspace;
    private final WatchService watchService;
    private final BiConsumer<String, String> listener;
    private final Map<WatchKey, Path> directories = new HashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread worker;

    WorkspaceWatcher(Path workspace, BiConsumer<String, String> listener) throws IOException {
        this.workspace = workspace;
        this.listener = listener;
        watchService = FileSystems.getDefault().newWatchService();
        registerTree(workspace);
        worker = Thread.ofVirtual().name("codex-workspace-watch-" + workspace.getFileName()).start(this::watch);
    }

    private void registerTree(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path directory : paths.filter(Files::isDirectory).filter(this::isObserved).toList()) {
                register(directory);
            }
        }
    }

    private void register(Path directory) throws IOException {
        WatchKey key = directory.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        directories.put(key, directory);
    }

    private void watch() {
        while (!closed.get()) {
            try {
                WatchKey key = watchService.take();
                Path directory = directories.get(key);
                if (directory == null) {
                    key.reset();
                    continue;
                }
                for (WatchEvent<?> event : key.pollEvents()) process(directory, event);
                if (!key.reset()) directories.remove(key);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException error) {
                listener.accept("watchError", Objects.toString(error.getMessage(), error.getClass().getSimpleName()));
            } catch (RuntimeException error) {
                if (!closed.get()) {
                    listener.accept("watchError", Objects.toString(error.getMessage(), error.getClass().getSimpleName()));
                }
            }
        }
    }

    private void process(Path directory, WatchEvent<?> event) throws IOException {
        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
            listener.accept("overflow", "");
            return;
        }
        @SuppressWarnings("unchecked")
        WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
        Path changed = directory.resolve(pathEvent.context()).normalize();
        if (!isObserved(changed)) return;
        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(changed)) {
            registerTree(changed);
        }
        listener.accept(kindName(event.kind()), workspace.relativize(changed).toString());
    }

    private boolean isObserved(Path path) {
        for (Path part : workspace.relativize(path)) {
            if (IGNORED_DIRECTORIES.contains(part.toString())) return false;
        }
        return true;
    }

    private static String kindName(WatchEvent.Kind<?> kind) {
        if (kind == StandardWatchEventKinds.ENTRY_CREATE) return "created";
        if (kind == StandardWatchEventKinds.ENTRY_MODIFY) return "modified";
        if (kind == StandardWatchEventKinds.ENTRY_DELETE) return "deleted";
        return kind.name().toLowerCase();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        worker.interrupt();
        try {
            watchService.close();
        } catch (IOException ignored) {
            // Nothing useful can be done while the owning chat session is closing.
        }
    }
}
