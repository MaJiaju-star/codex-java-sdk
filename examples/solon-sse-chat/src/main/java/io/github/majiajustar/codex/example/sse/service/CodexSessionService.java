package io.github.majiajustar.codex.example.sse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.majiajustar.codex.CodexClient;
import io.github.majiajustar.codex.CodexClientConfig;
import io.github.majiajustar.codex.CodexThread;
import io.github.majiajustar.codex.RetryPolicy;
import io.github.majiajustar.codex.thread.ThreadListOptions;
import io.github.majiajustar.codex.thread.ThreadOptions;
import io.github.majiajustar.codex.tool.ApprovalRequest;
import io.github.majiajustar.codex.tool.ToolCallContext;
import io.github.majiajustar.codex.tool.ToolCallResult;
import io.github.majiajustar.codex.tool.ToolObserver;
import io.github.majiajustar.codex.example.sse.model.ApiModels.CreateSessionRequest;
import io.github.majiajustar.codex.example.sse.model.ApiModels.HistoryView;
import io.github.majiajustar.codex.example.sse.model.ApiModels.SendMessageRequest;
import io.github.majiajustar.codex.example.sse.model.ApiModels.SessionView;
import io.github.majiajustar.codex.example.sse.model.ApiModels.TurnAccepted;
import io.github.majiajustar.codex.generated.v2.Personality;
import io.github.majiajustar.codex.generated.v2.SortDirection;
import io.github.majiajustar.codex.generated.v2.ThreadArchiveResponse;
import io.github.majiajustar.codex.generated.v2.ThreadListResponse;
import io.github.majiajustar.codex.generated.v2.ThreadSortKey;
import io.github.majiajustar.codex.generated.v2.ThreadUnarchiveResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Destroy;
import reactor.core.publisher.Flux;

@Component
public final class CodexSessionService {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentHashMap<String, ChatSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ChatSession> sessionsByThreadId = new ConcurrentHashMap<>();
    private final Path workspaceRoot = configuredWorkspaceRoot();
    private final String model = configuredModel();
    private volatile CodexClient client;

    public SessionView create(CreateSessionRequest request) throws IOException {
        Path workspace = resolveWorkspace(request.workspace());
        String name = request.name() == null || request.name().isBlank()
                ? "会话 " + (sessions.size() + 1)
                : request.name().strip();
        Personality personality = request.personality() == null || request.personality().isBlank()
                ? Personality.PRAGMATIC
                : Personality.fromWireValue(request.personality().strip());
        String serviceTier = request.serviceTier() == null || request.serviceTier().isBlank()
                ? null
                : request.serviceTier().strip();
        ThreadOptions options = ThreadOptions.builder()
                .model(model)
                .workingDirectory(workspace)
                .sandbox(ThreadOptions.Sandbox.WORKSPACE_WRITE)
                .approvalPolicy(ThreadOptions.ApprovalPolicy.ON_REQUEST)
                .personality(personality)
                .serviceName("solon-sse-chat")
                .serviceTier(serviceTier)
                .threadSource("java-sdk-solon-example")
                .build();
        CodexThread thread = request.threadId() == null || request.threadId().isBlank()
                ? client().startThread(options)
                : client().resumeThread(request.threadId().strip(), options);

        String id = UUID.randomUUID().toString();
        ChatSession session = new ChatSession(id, name, thread, workspace, mapper);
        sessions.put(id, session);
        sessionsByThreadId.put(thread.id(), session);
        return session.view();
    }

    public List<SessionView> list() {
        return sessions.values().stream()
                .map(ChatSession::view)
                .sorted(Comparator.comparing(SessionView::createdAt))
                .toList();
    }

    public Flux<String> events(String sessionId) {
        return required(sessionId).events();
    }

    public HistoryView history(String sessionId) {
        return required(sessionId).history();
    }

    public TurnAccepted send(SendMessageRequest request) {
        return required(request.sessionId()).send(request);
    }

    public SessionView interrupt(String sessionId) {
        ChatSession session = required(sessionId);
        session.interrupt();
        return session.view();
    }

    public void resolveApproval(String sessionId, String approvalId, String decision) {
        required(sessionId).resolveApproval(approvalId, parseDecision(decision));
    }

    public void closeSession(String sessionId) {
        ChatSession session = sessions.remove(sessionId);
        if (session == null) throw new IllegalArgumentException("会话不存在: " + sessionId);
        sessionsByThreadId.remove(session.threadId(), session);
        session.close();
    }

    public ThreadListResponse listThreads(
            Boolean archived,
            String cursor,
            Integer limit,
            String searchTerm) {
        ThreadListOptions.Builder builder = ThreadListOptions.builder()
                .sortKey(ThreadSortKey.RECENCY_AT)
                .sortDirection(SortDirection.DESC);
        if (archived != null) builder.archived(archived);
        if (cursor != null && !cursor.isBlank()) builder.cursor(cursor.strip());
        if (limit != null) builder.limit(Math.min(Math.max(limit, 1), 100));
        if (searchTerm != null && !searchTerm.isBlank()) builder.searchTerm(searchTerm.strip());
        return client().listThreads(builder.build());
    }

    public ThreadArchiveResponse archiveThread(String threadId) {
        if (threadId == null || threadId.isBlank()) throw new IllegalArgumentException("threadId 不能为空");
        String normalized = threadId.strip();
        ChatSession session = sessionsByThreadId.get(normalized);
        if (session != null && session.running()) {
            throw new IllegalStateException("归档前请先等待 Turn 完成或主动打断");
        }
        ThreadArchiveResponse response = client().archiveThread(normalized);
        if (session != null) {
            sessions.remove(session.id(), session);
            sessionsByThreadId.remove(normalized, session);
            session.close();
        }
        return response;
    }

    public ThreadUnarchiveResponse unarchiveThread(String threadId) {
        if (threadId == null || threadId.isBlank()) throw new IllegalArgumentException("threadId 不能为空");
        return client().unarchiveThread(threadId.strip());
    }

    public Path workspaceRoot() {
        return workspaceRoot;
    }

    public String model() {
        return model;
    }

    private ChatSession required(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("sessionId 不能为空");
        ChatSession session = sessions.get(sessionId);
        if (session == null) throw new IllegalArgumentException("会话不存在: " + sessionId);
        return session;
    }

    private Path resolveWorkspace(String requested) throws IOException {
        Path path = requested == null || requested.isBlank() ? workspaceRoot : Path.of(requested.strip());
        Path resolved = (path.isAbsolute() ? path : workspaceRoot.resolve(path)).normalize().toAbsolutePath();
        if (!resolved.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("工作目录必须位于允许的根目录内: " + workspaceRoot);
        }
        if (!Files.isDirectory(resolved)) throw new IllegalArgumentException("工作目录不存在: " + resolved);
        return resolved.toRealPath();
    }

    private synchronized CodexClient client() {
        if (client == null) {
            client = CodexClient.create(CodexClientConfig.builder()
                    .requestTimeout(Duration.ofMinutes(10))
                    .retryPolicy(RetryPolicy.overloadDefaults())
                    .approvalHandler(this::requestApproval)
                    .toolObserver(new BrowserToolObserver())
                    .build());
        }
        return client;
    }

    private static Path configuredWorkspaceRoot() {
        String configured = System.getenv("CODEX_EXAMPLE_WORKSPACE_ROOT");
        Path path = configured == null || configured.isBlank() ? Path.of("") : Path.of(configured);
        try {
            return path.toAbsolutePath().normalize().toRealPath();
        } catch (IOException error) {
            throw new IllegalStateException("CODEX_EXAMPLE_WORKSPACE_ROOT 不是有效目录: " + path, error);
        }
    }

    private static String configuredModel() {
        String configured = System.getenv("CODEX_EXAMPLE_MODEL");
        return configured == null || configured.isBlank() ? "gpt-5.5" : configured.strip();
    }

    private CompletionStage<ApprovalRequest.Decision> requestApproval(ApprovalRequest request) {
        ChatSession session = sessionsByThreadId.get(request.context().threadId());
        if (session == null) {
            return CompletableFuture.completedFuture(ApprovalRequest.Decision.DECLINE);
        }
        return session.requestApproval(request);
    }

    private static ApprovalRequest.Decision parseDecision(String value) {
        if (value == null) throw new IllegalArgumentException("decision 不能为空");
        return switch (value) {
            case "accept" -> ApprovalRequest.Decision.ACCEPT;
            case "acceptForSession" -> ApprovalRequest.Decision.ACCEPT_FOR_SESSION;
            case "decline" -> ApprovalRequest.Decision.DECLINE;
            case "cancel" -> ApprovalRequest.Decision.CANCEL;
            default -> throw new IllegalArgumentException("不支持的审批决定: " + value);
        };
    }

    @Destroy
    public void close() {
        sessions.values().forEach(ChatSession::close);
        sessions.clear();
        sessionsByThreadId.clear();
        CodexClient runningClient = client;
        if (runningClient != null) runningClient.close();
    }

    private final class BrowserToolObserver implements ToolObserver {
        @Override
        public void onStarted(ToolCallContext context) {
            ChatSession session = sessionsByThreadId.get(context.threadId());
            if (session != null) session.toolStarted(context);
        }

        @Override
        public void onOutput(ToolCallContext context, String delta) {
            ChatSession session = sessionsByThreadId.get(context.threadId());
            if (session != null) session.toolOutput(context, delta);
        }

        @Override
        public void onCompleted(ToolCallResult result) {
            ChatSession session = sessionsByThreadId.get(result.context().threadId());
            if (session != null) session.toolCompleted(result);
        }
    }
}
