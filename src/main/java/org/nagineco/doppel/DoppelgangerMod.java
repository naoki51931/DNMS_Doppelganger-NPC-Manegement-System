package org.nagineco.doppel;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Fabric 1.21.1 server-side MVP.
 * Ghosts are autonomous imitation-driven armor stands, NOT real player bots.
 * No block/entity/inventory rollback is performed.
 */
public class DoppelgangerMod implements ModInitializer {
    private static final int CHARACTERS = 24;
    private static final int DAY_TICKS = 24000;
    private static final List<List<Vec3d>> tracks = new ArrayList<>();
    private static final List<ArmorStandEntity> ghosts = new ArrayList<>();
    private static final List<HabitBrain> brains = new ArrayList<>();
    private static boolean aiMode = true;
    private static UUID owner;
    private static ServerWorld world;
    private static BlockPos dawn;
    private static int loopTick = 0;
    private static int day = 1;
    private static int selected = 0;
    private static boolean recording = false;
    private static boolean freePlay = false;

    @Override public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
            dispatcher.register(literal("doppel")
                .requires(s -> s.hasPermissionLevel(2))
                .then(literal("start").executes(c -> start(c.getSource())))
                .then(literal("next").executes(c -> next(c.getSource())))
                .then(literal("status").executes(c -> status(c.getSource())))
                .then(literal("select").then(argument("id", IntegerArgumentType.integer(1, 24))
                    .executes(c -> select(c.getSource(), IntegerArgumentType.getInteger(c, "id")))))
                .then(literal("ai").then(literal("on").executes(c -> ai(c.getSource(), true)))
                    .then(literal("off").executes(c -> ai(c.getSource(), false))))
                .then(literal("stop").executes(c -> stop(c.getSource())))
            )
        );
        ServerTickEvents.END_SERVER_TICK.register(DoppelgangerMod::tick);
    }

    private static int start(ServerCommandSource src) {
        if (recording) return say(src, "Already recording.");
        ServerPlayerEntity player;
        try { player = src.getPlayerOrThrow(); }
        catch (Exception e) { return say(src, "Run as an operator player."); }
        clearGhosts();
        tracks.clear();
        brains.clear();
        aiMode = true;
        owner = player.getUuid();
        world = player.getServerWorld();
        dawn = player.getBlockPos();
        loopTick = 0;
        selected = 0;
        day = 1;
        freePlay = false;
        recording = true;
        tracks.add(new ArrayList<>());
        brains.add(new HabitBrain());
        world.setTimeOfDay(0);
        return say(src, "Day 1, loop 1/24. Play, then /doppel next.");
    }

    private static void tick(MinecraftServer server) {
        if (!recording || world == null || owner == null) return;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(owner);
        if (player == null || player.getServerWorld() != world) return;
        if (!freePlay) {
            List<Vec3d> current = tracks.get(tracks.size() - 1);
            if (current.size() < DAY_TICKS) {
                current.add(player.getPos());
                brains.get(tracks.size() - 1).observe(player.getPos());
            }
            if (current.size() >= DAY_TICKS) {
                // Pause at end of recorded day until the operator advances.
                world.setTimeOfDay(23999);
            } else {
                world.setTimeOfDay(current.size());
            }
        }
        for (int i = 0; i < ghosts.size(); i++) {
            ArmorStandEntity ghost = ghosts.get(i);
            if (ghost.isRemoved() || i >= tracks.size()) continue;
            List<Vec3d> samples = tracks.get(i);
            if (!samples.isEmpty()) {
                Vec3d pos;
                if (aiMode) {
                    pos = brains.get(i).step(ghost.getPos(), loopTick);
                    // Bound AI to a local demonstration area and avoid solid blocks.
                    Vec3d origin = samples.get(0);
                    if (pos.squaredDistanceTo(origin) > 32 * 32
                            || !world.isAir(net.minecraft.util.math.BlockPos.ofFloored(pos))) {
                        pos = ghost.getPos();
                    }
                } else {
                    pos = samples.get(Math.min(loopTick, samples.size() - 1));
                }
                ghost.refreshPositionAndAngles(pos.x, pos.y, pos.z, ghost.getYaw(), 0);
            }
        }
        if (freePlay) loopTick = (loopTick + 1) % DAY_TICKS;
        else if (loopTick < DAY_TICKS - 1) loopTick++;
    }

    private static int next(ServerCommandSource src) {
        if (!recording) return say(src, "Start with /doppel start.");
        if (freePlay) {
            day++;
            loopTick = 0;
            world.setTimeOfDay(0);
            return say(src, "Day " + day + " started (AI NPC simulation).");
        }
        ServerPlayerEntity player = src.getServer().getPlayerManager().getPlayer(owner);
        if (player == null) return say(src, "Owner is offline.");
        // Rewind only the clock and owner's location. World changes persist!
        player.teleport(world, dawn.getX()+0.5, dawn.getY(), dawn.getZ()+0.5,
                player.getYaw(), player.getPitch());
        loopTick = 0;
        world.setTimeOfDay(0);
        int finished = tracks.size();
        if (finished == CHARACTERS) {
            freePlay = true;
            spawnGhosts();
            return say(src, "24 recordings completed. Use /doppel select 1..24.");
        }
        tracks.add(new ArrayList<>());
        brains.add(new HabitBrain());
        spawnGhosts();
        return say(src, "Day " + day + " loop " + (finished+1) + "/24.");
    }

    private static void spawnGhosts() {
        clearGhosts();
        int count = freePlay ? tracks.size() : tracks.size() - 1;
        for (int i = 0; i < count; i++) {
            ArmorStandEntity stand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
            Vec3d pos = tracks.get(i).isEmpty()
                ? Vec3d.ofCenter(dawn) : tracks.get(i).get(0);
            stand.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
            stand.setNoGravity(true);
            stand.setInvulnerable(true);
            stand.setCustomName(Text.literal("Doppel " + (i+1)));
            stand.setCustomNameVisible(true);
            world.spawnEntity(stand);
            ghosts.add(stand);
        }
    }

    private static int select(ServerCommandSource src, int id) {
        if (!freePlay) return say(src, "Complete 24 loops first.");
        ServerPlayerEntity player = src.getServer().getPlayerManager().getPlayer(owner);
        if (player == null) return say(src, "Owner is offline.");
        selected = id - 1;
        List<Vec3d> samples = tracks.get(selected);
        if (!samples.isEmpty()) {
            Vec3d pos = selected < ghosts.size() ? ghosts.get(selected).getPos()
                    : samples.get(Math.min(loopTick, samples.size()-1));
            player.teleport(world, pos.x, pos.y, pos.z, player.getYaw(), player.getPitch());
        }
        // This selects a ghost viewpoint/location; it does not possess a real NPC.
        return say(src, "Selected Doppel " + id + ". Player moved to its AI NPC position (not possession).");
    }

    private static int status(ServerCommandSource src) {
        return say(src, "Day " + day + " | recorded " + tracks.size()
            + "/24 | mode " + (freePlay ? "free-play" : "recording")
            + " | selected " + (selected+1) + " | AI " + aiMode + " | tick " + loopTick);
    }

    private static int stop(ServerCommandSource src) {
        clearGhosts();
        recording = false;
        freePlay = false;
        tracks.clear();
        brains.clear();
        owner = null;
        world = null;
        dawn = null;
        return say(src, "Stopped. Unsaved recordings discarded.");
    }

    private static int ai(ServerCommandSource src, boolean enabled) {
        aiMode = enabled;
        return say(src, "AI movement " + (enabled ? "enabled" : "disabled; replay mode"));
    }

    private static void clearGhosts() {
        for (ArmorStandEntity e : ghosts) if (!e.isRemoved()) e.discard();
        ghosts.clear();
    }
    private static int say(ServerCommandSource src, String msg) {
        src.sendFeedback(() -> Text.literal("[Doppel] " + msg), false);
        return 1;
    }
}
