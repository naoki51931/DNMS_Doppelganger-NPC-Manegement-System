package org.nagineco.doppel;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.nagineco.patch.memoryintegration.MemoryIntegrationPatch;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Logger;
import java.io.IOException;
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
    private static PersonalityMemoryStore memories;
    private static final Logger LOG = Logger.getLogger("Doppelganger.MIP");
    private static final Deque<String> mipLog = new ArrayDeque<>();
    private static boolean mipEnabled = true;
    private static int mipScheduleTick = 18000; // Minecraft midnight, 00:00
    private static long lastScheduledDay = Long.MIN_VALUE;
    private static long lastObservedTime = Long.MIN_VALUE;
    private static boolean scheduleInitialized = false;
    private static Path mipScheduleFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("doppelganger/mip.time");
    }
    private static Path mipSettingFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("doppelganger/mip.enabled");
    }
    private static void logMIP(String message) {
        String entry = "[MIP] " + message;
        LOG.info(entry);
        if (mipLog.size() >= 20) mipLog.removeFirst();
        mipLog.addLast(entry);
    }
    private static UUID owner;
    private static ServerWorld world;
    private static BlockPos dawn;
    private static int loopTick = 0;
    private static int day = 1;
    private static int selected = 0;
    private static boolean recording = false;
    private static boolean freePlay = false;

    @Override public void onInitialize() {
        try {
            Path setting = mipSettingFile();
            if (Files.exists(setting))
                mipEnabled = !Files.readString(setting, StandardCharsets.UTF_8).trim().equalsIgnoreCase("off");
        } catch (IOException ex) {
            logMIP("Could not load enabled setting; using default ON: " + ex.getMessage());
        }
        try {
            Path setting = mipScheduleFile();
            if (Files.exists(setting))
                mipScheduleTick = parseMinecraftClock(
                    Files.readString(setting, StandardCharsets.UTF_8).trim());
        } catch (IOException | IllegalArgumentException ex) {
            logMIP("Could not load MIP schedule; using 00:00: " + ex.getMessage());
            mipScheduleTick = 18000;
        }
        logMIP("Initialized; enabled=" + mipEnabled + "; daily time="
            + formatMinecraftClock(mipScheduleTick) + ". Use /doppel mip status|on|off|run|logs|time.");
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
            dispatcher.register(literal("doppel")
                .requires(s -> s.hasPermissionLevel(2))
                .then(literal("start").executes(c -> start(c.getSource())))
                .then(literal("next").executes(c -> next(c.getSource())))
                .then(literal("status").executes(c -> status(c.getSource())))
                .then(literal("select").then(argument("id", IntegerArgumentType.integer(1, 24))
                    .executes(c -> select(c.getSource(), IntegerArgumentType.getInteger(c, "id")))))
                .then(literal("preference").then(argument("reason", StringArgumentType.word())
                    .then(argument("strength", IntegerArgumentType.integer(0, 10))
                    .executes(c -> preference(c.getSource(),
                        StringArgumentType.getString(c, "reason"),
                        IntegerArgumentType.getInteger(c, "strength"))))))
                .then(literal("emotion").then(argument("value", StringArgumentType.word())
                    .executes(c -> emotion(c.getSource(), StringArgumentType.getString(c, "value")))))
                .then(literal("reason").then(argument("value", StringArgumentType.word())
                    .executes(c -> reason(c.getSource(), StringArgumentType.getString(c, "value")))))
                .then(literal("ai").then(literal("on").executes(c -> ai(c.getSource(), true)))
                    .then(literal("off").executes(c -> ai(c.getSource(), false))))
                .then(literal("mip")
                    .then(literal("status").executes(c -> mipStatus(c.getSource())))
                    .then(literal("on").executes(c -> mipToggle(c.getSource(), true)))
                    .then(literal("off").executes(c -> mipToggle(c.getSource(), false)))
                    .then(literal("run").executes(c -> mipRun(c.getSource())))
                    .then(literal("logs").executes(c -> mipLogs(c.getSource())))
                    .then(literal("time").executes(c -> mipTimeStatus(c.getSource()))
                        .then(argument("clock", StringArgumentType.word())
                            .executes(c -> mipTimeSet(c.getSource(),
                                StringArgumentType.getString(c, "clock"))))))
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
        try {
            memories = new PersonalityMemoryStore(
                FabricLoader.getInstance().getGameDir().resolve("config/doppelganger/memories"));
        } catch (IOException e) {
            return say(src, "Could not initialize Markdown memories: " + e.getMessage());
        }
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
        HabitBrain first = new HabitBrain();
        try { memories.restore(0, first); }
        catch (IOException e) { return say(src, "Could not read Nao 01 memory: " + e.getMessage()); }
        brains.add(first);
        world.setTimeOfDay(0);
        scheduleInitialized = false;
        lastScheduledDay = Long.MIN_VALUE;
        return say(src, "Day 1, loop 1/24. Play, then /doppel next.");
    }

    private static void tick(MinecraftServer server) {
        tickMIP(server);
        if (!recording || world == null || owner == null) return;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(owner);
        if (player == null || player.getServerWorld() != world) return;
        if (!freePlay) {
            List<Vec3d> current = tracks.get(tracks.size() - 1);
            if (current.size() < DAY_TICKS) {
                current.add(player.getPos());
                HabitBrain brain = brains.get(tracks.size() - 1);
                brain.observe(player.getPos());
                if (memories != null && current.size() % 20 == 0) {
                    Vec3d p = player.getPos();
                    memories.remember(tracks.size() - 1, day, current.size() - 1,
                        new PersonalityMemoryStore.VecRecord(p.x, p.y, p.z,
                            brain.latestMove().name(), brain.emotion(), brain.reason()));
                }
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
                    pos = memories == null ? brains.get(i).step(ghost.getPos(), loopTick)
                        : HabitBrain.applyMove(ghost.getPos(),
                            HabitBrain.sharedDecision(brains, i, loopTick, memories));
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
        if (memories != null) {
            try {
                memories.flush();
            } catch (IOException e) {
                logMIP("Memory flush failed: " + e.getMessage());
                return say(src, "Failed to save memories: " + e.getMessage());
            }
        }
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
        HabitBrain newBrain = new HabitBrain();
        try { memories.restore(tracks.size() - 1, newBrain); }
        catch (IOException e) {
            tracks.remove(tracks.size() - 1);
            return say(src, "Failed to load next Nao memory: " + e.getMessage());
        }
        brains.add(newBrain);
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
            + " | selected " + (selected+1) + " | AI " + aiMode + " | tick " + loopTick
            + (brains.isEmpty() ? "" : " | emotion " + brains.get(Math.min(selected, brains.size()-1)).emotion()
                + " | reason " + brains.get(Math.min(selected, brains.size()-1)).reason()));
    }

    private static int stop(ServerCommandSource src) {
        if (memories != null) {
            try {
                memories.flush();
            } catch (IOException e) {
                logMIP("Memory flush failed: " + e.getMessage());
                return say(src, "Could not save memories: " + e.getMessage());
            }
        }
        clearGhosts();
        recording = false;
        freePlay = false;
        tracks.clear();
        brains.clear();
        memories = null;
        owner = null;
        world = null;
        dawn = null;
        return say(src, "Stopped. Unsaved recordings discarded.");
    }

    private static int mipStatus(ServerCommandSource src) {
        return say(src, "MIP " + (mipEnabled ? "ON" : "OFF")
            + " | daily at " + formatMinecraftClock(mipScheduleTick)
            + " (tick " + mipScheduleTick + ")"
            + " | recent log entries: " + mipLog.size()
            + " | integrated index: " + (memories != null
                && Files.exists(memories.directory().resolve("nao_integrated_memory.md"))));
    }

    private static int parseMinecraftClock(String clock) {
        if (!clock.matches("(?:[01]\\\\d|2[0-3]):[0-5]\\\\d"))
            throw new IllegalArgumentException("Expected HH:mm from 00:00 to 23:59.");
        int hour = Integer.parseInt(clock.substring(0, 2));
        int minute = Integer.parseInt(clock.substring(3, 5));
        // Minecraft tick 0 is 06:00; midnight (00:00) is tick 18000.
        return ((hour * 1000 + minute * 1000 / 60) + 18000) % DAY_TICKS;
    }

    private static String formatMinecraftClock(int tick) {
        int minutes = ((tick + 6000) % DAY_TICKS) * 1440 / DAY_TICKS;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }

    private static int mipTimeStatus(ServerCommandSource src) {
        return say(src, "MIP scheduled daily at " + formatMinecraftClock(mipScheduleTick)
            + " (Minecraft tick " + mipScheduleTick + ").");
    }

    private static int mipTimeSet(ServerCommandSource src, String clock) {
        final int newTick;
        try { newTick = parseMinecraftClock(clock); }
        catch (IllegalArgumentException ex) { return say(src, ex.getMessage()); }
        try {
            Path file = mipScheduleFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, clock, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return say(src, "Could not save MIP schedule: " + ex.getMessage());
        }
        mipScheduleTick = newTick;
        scheduleInitialized = false;
        logMIP("Daily integration time changed to " + clock + " (tick " + newTick + ")");
        return say(src, "MIP now scheduled daily at " + clock + ".");
    }

    private static void tickMIP(MinecraftServer server) {
        if (!mipEnabled || !recording || world == null || memories == null) return;
        long current = world.getTimeOfDay();
        if (!scheduleInitialized) {
            lastObservedTime = current;
            scheduleInitialized = true;
            return;
        }
        long previous = lastObservedTime;
        lastObservedTime = current;
        // Ignore time jumps backwards (including the 24 training-loop rewinds).
        // On large forward jumps, do not execute missed schedules in a burst.
        if (current <= previous || current - previous > 20) return;
        long dayIndex = Math.floorDiv(current, DAY_TICKS);
        long target = dayIndex * DAY_TICKS + mipScheduleTick;
        if (previous < target && current >= target && dayIndex != lastScheduledDay) {
            lastScheduledDay = dayIndex;
            try {
                memories.flush();
                logMIP("Daily " + formatMinecraftClock(mipScheduleTick) + ": "
                    + MemoryIntegrationPatch.integrate(memories.directory()));
            } catch (IOException ex) {
                logMIP("Daily integration failed: " + ex.getMessage());
            }
        }
    }

    private static int mipToggle(ServerCommandSource src, boolean enabled) {
        try {
            Path file = mipSettingFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, enabled ? "on" : "off", StandardCharsets.UTF_8);
            mipEnabled = enabled;
            logMIP("Switched " + (enabled ? "ON" : "OFF") + " by command");
            if (enabled && memories != null) {
                memories.flush();
                logMIP(MemoryIntegrationPatch.integrate(memories.directory()));
            }
            return say(src, "MIP " + (enabled ? "ON" : "OFF") + " (setting saved).");
        } catch (IOException e) {
            logMIP("Toggle failed: " + e.getMessage());
            return say(src, "MIP toggle failed: " + e.getMessage());
        }
    }

    private static int mipRun(ServerCommandSource src) {
        if (!mipEnabled) return say(src, "MIP is OFF; use /doppel mip on first.");
        try {
            if (memories == null)
                memories = new PersonalityMemoryStore(
                    FabricLoader.getInstance().getGameDir().resolve("config/doppelganger/memories"));
            memories.flush();
            String result = MemoryIntegrationPatch.integrate(memories.directory());
            logMIP(result);
            return say(src, result);
        } catch (IOException e) {
            logMIP("Manual integration failed: " + e.getMessage());
            return say(src, "MIP failed: " + e.getMessage());
        }
    }

    private static int mipLogs(ServerCommandSource src) {
        if (mipLog.isEmpty()) return say(src, "No MIP logs yet.");
        for (String entry : mipLog)
            src.sendFeedback(() -> Text.literal(entry), false);
        return 1;
    }

    private static int preference(ServerCommandSource src, String reasonName, int strength) {
        if (!recording || memories == null) return say(src, "Start training first.");
        if (!isOwner(src)) return say(src, "Only the playable character's player can set personality.");
        try {
            HabitBrain.Reason reason = HabitBrain.Reason.valueOf(
                reasonName.toUpperCase(java.util.Locale.ROOT));
            memories.setPreference(reason, strength);
            return say(src, "Shared Nao personality: " + reason + "=" + strength + "/10");
        } catch (IllegalArgumentException ex) {
            return say(src, "Reasons: none, explore, build, collect, safety, social.");
        } catch (IOException ex) {
            return say(src, "Could not save personality: " + ex.getMessage());
        }
    }

    private static int emotion(ServerCommandSource src, String value) {
        if (!recording || freePlay) return say(src, "Emotion labels are set during training only.");
        if (!isOwner(src)) return say(src, "Only the training player can label emotions.");
        try {
            HabitBrain.Emotion label = HabitBrain.Emotion.valueOf(value.toUpperCase(java.util.Locale.ROOT));
            brains.get(tracks.size()-1).setEmotion(label);
            return say(src, "Self-reported emotion: " + label);
        } catch (IllegalArgumentException ex) {
            return say(src, "Use neutral, happy, curious, afraid, angry or sad.");
        }
    }

    private static int reason(ServerCommandSource src, String value) {
        if (!recording || freePlay) return say(src, "Reason labels are set during training only.");
        if (!isOwner(src)) return say(src, "Only the training player can label motivations.");
        try {
            HabitBrain.Reason label = HabitBrain.Reason.valueOf(value.toUpperCase(java.util.Locale.ROOT));
            brains.get(tracks.size()-1).setReason(label);
            return say(src, "Self-reported reason: " + label);
        } catch (IllegalArgumentException ex) {
            return say(src, "Use none, explore, build, collect, safety or social.");
        }
    }

    private static boolean isOwner(ServerCommandSource src) {
        try { return owner != null && owner.equals(src.getPlayerOrThrow().getUuid()); }
        catch (Exception ex) { return false; }
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
