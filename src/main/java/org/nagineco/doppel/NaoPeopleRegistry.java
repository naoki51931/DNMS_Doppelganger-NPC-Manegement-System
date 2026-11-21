package org.nagineco.doppel;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Persistent people registry for Nao identities and external Minecraft/world characters. */
public final class NaoPeopleRegistry {
    public static final class Person {
        public String id, name, kind;
        public String residence = "";
        public Map<String, String> relationships = new LinkedHashMap<>();
        public List<String> sharedEvents = new ArrayList<>();
    }
    private static final Gson GSON = new Gson();
    private final Path path;
    private final Map<String, Person> people = new LinkedHashMap<>();
    public NaoPeopleRegistry(Path path) { this.path = path; }
    public void load() throws IOException {
        people.clear();
        if (!Files.isRegularFile(path)) return;
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Person[] data = GSON.fromJson(r, Person[].class);
            if (data == null || data.length > 10000) throw new IOException("Invalid people registry");
            for (Person p : data) {
                if (p == null || p.id == null || p.name == null || p.kind == null
                    || people.containsKey(p.id)) throw new IOException("Invalid person");
                if (p.relationships == null) p.relationships = new LinkedHashMap<>();
                if (p.sharedEvents == null) p.sharedEvents = new ArrayList<>();
                if (p.residence == null) p.residence = "";
                people.put(p.id, p);
            }
        } catch (RuntimeException e) { throw new IOException("Invalid people JSON", e); }
    }
    public Person add(String id, String name, String kind) {
        if (people.containsKey(id)) return people.get(id);
        Person p = new Person(); p.id = id; p.name = name; p.kind = kind;
        people.put(id, p); return p;
    }
    public Person get(String id) { return people.get(id); }
    public int size() { return people.size(); }
    public void relate(String from, String to, String role) {
        Person a = require(from), b = require(to);
        a.relationships.put(to, role);
        if (role.equals("SPOUSE") || role.equals("PARTNER") || role.equals("FRIEND")
            || role.equals("COLLEAGUE") || role.equals("HOUSEMATE"))
            b.relationships.put(from, role);
        if (role.equals("PARENT")) b.relationships.put(from, "CHILD");
        if (role.equals("CHILD")) b.relationships.put(from, "PARENT");
    }
    public void event(String first, String second, String detail) {
        Person a = require(first), b = require(second);
        String entry = UUID.randomUUID() + " | " + first + " + " + second + " | " + detail;
        a.sharedEvents.add(entry); b.sharedEvents.add(entry);
    }
    private Person require(String id) {
        Person p = people.get(id);
        if (p == null) throw new IllegalArgumentException("Unknown person " + id);
        return p;
    }
    public void save() throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.writeString(tmp, GSON.toJson(people.values()), StandardCharsets.UTF_8);
            try { Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
    }
}
