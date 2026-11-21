package org.nagineco.doppel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** One original journal per Nao. Midnight publishes an atomic per-owner line-count watermark. */
public final class SharedOriginalMemory {
    private SharedOriginalMemory() {}
    private static final String INDEX = "shared_memory_index.properties";
    private static Path journal(Path dir, int slot) {
        if (slot < 1 || slot > 256) throw new IllegalArgumentException("Nao slot");
        return dir.resolve(String.format(Locale.ROOT, "nao_%02d.md", slot));
    }
    public static void publish(Path dir, int activeCount) throws IOException {
        if (activeCount < 0 || activeCount > 256) throw new IOException("Invalid active Nao count");
        Properties index = new Properties();
        index.setProperty("version", "1");
        index.setProperty("active", Integer.toString(activeCount));
        for (int slot=1; slot<=activeCount; slot++) {
            Path path=journal(dir,slot);
            if (!Files.isRegularFile(path)) throw new IOException("Missing original journal: " + path);
            long count;
            try (var lines=Files.lines(path, StandardCharsets.UTF_8)) {
                count=lines.filter(line -> !line.startsWith("- shared_from_nao=")).count();
            }
            index.setProperty("nao."+slot, Long.toString(count));
        }
        Path temp=Files.createTempFile(dir, "shared-index-", ".tmp");
        try {
            try (var out=Files.newOutputStream(temp)) { index.store(out, "DNMS midnight shared original memory watermarks"); }
            try { Files.move(temp, dir.resolve(INDEX), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, dir.resolve(INDEX), StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    public static Optional<String> recall(Path dir, int requester, String topic) throws IOException {
        if (requester < 1 || requester > 256 || topic == null || topic.isBlank()) return Optional.empty();
        Properties index=new Properties();
        Path indexFile=dir.resolve(INDEX);
        if (Files.isRegularFile(indexFile))
            try (var in=Files.newInputStream(indexFile)) { index.load(in); }
        int active;
        try { active=Math.min(256,Math.max(0,Integer.parseInt(index.getProperty("active","0")))); }
        catch (NumberFormatException ex) { throw new IOException("Malformed shared memory index",ex); }
        String[] terms=topic.toLowerCase(Locale.ROOT).split("[ _-]+");
        String best=null; int bestScore=0;
        for (int slot=1; slot<=Math.max(active,requester); slot++) {
            if (slot!=requester && slot>active) continue;
            long limit=Long.MAX_VALUE;
            if (slot!=requester) {
                try { limit=Long.parseLong(index.getProperty("nao."+slot,"0")); }
                catch (NumberFormatException ex) { throw new IOException("Malformed memory watermark",ex); }
            }
            Path path=journal(dir,slot);
            if (!Files.isRegularFile(path)) continue;
            try (var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
                String line; long seen=0; boolean legacyShared=false;
                while (seen<limit && (line=reader.readLine())!=null) {
                    if (line.equals("<!-- DNMS_SHARED_COPY_BEGIN -->")) { legacyShared=true; continue; }
                    if (line.equals("<!-- DNMS_SHARED_COPY_END -->")) { legacyShared=false; continue; }
                    if (legacyShared || line.startsWith("- shared_from_nao=")) continue;
                    seen++;
                    if (!line.startsWith("- day=")) continue;
                    int score=0; String lower=line.toLowerCase(Locale.ROOT);
                    for (String term:terms) if (!term.isBlank() && lower.contains(term)) score++;
                    if (score>bestScore) { bestScore=score; best="source_nao="+slot+" "+line; }
                }
            }
        }
        return Optional.ofNullable(best);
    }
}
