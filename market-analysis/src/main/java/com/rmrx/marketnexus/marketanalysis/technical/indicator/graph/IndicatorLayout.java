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
 * {@link #version()} is a hash of that order; stored rows with another version cannot be used as a seed.
 */
public final class IndicatorLayout {

    private final List<OutputRef> entries;
    private final Map<OutputRef, Integer> positions = new HashMap<>();
    private final long version;

    public IndicatorLayout(List<OutputRef> entries) {
        this.entries = List.copyOf(entries);
        for (int i = 0; i < this.entries.size(); i++) {
            if (positions.put(this.entries.get(i), i) != null) {
                throw new IllegalArgumentException("duplicate layout entry " + this.entries.get(i));
            }
        }
        this.version = hash(this.entries);
    }

    public List<OutputRef> entries() {
        return entries;
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

    private static long hash(List<OutputRef> entries) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (OutputRef entry : entries) {
                digest.update(entry.canonical().getBytes(StandardCharsets.UTF_8));
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
