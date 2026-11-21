package org.nagineco.doppel;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Player-state snapshot with full item-stack component NBT (v2). */
public final class PlayerStateStore {
    private static final Gson GSON = new Gson();
    private PlayerStateStore() {}
    public static final class Slot {
        public int index;
        public String item;
        public int count;
        public String stackNbt;
    }
    public static final class State {
        public int version = 2;
        public String dimension;
        public double x, y, z;
        public float yaw, pitch, health;
        public int experienceLevel, totalExperience;
        public float experienceProgress;
        public List<Slot> inventory = new ArrayList<>();
    }
    public static void save(Path file, ServerPlayerEntity player) throws IOException {
        State s = new State();
        s.dimension = player.getServerWorld().getRegistryKey().getValue().toString();
        s.x = player.getX(); s.y = player.getY(); s.z = player.getZ();
        s.yaw = player.getYaw(); s.pitch = player.getPitch(); s.health = player.getHealth();
        s.experienceLevel = player.experienceLevel;
        s.totalExperience = player.totalExperience;
        s.experienceProgress = player.experienceProgress;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            Slot slot = new Slot();
            slot.index = i;
            slot.item = Registries.ITEM.getId(stack.getItem()).toString();
            slot.count = stack.getCount();
            NbtCompound tag = stack.toNbt(player.getRegistryManager());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, bytes);
            slot.stackNbt = Base64.getEncoder().encodeToString(bytes.toByteArray());
            s.inventory.add(slot);
        }
        Files.createDirectories(file.getParent());
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) { GSON.toJson(s, out); }
    }
    public static State load(Path file) throws IOException {
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            State s = GSON.fromJson(in, State.class);
            if (s == null || (s.version != 1 && s.version != 2) || s.inventory == null || s.dimension == null
                || !Double.isFinite(s.x) || !Double.isFinite(s.y) || !Double.isFinite(s.z)
                || !Float.isFinite(s.health) || s.health < 0 || !Float.isFinite(s.experienceProgress))
                throw new IOException("Invalid player state");
            Set<Integer> seen = new HashSet<>();
            for (Slot slot : s.inventory) {
                if (slot == null || slot.index < 0 || slot.index >= 46 || !seen.add(slot.index)
                    || slot.count <= 0 || slot.count > 99 || slot.item == null
                    || Identifier.tryParse(slot.item) == null
                    || (s.version == 2 && (slot.stackNbt == null || slot.stackNbt.length() > 2_000_000)))
                    throw new IOException("Invalid inventory slot");
            }
            return s;
        } catch (JsonParseException | IllegalStateException ex) {
            throw new IOException("Malformed player state", ex);
        }
    }
    /** Apply only after the operator explicitly resumes; require the original dimension. */
    public static void restore(ServerPlayerEntity player, State s) throws IOException {
        if (!player.getServerWorld().getRegistryKey().getValue().toString().equals(s.dimension))
            throw new IOException("Player must be in saved dimension " + s.dimension);
        if (s.inventory.stream().anyMatch(slot -> slot.index >= player.getInventory().size()
                || !Registries.ITEM.containsId(Identifier.of(slot.item))))
            throw new IOException("Snapshot references unknown items");
        // Decode and validate every stack before changing any inventory slot.
        Map<Integer, ItemStack> restored = new HashMap<>();
        for (Slot slot : s.inventory) {
            ItemStack stack;
            if (s.version == 2) {
                try {
                    byte[] bytes = Base64.getDecoder().decode(slot.stackNbt);
                    NbtCompound tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes),
                        NbtSizeTracker.of(4L * 1024 * 1024));
                    stack = ItemStack.fromNbt(player.getRegistryManager(), tag)
                        .orElseThrow(() -> new IOException("Invalid item stack NBT in slot " + slot.index));
                } catch (RuntimeException ex) {
                    throw new IOException("Cannot decode saved item in slot " + slot.index, ex);
                }
                if (stack.isEmpty() || stack.getCount() != slot.count
                        || !Registries.ITEM.getId(stack.getItem()).toString().equals(slot.item))
                    throw new IOException("Saved item mismatch in slot " + slot.index);
            } else {
                stack = new ItemStack(Registries.ITEM.get(Identifier.of(slot.item)), slot.count);
            }
            restored.put(slot.index, stack);
        }
        player.getInventory().clear();
        for (Map.Entry<Integer, ItemStack> entry : restored.entrySet())
            player.getInventory().setStack(entry.getKey(), entry.getValue());
        player.getInventory().markDirty();
        player.experienceLevel = s.experienceLevel;
        player.totalExperience = s.totalExperience;
        player.experienceProgress = s.experienceProgress;
        player.setHealth(Math.min(player.getMaxHealth(), s.health));
        player.teleport(player.getServerWorld(), s.x, s.y, s.z, s.yaw, s.pitch);
    }
}
