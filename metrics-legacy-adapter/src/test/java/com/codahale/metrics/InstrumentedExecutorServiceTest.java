package com.codahale.metrics;

import org.junit.After;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class InstrumentedExecutorServiceTest {

    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    @After
    public void tearDown() {
        executorService.shutdown();
    }

    @Test
    public void testCreate() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        InstrumentedExecutorService instrumentedExecutorService = new InstrumentedExecutorService(executorService,
                registry, "test-instrumented");
        CountDownLatch countDownLatch = new CountDownLatch(10);
        for (int i = 0; i < 10; i++) {
            instrumentedExecutorService.submit(countDownLatch::countDown);
        }
        countDownLatch.await(5, TimeUnit.SECONDS);

        assertThat(registry.getMetrics()).containsOnlyKeys("test-instrumented.completed",
                "test-instrumented.submitted", "test-instrumented.duration", "test-instrumented.idle",
                "test-instrumented.running");
        assertThat(registry.meter("test-instrumented.completed").getCount()).isEqualTo(10);
        assertThat(registry.meter("test-instrumented.submitted").getCount()).isEqualTo(10);
        assertThat(registry.counter("test-instrumented.running").getCount()).isEqualTo(0);
        assertThat(registry.timer("test-instrumented.duration").getCount()).isEqualTo(10);
        assertThat(registry.timer("test-instrumented.idle").getCount()).isEqualTo(10);
    }
}