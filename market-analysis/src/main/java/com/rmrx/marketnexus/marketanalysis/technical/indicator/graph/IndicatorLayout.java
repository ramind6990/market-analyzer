package com.rmrx.marketnexus.marketanalysis.technical.indicator.graph;

import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.OutputRef;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Column order of the stored values of a series: every output of every node (public and internal), in graph order.
 * This one list drives the stored names ({@code indicator_layout.entries}), the written values and the seeds read back,
 * so position i means the same everywhere.
 * <p>
 * {@link #names()} are the stored names: canonical output, with {@code @version} after the type when the definition's
 * formula version is above 1. {@link #version()} is a hash of the names; stored rows with another version are never
 * used as a seed.
 */
public final class IndicatorLayout {

    private final List<OutputRef> entries;
    private final List<String> names;
    private final Map<OutputRef, Integer> positions = new HashMap<>();
    private final long version;

    public IndicatorLayout(List<OutputRef> entries, List<String> names) {
        if (entries.size() != names.size()) {
            throw new IllegalArgumentException("entries and names differ in size");
        }
        this.entries = List.copyOf(entries);
        this.names = List.copyOf(names);
        for (int i = 0; i < this.entries.size(); i++) {
            if (positions.put(this.entries.get(i), i) != null) {
                throw new IllegalArgumentException("duplicate layout entry " + this.entries.get(i));
            }
        }
        this.version = hash(this.names);
    }

    /** Stored name of an output: {@code RSI(period=14,source=CLOSE).value}, or {@code RSI@2(...).value} for version 2. */
    public static String name(OutputRef output, int definitionVersion) {
        String canonical = output.canonical();
        if (definitionVersion <= 1) {
            return canonical;
        }
        String type = output.spec().type();
        return type + "@" + definitionVersion + canonical.substring(type.length());
    }

    public List<OutputRef> entries() {
        return entries;
    }

    /** Stored names, same order as {@link #entries()}. */
    public List<String> names() {
        return names;
    }

    public int size() {
        return entries.size();
    }

    public long version() {
        return version;
    }

    /** Position of the output in a stored row, {@code -1} when not part of this layout. */
    public int indexOf(OutputRef output) {
        return positions.getOrDefault(output, -1);
    }

    private static long hash(List<String> names) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String name : names) {
                digest.update(name.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return ByteBuffer.wrap(digest.digest()).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String toString() {
        return "IndicatorLayout[version=" + version + ", entries=" + entries.size() + "]";
    }
}
