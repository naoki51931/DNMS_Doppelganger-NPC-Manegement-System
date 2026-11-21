package org.nagineco.doppel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Read actual replicated episodic journal lines, not only the MIP movement index. */
public final class NaoMemoryRecall {
    private NaoMemoryRecall() {}
    public static Optional<String> recall(Path directory, int slot, String topic) throws IOException {
        return SharedOriginalMemory.recall(directory, slot, topic);
    }
}
