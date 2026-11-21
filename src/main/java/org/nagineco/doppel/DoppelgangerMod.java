package org.nagineco.doppel;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.nagineco.patch.memoryintegration.MemoryIntegrationPatch;
import org.nagineco.patch.automovement.AutomaticMovementPatch;
import org.nagineco.patch.rewardlearning.RewardLearningPatch;
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
import java.util.Map;
import java.util.HashMap;
import net.minecraft.item.ItemStack;

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
    private static NaoLifeStore life;
    private static SharedMemoryReader sharedKnowledge;
    private static boolean lifeAiEnabled = false;
    private static Path lifeFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("doppelganger/nao_lives.json");
    }
    private static Path lifeSettingFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("doppelganger/life-ai.enabled");
    }
    private static void initializeLife(int count) throws IOException {
        life = new NaoLifeStore(lifeFile());
        life.load();
        for (int i = 0; i < count; i++) life.ensure(i);
        life.save();
        sharedKnowledge = new SharedMemoryReader(memories.directory().resolve("nao_integrated_memory.md"));
        sharedKnowledge.refresh();
    }
    private static int lifeToggle(ServerCommandSource src, boolean enabled) {
        if (!isOwner(src)) return say(src, "Only playable Nao can change life AI.");
        try {
            Files.createDirectories(lifeSettingFile().getParent());
            Files.writeString(lifeSettingFile(), enabled ? "on" : "off", StandardCharsets.UTF_8);
            lifeAiEnabled = enabled;
            return say(src, "Nao life AI " + (enabled ? "ON" : "OFF"));
        } catch (IOException ex) { return say(src, "Life AI setting failed: " + ex.getMessage()); }
    }
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
    private static boolean playableAutoMove = false;
    private static boolean rewardLearning = false;
    private static Path rewardSettingFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("doppelganger/reward-learning.enabled");
    }
    private static Path snapshotFile() {
        return FabricLoader.getInstance().getGameDir().resolve("config/doppelganger/recordings.json");
    }
    private static Path checkpointRoot() {
        return FabricLoader.getInstance().getGameDir().resolve("config/doppelganger/checkpoints");
    }
    private static Path playerSnapshotFile() {
        return FabricLoader.getInstance().getGameDir().resolve("config/doppelganger/player.json");
    }
    private static void saveSession() throws IOException {
        if (memories == null) return;
        memories.flush();
        RecordingSnapshotStore.save(snapshotFile(), tracks, day, selected, freePlay);
        ServerPlayerEntity player = owner == null || world == null ? null
            : world.getServer().getPlayerManager().getPlayer(owner);
        if (player == null || player.getServerWorld() != world)
            throw new IOException("Playable Nao must be online in the recording dimension to checkpoint");
        Path playerFile = playerSnapshotFile();
        PlayerStateStore.save(playerFile, player);
        if (life != null) life.save();
        GenerationCheckpointStore.publish(checkpointRoot(), snapshotFile(),
            memories.directory(), playerFile, life == null ? null : lifeFile());
    }
    private static int saveCommand(ServerCommandSource src) {
        if (!recording) return say(src, "No active session to save.");
        if (!isOwner(src)) return say(src, "Only the playable Nao can save this session.");
        try {
            saveSession();
            return say(src, "Saved session to config/doppelganger/recordings.json");
        } catch (IOException ex) {
            return say(src, "Session save failed: " + ex.getMessage());
        }
    }
    private static int resume(ServerCommandSource src) {
        if (recording) return say(src, "Stop the active session before resuming.");
        ServerPlayerEntity player;
        try { player = src.getPlayerOrThrow(); }
        catch (Exception ex) { return say(src, "Run as an operator player."); }
        RecordingSnapshotStore.Snapshot snapshot;
        List<List<Vec3d>> loadedTracks;
        PersonalityMemoryStore loadedMemories;
        List<HabitBrain> loadedBrains = new ArrayList<>();
        PlayerStateStore.State restoredPlayerState = null;
        try {
            Path generation = GenerationCheckpointStore.latest(checkpointRoot());
            Path recordingFile = generation == null ? snapshotFile()
                : generation.resolve("recordings.json");
            snapshot = RecordingSnapshotStore.load(recordingFile);
            PlayerStateStore.State playerState = generation == null ? null
                : (Files.exists(generation.resolve("player.json"))
                    ? PlayerStateStore.load(generation.resolve("player.json")) : null);
            if (playerState != null && !player.getServerWorld().getRegistryKey().getValue().toString()
                    .equals(playerState.dimension))
                return say(src, "Move to saved dimension before resuming: " + playerState.dimension);
            if (generation != null && Files.isRegularFile(generation.resolve("nao_lives.json"))) {
                Files.createDirectories(lifeFile().getParent());
                Files.copy(generation.resolve("nao_lives.json"), lifeFile(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            if (generation != null) GenerationCheckpointStore.restoreMemories(generation,
                FabricLoader.getInstance().getGameDir().resolve("config/doppelganger/memories"));
            loadedTracks = RecordingSnapshotStore.restoreTracks(snapshot);
            if (loadedTracks.isEmpty()) return say(src, "Snapshot has no recordings.");
            loadedMemories = new PersonalityMemoryStore(
                FabricLoader.getInstance().getGameDir().resolve("config/doppelganger/memories"));
            for (int i = 0; i < loadedTracks.size(); i++) {
                HabitBrain brain = new HabitBrain();
                loadedMemories.restore(i, brain);
                loadedMemories.restoreRewards(i, brain);
                loadedBrains.add(brain);
            }
            restoredPlayerState = playerState;
            memories = loadedMemories;
            initializeLife(loadedTracks.size());
            if (restoredPlayerState != null) PlayerStateStore.restore(player, restoredPlayerState);
        } catch (IOException ex) {
            return say(src, "Could not resume session: " + ex.getMessage());
        }
        clearGhosts();
        tracks.clear();
        tracks.addAll(loadedTracks);
        brains.clear();
        brains.addAll(loadedBrains);
        memories = loadedMemories;
        owner = player.getUuid();
        world = player.getServerWorld();
        dawn = tracks.get(0).isEmpty() ? player.getBlockPos()
            : net.minecraft.util.math.BlockPos.ofFloored(tracks.get(0).get(0));
        day = snapshot.day;
        selected = snapshot.selected;
        freePlay = snapshot.freePlay;
        recording = true;
        aiMode = true;
        playableAutoMove = false;
        loopTick = freePlay ? 0 : Math.min(DAY_TICKS - 1, tracks.get(tracks.size()-1).size());
        lastAutoTick = -1;
        lastCheckpointTick = Long.MIN_VALUE;
        resetObservations(player);
        if (!freePlay) {
            List<Vec3d> active = tracks.get(tracks.size() - 1);
            if (!active.isEmpty() && restoredPlayerState == null) {
                Vec3d last = active.get(active.size() - 1);
                player.teleport(world, last.x, last.y, last.z, player.getYaw(), player.getPitch());
            }
            brains.get(tracks.size() - 1).resetObservationAnchor(player.getPos());
        }
        scheduleInitialized = false;
        spawnGhosts();
        report("Resumed JSON snapshot with " + tracks.size() + " recorded Nao tracks.");
        return say(src, "Resumed saved session: " + tracks.size() + "/24 tracks, day " + day
            + ". Automatic movement remains OFF.");
    }
    private static int lastAutoTick = -1;
    private static long lastCheckpointTick = Long.MIN_VALUE;
    private static boolean lastTickWasAutomatic = false;


    private static final Deque<String> reports = new ArrayDeque<>();
    private static final Map<String,Integer> inventoryBaseline = new HashMap<>();
    private static float healthBaseline = Float.NaN;
    private static int observedTick = -1;
    private static void report(String msg) {
        String entry = "[DNMS] " + msg;
        if (reports.size() == 100) reports.removeFirst();
        reports.addLast(entry);
        LOG.info(entry);
    }
    private static Map<String,Integer> inventory(ServerPlayerEntity player) {
        Map<String,Integer> result = new HashMap<>();
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty()) result.merge(stack.getItem().toString(), stack.getCount(), Integer::sum);
        }
        return result;
    }
    private static void resetObservations(ServerPlayerEntity player) {
        inventoryBaseline.clear();
        inventoryBaseline.putAll(inventory(player));
        healthBaseline = player.getHealth();
        observedTick = -1;
        lastTickWasAutomatic = false;
    }
    private static void observePlayable(ServerPlayerEntity player) {
        if (memories == null || tracks.isEmpty() || loopTick % 20 != 0 || observedTick == loopTick) return;
        observedTick = loopTick;
        int index = freePlay ? selected : tracks.size() - 1;
        float health = player.getHealth();
        if (!Float.isNaN(healthBaseline) && health < healthBaseline) {
            if (rewardLearning && !playableAutoMove) applyReward(index, health - healthBaseline);
            memories.rememberEvent(index, day, loopTick, "damage", "score=" + (health - healthBaseline) + " health=" + health);
            report("Playable Nao took damage: " + healthBaseline + " -> " + health);
        }
        healthBaseline = health;
        Map<String,Integer> now = inventory(player);
        for (Map.Entry<String,Integer> e : now.entrySet()) {
            int delta = e.getValue() - inventoryBaseline.getOrDefault(e.getKey(), 0);
            if (delta > 0) {
                if (rewardLearning && !playableAutoMove) applyReward(index, delta);
                memories.rememberEvent(index, day, loopTick, "item_gain", "score=+" + delta + " item=" + e.getKey());
                report("Playable Nao obtained " + e.getKey() + " x" + delta);
            }
        }
        for (Map.Entry<String,Integer> e : inventoryBaseline.entrySet()) {
            int delta = e.getValue() - now.getOrDefault(e.getKey(), 0);
            if (delta > 0) memories.rememberEvent(index, day, loopTick, "item_loss", "score=0 count=-" + delta + " item=" + e.getKey());
        }
        inventoryBaseline.clear();
        inventoryBaseline.putAll(now);
    }
    private static void applyReward(int index, double score) {
        if (index < 0 || index >= brains.size()) return;
        HabitBrain brain = brains.get(index);
        if (brain.lastManualMove() == null) return;
        memories.rememberReward(index, day, loopTick, brain, score);
        RewardLearningPatch.apply(brain, loopTick, score);
    }

    private static int rewardToggle(ServerCommandSource src, boolean enabled) {
        if (!isOwner(src)) return say(src, "Only the playable Nao can change reward learning.");
        try {
            Path file = lifeSettingFile();
            lifeAiEnabled = Files.exists(file) && Files.readString(file, StandardCharsets.UTF_8).trim().equalsIgnoreCase("on");
        } catch (IOException ex) { lifeAiEnabled = false; }
        try {
            Path file = rewardSettingFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, enabled ? "on" : "off", StandardCharsets.UTF_8);
            rewardLearning = enabled;
            return say(src, "Reward learning " + (enabled ? "ON" : "OFF") + " (saved).");
        } catch (IOException ex) {
            return say(src, "Could not save reward learning setting: " + ex.getMessage());
        }
    }

    private static int showReports(ServerCommandSource src) {
        if (reports.isEmpty()) return say(src, "No DNMS reports yet.");
        for (String entry : reports) src.sendFeedback(() -> Text.literal(entry), false);
        return 1;
    }
    private static int lifeStatus(ServerCommandSource src) {
        if (life == null) return say(src, "Life AI " + (lifeAiEnabled ? "ON" : "OFF") + " | no session");
        return say(src, "Life AI " + (lifeAiEnabled ? "ON" : "OFF")
            + " | persistent identities " + life.count()
            + " | shared MIP memories " + (sharedKnowledge == null ? 0 : sharedKnowledge.entries())
            + " | spawned stand-ins " + ghosts.size());
    }
    private static int npcReport(ServerCommandSource src) {
        for (int i = 0; i < ghosts.size(); i++) {
            ArmorStandEntity npc = ghosts.get(i);
            if (!npc.isRemoved()) report("NPC Nao " + (i+1) + " position="
                + npc.getBlockPos().toShortString() + " samples=" + brains.get(i).samples());
        }
        return showReports(src);
    }

    @Override public void onInitialize() {
        try {
            Path file = rewardSettingFile();
            rewardLearning = Files.exists(file) && Files.readString(file, StandardCharsets.UTF_8).trim().equalsIgnoreCase("on");
        } catch (IOException ex) { rewardLearning = false; }
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
                .then(literal("save").executes(c -> saveCommand(c.getSource())))
                .then(literal("life")
                    .then(literal("on").executes(c -> lifeToggle(c.getSource(), true)))
                    .then(literal("off").executes(c -> lifeToggle(c.getSource(), false)))
                    .then(literal("status").executes(c -> lifeStatus(c.getSource()))))
                .then(literal("reward")
                    .then(literal("on").executes(c -> rewardToggle(c.getSource(), true)))
                    .then(literal("off").executes(c -> rewardToggle(c.getSource(), false)))
                    .then(literal("status").executes(c -> say(c.getSource(), "Reward learning " + (rewardLearning ? "ON" : "OFF")))))
                .then(literal("resume").executes(c -> resume(c.getSource())))
                .then(literal("next").executes(c -> next(c.getSource())))
                .then(literal("status").executes(c -> status(c.getSource())))
                .then(literal("logs").executes(c -> showReports(c.getSource())))
                .then(literal("npc").then(literal("report").executes(c -> npcReport(c.getSource()))))
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
                .then(literal("automove")
                    .then(literal("on").executes(c -> autoMove(c.getSource(), true)))
                    .then(literal("off").executes(c -> autoMove(c.getSource(), false)))
                    .then(literal("status").executes(c -> say(c.getSource(),
                        "AMP " + (playableAutoMove ? "ON" : "OFF")))))
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
        reports.clear();
        try {
            memories = new PersonalityMemoryStore(
                FabricLoader.getInstance().getGameDir().resolve("config/doppelganger/memories"));
        } catch (IOException e) {
            return say(src, "Could not initialize Markdown memories: " + e.getMessage());
        }
        aiMode = true;
        playableAutoMove = false;
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
        try { memories.restore(0, first); memories.restoreRewards(0, first); }
        catch (IOException e) { return say(src, "Could not read Nao 01 memory: " + e.getMessage()); }
        brains.add(first);
        try { initializeLife(1); }
        catch (IOException e) { recording = false; return say(src, "Life identity load failed: " + e.getMessage()); }
        world.setTimeOfDay(0);
        resetObservations(player);
        lastCheckpointTick = Long.MIN_VALUE;
        report("Playable Nao training started; automatic observation enabled.");
        scheduleInitialized = false;
        lastScheduledDay = Long.MIN_VALUE;
        return say(src, "Day 1, loop 1/24. Play, then /doppel next.");
    }

    private static void tick(MinecraftServer server) {
        tickMIP(server);
        if (!recording || world == null || owner == null) return;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(owner);
        if (player == null || player.getServerWorld() != world) return;
        lastTickWasAutomatic = playableAutoMove;
        if (playableAutoMove && memories != null && !brains.isEmpty()
                && loopTick != lastAutoTick) {
            lastAutoTick = loopTick;
            int currentIndex = freePlay ? Math.min(selected, brains.size()-1) : tracks.size()-1;
            HabitBrain.Move choice = AutomaticMovementPatch.choose(brains, currentIndex, loopTick, memories, rewardLearning);
            AutomaticMovementPatch.move(player, choice);
            if (loopTick % 200 == 0) report("Playable Nao AMP selected " + choice);
        }
        observePlayable(player);
        if (!freePlay) {
            List<Vec3d> current = tracks.get(tracks.size() - 1);
            if (current.size() < DAY_TICKS) {
                current.add(player.getPos());
                HabitBrain brain = brains.get(tracks.size() - 1);
                // Do not train the policy on its own generated movement.
                if (!lastTickWasAutomatic) brain.observe(player.getPos());
                if (!lastTickWasAutomatic && memories != null && current.size() % 20 == 0) {
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
        if (lifeAiEnabled && sharedKnowledge != null && loopTick % 200 == 0) {
            try { sharedKnowledge.refresh(); }
            catch (IOException ex) { report("Shared memory read failed: " + ex.getMessage()); }
        }
        for (int i = 0; i < ghosts.size(); i++) {
            ArmorStandEntity ghost = ghosts.get(i);
            if (ghost.isRemoved() || i >= tracks.size()) continue;
            List<Vec3d> samples = tracks.get(i);
            if (samples.isEmpty()) continue;
            Vec3d previous = ghost.getPos();
            Vec3d pos = previous;
            if (!aiMode) {
                pos = samples.get(Math.min(loopTick, samples.size() - 1));
            } else if (lifeAiEnabled && life != null && i < life.count() && memories != null) {
                NaoLifeStore.Nao nao = life.get(i);
                long time = world.getTime();
                if (loopTick % 20 == 0)
                    NaoLifeAI.plan(nao, sharedKnowledge, world, previous, time);
                HabitBrain.Move move = NaoLifeAI.move(nao, brains, i, loopTick,
                    memories, rewardLearning, sharedKnowledge, previous);
                Vec3d proposed = HabitBrain.applyMove(previous, move);
                if (proposed.squaredDistanceTo(samples.get(0)) <= 32 * 32
                        && world.isAir(BlockPos.ofFloored(proposed))
                        && !NaoLifeAI.danger(world, proposed)) pos = proposed;
                boolean moved = pos.squaredDistanceTo(previous) > 0.000001;
                if (loopTick % 20 == 0) {
                    NaoLifeAI.update(nao, moved);
                    if (moved && nao.goal == NaoLifeStore.Goal.EXPLORE)
                        life.learn(i, NaoLifeStore.Goal.EXPLORE);
                    nao.blockedSteps = !moved && move != HabitBrain.Move.STAY
                        ? Math.min(100, nao.blockedSteps + 1) : 0;
                    nao.lastAction = move.name();
                    nao.lastOutcome = moved ? "MOVED" : "WAITED_OR_BLOCKED";
                }
            } else {
                pos = memories == null ? brains.get(i).step(previous, loopTick)
                    : HabitBrain.applyMove(previous,
                        HabitBrain.sharedDecision(brains, i, loopTick, memories, rewardLearning));
                if (pos.squaredDistanceTo(samples.get(0)) > 32 * 32
                        || !world.isAir(BlockPos.ofFloored(pos))) pos = previous;
            }
            ghost.refreshPositionAndAngles(pos.x, pos.y, pos.z, ghost.getYaw(), 0);
        }
        long serverTick = server.getTicks();
        if (serverTick - lastCheckpointTick >= 1200 || lastCheckpointTick == Long.MIN_VALUE) {
            try { saveSession(); lastCheckpointTick = serverTick; }
            catch (IOException ex) {
                lastCheckpointTick = serverTick; // Retry at the next interval, not every tick.
                report("Periodic JSON save failed: " + ex.getMessage());
            }
        }
        if (freePlay) loopTick = (loopTick + 1) % DAY_TICKS;
        else if (loopTick < DAY_TICKS - 1) loopTick++;
    }

    private static int next(ServerCommandSource src) {
        if (!recording) return say(src, "Start with /doppel start.");
        if (freePlay) {
            try { saveSession(); }
            catch (IOException ex) { return say(src, "Day not advanced because save failed: " + ex.getMessage()); }
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
        resetObservations(player);
        if (!brains.isEmpty()) brains.get(brains.size() - 1).resetObservationAnchor(player.getPos());
        int finished = tracks.size();
        if (finished == CHARACTERS) {
            freePlay = true;
            spawnGhosts();
            try { saveSession(); }
            catch (IOException ex) { return say(src, "Training completed but save failed: " + ex.getMessage()); }
            return say(src, "24 recordings completed. Use /doppel select 1..24.");
        }
        tracks.add(new ArrayList<>());
        HabitBrain newBrain = new HabitBrain();
        try { memories.restore(tracks.size() - 1, newBrain); memories.restoreRewards(tracks.size() - 1, newBrain); }
        catch (IOException e) {
            tracks.remove(tracks.size() - 1);
            return say(src, "Failed to load next Nao memory: " + e.getMessage());
        }
        brains.add(newBrain);
        try { if (life != null) { life.ensure(tracks.size() - 1); life.save(); } }
        catch (IOException ex) { return say(src, "Nao identity save failed: " + ex.getMessage()); }
        spawnGhosts();
        try { saveSession(); }
        catch (IOException ex) { return say(src, "Loop advanced but save failed: " + ex.getMessage()); }
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
        if (!recording) return say(src, "No active session.");
        if (!isOwner(src)) return say(src, "Only the playable Nao can stop this session.");
        try {
            saveSession();
        } catch (IOException ex) {
            return say(src, "Stop cancelled because saving failed: " + ex.getMessage());
        }
        clearGhosts();
        recording = false;
        freePlay = false;
        playableAutoMove = false;
        tracks.clear();
        brains.clear();
        memories = null;
        owner = null;
        world = null;
        dawn = null;
        return say(src, "Stopped and saved. Resume with /doppel resume.");
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
        if (!clock.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]"))
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

    private static int autoMove(ServerCommandSource src, boolean enabled) {
        if (!recording || !isOwner(src)) return say(src, "Start DNMS as the playable Nao first.");
        playableAutoMove = enabled;
        lastTickWasAutomatic = enabled;
        lastAutoTick = -1;
        if (!freePlay && !brains.isEmpty()) {
            ServerPlayerEntity player = src.getServer().getPlayerManager().getPlayer(owner);
            if (player != null) brains.get(tracks.size() - 1).resetObservationAnchor(player.getPos());
        }
        report("Playable Nao automatic movement " + (enabled ? "enabled; generated movement excluded from habit training" : "disabled"));
        return say(src, "AMP " + (enabled ? "ON" : "OFF")
            + ": shared personality controls horizontal movement; use /doppel automove off to stop.");
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
