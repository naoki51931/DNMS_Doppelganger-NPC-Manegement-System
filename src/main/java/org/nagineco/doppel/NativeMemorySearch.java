package org.nagineco.doppel;

/** Optional native acceleration for large MIP indexes. Java fallback is always available. */
public final class NativeMemorySearch {
    private static final boolean AVAILABLE;
    static {
        boolean loaded = false;
        try {
            if (Boolean.getBoolean("doppel.nativeMemory")) {
                System.loadLibrary("dnms_memory");
                loaded = true;
            }
        } catch (UnsatisfiedLinkError | SecurityException ignored) { }
        AVAILABLE = loaded;
    }
    private NativeMemorySearch() {}
    public static boolean available() { return AVAILABLE; }
    public static native int nearestIndex(double[] packed, int count, int goal,
                                          double x, double y, double z, double radiusSquared);
}
