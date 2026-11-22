package org.nagineco.doppel.consent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

/**
 * Server-side, opt-in consent ledger. Not wired to NPC spawning until all
 * creation paths have been audited; callers must check allowed(...) first.
 */
public final class ConsentStore {
    public static final int POLICY_VERSION = 1;
    private final Path file;

    public record Consent(boolean cloning, boolean memorySharing,
                          boolean autonomy, String subjectId,
                          String grantedBy, String updatedAt, int version) {
        public boolean allowed() { return cloning && !subjectId.isBlank() && !grantedBy.isBlank(); }
    }

    public ConsentStore(Path configDirectory) {
        this.file = configDirectory.resolve("doppelganger").resolve("consent.properties");
    }

    public synchronized Consent read() throws IOException {
        if (!Files.isRegularFile(file)) return denied();
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) { p.load(in); }
        int version;
        try { version = Integer.parseInt(p.getProperty("policyVersion", "0")); }
        catch (NumberFormatException e) { return denied(); }
        if (version != POLICY_VERSION) return denied();
        return new Consent(
            Boolean.parseBoolean(p.getProperty("cloning", "false")),
            Boolean.parseBoolean(p.getProperty("memorySharing", "false")),
            Boolean.parseBoolean(p.getProperty("autonomy", "false")),
            p.getProperty("subjectId", ""),
            p.getProperty("grantedBy", ""),
            p.getProperty("updatedAt", ""),
            version);
    }

    public synchronized Consent grant(UUID subjectId, UUID actorId,
                                       boolean cloning, boolean memorySharing,
                                       boolean autonomy) throws IOException {
        if (subjectId == null || actorId == null || !subjectId.equals(actorId)) {
            throw new IllegalArgumentException("Consent must be granted by the data subject");
        }
        Consent consent = new Consent(cloning, memorySharing, autonomy,
            subjectId.toString(), actorId.toString(), Instant.now().toString(), POLICY_VERSION);
        write(consent);
        return consent;
    }

    public synchronized void revoke(UUID subjectId) throws IOException {
        Consent current = read();
        if (subjectId == null || !subjectId.toString().equals(current.subjectId())) {
            throw new IllegalArgumentException("Only the consenting subject may revoke");
        }
        write(new Consent(false, false, false, current.subjectId(), current.grantedBy(),
            Instant.now().toString(), POLICY_VERSION));
    }

    private static Consent denied() {
        return new Consent(false, false, false, "", "", "", POLICY_VERSION);
    }

    private void write(Consent c) throws IOException {
        Files.createDirectories(file.getParent());
        Properties p = new Properties();
        p.setProperty("policyVersion", Integer.toString(c.version()));
        p.setProperty("cloning", Boolean.toString(c.cloning()));
        p.setProperty("memorySharing", Boolean.toString(c.memorySharing()));
        p.setProperty("autonomy", Boolean.toString(c.autonomy()));
        p.setProperty("subjectId", c.subjectId());
        p.setProperty("grantedBy", c.grantedBy());
        p.setProperty("updatedAt", c.updatedAt());
        Path temp = Files.createTempFile(file.getParent(), "consent-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temp)) { p.store(out, "DNMS consent"); }
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }
}
