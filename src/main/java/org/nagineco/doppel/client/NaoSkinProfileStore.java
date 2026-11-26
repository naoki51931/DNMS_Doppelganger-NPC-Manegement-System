package org.nagineco.doppel.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Stores texture profile properties, not duplicate NPC images. */
final class NaoSkinProfileStore {
    private static final Map<UUID, String> saved = new HashMap<>();
    private static final Map<UUID, CompletableFuture<SkinTextures>> pending = new HashMap<>();
    private static Path path(UUID id) {
        return FabricLoader.getInstance().getConfigDir().resolve("doppelganger/client-skins").resolve(id + ".properties");
    }
    static void save(UUID id, GameProfile profile) {
        var properties = profile.getProperties().get("textures");
        if (properties.isEmpty()) return;
        Property property = properties.iterator().next();
        if (!property.hasSignature()) return;
        String key = property.value() + ":" + property.signature();
        if (key.equals(saved.get(id))) return;
        try {
            Path target = path(id);
            Files.createDirectories(target.getParent());
            Properties data = new Properties();
            data.setProperty("name", profile.getName());
            data.setProperty("value", property.value());
            data.setProperty("signature", property.signature());
            Path temp = Files.createTempFile(target.getParent(), "skin-", ".tmp");
            try {
                try (var out = Files.newOutputStream(temp)) { data.store(out, "DNMS signed skin profile"); }
                try { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException ex) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temp); }
            saved.put(id, key);
            pending.remove(id);
        } catch (java.io.IOException ignored) { }
    }
    static SkinTextures resolve(UUID id) {
        var fallback = DefaultSkinHelper.getSkinTextures(id);
        var future = pending.computeIfAbsent(id, NaoSkinProfileStore::load);
        return future.isCompletedExceptionally() ? fallback : future.getNow(fallback);
    }
    private static CompletableFuture<SkinTextures> load(UUID id) {
        try {
            Properties data = new Properties();
            try (var in = Files.newInputStream(path(id))) { data.load(in); }
            GameProfile profile = new GameProfile(id, data.getProperty("name", "Doppel"));
            profile.getProperties().put("textures", new Property("textures", data.getProperty("value"), data.getProperty("signature")));
            return MinecraftClient.getInstance().getSkinProvider().fetchSkinTextures(profile)
                .thenApply(result -> result.orElseGet(() -> DefaultSkinHelper.getSkinTextures(id)));
        } catch (Exception ex) {
            return CompletableFuture.completedFuture(DefaultSkinHelper.getSkinTextures(id));
        }
    }
}
