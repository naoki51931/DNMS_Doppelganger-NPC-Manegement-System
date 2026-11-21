package org.nagineco.doppel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Human-readable Markdown is the persistence format.
 * One shared personality file, plus one independent memory file per Nao.
 * All files stay on the Minecraft server, never committed to GitHub.
 */
public final class PersonalityMemoryStore {
    private final Path directory;
    private final int[] preference = new int[HabitBrain.Reason.values().length];
    private final List<String>[] pending;
    private final int[] eventCounts = new int[256];

    @SuppressWarnings("unchecked")
    public PersonalityMemoryStore(Path directory) throws IOException {
        this.directory = directory;
        Files.createDirectories(directory);
        pending = (List<String>[]) new List<?>[256];
        for (int i = 0; i < 256; i++) pending[i] = new ArrayList<>();
        for (int i = 0; i < preference.length; i++) preference[i] = 3;
        loadPersonality();
        for (int i = 0; i < 256; i++) {
            Path file = memoryPath(i);
            if (!Files.exists(file)) {
                Files.writeString(file, "# Nao " + String.format(Locale.ROOT, "%02d", i + 1)
                        + " — individual memories\n\n"
                        + "Each entry is an observed action and a player-supplied emotional label.\n\n",
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            }
        }
    }

    private Path memoryPath(int index) {
        if (index < 0 || index >= 256) throw new IllegalArgumentException("Nao index");
        return directory.resolve(String.format(Locale.ROOT, "nao_%02d.md", index + 1));
    }

    private Path personalityPath() { return directory.resolve("nao_personality.md"); }

    private void loadPersonality() throws IOException {
        Path file = personalityPath();
        if (!Files.exists(file)) {
            savePersonality();
            return;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!line.startsWith("- preference.")) continue;
            String[] parts = line.substring(13).split(":", 2);
            if (parts.length != 2) continue;
            try {
                HabitBrain.Reason reason = HabitBrain.Reason.valueOf(parts[0].trim().toUpperCase(Locale.ROOT));
                preference[reason.ordinal()] = Math.max(0, Math.min(10, Integer.parseInt(parts[1].trim())));
            } catch (IllegalArgumentException ignored) {
                // Keep defaults for malformed entries.
            }
        }
    }

    public int preference(HabitBrain.Reason reason) {
        return preference[reason.ordinal()];
    }

    public void setPreference(HabitBrain.Reason reason, int value) throws IOException {
        preference[reason.ordinal()] = Math.max(0, Math.min(10, value));
        savePersonality();
    }

    public void savePersonality() throws IOException {
        StringBuilder text = new StringBuilder("# Nao — shared personality\n\n"
            + "All Nao characters reference this single preference profile.\n"
            + "Preference strength: 0 (avoid) to 10 (strongly prefer).\n"
            + "Values are player-authored; they are not inferred emotions.\n\n");
        for (HabitBrain.Reason r : HabitBrain.Reason.values())
            text.append("- preference.").append(r.name().toLowerCase(Locale.ROOT))
                .append(": ").append(preference[r.ordinal()]).append("\n");
        Files.writeString(personalityPath(), text.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public void remember(int index, int day, int tick, VecRecord event) {
        if (index < 0 || index >= 256) return;
        pending[index].add("- day=" + day + " tick=" + tick
            + " position=(" + event.x() + "," + event.y() + "," + event.z() + ")"
            + " action=" + event.action()
            + " emotion=" + event.emotion()
            + " reason=" + event.reason() + "\n");
        eventCounts[index]++;
    }

    /** Structured, automatically observed gameplay event; never an inferred emotion. */
    public void rememberEvent(int index, int day, int tick, String type, String detail) {
        if (index < 0 || index >= 256) return;
        String safe = detail.replace('\n', ' ').replace('\r', ' ');
        pending[index].add("- day=" + day + " tick=" + tick + " event=" + type
            + " detail=" + safe + "\n");
        eventCounts[index]++;
    }

    public void rememberReward(int index, int day, int tick, HabitBrain brain, double score) {
        if (index < 0 || index >= 256 || brain.lastManualMove() == null || score == 0) return;
        pending[index].add("- day=" + day + " tick=" + tick
            + " reward_move=" + brain.lastManualMove()
            + " reward_emotion=" + brain.lastManualEmotion()
            + " reward_reason=" + brain.lastManualReason()
            + " reward_part=" + brain.lastManualDayPart()
            + " reward_score=" + score + "\n");
    }

    public void restoreRewards(int index, HabitBrain brain) throws IOException {
        for (String line : Files.readAllLines(memoryPath(index), StandardCharsets.UTF_8)) {
            if (!line.startsWith("- day=") || !line.contains(" reward_move=")) continue;
            try {
                String move = null, emotion = null, reason = null;
                int part = 0;
                double score = 0;
                for (String token : line.split(" ")) {
                    if (token.startsWith("reward_move=")) move = token.substring(12);
                    if (token.startsWith("reward_emotion=")) emotion = token.substring(15);
                    if (token.startsWith("reward_reason=")) reason = token.substring(14);
                    if (token.startsWith("reward_part=")) part = Integer.parseInt(token.substring(12));
                    if (token.startsWith("reward_score=")) score = Double.parseDouble(token.substring(13));
                }
                if (move != null && emotion != null && reason != null && part >= 0 && part <= 1)
                    brain.learnReward(HabitBrain.Move.valueOf(move), HabitBrain.Emotion.valueOf(emotion),
                        HabitBrain.Reason.valueOf(reason), part, score);
            } catch (IllegalArgumentException ignored) { /* Ignore malformed reward records. */ }
        }
    }

    /** Copy every source episode into every other Nao journal, retaining source provenance. */
    public void replicateAll(int activeCount) throws IOException {
        flush();
        int count = Math.min(256, Math.max(0, activeCount));
        List<String> originals = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            StringBuilder own = new StringBuilder();
            boolean inShared = false;
            for (String line : Files.readAllLines(memoryPath(i), StandardCharsets.UTF_8)) {
                if (line.equals("<!-- DNMS_SHARED_COPY_BEGIN -->")) { inShared = true; continue; }
                if (line.equals("<!-- DNMS_SHARED_COPY_END -->")) { inShared = false; continue; }
                if (!inShared) own.append(line).append("\n");
            }
            originals.add(own.toString());
        }
        for (int i = 0; i < count; i++) {
            StringBuilder copy = new StringBuilder(originals.get(i));
            copy.append("\n<!-- DNMS_SHARED_COPY_BEGIN -->\n## Shared full-memory copies\n");
            for (int j = 0; j < count; j++) {
                if (i == j) continue;
                copy.append("### Source Nao ").append(j + 1).append("\n");
                for (String line : originals.get(j).split("\n"))
                    if (line.startsWith("- day="))
                        copy.append("- shared_from_nao=").append(j + 1)
                            .append(" ").append(line.substring(2)).append("\n");
            }
            copy.append("<!-- DNMS_SHARED_COPY_END -->\n");
            Path target = memoryPath(i);
            Path tmp = target.resolveSibling(target.getFileName() + ".replica.tmp");
            try {
                Files.writeString(tmp, copy.toString(), StandardCharsets.UTF_8);
                try { Files.move(tmp, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
                catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                    Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } finally { Files.deleteIfExists(tmp); }
        }
    }

    public void flush() throws IOException {
        for (int i = 0; i < 256; i++) {
            if (pending[i].isEmpty()) continue;
            Files.writeString(memoryPath(i), String.join("", pending[i]), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            pending[i].clear();
        }
    }

    public void restore(int index, HabitBrain brain) throws IOException {
        for (String line : Files.readAllLines(memoryPath(index), StandardCharsets.UTF_8)) {
            if (!line.startsWith("- day=") || !line.contains(" action=")) continue;
            String[] tokens = line.split(" ");
            try {
                int tick = 0;
                HabitBrain.Move move = HabitBrain.Move.STAY;
                HabitBrain.Emotion emotion = HabitBrain.Emotion.NEUTRAL;
                HabitBrain.Reason reason = HabitBrain.Reason.NONE;
                for (String token : tokens) {
                    if (token.startsWith("tick=")) tick = Integer.parseInt(token.substring(5));
                    if (token.startsWith("action=")) move = HabitBrain.Move.valueOf(token.substring(7));
                    if (token.startsWith("emotion=")) emotion = HabitBrain.Emotion.valueOf(token.substring(8));
                    if (token.startsWith("reason=")) reason = HabitBrain.Reason.valueOf(token.substring(7));
                }
                brain.learnMemory(move, emotion, reason, tick);
                eventCounts[index]++;
            } catch (IllegalArgumentException ignored) {
                // Skip corrupted entries rather than failing the whole save.
            }
        }
    }

    public int eventCount(int index) { return eventCounts[index]; }
    public Path directory() { return directory; }

    public record VecRecord(double x, double y, double z, String action,
                            HabitBrain.Emotion emotion, HabitBrain.Reason reason) {}
}
