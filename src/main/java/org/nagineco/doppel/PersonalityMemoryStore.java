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
    private final int[] eventCounts = new int[24];

    @SuppressWarnings("unchecked")
    public PersonalityMemoryStore(Path directory) throws IOException {
        this.directory = directory;
        Files.createDirectories(directory);
        pending = (List<String>[]) new List<?>[24];
        for (int i = 0; i < 24; i++) pending[i] = new ArrayList<>();
        for (int i = 0; i < preference.length; i++) preference[i] = 3;
        loadPersonality();
        for (int i = 0; i < 24; i++) {
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
        if (index < 0 || index >= 24) throw new IllegalArgumentException("Nao index");
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
            + "All 24 Nao characters reference this single preference profile.\n"
            + "Preference strength: 0 (avoid) to 10 (strongly prefer).\n"
            + "Values are player-authored; they are not inferred emotions.\n\n");
        for (HabitBrain.Reason r : HabitBrain.Reason.values())
            text.append("- preference.").append(r.name().toLowerCase(Locale.ROOT))
                .append(": ").append(preference[r.ordinal()]).append("\n");
        Files.writeString(personalityPath(), text.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public void remember(int index, int day, int tick, VecRecord event) {
        if (index < 0 || index >= 24) return;
        pending[index].add("- day=" + day + " tick=" + tick
            + " position=(" + event.x() + "," + event.y() + "," + event.z() + ")"
            + " action=" + event.action()
            + " emotion=" + event.emotion()
            + " reason=" + event.reason() + "\n");
        eventCounts[index]++;
    }

    /** Structured, automatically observed gameplay event; never an inferred emotion. */
    public void rememberEvent(int index, int day, int tick, String type, String detail) {
        if (index < 0 || index >= 24) return;
        String safe = detail.replace('\n', ' ').replace('\r', ' ');
        pending[index].add("- day=" + day + " tick=" + tick + " event=" + type
            + " detail=" + safe + "\n");
        eventCounts[index]++;
    }

    public void flush() throws IOException {
        for (int i = 0; i < 24; i++) {
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
