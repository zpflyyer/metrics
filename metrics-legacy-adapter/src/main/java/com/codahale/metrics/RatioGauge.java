package com.codahale.metrics;

@Deprecated
public abstract class RatioGauge implements Gauge<Double> {

    public static class Ratio {

        private io.dropwizard.metrics5.RatioGauge.Ratio delegate;

        public static Ratio of(double numerator, double denominator) {
            return new Ratio(io.dropwizard.metrics5.RatioGauge.Ratio.of(numerator, denominator));
        }

        public Ratio(io.dropwizard.metrics5.RatioGauge.Ratio delegate) {
            this.delegate = delegate;
        }

        public double getValue() {
            return delegate.getValue();
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }

    protected abstract Ratio getRatio();

    @Override
    public Double getValue() {
        return getRatio().getValue();
    }
}
