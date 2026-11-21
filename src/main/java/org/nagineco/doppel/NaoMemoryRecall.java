package org.nagineco.doppel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Read actual replicated episodic journal lines, not only the MIP movement index. */
public final class NaoMemoryRecall {
    private NaoMemoryRecall() {}
    public static Optional<String> recall(Path directory, int slot, String topic) throws IOException {
        if (slot < 1 || slot > 256) return Optional.empty();
        Path file = directory.resolve(String.format(Locale.ROOT, "nao_%02d.md", slot));
        if (!Files.isRegularFile(file)) return Optional.empty();
        String term = topic.toLowerCase(Locale.ROOT);
        String best = null;
        int score = -1;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!(line.startsWith("- day=") || line.startsWith("- shared_from_nao="))) continue;
            int points = 0;
            for (String token : term.split("[ _-]+"))
                if (!token.isBlank() && line.toLowerCase(Locale.ROOT).contains(token)) points++;
            if (points > score) { score = points; best = line; }
        }
        return score > 0 ? Optional.of(best) : Optional.empty();
    }
}
