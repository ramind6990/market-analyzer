package com.rmrx.marketnexus.marketanalysis.technical.indicator.definition;

/** How an indicator depends on history (catalog column "Model"). A type can have several. */
public enum ComputationModel {
    /** Depends only on the current (and maybe previous) bar. */
    STATELESS,
    /** Depends only on the last N bars; exact once N bars exist. */
    WINDOW,
    /** value = f(previous value, new bar); converges, needs warm-up or a seed. */
    RECURSIVE,
    /** Running total from an anchor; the absolute level depends on the anchor. */
    CUMULATIVE,
    /** Built from other indicators. */
    COMPOSITE,
    /** Later bars change or confirm earlier values; does not fit the append-only model. */
    REPAINTING
}
