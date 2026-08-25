package io.github.majiajustar.codex.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/** Cursor-paginated model catalog. */
public record CodexModelList(List<CodexModel> data, String nextCursor, JsonNode raw) {
    public CodexModelList {
        data = List.copyOf(data);
    }

    /** Parse a {@code model/list} response. */
    public static CodexModelList from(JsonNode value) {
        var models = new ArrayList<CodexModel>();
        value.path("data").forEach(model -> models.add(CodexModel.from(model)));
        var cursor = value.get("nextCursor");
        return new CodexModelList(
                models, cursor == null || cursor.isNull() ? null : cursor.asText(), value);
    }
}
