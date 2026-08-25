package io.github.majiajustar.codex.example.sse.controller;

import io.github.majiajustar.codex.exception.CodexTimeoutException;
import io.github.majiajustar.codex.exception.CodexTransportException;
import io.github.majiajustar.codex.exception.InvalidParamsException;
import io.github.majiajustar.codex.exception.JsonRpcException;
import io.github.majiajustar.codex.exception.ServerBusyException;
import io.github.majiajustar.codex.example.sse.model.ApiModels.ApprovalDecisionRequest;
import io.github.majiajustar.codex.example.sse.model.ApiModels.ApiError;
import io.github.majiajustar.codex.example.sse.model.ApiModels.CreateSessionRequest;
import io.github.majiajustar.codex.example.sse.model.ApiModels.SendMessageRequest;
import io.github.majiajustar.codex.example.sse.model.ApiModels.SessionRequest;
import io.github.majiajustar.codex.example.sse.model.ApiModels.SessionView;
import io.github.majiajustar.codex.example.sse.model.ApiModels.ThreadRequest;
import io.github.majiajustar.codex.example.sse.service.CodexSessionService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.noear.solon.annotation.Body;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Delete;
import org.noear.solon.annotation.Get;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.annotation.Param;
import org.noear.solon.annotation.Post;
import org.noear.solon.annotation.Produces;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.util.MimeType;
import reactor.core.publisher.Flux;

@Controller
@Mapping("/api/codex")
public final class ChatController {
    @Inject
    private CodexSessionService sessions;

    @Get
    @Mapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "workspaceRoot", sessions.workspaceRoot().toString(),
                "model", sessions.model(),
                "javaVersion", Runtime.version().toString());
    }

    @Post
    @Mapping("/sessions")
    public Object create(@Body CreateSessionRequest request) {
        try {
            return sessions.create(request);
        } catch (RuntimeException | IOException error) {
            return failure(error);
        }
    }

    @Get
    @Mapping("/sessions")
    public List<SessionView> list() {
        return sessions.list();
    }

    @Delete
    @Mapping("/sessions")
    public Object close(@Param String sessionId) {
        try {
            sessions.closeSession(sessionId);
            return Map.of("closed", true);
        } catch (RuntimeException error) {
            return failure(error);
        }
    }

    @Get
    @Mapping("/events")
    @Produces(MimeType.TEXT_EVENT_STREAM_UTF8_VALUE)
    public Flux<String> events(@Param String sessionId) {
        return sessions.events(sessionId);
    }

    @Get
    @Mapping("/history")
    public Object history(@Param String sessionId) {
        try {
            return sessions.history(sessionId);
        } catch (RuntimeException error) {
            return failure(error);
        }
    }

    @Post
    @Mapping("/messages")
    public Object send(@Body SendMessageRequest request) {
        try {
            return sessions.send(request);
        } catch (RuntimeException error) {
            return failure(error);
        }
    }

    @Post
    @Mapping("/interrupt")
    public Object interrupt(@Body SessionRequest request) {
        try {
            return sessions.interrupt(request.sessionId());
        } catch (RuntimeException error) {
            return failure(error);
        }
    }

    @Post
    @Mapping("/approvals")
    public Object approve(@Body ApprovalDecisionRequest request) {
        try {
            sessions.resolveApproval(
                    request.sessionId(), request.approvalId(), request.decision());
            return Map.of("resolved", true);
        } catch (RuntimeException error) {
            return failure(error);
        }
    }

    @Get
    @Mapping("/threads")
    public Object threads(
            @Param(required = false) String archived,
            @Param(required = false) String cursor,
            @Param(required = false) String limit,
            @Param(required = false) String searchTerm) {
        try {
            Boolean archivedValue = null;
            if (archived != null && !archived.isBlank()) {
                archivedValue = switch (archived.toLowerCase()) {
                    case "true" -> true;
                    case "false" -> false;
                    default -> throw new IllegalArgumentException("archived 必须是 true 或 false");
                };
            }
            Integer limitValue = limit == null || limit.isBlank()
                    ? null
                    : Integer.valueOf(limit);
            return sessions.listThreads(archivedValue, cursor, limitValue, searchTerm);
        } catch (RuntimeException error) {
            return failure(error);
        }
    }

    @Post
    @Mapping("/threads/archive")
    public Object archive(@Body ThreadRequest request) {
        try {
            return sessions.archiveThread(request.threadId());
        } catch (RuntimeException error) {
            return failure(error);
        }
    }

    @Post
    @Mapping("/threads/unarchive")
    public Object unarchive(@Body ThreadRequest request) {
        try {
            return sessions.unarchiveThread(request.threadId());
        } catch (RuntimeException error) {
            return failure(error);
        }
    }

    private static ApiError failure(Exception error) {
        Context context = Context.current();
        if (error instanceof ServerBusyException busy) {
            context.status(503);
            return new ApiError(busy.rpcMessage(), busy.getClass().getSimpleName(), busy.code(), true);
        }
        if (error instanceof InvalidParamsException invalid) {
            context.status(400);
            return new ApiError(invalid.rpcMessage(), invalid.getClass().getSimpleName(), invalid.code(), false);
        }
        if (error instanceof IllegalArgumentException) {
            context.status(400);
            return new ApiError(error.getMessage(), error.getClass().getSimpleName(), null, false);
        }
        if (error instanceof IllegalStateException) {
            context.status(409);
            return new ApiError(error.getMessage(), error.getClass().getSimpleName(), null, false);
        }
        if (error instanceof CodexTimeoutException || error instanceof CodexTransportException) {
            context.status(503);
            return new ApiError(error.getMessage(), error.getClass().getSimpleName(), null, false);
        }
        if (error instanceof JsonRpcException rpc) {
            context.status(502);
            return new ApiError(rpc.rpcMessage(), rpc.getClass().getSimpleName(), rpc.code(), false);
        }
        context.status(500);
        return new ApiError(error.getMessage(), error.getClass().getSimpleName(), null, false);
    }
}
