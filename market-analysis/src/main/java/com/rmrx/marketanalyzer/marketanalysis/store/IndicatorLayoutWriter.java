package com.rmrx.marketanalyzer.marketanalysis.store;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.graph.IndicatorGraph;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.graph.IndicatorLayout;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.graph.IndicatorNode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Saves the names of the stored values ({@code analysis.indicator_layout}): {@code entries[i]} names
 * {@code indicator_value.node_values[i]}, {@code internal[i]} marks hidden seed state. Idempotent: the version is a
 * hash of the names; a row written before {@code internal} existed gets it filled.
 */
@Repository
public class IndicatorLayoutWriter {

    private static final String UPSERT = """
            INSERT INTO analysis.indicator_layout (layout_version, entries, internal)
            VALUES (:layoutVersion, :entries, :internal)
            ON CONFLICT (layout_version) DO UPDATE SET internal = EXCLUDED.internal
                WHERE analysis.indicator_layout.internal IS NULL
            """;

    private final JdbcClient jdbc;

    public IndicatorLayoutWriter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void save(IndicatorGraph graph) {
        IndicatorLayout layout = graph.layout();
        jdbc.sql(UPSERT)
                .param("layoutVersion", layout.version())
                .param("entries", layout.names().toArray(String[]::new))
                .param("internal", internalFlags(graph))
                .update();
    }

    /** Same order as the layout: nodes in graph order, outputs in definition order. */
    private static Boolean[] internalFlags(IndicatorGraph graph) {
        List<Boolean> flags = new ArrayList<>();
        for (IndicatorNode node : graph.nodes()) {
            for (OutputDef output : node.definition().outputs()) {
                flags.add(!output.isPublic());
            }
        }
        if (flags.size() != graph.layout().size()) {
            throw new IllegalStateException("internal flags do not match layout " + graph.layout());
        }
        return flags.toArray(Boolean[]::new);
    }
}
