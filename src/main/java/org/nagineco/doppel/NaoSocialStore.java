package org.nagineco.doppel;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.nagineco.doppel.social.NaoRelationshipEngine;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;

/** JSON persistence adapter for directed relationship data, independent of personality. */
public final class NaoSocialStore {
    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<Map<String, NaoRelationshipEngine.Relationship>>(){}.getType();
    private final Path file;
    private final NaoRelationshipEngine engine = new NaoRelationshipEngine();
    public NaoSocialStore(Path file) { this.file = file; }
    public NaoRelationshipEngine engine() { return engine; }
    public void load() throws IOException {
        if (!Files.isRegularFile(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, NaoRelationshipEngine.Relationship> snapshot = GSON.fromJson(reader, TYPE);
            if (snapshot == null || snapshot.size() > 24 * 23)
                throw new IOException("Invalid social snapshot size");
            engine.restore(snapshot);
        } catch (RuntimeException ex) { throw new IOException("Malformed social snapshot", ex); }
    }
    public void save() throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(engine.snapshot(), TYPE, writer);
            }
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
