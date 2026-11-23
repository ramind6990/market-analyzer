package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block.Seed;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.OutputRef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.Source;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/** What a definition gets while building its ta4j indicators for one job. */
public interface BuildContext {

    /** Candles of the job: lookback bars before the first new bar, then the new bars. */
    BarSeries series();

    /** Price field indicator, or the already built output of a dependency. */
    Indicator<Num> source(Source source);

    /** Stored values of one output of the indicator being built; {@link Seed#none()} without a valid seed. */
    Seed seed(OutputRef output);

    default Seed seed(IndicatorSpec spec, String output) {
        return seed(spec.output(output));
    }
}
