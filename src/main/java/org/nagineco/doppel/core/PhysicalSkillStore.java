package org.nagineco.doppel.core;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/** Versioned, atomic, Minecraft-independent physical skill checkpoint. */
public final class PhysicalSkillStore {
    private static final int MAGIC = 0x4450534B; // DPSK
    private static final int VERSION = 1;
    private PhysicalSkillStore() {}

    public static void save(Path file, List<PhysicalSkillSync.Skill> states) throws IOException {
        if (states == null || states.isEmpty() || states.size() > 256 || states.stream().anyMatch(java.util.Objects::isNull))
            throw new IOException("Invalid physical skill snapshot");
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(states.size());
                for (var s : states) {
                    out.writeDouble(s.technique());
                    out.writeDouble(s.coordination());
                    out.writeDouble(s.proficiency());
                    out.writeDouble(s.strength());
                    out.writeDouble(s.agility());
                    out.writeDouble(s.endurance());
                }
            }
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    public static List<PhysicalSkillSync.Skill> load(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION)
                throw new IOException("Unknown physical skill snapshot format");
            int count = in.readInt();
            if (count < 1 || count > 256) throw new IOException("Invalid physical skill NPC count");
            List<PhysicalSkillSync.Skill> states = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                try {
                    states.add(new PhysicalSkillSync.Skill(in.readDouble(), in.readDouble(),
                        in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble()));
                } catch (IllegalArgumentException ex) {
                    throw new IOException("Corrupt physical skill values", ex);
                }
            }
            if (in.read() != -1) throw new IOException("Trailing bytes in physical skill snapshot");
            return List.copyOf(states);
        } catch (EOFException ex) {
            throw new IOException("Truncated physical skill snapshot", ex);
        }
    }
}
