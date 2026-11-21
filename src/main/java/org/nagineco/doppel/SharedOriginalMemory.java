package org.nagineco.doppel;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Original journals + atomic midnight inverted keyword index with byte-offset postings. */
public final class SharedOriginalMemory {
    private SharedOriginalMemory() {}
    private static final String INDEX = "shared_memory_index.properties";
    private static final String KEYWORDS = "shared_memory_keywords.tsv";
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}_]+");
    private record Hit(int slot, long offset) {}
    private static Path journal(Path dir, int slot) {
        if (slot < 1 || slot > 256) throw new IllegalArgumentException("Nao slot");
        return dir.resolve(String.format(Locale.ROOT, "nao_%02d.md", slot));
    }
    private static String decode(String line) {
        return new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
    }
    private static Set<String> words(String value) {
        Set<String> result = new HashSet<>();
        Matcher m = WORD.matcher(value.toLowerCase(Locale.ROOT));
        while (m.find()) {
            String token = m.group();
            result.add(token);
            // Japanese word boundaries are not whitespace-delimited.
            if (token.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN
                    || Character.UnicodeScript.of(c) == Character.UnicodeScript.HIRAGANA
                    || Character.UnicodeScript.of(c) == Character.UnicodeScript.KATAKANA)) {
                int[] cp = token.codePoints().toArray();
                for (int width = 2; width <= 3; width++)
                    for (int i = 0; i + width <= cp.length; i++)
                        result.add(new String(cp, i, width));
            }
        }
        return result;
    }
    private static String encode(String word) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(word.getBytes(StandardCharsets.UTF_8));
    }
    private static String readHit(Path dir, Hit hit) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(journal(dir, hit.slot).toFile(), "r")) {
            file.seek(hit.offset);
            String line = file.readLine();
            return line == null ? "" : decode(line);
        }
    }
    public static void publish(Path dir, int activeCount) throws IOException {
        if (activeCount < 0 || activeCount > 256) throw new IOException("Invalid active Nao count");
        Files.createDirectories(dir);
        Properties index = new Properties();
        index.setProperty("version", "3");
        index.setProperty("active", Integer.toString(activeCount));
        Map<String, List<Hit>> inverted = new TreeMap<>();
        for (int slot = 1; slot <= activeCount; slot++) {
            Path path = journal(dir, slot);
            if (!Files.isRegularFile(path)) throw new IOException("Missing original journal: " + path);
            long count = 0;
            boolean copied = false;
            try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
                while (true) {
                    long offset = file.getFilePointer();
                    String raw = file.readLine();
                    if (raw == null) break;
                    String line = decode(raw);
                    if (line.equals("<!-- DNMS_SHARED_COPY_BEGIN -->")) { copied = true; continue; }
                    if (line.equals("<!-- DNMS_SHARED_COPY_END -->")) { copied = false; continue; }
                    if (copied || line.startsWith("- shared_from_nao=")) continue;
                    count++;
                    if (!line.startsWith("- day=")) continue;
                    for (String word : words(line))
                        inverted.computeIfAbsent(word, ignored -> new ArrayList<>()).add(new Hit(slot, offset));
                }
            }
            index.setProperty("nao." + slot, Long.toString(count));
        }
        Path keywordTemp = Files.createTempFile(dir, "keyword-", ".tmp");
        Path propertyTemp = Files.createTempFile(dir, "shared-index-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(keywordTemp, StandardCharsets.UTF_8)) {
                for (var entry : inverted.entrySet()) {
                    writer.write(encode(entry.getKey()));
                    for (Hit hit : entry.getValue())
                        writer.write("\t" + hit.slot + ":" + hit.offset);
                    writer.newLine();
                }
            }
            try (OutputStream out = Files.newOutputStream(propertyTemp)) {
                index.store(out, "DNMS original memory watermarks and keyword index");
            }
            move(keywordTemp, dir.resolve(KEYWORDS));
            move(propertyTemp, dir.resolve(INDEX)); // commit marker published last
        } finally {
            Files.deleteIfExists(keywordTemp);
            Files.deleteIfExists(propertyTemp);
        }
    }
    private static void move(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }
    public static Optional<String> recall(Path dir, int requester, String topic) throws IOException {
        if (requester < 1 || requester > 256 || topic == null || topic.isBlank()) return Optional.empty();
        Properties state = new Properties();
        Path propertyFile = dir.resolve(INDEX);
        if (Files.isRegularFile(propertyFile))
            try (InputStream in = Files.newInputStream(propertyFile)) { state.load(in); }
        int active;
        try { active = Math.min(256, Math.max(0, Integer.parseInt(state.getProperty("active", "0")))); }
        catch (NumberFormatException ex) { throw new IOException("Malformed shared index", ex); }
        Set<String> terms = words(topic);
        if (terms.isEmpty()) return Optional.empty();
        Map<Hit, Integer> scores = new HashMap<>();
        Path keywordFile = dir.resolve(KEYWORDS);
        // Index is only valid when both files are present and the version matches.
        if ("3".equals(state.getProperty("version")) && Files.isRegularFile(keywordFile)) {
            try (BufferedReader reader = Files.newBufferedReader(keywordFile, StandardCharsets.UTF_8)) {
                String line;
                Set<String> wanted = new HashSet<>();
                for (String term : terms) wanted.add(encode(term));
                while ((line = reader.readLine()) != null) {
                    int tab = line.indexOf('\t');
                    if (tab < 0 || !wanted.contains(line.substring(0, tab))) continue;
                    String[] entries = line.substring(tab + 1).split("\t");
                    for (String entry : entries) {
                        int colon = entry.indexOf(':');
                        if (colon < 0) continue;
                        try {
                            int slot = Integer.parseInt(entry.substring(0, colon));
                            long offset = Long.parseLong(entry.substring(colon + 1));
                            if (slot >= 1 && slot <= active && slot != requester)
                                scores.merge(new Hit(slot, offset), 1, Integer::sum);
                        } catch (NumberFormatException ex) { throw new IOException("Corrupt keyword posting", ex); }
                    }
                }
            }
        } else {
            // Backward-compatible lookup for pre-index saves.
            for (int slot = 1; slot <= active; slot++) {
                if (slot == requester) continue;
                Path path = journal(dir, slot);
                if (!Files.isRegularFile(path)) continue;
                long limit;
                try { limit = Long.parseLong(state.getProperty("nao." + slot, "0")); }
                catch (NumberFormatException ex) { throw new IOException("Corrupt shared watermark", ex); }
                try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
                    long seen = 0;
                    while (seen < limit) {
                        long offset = file.getFilePointer();
                        String raw = file.readLine();
                        if (raw == null) break;
                        String line = decode(raw);
                        if (line.startsWith("- shared_from_nao=")) continue;
                        seen++;
                        if (!line.startsWith("- day=")) continue;
                        Set<String> lineWords = words(line);
                        int score = 0;
                        for (String term : terms) if (lineWords.contains(term)) score++;
                        if (score > 0) scores.put(new Hit(slot, offset), score);
                    }
                }
            }
        }
        // Own journal is immediately visible, including events since midnight.
        Path own = journal(dir, requester);
        if (Files.isRegularFile(own)) {
            try (RandomAccessFile file = new RandomAccessFile(own.toFile(), "r")) {
                boolean copied = false;
                while (true) {
                    long offset = file.getFilePointer();
                    String raw = file.readLine();
                    if (raw == null) break;
                    String line = decode(raw);
                    if (line.equals("<!-- DNMS_SHARED_COPY_BEGIN -->")) { copied = true; continue; }
                    if (line.equals("<!-- DNMS_SHARED_COPY_END -->")) { copied = false; continue; }
                    if (copied || !line.startsWith("- day=")) continue;
                    Set<String> lineWords = words(line);
                    int score = 0;
                    for (String term : terms) if (lineWords.contains(term)) score++;
                    if (score > 0) scores.put(new Hit(requester, offset), score);
                }
            }
        }
        Hit best = null;
        int bestScore = 0;
        for (var entry : scores.entrySet())
            if (entry.getValue() > bestScore || (entry.getValue() == bestScore && best != null
                    && (entry.getKey().slot < best.slot || (entry.getKey().slot == best.slot && entry.getKey().offset < best.offset)))) {
                best = entry.getKey(); bestScore = entry.getValue();
            }
        return best == null ? Optional.empty() : Optional.of("source_nao=" + best.slot + " " + readHit(dir, best));
    }
}
