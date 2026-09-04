package io.github.majiajustar.codex.config;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 最终生效配置、字段来源以及可选的完整配置层。 */
public record ConfigSnapshot(
        JsonNode config,
        Map<String, ConfigLayerMetadata> origins,
        List<ConfigLayer> layers,
        JsonNode raw) {
    public ConfigSnapshot {
        origins = Map.copyOf(origins);
        layers = List.copyOf(layers);
    }

    static ConfigSnapshot from(JsonNode value) {
        var origins = new LinkedHashMap<String, ConfigLayerMetadata>();
        value.path("origins").properties().forEach(entry ->
                origins.put(entry.getKey(), ConfigLayerMetadata.from(entry.getValue())));
        var layers = new ArrayList<ConfigLayer>();
        value.path("layers").forEach(layer -> layers.add(ConfigLayer.from(layer)));
        return new ConfigSnapshot(value.path("config"), origins, layers, value);
    }
}
