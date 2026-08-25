package io.github.majiajustar.codex.example.sse.model;

import java.util.List;

public final class ApiModels {
    private ApiModels() {}

    public record CreateSessionRequest(
            String name,
            String workspace,
            String threadId,
            String personality,
            String serviceTier) {}

    public record SendMessageRequest(
            String sessionId,
            String message,
            String reasoningEffort,
            String reasoningSummary) {}

    public record SessionRequest(String sessionId) {}

    public record ThreadRequest(String threadId) {}

    public record ApprovalDecisionRequest(String sessionId, String approvalId, String decision) {}

    public record HistoryView(String sessionId, String threadId, List<HistoryTurn> turns) {}

    public record HistoryTurn(
            String id,
            String status,
            String error,
            Long startedAt,
            Long completedAt,
            Long durationMs,
            List<HistoryItem> items) {}

    public record HistoryItem(
            String id,
            String type,
            String phase,
            String text,
            String command,
            String workingDirectory,
            String status,
            String output) {}

    public record SessionView(
            String id,
            String name,
            String threadId,
            String workspace,
            boolean running,
            String activeTurnId,
            String createdAt,
            String updatedAt) {}

    public record TurnAccepted(String sessionId, String turnId) {}

    public record ApiError(String error, String type, Integer code, boolean retryable) {
        public ApiError(String error) {
            this(error, "validation", null, false);
        }
    }

    public record BashPath(String token, String resolvedPath, boolean insideWorkspace) {}

    public record BashObservation(String operation, String command, List<BashPath> paths) {}
}
