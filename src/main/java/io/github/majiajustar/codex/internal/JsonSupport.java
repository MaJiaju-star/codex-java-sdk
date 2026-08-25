package io.github.majiajustar.codex.internal;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Shared JSON codec used by the SDK implementation. */
public final class JsonSupport {
    public static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonSupport() {}
}
