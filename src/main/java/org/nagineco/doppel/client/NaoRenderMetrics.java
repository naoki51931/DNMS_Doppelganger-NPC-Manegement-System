package org.nagineco.doppel.client;

import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;
import org.slf4j.LoggerFactory;

/** Measures successful renderer calls, not GPU frame time or FPS. */
public final class NaoRenderMetrics {
    private static final boolean ENABLED = Boolean.getBoolean("dnms.render.metrics");
    private static final long[] samples = new long[4096];
    private static final HashSet<UUID> entities = new HashSet<>();
    private static int count;
    private static long windowStart;
    public static long begin() { return ENABLED ? System.nanoTime() : 0L; }
    public static void end(UUID id, long start) {
        if (!ENABLED) return;
        long now = System.nanoTime();
        if (count == 0) windowStart = start;
        samples[count++] = now - start;
        entities.add(id);
        if (count == samples.length || now - windowStart >= 5_000_000_000L) {
            Arrays.sort(samples, 0, count);
            long p95 = samples[(int)Math.ceil(count * .95) - 1];
            var runtime = Runtime.getRuntime();
            LoggerFactory.getLogger("DNMS-render").info(
                "DNMS_RENDER_METRICS calls={} distinctNpc={} p95CpuUs={} maxCpuUs={} heapBytes={}",
                count, entities.size(), p95 / 1000.0, samples[count-1] / 1000.0,
                runtime.totalMemory() - runtime.freeMemory());
            count = 0;
            entities.clear();
        }
    }
}
