package io.github.majiajustar.codex.example.sse.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.majiajustar.codex.example.sse.model.ApiModels.BashObservation;
import io.github.majiajustar.codex.example.sse.model.ApiModels.BashPath;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Best-effort observability for shell commands; this class is not a security boundary. */
final class BashCommandMonitor {
    private static final Set<String> READ_COMMANDS = Set.of(
            "cat", "type", "head", "tail", "less", "more", "rg", "grep", "find", "ls", "dir", "get-content");
    private static final Set<String> CREATE_COMMANDS = Set.of(
            "touch", "mkdir", "new-item");
    private static final Set<String> DELETE_COMMANDS = Set.of(
            "rm", "rmdir", "del", "erase", "remove-item");
    private static final Set<String> EDIT_COMMANDS = Set.of(
            "sed", "perl", "tee", "set-content", "add-content");
    private static final Set<String> COPY_COMMANDS = Set.of(
            "cp", "copy", "copy-item");
    private static final Set<String> MOVE_COMMANDS = Set.of(
            "mv", "move", "move-item", "rename-item");

    private BashCommandMonitor() {}

    static BashObservation inspect(JsonNode item, Path workspace) {
        String command = commandText(item.path("command"));
        List<String> tokens = tokenize(command);
        String executable = tokens.isEmpty() ? "" : basename(tokens.getFirst()).toLowerCase(Locale.ROOT);
        String operation = classify(executable);
        ArrayList<BashPath> paths = new ArrayList<>();
        for (int index = 1; index < tokens.size(); index++) {
            String token = cleanRedirection(tokens.get(index));
            if (!looksLikePath(token)) continue;
            try {
                Path path = Path.of(token);
                Path resolved = (path.isAbsolute() ? path : workspace.resolve(path)).normalize().toAbsolutePath();
                paths.add(new BashPath(token, resolved.toString(), resolved.startsWith(workspace)));
            } catch (InvalidPathException ignored) {
                // Shell syntax and expansions are intentionally ignored by this observer.
            }
        }
        return new BashObservation(operation, command, List.copyOf(paths));
    }

    private static String commandText(JsonNode command) {
        if (command.isArray()) {
            ArrayList<String> parts = new ArrayList<>();
            command.forEach(node -> parts.add(node.asText()));
            return String.join(" ", parts);
        }
        return command.asText("");
    }

    private static String classify(String executable) {
        if (READ_COMMANDS.contains(executable)) return "read";
        if (CREATE_COMMANDS.contains(executable)) return "create";
        if (DELETE_COMMANDS.contains(executable)) return "delete";
        if (EDIT_COMMANDS.contains(executable)) return "edit";
        if (COPY_COMMANDS.contains(executable)) return "copy";
        if (MOVE_COMMANDS.contains(executable)) return "move";
        return "execute";
    }

    private static String basename(String executable) {
        String normalized = executable.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash < 0 ? normalized : normalized.substring(slash + 1);
    }

    private static boolean looksLikePath(String token) {
        if (token.isBlank()
                || token.startsWith("-")
                || token.contains("://")
                || token.equals("|")
                || token.equals("&&")
                || token.equals("||")
                || token.contains("=")
                || token.startsWith("$")
                || token.startsWith("%")) {
            return false;
        }
        return token.contains("/")
                || token.contains("\\")
                || token.startsWith(".")
                || token.matches("^[A-Za-z]:.*")
                || token.matches(".*\\.[A-Za-z0-9_-]{1,12}$");
    }

    private static String cleanRedirection(String token) {
        String value = token;
        while (value.startsWith(">") || value.startsWith("<")) value = value.substring(1);
        return value;
    }

    private static List<String> tokenize(String command) {
        ArrayList<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int index = 0; index < command.length(); index++) {
            char character = command.charAt(index);
            if (quote != 0) {
                if (character == quote) {
                    quote = 0;
                } else {
                    current.append(character);
                }
            } else if (character == '\'' || character == '"') {
                quote = character;
            } else if (Character.isWhitespace(character)) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(character);
            }
        }
        if (!current.isEmpty()) tokens.add(current.toString());
        return tokens;
    }
}
