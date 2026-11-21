package org.nagineco.doppel;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Stable identities, independent goals and individual skills; no separate personalities. */
public final class NaoLifeStore {
    public enum Goal { EXPLORE, COLLECT, BUILD, REST, SOCIAL, SAFETY }
    public static final class Nao {
        public String id;
        public int number;
        public Goal goal = Goal.EXPLORE;
        public int exploration, collection, building, social;
        public int energy = 100;
        public long goalUntilTick;
        public int blockedSteps;
        public long decisions;
        public Map<String, Integer> inventory = new HashMap<>();
        public Map<String, Double> goalRewards = new HashMap<>();
        public Map<String, Integer> goalAttempts = new HashMap<>();
        public String lastAction = "NONE";
        public String lastOutcome = "NONE";
        /** Appearance identifier, not an authenticated Mojang profile or rendered skin. */
        public String skinKey = "nao/default";
    }
    private static final Gson GSON = new Gson();
    private final Path file;
    private final List<Nao> members = new ArrayList<>();
    public NaoLifeStore(Path file) { this.file = file; }
    public void load() throws IOException {
        members.clear();
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Nao[] data = GSON.fromJson(reader, Nao[].class);
            if (data == null || data.length > 24) throw new IOException("Invalid Nao identity list");
            Set<String> ids = new HashSet<>();
            for (Nao n : data) {
                if (n == null || n.id == null || n.goal == null || n.number != members.size()+1
                    || !ids.add(n.id)) throw new IOException("Invalid or duplicated Nao identity");
                if (n.inventory == null) n.inventory = new HashMap<>();
                if (n.goalRewards == null) n.goalRewards = new HashMap<>();
                if (n.goalAttempts == null) n.goalAttempts = new HashMap<>();
                if (n.skinKey == null || n.skinKey.isBlank()) n.skinKey = "nao/default";
                try { new org.nagineco.doppel.appearance.NaoAppearanceRegistry.NaoAppearance(n.number, UUID.fromString(n.id), n.skinKey, true); }
                catch (IllegalArgumentException ex) { throw new IOException("Invalid Nao appearance or UUID", ex); }
                members.add(n);
            }
        } catch (RuntimeException ex) { throw new IOException("Malformed Nao identities", ex); }
    }
    public Nao ensure(int index) {
        if (index < 0 || index >= 24) throw new IllegalArgumentException("Nao index");
        while (members.size() <= index) {
            Nao n = new Nao();
            n.number = members.size() + 1;
            n.id = UUID.randomUUID().toString();
            members.add(n);
        }
        return members.get(index);
    }
    public org.nagineco.doppel.appearance.NaoAppearanceRegistry appearanceRegistry() {
        org.nagineco.doppel.appearance.NaoAppearanceRegistry registry = new org.nagineco.doppel.appearance.NaoAppearanceRegistry();
        for (Nao n : members) registry.ensure(n.number, UUID.fromString(n.id), n.skinKey);
        return registry;
    }
    public int count() { return members.size(); }
    public Nao get(int index) { return members.get(index); }
    public void save() throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(members, writer);
            }
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    public void learn(int index, Goal goal) {
        Nao n = ensure(index);
        switch (goal) {
            case EXPLORE -> n.exploration++;
            case COLLECT -> n.collection++;
            case BUILD -> n.building++;
            case SOCIAL -> n.social++;
            case REST -> n.energy = Math.min(100, n.energy + 5);
            case SAFETY -> n.energy = Math.min(100, n.energy + 2);
        }
    }
}
