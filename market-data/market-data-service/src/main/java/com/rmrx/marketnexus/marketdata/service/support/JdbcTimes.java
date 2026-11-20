package com.rmrx.marketnexus.marketdata.service.support;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;

/** JDBC helpers for {@code timestamptz} columns. */
public final class JdbcTimes {

    private JdbcTimes() {
    }

    /** Reads a {@code timestamptz} column as an {@link Instant} ({@code null} stays {@code null}). */
    public static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
