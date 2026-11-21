package org.nagineco.doppel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Publish a complete JSON + Markdown generation by atomically changing CURRENT.
 * Incomplete generation directories are ignored on recovery.
 */
public final class GenerationCheckpointStore {
    private GenerationCheckpointStore() {}
    private static final String[] MEMORY_FILES = makeMemoryFiles();
    private static String[] makeMemoryFiles() {
        List<String> names = new ArrayList<>();
        names.add("nao_personality.md");
        for (int i = 1; i <= 24; i++) names.add(String.format(java.util.Locale.ROOT, "nao_%02d.md", i));
        return names.toArray(new String[0]);
    }
    public static Path latest(Path root) throws IOException {
        Path marker = root.resolve("CURRENT");
        if (!Files.exists(marker)) return null;
        String id = Files.readString(marker, StandardCharsets.UTF_8).trim();
        if (!id.matches("generation_[0-9]{12,20}")) throw new IOException("Invalid checkpoint marker");
        Path generation = root.resolve(id);
        if (!Files.isRegularFile(generation.resolve("COMPLETE"))
                || !Files.isRegularFile(generation.resolve("recordings.json")))
            throw new IOException("Incomplete checkpoint generation " + id);
        for (String name : MEMORY_FILES)
            if (!Files.isRegularFile(generation.resolve("memories").resolve(name)))
                throw new IOException("Missing memory " + name);
        return generation;
    }
    public static void publish(Path root, Path json, Path memories, Path playerState) throws IOException {
        Files.createDirectories(root);
        String id = String.format(java.util.Locale.ROOT, "generation_%016d", System.nanoTime() & Long.MAX_VALUE);
        Path generation = root.resolve(id);
        Path memoryDest = generation.resolve("memories");
        Files.createDirectories(memoryDest);
        Files.copy(json, generation.resolve("recordings.json"));
        for (String name : MEMORY_FILES)
            Files.copy(memories.resolve(name), memoryDest.resolve(name));
        if (playerState != null) Files.copy(playerState, generation.resolve("player.json"));
        Files.writeString(generation.resolve("COMPLETE"), "v1\n", StandardCharsets.UTF_8);
        Path temp = root.resolve("CURRENT.tmp");
        try {
            Files.writeString(temp, id + "\n", StandardCharsets.UTF_8);
            try {
                Files.move(temp, root.resolve("CURRENT"),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, root.resolve("CURRENT"), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }
    public static void restoreMemories(Path generation, Path target) throws IOException {
        Files.createDirectories(target);
        for (String name : MEMORY_FILES)
            Files.copy(generation.resolve("memories").resolve(name), target.resolve(name),
                StandardCopyOption.REPLACE_EXISTING);
    }
}
