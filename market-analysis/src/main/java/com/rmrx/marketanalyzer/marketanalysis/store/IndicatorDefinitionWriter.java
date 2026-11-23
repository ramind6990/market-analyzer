package com.rmrx.marketanalyzer.marketanalysis.store;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.IndicatorDefinition;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.schema.ParamDef;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Saves the indicator types known to the code ({@code analysis.indicator_definition}), one row per formula version.
 * Existing rows are kept, so older versions stay documented after the code moved on.
 */
@Repository
public class IndicatorDefinitionWriter {

    private static final String INSERT = """
            INSERT INTO analysis.indicator_definition (type, version, family, description, params, outputs)
            VALUES (:type, :version, :family, :description, CAST(:params AS jsonb), CAST(:outputs AS jsonb))
            ON CONFLICT (type, version) DO NOTHING
            """;

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final JdbcClient jdbc;

    public IndicatorDefinitionWriter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void saveAll(Collection<IndicatorDefinition> definitions) {
        for (IndicatorDefinition definition : definitions) {
            jdbc.sql(INSERT)
                    .param("type", definition.type())
                    .param("version", definition.version())
                    .param("family", definition.family().name())
                    .param("description", definition.description() == null ? "" : definition.description())
                    .param("params", MAPPER.writeValueAsString(params(definition)))
                    .param("outputs", MAPPER.writeValueAsString(outputs(definition)))
                    .update();
        }
    }

    private static List<Map<String, Object>> params(IndicatorDefinition definition) {
        return definition.paramSchema().params().stream().map(IndicatorDefinitionWriter::param).toList();
    }

    private static Map<String, Object> param(ParamDef param) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("name", param.name());
        json.put("kind", param.kind().name());
        json.put("default", param.defaultValue() == null ? null : param.defaultValue().canonical());
        json.put("min", param.min());
        json.put("max", param.max());
        json.put("allowed", param.allowed().stream().sorted().toList());
        json.put("description", param.description());
        return json;
    }

    private static List<Map<String, Object>> outputs(IndicatorDefinition definition) {
        return definition.outputs().stream().map(IndicatorDefinitionWriter::output).toList();
    }

    private static Map<String, Object> output(OutputDef output) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("name", output.name());
        json.put("visibility", output.visibility().name());
        json.put("description", output.description());
        return json;
    }
}
