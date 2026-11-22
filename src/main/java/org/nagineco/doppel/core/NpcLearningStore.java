package org.nagineco.doppel.core;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Versioned, atomic, Minecraft-independent binary learning snapshots. */
public final class NpcLearningStore {
    private static final int MAGIC = 0x444E4C53; // DNLS
    private static final int VERSION = 1;
    private NpcLearningStore() {}

    public static void save(Path file, NpcLearningState state) throws IOException {
        if (file == null || state == null) throw new IllegalArgumentException("Missing learning data");
        NpcLearningState.Snapshot snapshot = state.snapshot();
        int[][][][] counts = snapshot.counts();
        double[][][][] rewards = snapshot.rewards();
        int emotions = counts[0].length, motivations = counts[0][0].length;
        int movements = counts[0][0][0].length;
        Path absolute = file.toAbsolutePath();
        Path parent = absolute.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".dnms-learning-", ".tmp");
        try {
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(temporary))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(emotions);
                out.writeInt(motivations);
                out.writeInt(movements);
                out.writeInt(snapshot.emotion());
                out.writeInt(snapshot.motivation());
                out.writeInt(snapshot.samples());
                for (int p = 0; p < 2; p++)
                    for (int e = 0; e < emotions; e++)
                        for (int m = 0; m < motivations; m++)
                            for (int a = 0; a < movements; a++) {
                                out.writeInt(counts[p][e][m][a]);
                                out.writeDouble(rewards[p][e][m][a]);
                            }
            }
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static NpcLearningState load(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION)
                throw new IOException("Unsupported learning snapshot");
            int emotions = in.readInt(), motivations = in.readInt(), movements = in.readInt();
            if (emotions < 1 || emotions > 64 || motivations < 1 || motivations > 64
                    || movements < 1 || movements > 64)
                throw new IOException("Invalid learning dimensions");
            int emotion = in.readInt(), motivation = in.readInt(), samples = in.readInt();
            int[][][][] counts = new int[2][emotions][motivations][movements];
            double[][][][] rewards = new double[2][emotions][motivations][movements];
            for (int p = 0; p < 2; p++)
                for (int e = 0; e < emotions; e++)
                    for (int m = 0; m < motivations; m++)
                        for (int a = 0; a < movements; a++) {
                            counts[p][e][m][a] = in.readInt();
                            rewards[p][e][m][a] = in.readDouble();
                        }
            if (in.read() != -1) throw new IOException("Trailing snapshot data");
            try {
                return NpcLearningState.restore(new NpcLearningState.Snapshot(
                    counts, rewards, emotion, motivation, samples));
            } catch (IllegalArgumentException ex) {
                throw new IOException("Corrupt learning snapshot", ex);
            }
        }
    }
}
