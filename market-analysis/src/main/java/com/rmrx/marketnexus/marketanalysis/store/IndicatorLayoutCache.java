package com.rmrx.marketnexus.marketanalysis.store;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stored layouts ({@code analysis.indicator_layout}) by version, loaded on first use. A layout never changes once
 * written, so entries are cached for good.
 */
@Repository
public class IndicatorLayoutCache {

    private static final String SELECT = """
            SELECT entries, internal FROM analysis.indicator_layout WHERE layout_version = :layoutVersion
            """;

    private final JdbcClient jdbc;
    private final Map<Long, StoredLayout> layouts = new ConcurrentHashMap<>();

    public IndicatorLayoutCache(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public StoredLayout get(long layoutVersion) {
        return layouts.computeIfAbsent(layoutVersion, this::load);
    }

    private StoredLayout load(long layoutVersion) {
        Optional<StoredLayout> layout = jdbc.sql(SELECT)
                .param("layoutVersion", layoutVersion)
                .query((rs, n) -> new StoredLayout((String[]) rs.getArray("entries").getArray(),
                        internal(rs.getArray("internal"))))
                .optional();
        return layout.orElseThrow(() -> new IllegalStateException("unknown layout version " + layoutVersion));
    }

    private static boolean[] internal(Array array) throws SQLException {
        if (array == null) {
            return null;
        }
        Boolean[] boxed = (Boolean[]) array.getArray();
        boolean[] flags = new boolean[boxed.length];
        for (int i = 0; i < boxed.length; i++) {
            flags[i] = Boolean.TRUE.equals(boxed[i]);
        }
        return flags;
    }

    /**
     * @param entries  name of each stored value, in {@code node_values} order
     * @param internal per entry: hidden seed state; {@code null} when unknown (layout row older than the flags)
     */
    public record StoredLayout(String[] entries, boolean[] internal) {

        public boolean isInternal(int index) {
            return internal != null && internal[index];
        }
    }
}
