package org.nagineco.doppel;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.Vec3d;

/** Durable, bounded JSON snapshot of the 24 playable training tracks. */
public final class RecordingSnapshotStore {
    private static final Gson GSON = new Gson();
    private static final int MAX_TRACKS = 24;
    private static final int MAX_SAMPLES = 24000;
    private RecordingSnapshotStore() {}

    public static final class Snapshot {
        public int version = 1;
        public int day = 1;
        public int selected = 0;
        public boolean freePlay;
        public List<List<double[]>> tracks = new ArrayList<>();
    }

    public static void save(Path file, List<List<Vec3d>> tracks, int day,
                            int selected, boolean freePlay) throws IOException {
        Snapshot data = new Snapshot();
        data.day = day;
        data.selected = selected;
        data.freePlay = freePlay;
        for (List<Vec3d> track : tracks) {
            List<double[]> coordinates = new ArrayList<>();
            for (Vec3d v : track) coordinates.add(new double[]{v.x, v.y, v.z});
            data.tracks.add(coordinates);
        }
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static Snapshot load(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Snapshot data = GSON.fromJson(reader, Snapshot.class);
            if (data == null || data.version != 1 || data.tracks == null
                    || data.tracks.size() > MAX_TRACKS || data.day < 1
                    || data.selected < 0 || data.selected >= MAX_TRACKS)
                throw new IOException("Invalid DNMS snapshot header");
            for (List<double[]> track : data.tracks) {
                if (track == null || track.size() > MAX_SAMPLES)
                    throw new IOException("Invalid track length");
                for (double[] point : track) {
                    if (point == null || point.length != 3
                            || !Double.isFinite(point[0]) || !Double.isFinite(point[1])
                            || !Double.isFinite(point[2]))
                        throw new IOException("Invalid coordinates");
                }
            }
            if (data.freePlay && data.tracks.size() != MAX_TRACKS)
                throw new IOException("Incomplete free-play snapshot");
            return data;
        } catch (JsonParseException | IllegalStateException ex) {
            throw new IOException("Malformed DNMS snapshot", ex);
        }
    }

    public static List<List<Vec3d>> restoreTracks(Snapshot data) {
        List<List<Vec3d>> restored = new ArrayList<>();
        for (List<double[]> track : data.tracks) {
            List<Vec3d> points = new ArrayList<>();
            for (double[] p : track) points.add(new Vec3d(p[0], p[1], p[2]));
            restored.add(points);
        }
        return restored;
    }
}
