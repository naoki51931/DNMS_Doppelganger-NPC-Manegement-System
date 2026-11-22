package org.nagineco.doppel;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.nagineco.patch.memoryintegration.MemoryIntegrationPatch;
import org.nagineco.patch.automovement.AutomaticMovementPatch;
import org.nagineco.patch.rewardlearning.RewardLearningPatch;
import org.nagineco.patch.naogrowth.NaoGrowthPatch;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Logger;
import java.io.IOException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.text.ClickEvent;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.EulerAngle;
import net.minecraft.world.GameMode;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

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
    /** Ephemeral navigation cache; never part of saved NPC memory/checkpoints. */
    private record RouteWaypoint(String phase, int x, int z, int floorY) {}
    private static final RouteWaypoint[] routeWaypoints = new RouteWaypoint[256];
    /** CI-only baseline: compare actual world entity positions after normal server ticks. */
    private static final List<Vec3d> ciInitialPositions = new ArrayList<>();
    private static long ciMovementStartTick = -1;


    private static boolean aiMode = true;
    private static PersonalityMemoryStore memories;
    private static NaoLifeStore life;
    private static NaoSocialStore social;
    private static SharedMemoryReader sharedKnowledge;
    private static NaoPeopleRegistry people;
    private static boolean lifeAiEnabled = false;
    private static boolean offlineNpcMode = false;
    private static Path offlineNpcModeFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("doppelganger/offline-npcs.enabled");
    }
    private static int offlineNpcModeSet(ServerCommandSource src, boolean enabled) {
        try {
            Files.createDirectories(offlineNpcModeFile().getParent());
            Files.writeString(offlineNpcModeFile(), enabled ? "on" : "off", StandardCharsets.UTF_8);
            offlineNpcMode = enabled;
            return say(src, "Owner-offline NPC simulation " + (enabled ? "ON" : "OFF"));
        } catch (IOException ex) { return say(src, "Offline NPC setting failed: " + ex.getMessage()); }
    }
    private static int offlineNpcModeStatus(ServerCommandSource src) {
        return say(src, "Owner-offline NPC simulation " + (offlineNpcMode ? "ON" : "OFF")
            + " (requires an initialized or resumed session and /doppel life on).");
    }

    private static org.nagineco.doppel.core.PhysicalSkillSync.Mode physicalSkillMode =
        org.nagineco.doppel.core.PhysicalSkillSync.Mode.FULL;
    private static org.nagineco.doppel.core.PhysicalSkillLearningEngine physicalSkills;
    private static Path physicalSkillsFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("doppelganger/physical-skills.bin");
    }
    private static int physicalSkillPractice(ServerCommandSource src, int slot, int minutes) {
        if (life == null || slot > life.count())
            return say(src, "Nao slot is not initialized.");
        try {
            int count = life.count();
            if (physicalSkills == null || physicalSkills.count() != count) {
                var next = new org.nagineco.doppel.core.PhysicalSkillLearningEngine(count, physicalSkillMode, 1);
                if (Files.isRegularFile(physicalSkillsFile())) {
                    var saved = org.nagineco.doppel.core.PhysicalSkillStore.load(physicalSkillsFile());
                    if (saved.size() > count) return say(src, "Saved skill snapshot has more NPCs; refusing to discard skill data.");
                    var expanded = new java.util.ArrayList<>(saved);
                    while (expanded.size() < count)
                        expanded.add(new org.nagineco.doppel.core.PhysicalSkillSync.Skill(0, 0, 0, 0, 0, 0));
                    next.restore(expanded);
                }
                physicalSkills = next;
            }
            physicalSkills.configure(physicalSkillMode, 1);
            physicalSkills.practice(slot - 1, minutes, 1, 1);
            org.nagineco.doppel.core.PhysicalSkillStore.save(physicalSkillsFile(), physicalSkills.snapshot());
            return say(src, "Nao " + slot + " practiced for " + minutes
                + " minutes; skill sharing mode: " + physicalSkillMode);
        } catch (IOException | IllegalArgumentException ex) {
            return say(src, "Physical skill practice failed: " + ex.getMessage());
        }
    }
    private static void ensurePhysicalSkills(int count) throws IOException {
        if (count < 1 || count > 256) throw new IOException("Invalid physical skill NPC count");
        if (physicalSkills != null && physicalSkills.count() == count) return;
        var next = new org.nagineco.doppel.core.PhysicalSkillLearningEngine(count, physicalSkillMode, 1);
        if (Files.isRegularFile(physicalSkillsFile())) {
            var saved = org.nagineco.doppel.core.PhysicalSkillStore.load(physicalSkillsFile());
            if (saved.size() > count) throw new IOException("Skill snapshot exceeds active NPC count");
            var expanded = new java.util.ArrayList<>(saved);
            while (expanded.size() < count)
                expanded.add(new org.nagineco.doppel.core.PhysicalSkillSync.Skill(0, 0, 0, 0, 0, 0));
            next.restore(expanded);
        }
        physicalSkills = next;
    }

    private static void savePhysicalSkills() throws IOException {
        if (physicalSkills != null)
            org.nagineco.doppel.core.PhysicalSkillStore.save(physicalSkillsFile(), physicalSkills.snapshot());
    }

    private static Path physicalSkillModeFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("doppelganger/physical-skill-mode.txt");
    }
    private static int physicalSkillModeSet(ServerCommandSource src,
                                             org.nagineco.doppel.core.PhysicalSkillSync.Mode mode) {
        try {
            Files.createDirectories(physicalSkillModeFile().getParent());
            org.nagineco.doppel.core.AtomicTextFile.write(physicalSkillModeFile(), mode.name() + "\n");
            physicalSkillMode = mode;
            if (physicalSkills != null) physicalSkills.configure(mode, 1);
            return say(src, "Physical skill sync policy: " + mode
                + " (manual practice enabled; automatic activity tracking pending)");
        } catch (IOException ex) {
            return say(src, "Physical skill mode save failed: " + ex.getMessage());
        }
    }
    private static int physicalSkillModeStatus(ServerCommandSource src) {
        return say(src, "Physical skill sync policy: " + physicalSkillMode
            + " (manual practice enabled; automatic activity tracking pending)");
    }

    // Server-only interactive chat dashboard: no client mod or web server required.
    private static final int DEFAULT_NAOS = 24;
    private static int configuredMaxNaos = 256;
    private static Path maxNaosFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("doppelganger/max-naos.txt");
    }
    private static Text dashboardButton(String label, String command) {
        return Text.literal(" [" + label + "]").formatted(Formatting.AQUA)
            .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }
    private static int dashboard(ServerCommandSource src) {
        int count = life == null ? 0 : life.count();
        src.sendFeedback(() -> Text.literal("DOPPELGÄNGER UNLIMITED | 外側の世界 / Outer World")
            .formatted(Formatting.GOLD), false);
        src.sendFeedback(() -> Text.literal("奈央 / Naos: " + count + "/" + configuredMaxNaos
            + " | default: " + DEFAULT_NAOS + " | active: " + recording
            + " | free-play: " + freePlay), false);
        src.sendFeedback(() -> Text.literal("追加 / Add:")
            .append(dashboardButton("+1", "/doppel dashboard add 1"))
            .append(dashboardButton("+4", "/doppel dashboard add 4"))
            .append(dashboardButton("初期24 / Bootstrap", "/doppel dashboard bootstrap")), false);
        src.sendFeedback(() -> Text.literal("管理 / Manage:")
            .append(dashboardButton("状態 / Status", "/doppel dashboard"))
            .append(dashboardButton("生活 / Life", "/doppel life status"))
            .append(dashboardButton("記憶 / MIP", "/doppel mip status")), false);
        src.sendFeedback(() -> Text.literal("設定 / Configuration: /doppel dashboard max <1..256> (restart-safe)")
            .formatted(Formatting.GRAY), false);
        if (life != null) for (int i = 0; i < life.count(); i++) {
            final NaoLifeStore.Nao n = life.get(i);
            final int slot = i + 1;
            src.sendFeedback(() -> Text.literal(String.format(java.util.Locale.ROOT,
                "%02d %s | %s | %s", slot, n.occupation, n.residence, n.longTermGoal))
                .append(dashboardButton("詳細 / Details", "/doppel dashboard profile " + slot)), false);
        }
        return 1;
    }
    private static int setMaxNaos(ServerCommandSource src, int maximum) {
        if (recording) return say(src, "Stop the active session before changing the maximum.");
        if (life != null && maximum < life.count())
            return say(src, "Maximum cannot be smaller than existing Nao identities.");
        try {
            writeAtomic(maxNaosFile(), Integer.toString(maximum));
            configuredMaxNaos = maximum;
            return say(src, "Maximum Nao identities set to " + maximum + ". Default bootstrap is 24.");
        } catch (IOException ex) { return say(src, "Could not save maximum: " + ex.getMessage()); }
    }
    private static int bootstrapNaos(ServerCommandSource src) {
        if (recording) return say(src, "Session already active.");
        ServerPlayerEntity player;
        try { player = src.getPlayerOrThrow(); }
        catch (Exception ex) { return say(src, "Run as an operator player."); }
        clearGhosts();
        tracks.clear(); brains.clear(); reports.clear();
        try {
            memories = new PersonalityMemoryStore(FabricLoader.getInstance().getGameDir()
                .resolve("config/doppelganger/memories"));
            initializeLife(DEFAULT_NAOS);
            for (int i = 0; i < DEFAULT_NAOS; i++) {
                tracks.add(new ArrayList<>(List.of(player.getPos())));
                HabitBrain brain = new HabitBrain();
                memories.restore(i, brain);
                memories.restoreRewards(i, brain);
                brains.add(brain);
            }
        } catch (IOException ex) {
            tracks.clear(); brains.clear();
            return say(src, "Could not bootstrap Naos: " + ex.getMessage());
        }
        owner = player.getUuid();
        world = player.getServerWorld();
        dawn = player.getBlockPos();
        day = 1; selected = 0; loopTick = 0;
        freePlay = true; recording = true; aiMode = true; playableAutoMove = false;
        scheduleInitialized = false; lastCheckpointTick = Long.MIN_VALUE;
        resetObservations(player);
        spawnGhosts();
        try { saveSession(); }
        catch (IOException ex) { report("Bootstrap checkpoint failed: " + ex.getMessage()); }
        return dashboard(src);
    }
    private static int addNaos(ServerCommandSource src, int amount) {
        if (!recording || !freePlay || life == null || world == null || memories == null)
            return say(src, "Start a free-play session first with /doppel dashboard bootstrap.");
        if (!isOwner(src)) return say(src, "Only the owner may add Naos.");
        if (tracks.size() + amount > configuredMaxNaos)
            return say(src, "Configured Nao maximum reached.");
        try {
            for (int i = 0; i < amount; i++) {
                int slot = tracks.size();
                life.ensure(slot);
                tracks.add(new ArrayList<>(List.of(Vec3d.ofCenter(dawn))));
                HabitBrain brain = new HabitBrain();
                memories.restore(slot, brain);
                memories.restoreRewards(slot, brain);
                brains.add(brain);
            }
            life.save();
            if (people != null) { for (int i = 0; i < life.count(); i++) {
                NaoLifeStore.Nao n = life.get(i); people.add(n.id, "Nao_" + n.number, "NAO");
            } people.save(); }
            spawnGhosts();
            saveSession();
            return dashboard(src);
        } catch (IOException ex) { return say(src, "Nao addition failed: " + ex.getMessage()); }
    }
    private static int profile(ServerCommandSource src, int slot) {
        if (life == null || slot > life.count()) return say(src, "Nao does not exist.");
        NaoLifeStore.Nao n = life.get(slot - 1);
        return say(src, "Nao " + slot + " | occupation=" + n.occupation + " | home=" + n.residence
            + " | goal=" + n.longTermGoal + " | work=" + n.workStart + "-" + n.workEnd
            + " | relationships=" + n.personalRelationships);
    }
    private static int updateProfile(ServerCommandSource src, int slot, String field, String value) {
        if (!isOwner(src) || life == null || slot > life.count()) return say(src, "Owner and existing Nao required.");
        NaoLifeStore.Nao n = life.get(slot - 1);
        if (!value.matches("[A-Za-z0-9_/-]{1,48}")) return say(src, "Use 1-48 letters, digits, _, / or -.");
        switch (field) {
            case "job" -> n.occupation = value;
            case "home" -> n.residence = value;
            case "goal" -> n.longTermGoal = value;
            default -> { return say(src, "Unknown profile field."); }
        }
        try { life.save(); return profile(src, slot); }
        catch (IOException ex) { return say(src, "Profile save failed: " + ex.getMessage()); }
    }
    private static int relationshipRole(ServerCommandSource src, int from, int to, String role) {
        if (!isOwner(src) || life == null || from > life.count() || to > life.count() || from == to)
            return say(src, "Two distinct existing Naos and owner required.");
        if (!org.nagineco.doppel.core.SocialRules.validNaoRelationship(role))
            return say(src, "Invalid relationship role.");
        life.get(from - 1).personalRelationships.put(life.get(to - 1).id, role);
        try { life.save(); return say(src, "Nao " + from + " -> " + to + ": " + role); }
        catch (IOException ex) { return say(src, "Relationship save failed: " + ex.getMessage()); }
    }
    private static void dailyLifeEvents() {
        if (!freePlay || life == null || memories == null || world == null || loopTick % 200 != 0) return;
        long currentDay = Math.floorDiv(world.getTimeOfDay(), 24000);
        int hour = (int) ((world.getTimeOfDay() % 24000 + 6000) % 24000 / 1000);
        for (int i = 0; i < life.count(); i++) {
            NaoLifeStore.Nao n = life.get(i);
            if (n.lastRoutineDay != currentDay && hour >= 9) {
                // One bounded work/practice event per simulated day, never once per tick.
                // The routine marker is persisted by NaoLifeStore with the normal checkpoint.
                try {
                    ensurePhysicalSkills(life.count());
                    physicalSkills.configure(physicalSkillMode, 1);
                    physicalSkills.practice(i, 30, 0.75, 0.4);
                } catch (IOException | IllegalArgumentException ex) {
                    LOG.warning("Physical skill routine deferred: " + ex.getMessage());
                }

                n.lastRoutineDay = currentDay;
                String activity = hour < n.workEnd ? "work:" + n.occupation : "home:" + n.residence;
                memories.rememberEvent(i, day, loopTick, "life_routine",
                    "activity=" + activity + " long_term_goal=" + n.longTermGoal);
                n.lastAction = "ROUTINE";
            }
            if (n.lastRelationshipDay != currentDay && hour >= 18 && !n.personalRelationships.isEmpty()) {
                n.lastRelationshipDay = currentDay;
                for (Map.Entry<String, String> relation : n.personalRelationships.entrySet())
                    memories.rememberEvent(i, day, loopTick, "life_relationship",
                        "with=" + relation.getKey() + " role=" + relation.getValue()
                            + " activity=evening_checkin");
            }
        }
    }
    private static Path peopleFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("doppelganger/people.json");
    }
    private static Path socialFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("doppelganger/nao_social.json");
    }
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
        ensurePhysicalSkills(life.count());
        // Validate stable logical UUIDs and appearance keys before publishing a checkpoint.
        // Existing armor-stand stand-ins remain unchanged until rendering integration.
        try { life.appearanceRegistry(); }
        catch (RuntimeException ex) { throw new IOException("Invalid Nao appearance registry", ex); }
        life.save();
        social = new NaoSocialStore(socialFile());
        social.load();
        people = new NaoPeopleRegistry(peopleFile());
        people.load();
        for (int i = 0; i < life.count(); i++) {
            NaoLifeStore.Nao n = life.get(i);
            people.add(n.id, "Nao_" + n.number, "NAO");
        }
        people.save();
        sharedKnowledge = new SharedMemoryReader(memories.directory().resolve("nao_integrated_memory.md"));
        sharedKnowledge.refresh();
    }
    private static Path sessionOwnerFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("doppelganger/session-owner.txt");
    }
    private static String integrateAndReplicate() throws IOException {
        memories.flush();
        String result = MemoryIntegrationPatch.integrate(memories.directory());
        memories.replicateAll(life == null ? tracks.size() : life.count());
        return result + "; full attributed copies replicated";
    }
    private static Path mipCompletedFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("doppelganger/mip-completed.txt");
    }
    private static void writeAtomic(Path file, String value) throws IOException {
        org.nagineco.doppel.core.AtomicTextFile.write(file, value);
    }
    private static void restoreMipCompletion() {
        lastIntegratedTrainingDay = Integer.MIN_VALUE;
        try {
            if (Files.isRegularFile(mipCompletedFile()))
                lastIntegratedTrainingDay = Integer.parseInt(
                    Files.readString(mipCompletedFile(), StandardCharsets.UTF_8).trim());
        } catch (IOException | NumberFormatException ex) {
            LOG.warning("Could not restore MIP completion marker: " + ex.getMessage());
        }
    }
    private static long lastHeadlessResumeAttempt = Long.MIN_VALUE;
    private static void tryAutomaticResume(MinecraftServer server) {
        if (recording || server.getTicks() % 40 != 0 || !Files.isRegularFile(sessionOwnerFile())) return;
        try {
            UUID expected = UUID.fromString(Files.readString(sessionOwnerFile(), StandardCharsets.UTF_8).trim());
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(expected);
            if (offlineNpcMode && lifeAiEnabled) {
                // Do not require the owner to connect before resuming autonomous NPCs.
                // Throttle failed attempts to avoid repeated file restores and log spam.
                if (lastHeadlessResumeAttempt != Long.MIN_VALUE
                        && server.getTicks() - lastHeadlessResumeAttempt < 1200) return;
                lastHeadlessResumeAttempt = server.getTicks();
                restoreSession(server, null, expected, false);
            } else if (player != null) {
                restoreSession(server, player, expected, true);
            }
        } catch (IOException | IllegalArgumentException ex) {
            LOG.warning("Automatic DNMS resume deferred: " + ex.getMessage());
        }
    }
    private static int socialTalk(ServerCommandSource src, int from, int to) {
        if (!isOwner(src)) return say(src, "Only playable Nao may trigger social interactions.");
        if (social == null || life == null || from == to || from > life.count() || to > life.count())
            return say(src, "Both distinct Nao identities must be initialized.");
        var result = social.engine().apply(UUID.fromString(life.get(from - 1).id),
            UUID.fromString(life.get(to - 1).id),
            org.nagineco.doppel.social.NaoRelationshipEngine.Interaction.TALK);
        life.observeAction(from - 1, NaoActionLearning.Action.TALK, true);
        try { social.save(); life.save(); }
        catch (IOException ex) { return say(src, "Social event applied but persistence failed: " + ex.getMessage()); }
        return say(src, "Nao " + from + " talked to " + to + "; familiarity=" + result.familiarity());
    }
    private static int socialStatus(ServerCommandSource src, int from, int to) {
        if (social == null || life == null || from == to || from > life.count() || to > life.count())
            return say(src, "Both distinct Nao identities must be initialized.");
        var r = social.engine().get(UUID.fromString(life.get(from - 1).id),
            UUID.fromString(life.get(to - 1).id));
        return say(src, "Nao " + from + " -> " + to + ": familiarity=" + r.familiarity()
            + ", trust=" + r.trust() + ", affection=" + r.affection()
            + ", romanceOptIn=" + r.romanceOptIn());
    }
    /** Lightweight context-based dialogue, not generative language understanding. */
    private static void autonomousSocialTick() {
        if (!lifeAiEnabled || social == null || life == null || memories == null) return;
        if (loopTick % 400 != 0 || ghosts.size() < 2) return;
        var engine = social.engine();
        // Minecraft adapter: snapshot world coordinates for the game-independent policy.
        int count = Math.min(ghosts.size(), life.count());
        boolean[] active = new boolean[count];
        double[] x = new double[count], y = new double[count], z = new double[count];
        for (int i = 0; i < count; i++) {
            ArmorStandEntity ghost = ghosts.get(i);
            active[i] = !ghost.isRemoved();
            if (active[i]) {
                Vec3d position = ghost.getPos();
                x[i] = position.x;
                y[i] = position.y;
                z[i] = position.z;
            }
        }
        for (int i = 0; i < count; i++) {
            if (!active[i]) continue;
            int partner = org.nagineco.doppel.core.SocialPartnerSelector.nearest(
                i, active, x, y, z, 36.0);
            if (partner < 0 || i > partner) continue; // One conversation per pair.
            UUID a = UUID.fromString(life.get(i).id);
            UUID b = UUID.fromString(life.get(partner).id);
            var beforeA = engine.get(a, b);
            var beforeB = engine.get(b, a);
            NaoLifeStore.Nao speaker = life.get(i);
            NaoLifeStore.Nao listener = life.get(partner);
            String[] openings = {"そういえば、", "ねえ、", "ちょっと聞いて。", "この前のことだけど、"};
            String opening = openings[Math.floorMod(i * 7 + partner + day, openings.length)];
            String topic = switch (speaker.goal) {
                case COLLECT -> "集めたものが " + speaker.inventory.size()
                    + " 種類あるんだ。必要なものはある？";
                case BUILD -> "次は何を作ろうか。手伝ってくれる？";
                case EXPLORE -> "ここは " + ghosts.get(i).getBlockPos().toShortString()
                    + " だね。近くで面白い場所を探そう。";
                case REST -> "今日は少し疲れたよ。休みながら話そう。";
                case SAFETY -> "危険な場所には気をつけよう。";
                case SOCIAL -> "最近どうしてる？一緒に何かしたいな。";
            };
            String recalled = "";
            if (sharedKnowledge != null) {
                var memory = sharedKnowledge.nearest(speaker.goal, ghosts.get(i).getPos(), 16);
                if (memory.isPresent()) {
                    var e = memory.get();
                    recalled = " 奈央" + e.nao() + "が以前この辺りで"
                        + e.reason().name().toLowerCase(java.util.Locale.ROOT)
                        + "していた記録も覚えているよ。";
                }
            }
            String relationship = beforeA.trust() >= 70
                ? "あなたとなら安心して話せる。" : beforeA.familiarity() <= 4
                ? "まだお互いのことをよく知らないけど、" : "";
            String response = switch (listener.goal) {
                case COLLECT -> "まず材料を探してみよう。";
                case BUILD -> "一緒に形にしてみたい。";
                case EXPLORE -> "面白そう、行ってみよう。";
                case REST -> "少し休んでからね。";
                case SAFETY -> "安全を確かめてからにしよう。";
                case SOCIAL -> "うん、もっと話そう。";
            };
            String line = opening + relationship + topic + recalled;
            String reply = "奈央" + (partner + 1) + ": " + response;
            engine.apply(a, b, org.nagineco.doppel.social.NaoRelationshipEngine.Interaction.TALK);
            engine.apply(b, a, org.nagineco.doppel.social.NaoRelationshipEngine.Interaction.TALK);
            life.learn(i, NaoLifeStore.Goal.SOCIAL);
            life.learn(partner, NaoLifeStore.Goal.SOCIAL);
            life.observeAction(i, NaoActionLearning.Action.TALK, true);
            life.observeAction(partner, NaoActionLearning.Action.TALK, true);
            memories.rememberEvent(i, day, loopTick, "npc_dialogue",
                "to=Nao" + (partner + 1) + " text=" + line);
            memories.rememberEvent(partner, day, loopTick, "npc_dialogue",
                "from=Nao" + (i + 1) + " text=" + line + " reply=" + response);
            report("Nao " + (i + 1) + " -> Nao " + (partner + 1) + ": " + line);
            report(reply);
            // Romance is mutual and gradual. Neither participant can unilaterally
            // force a relationship; each independently opts in after familiarity.
            if (beforeA.familiarity() >= 12 && beforeA.trust() >= 50
                    && beforeB.familiarity() >= 12 && beforeB.trust() >= 50) {
                if (!beforeA.romanceOptIn()) engine.setRomanceOptIn(a, b, true);
                if (!beforeB.romanceOptIn()) engine.setRomanceOptIn(b, a, true);
                var ra = engine.apply(a, b,
                    org.nagineco.doppel.social.NaoRelationshipEngine.Interaction.ROMANCE_REQUEST);
                var rb = engine.apply(b, a,
                    org.nagineco.doppel.social.NaoRelationshipEngine.Interaction.ROMANCE_REQUEST);
                if (ra.affection() > beforeA.affection() && rb.affection() > beforeB.affection()) {
                    memories.rememberEvent(i, day, loopTick, "npc_romance",
                        "mutual relationship with Nao" + (partner + 1));
                    memories.rememberEvent(partner, day, loopTick, "npc_romance",
                        "mutual relationship with Nao" + (i + 1));
                    report("Nao " + (i + 1) + " and Nao " + (partner + 1)
                        + " grew closer by mutual choice.");
                }
            }
        }
    }

    private static NaoRegionRollback regionSnapshot;
    private static boolean regionRollbackEnabled;
    private static final NaoControlSession controlSession = new NaoControlSession();
    private static Vec3d ownerBeforeControl;

    private static int regionOn(ServerCommandSource src, int radius) {
        if (!isOwner(src) || !recording || world == null) return say(src, "Active owner session required.");
        ServerPlayerEntity player = src.getServer().getPlayerManager().getPlayer(owner);
        if (player == null) return say(src, "Owner offline.");
        try {
            regionSnapshot = NaoRegionRollback.capture(world, player.getBlockPos(), radius, 4);
            regionRollbackEnabled = true;
            return say(src, "Bounded block rollback enabled for " + regionSnapshot.size() + " blocks; no block entities.");
        } catch (RuntimeException ex) { return say(src, "Region snapshot refused: " + ex.getMessage()); }
    }
    private static int regionOff(ServerCommandSource src) {
        if (!isOwner(src)) return say(src, "Owner only.");
        regionRollbackEnabled = false;
        regionSnapshot = null;
        return say(src, "Bounded block rollback disabled.");
    }
    private static int advanceFreePlayDay(ServerCommandSource src) {
        if (!recording || !freePlay || tracks.size() < CHARACTERS || life == null
                || life.count() < CHARACTERS)
            return say(src, "All 24 Nao identities must be present before advancing free-play days.");
        if (!isOwner(src)) return say(src, "Only the playable Nao owner may advance the day.");
        ServerPlayerEntity player = src.getServer().getPlayerManager().getPlayer(owner);
        if (player == null || player.getServerWorld() != world)
            return say(src, "Playable Nao must be online in the session dimension.");
        integrateTrainingMidnight();
        try { saveSession(); }
        catch (IOException ex) { return say(src, "Day advance cancelled: " + ex.getMessage()); }
        day++;
        loopTick = 0;
        world.setTimeOfDay(0); // Advance the clock only; preserve NPC state and world changes.
        scheduleInitialized = false;
        lastObservedTime = Long.MIN_VALUE;
        resetObservations(player);
        report("Free-play day " + day + " started; all NPC brains and positions retained.");
        try { saveSession(); }
        catch (IOException ex) { report("New-day checkpoint failed: " + ex.getMessage()); }
        return say(src, "Free-play day " + day + ": selected playable Nao "
            + (selected + 1) + "; other Nao NPC AI continues.");
    }

    private static int controlNao(ServerCommandSource src, int slot) {
        if (!isOwner(src) || !freePlay || life == null || tracks.size() < CHARACTERS || slot > life.count()
                || slot > ghosts.size() || ghosts.get(slot - 1).isRemoved())
            return say(src, "Free-play and a live NPC are required.");
        ServerPlayerEntity player = src.getServer().getPlayerManager().getPlayer(owner);
        if (player == null) return say(src, "Owner offline.");
        if (controlSession.selection().mode() == NaoControlSession.Mode.OWNER)
            ownerBeforeControl = player.getPos();
        // Release the previous controlled NPC before assigning the new one.
        controlSession.select(slot, UUID.fromString(life.get(slot - 1).id));
        selected = slot - 1;
        Vec3d pos = ghosts.get(selected).getPos();
        player.teleport(world, pos.x, pos.y, pos.z, player.getYaw(), player.getPitch());
        return say(src, "Playable character is Nao " + slot
            + "; this NPC AI pauses while controlled; all other NPCs continue.");
    }
    private static int controlRelease(ServerCommandSource src) {
        if (!isOwner(src)) return say(src, "Owner only.");
        controlSession.release();
        ServerPlayerEntity player = src.getServer().getPlayerManager().getPlayer(owner);
        if (player != null && world != null && ownerBeforeControl != null)
            player.teleport(world, ownerBeforeControl.x, ownerBeforeControl.y,
                ownerBeforeControl.z, player.getYaw(), player.getPitch());
        ownerBeforeControl = null;
        return say(src, "Owner control restored.");
    }
    private static int recallNao(ServerCommandSource src, int slot, String topic) {
        if (life == null || memories == null || slot > life.count()) return say(src, "Nao not initialized.");
        try {
            var result = NaoMemoryRecall.recall(memories.directory(), slot, topic);
            if (result.isEmpty()) return say(src, "No matching personal or replicated episode.");
            life.get(slot - 1).lastRecalledMemory = result.get();
            return say(src, "Nao " + slot + " recalls: " + result.get());
        } catch (IOException ex) { return say(src, "Recall failed: " + ex.getMessage()); }
    }
    private static String personId(String token) {
        if (token.matches("nao_[0-9]{1,3}") && life != null) {
            int slot = Integer.parseInt(token.substring(4));
            if (slot >= 1 && slot <= life.count()) return life.get(slot - 1).id;
        }
        return token;
    }
    private static int personAdd(ServerCommandSource src, String id) {
        if (!isOwner(src) || people == null) return say(src, "Owner and initialized session required.");
        if (!org.nagineco.doppel.core.SocialRules.validExternalPersonId(id)) return say(src, "Invalid person ID.");
        people.add(id, id, "EXTERNAL");
        try { people.save(); return say(src, "Registered external person: " + id); }
        catch (IOException ex) { return say(src, "Registry save failed: " + ex.getMessage()); }
    }
    private static int personRelate(ServerCommandSource src, String first, String second, String role) {
        if (!isOwner(src) || people == null) return say(src, "Owner and initialized session required.");
        if (!org.nagineco.doppel.core.SocialRules.validPersonRelationship(role))
            return say(src, "Invalid role.");
        try {
            people.relate(personId(first), personId(second), role);
            people.save(); return say(src, "Relationship saved: " + first + " -> " + second + " = " + role);
        } catch (IllegalArgumentException | IOException ex) { return say(src, ex.getMessage()); }
    }
    private static int personEvent(ServerCommandSource src, String first, String second, String event) {
        if (!isOwner(src) || people == null) return say(src, "Owner and initialized session required.");
        try {
            people.event(personId(first), personId(second), event);
            people.save(); return say(src, "Shared life event saved.");
        } catch (IllegalArgumentException | IOException ex) { return say(src, ex.getMessage()); }
    }
    private static int personStatus(ServerCommandSource src, String id) {
        if (people == null) return say(src, "No people registry.");
        var p = people.get(personId(id));
        return p == null ? say(src, "Unknown person") : say(src, p.name + " (" + p.kind
            + ") relationships=" + p.relationships + " sharedEvents=" + p.sharedEvents.size());
    }
    private static int planStatus(ServerCommandSource src, int slot) {
        if (life == null || slot > life.count()) return say(src, "Nao not initialized.");
        var plan = NaoGoalPlanner.choosePersonal(life.get(slot - 1), sharedKnowledge, world == null ? 0 : world.getTime());
        return say(src, "Nao " + slot + " plan=" + plan.primary()
            + " fallback=" + plan.fallback() + " evidence=" + plan.evidence());
    }
    private static int growthProposal(ServerCommandSource src, int slot) {
        if (!isOwner(src) || life == null || slot > life.count()) return say(src, "Playable Nao owner required.");
        var nao = life.get(slot - 1);
        var growth = new NaoPersonalityGrowth();
        for (var action : NaoActionLearning.Action.values()) {
            int attempts = nao.actionAttempts.getOrDefault(action.name(), 0);
            int successes = nao.actionSuccesses.getOrDefault(action.name(), 0);
            for (int i = 0; i < Math.min(1000, attempts); i++)
                growth.observe(action.name().toLowerCase(java.util.Locale.ROOT), i < successes);
        }
        var suggestions = growth.suggestions();
        if (suggestions.isEmpty()) return say(src, "At least 10 attempts needed for a proposal.");
        for (var proposal : suggestions)
            src.sendFeedback(() -> Text.literal("[Doppel] Playable Nao proposal: "
                + proposal.trait() + "=" + proposal.proposedStrength() + "/10 from "
                + proposal.observations() + " observations. NOT applied."), false);
        return suggestions.size();
    }
    private static boolean mannequinAnimation = true;
    private static Path mannequinAnimationFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("doppelganger/mannequin-animation.enabled");
    }
    private static int appearanceAnimation(ServerCommandSource src, boolean enabled) {
        if (!isOwner(src)) return say(src, "Only playable Nao may change mannequin animation.");
        try {
            Files.createDirectories(mannequinAnimationFile().getParent());
            Files.writeString(mannequinAnimationFile(), enabled ? "on" : "off", StandardCharsets.UTF_8);
            mannequinAnimation = enabled;
            return say(src, "Stand-in walking animation " + (enabled ? "ON" : "OFF"));
        } catch (IOException ex) {
            return say(src, "Cannot save mannequin animation setting: " + ex.getMessage());
        }
    }
    private static int appearanceStatus(ServerCommandSource src, int slot) {
        if (life == null || slot > life.count()) return say(src, "Nao " + slot + " is not initialized.");
        NaoLifeStore.Nao nao = life.get(slot - 1);
        return say(src, "Nao " + slot + " UUID=" + nao.id + " skinKey=" + nao.skinKey
            + " (appearance metadata only; currently rendered as armor stand).");
    }
    private static int appearanceSet(ServerCommandSource src, int slot, String skinKey) {
        if (!isOwner(src)) return say(src, "Only playable Nao may set NPC appearance.");
        if (life == null || slot > life.count()) return say(src, "Nao " + slot + " is not initialized.");
        NaoLifeStore.Nao nao = life.get(slot - 1);
        try {
            new org.nagineco.doppel.appearance.NaoAppearanceRegistry.NaoAppearance(
                slot, UUID.fromString(nao.id), skinKey, true);
        } catch (IllegalArgumentException ex) {
            return say(src, "Invalid skin key. Use 1-128 letters, digits, _, ., /, :, -; no '..'.");
        }
        String previous = nao.skinKey;
        nao.skinKey = skinKey;
        try {
            life.save();
        } catch (IOException ex) {
            nao.skinKey = previous;
            return say(src, "Appearance save failed: " + ex.getMessage());
        }
        return say(src, "Nao " + slot + " skin key saved as " + skinKey
            + "; model/skin rendering not implemented yet.");
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
    private static int lastIntegratedTrainingDay = Integer.MIN_VALUE;
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
    private static int growthTick = 0; // default 06:00: training day start
    private static long growthObserved = Long.MIN_VALUE;
    private static long growthDay = Long.MIN_VALUE;
    private static Path growthFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("doppelganger/growth.time");
    }
    private static int growthStatus(ServerCommandSource src) {
        return say(src, "Nao growth: +1 per day at " + NaoGrowthPatch.formatClock(growthTick)
            + " (Minecraft time), maximum 24; training only.");
    }
    private static int growthTime(ServerCommandSource src, String clock) {
        if (!isOwner(src)) return say(src, "Only playable Nao may set growth time.");
        int tick;
        try { tick = NaoGrowthPatch.parseClock(clock); }
        catch (IllegalArgumentException ex) { return say(src, ex.getMessage()); }
        try {
            Files.createDirectories(growthFile().getParent());
            Files.writeString(growthFile(), clock, StandardCharsets.UTF_8);
        } catch (IOException ex) { return say(src, "Growth schedule save failed: " + ex.getMessage()); }
        growthTick = tick;
        growthObserved = Long.MIN_VALUE;
        return say(src, "Nao growth scheduled daily at " + clock + "; +1, up to 24.");
    }
    private static void tickGrowth(MinecraftServer server) {
        if (!recording || freePlay || world == null || owner == null || tracks.size() >= CHARACTERS) return;
        long now = world.getTimeOfDay();
        long previous = growthObserved;
        growthObserved = now;
        long dayIndex = Math.floorDiv(now, DAY_TICKS);
        if (!NaoGrowthPatch.crossed(previous, now, growthTick) || dayIndex == growthDay) return;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(owner);
        if (player == null || player.getServerWorld() != world) return;
        growthDay = dayIndex;
        // Reuse the established one-at-a-time transition and its checkpoint.
        next(player.getCommandSource());
    }
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
    private static void rememberGhostPositions() {
        if (life == null) return;
        for (int i = 0; i < ghosts.size() && i < life.count(); i++) {
            ArmorStandEntity stand = ghosts.get(i);
            if (!stand.isRemoved()) life.rememberPosition(i, stand.getX(), stand.getY(), stand.getZ());
        }
    }
    private static void saveSession() throws IOException {
        if (memories == null) return;
        memories.flush();
        NpcLearningCheckpoint.save(memories.directory(), brains);
        savePhysicalSkills();
        RecordingSnapshotStore.save(snapshotFile(), tracks, day, selected, freePlay);
        ServerPlayerEntity player = owner == null || world == null ? null
            : world.getServer().getPlayerManager().getPlayer(owner);
        if (player == null || player.getServerWorld() != world)
            throw new IOException("Playable Nao must be online in the recording dimension to checkpoint");
        Path playerFile = playerSnapshotFile();
        PlayerStateStore.save(playerFile, player);
        rememberGhostPositions();
        if (life != null) life.save();
        if (social != null) social.save();
        if (people != null) people.save();
        if (owner != null) writeAtomic(sessionOwnerFile(), owner.toString());
        GenerationCheckpointStore.publish(checkpointRoot(), snapshotFile(),
            memories.directory(), playerFile, life == null ? null : lifeFile(),
            social == null ? null : socialFile(), people == null ? null : peopleFile(),
            physicalSkills == null ? null : physicalSkillsFile());
    }
    private static void saveOfflineSession() throws IOException {
        if (memories == null || owner == null || world == null) return;
        Path playerFile = playerSnapshotFile();
        if (!Files.isRegularFile(playerFile))
            throw new IOException("Missing last playable owner snapshot");
        memories.flush();
        NpcLearningCheckpoint.save(memories.directory(), brains);
        savePhysicalSkills();
        RecordingSnapshotStore.save(snapshotFile(), tracks, day, selected, freePlay);
        rememberGhostPositions();
        if (life != null) life.save();
        if (social != null) social.save();
        if (people != null) people.save();
        writeAtomic(sessionOwnerFile(), owner.toString());
        GenerationCheckpointStore.publish(checkpointRoot(), snapshotFile(),
            memories.directory(), playerFile, life == null ? null : lifeFile(),
            social == null ? null : socialFile(), people == null ? null : peopleFile(),
            physicalSkills == null ? null : physicalSkillsFile());
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
        if (recording) return say(src, "Session already active; select a playable Nao with /doppel control select <slot>.");
        ServerPlayerEntity player;
        try { player = src.getPlayerOrThrow(); }
        catch (Exception ex) { return say(src, "Run as an operator player."); }
        return restoreSession(src.getServer(), player, player.getUuid(), true);
    }
    private static int restoreSession(MinecraftServer server, ServerPlayerEntity player,
                                      UUID expectedOwner, boolean restoreOwnerSnapshot) {
        if (recording) return 0;
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
            if (restoreOwnerSnapshot && playerState != null && !player.getServerWorld().getRegistryKey().getValue().toString()
                    .equals(playerState.dimension))
                return say(player.getCommandSource(), "Move to saved dimension before resuming: " + playerState.dimension);
            if (generation != null && Files.isRegularFile(generation.resolve("nao_lives.json"))) {
                Files.createDirectories(lifeFile().getParent());
                Files.copy(generation.resolve("nao_lives.json"), lifeFile(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            if (generation != null && Files.isRegularFile(generation.resolve("nao_social.json"))) {
                Files.createDirectories(socialFile().getParent());
                Files.copy(generation.resolve("nao_social.json"), socialFile(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            if (generation != null && Files.isRegularFile(generation.resolve("people.json"))) {
                Files.createDirectories(peopleFile().getParent());
                Files.copy(generation.resolve("people.json"), peopleFile(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            if (generation != null) {
                Path savedPhysical = generation.resolve("physical-skills.bin");
                if (Files.isRegularFile(savedPhysical)) {
                    Files.createDirectories(physicalSkillsFile().getParent());
                    Files.copy(savedPhysical, physicalSkillsFile(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } else {
                    // Legacy checkpoints have no physical skills: never reuse newer,
                    // potentially unsaved skill data from the working directory.
                    Files.deleteIfExists(physicalSkillsFile());
                }
                physicalSkills = null;
            }
            if (generation != null) GenerationCheckpointStore.restoreMemories(generation,
                FabricLoader.getInstance().getGameDir().resolve("config/doppelganger/memories"));
            loadedTracks = RecordingSnapshotStore.restoreTracks(snapshot);
            if (loadedTracks.isEmpty()) { LOG.warning("DNMS checkpoint has no recordings"); return 0; }
            if (generation != null) {
                Path learningManifest = generation.resolve("learning_count.txt");
                if (Files.isRegularFile(learningManifest)) {
                    int learningCount;
                    try {
                        learningCount = Integer.parseInt(Files.readString(learningManifest).trim());
                    } catch (NumberFormatException ex) {
                        throw new IOException("Invalid NPC learning checkpoint count", ex);
                    }
                    if (learningCount != loadedTracks.size())
                        throw new IOException("NPC learning checkpoint count " + learningCount
                            + " differs from recorded NPC count " + loadedTracks.size());
                }
            }
            loadedMemories = new PersonalityMemoryStore(
                FabricLoader.getInstance().getGameDir().resolve("config/doppelganger/memories"));
            for (int i = 0; i < loadedTracks.size(); i++) {
                loadedBrains.add(NpcLearningCheckpoint.load(loadedMemories.directory(), i, loadedMemories));
            }
            if (player == null) {
                if (!snapshot.freePlay || loadedTracks.size() < CHARACTERS || playerState == null)
                    throw new IOException("Unattended restoration requires completed 24-Nao free-play and saved owner dimension");
                Identifier dimension = Identifier.tryParse(playerState.dimension);
                if (dimension == null || server.getWorld(RegistryKey.of(RegistryKeys.WORLD, dimension)) == null)
                    throw new IOException("Saved NPC dimension is unavailable: " + playerState.dimension);
            }
            restoredPlayerState = playerState;
            memories = loadedMemories;
            initializeLife(loadedTracks.size());
            if (life.count() != loadedTracks.size())
                throw new IOException("Checkpoint identity count differs from track count: "
                    + life.count() + " vs " + loadedTracks.size());
            if (restoreOwnerSnapshot && restoredPlayerState != null) PlayerStateStore.restore(player, restoredPlayerState);
        } catch (IOException ex) {
            LOG.warning("Could not resume DNMS session: " + ex.getMessage());
            return player == null ? 0 : say(player.getCommandSource(), "Could not resume session: " + ex.getMessage());
        }
        clearGhosts();
        tracks.clear();
        tracks.addAll(loadedTracks);
        brains.clear();
        brains.addAll(loadedBrains);
        memories = loadedMemories;
        owner = expectedOwner;
        if (player == null) {
            if (restoredPlayerState == null) {
                LOG.warning("Cannot restore unattended DNMS session without saved player dimension");
                return 0;
            }
            Identifier dimension = Identifier.tryParse(restoredPlayerState.dimension);
            world = dimension == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, dimension));
            if (world == null) {
                LOG.warning("Saved DNMS dimension unavailable: " + restoredPlayerState.dimension);
                return 0;
            }
        } else world = player.getServerWorld();
        dawn = tracks.get(0).isEmpty() ? (player == null
            ? BlockPos.ofFloored(restoredPlayerState.x, restoredPlayerState.y, restoredPlayerState.z)
            : player.getBlockPos())
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
        if (player != null) resetObservations(player);
        if (!freePlay && player != null) {
            List<Vec3d> active = tracks.get(tracks.size() - 1);
            if (!active.isEmpty() && restoredPlayerState == null) {
                Vec3d last = active.get(active.size() - 1);
                player.teleport(world, last.x, last.y, last.z, player.getYaw(), player.getPitch());
            }
            brains.get(tracks.size() - 1).resetObservationAnchor(player.getPos());
        }
        scheduleInitialized = false;
        restoreMipCompletion();
        spawnGhosts();
        report("Resumed JSON snapshot with " + tracks.size() + " recorded Nao tracks.");
        if (player == null) {
            LOG.info("DNMS unattended NPC simulation restored: " + tracks.size() + " identities, day " + day);
            return 1;
        }
        return say(player.getCommandSource(), "Resumed saved session: " + tracks.size()
            + "/24 tracks, day " + day + ". Automatic movement remains OFF.");
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
            Path setting = mannequinAnimationFile();
            mannequinAnimation = !Files.exists(setting)
                || !Files.readString(setting, StandardCharsets.UTF_8).trim().equalsIgnoreCase("off");
        } catch (IOException ex) {
            mannequinAnimation = true;
            LOG.warning("Could not load mannequin animation setting: " + ex.getMessage());
        }
        try { if (Files.exists(growthFile())) growthTick = NaoGrowthPatch.parseClock(Files.readString(growthFile(), StandardCharsets.UTF_8).trim()); }
        catch (IOException | IllegalArgumentException ex) { growthTick = 0; LOG.warning("Growth time reset to 06:00: " + ex.getMessage()); }
        try {
            Path file = rewardSettingFile();
            rewardLearning = Files.exists(file) && Files.readString(file, StandardCharsets.UTF_8).trim().equalsIgnoreCase("on");
        } catch (IOException ex) { rewardLearning = false; }
        try {
            if (Files.isRegularFile(physicalSkillModeFile()))
                physicalSkillMode = org.nagineco.doppel.core.PhysicalSkillSync.Mode.parse(
                    Files.readString(physicalSkillModeFile(), StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException ex) {
            physicalSkillMode = org.nagineco.doppel.core.PhysicalSkillSync.Mode.FULL;
        }
        try {
            offlineNpcMode = Files.isRegularFile(offlineNpcModeFile())
                && Files.readString(offlineNpcModeFile(), StandardCharsets.UTF_8).trim().equalsIgnoreCase("on");
        } catch (IOException ex) { offlineNpcMode = false; }
        try {
            Path setting = mipSettingFile();
            if (Files.exists(setting))
                mipEnabled = !Files.readString(setting, StandardCharsets.UTF_8).trim().equalsIgnoreCase("off");
        } catch (IOException ex) {
            logMIP("Could not load enabled setting; using default ON: " + ex.getMessage());
        }
        try {
            mipScheduleTick = Files.isRegularFile(mipScheduleFile())
                ? parseMinecraftClock(Files.readString(mipScheduleFile(), StandardCharsets.UTF_8).trim())
                : 18000;
        } catch (IOException | IllegalArgumentException ex) {
            mipScheduleTick = 18000;
            LOG.warning("Invalid MIP schedule; defaulting to 00:00: " + ex.getMessage());
        }
        logMIP("Initialized; enabled=" + mipEnabled + "; daily time="
            + formatMinecraftClock(mipScheduleTick) + ". Use /doppel mip status|on|off|run|logs|time.");
        try { configuredMaxNaos = Files.isRegularFile(maxNaosFile())
            ? Integer.parseInt(Files.readString(maxNaosFile(), StandardCharsets.UTF_8).trim()) : 256;
            if (configuredMaxNaos < 1 || configuredMaxNaos > 256) configuredMaxNaos = DEFAULT_NAOS;
        } catch (IOException | NumberFormatException ex) { configuredMaxNaos = DEFAULT_NAOS; }
        // Initialize the actual DNMS identity, memory, and mannequin pipeline headlessly.
        // Isolated CI command: never available on ordinary game servers.
        if (Boolean.getBoolean("dnms.ci.smoke")) {
            CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
                dispatcher.register(literal("dnms_ci_bootstrap")
                    .requires(source -> source.hasPermissionLevel(4) && source.getEntity() == null)
                    .executes(context -> {
                        if (recording) return 0;
                        MinecraftServer server = context.getSource().getServer();
                        clearGhosts();
                        tracks.clear();
                        brains.clear();
                        reports.clear();
                        try {
                            memories = new PersonalityMemoryStore(FabricLoader.getInstance().getGameDir()
                                .resolve("config/doppelganger/memories"));
                            initializeLife(DEFAULT_NAOS);
                            world = server.getOverworld();
                            dawn = world.getSpawnPos();
                            owner = UUID.nameUUIDFromBytes("dnms-ci-owner".getBytes(StandardCharsets.UTF_8));
                            day = 1;
                            selected = 0;
                            loopTick = 0;
                            for (int slot = 0; slot < DEFAULT_NAOS; slot++) {
                                tracks.add(new ArrayList<>(List.of(Vec3d.ofCenter(dawn))));
                                HabitBrain brain = new HabitBrain();
                                memories.restore(slot, brain);
                                memories.restoreRewards(slot, brain);
                                brains.add(brain);
                            }
                            freePlay = true;
                            recording = true;
                            aiMode = true;
                            lifeAiEnabled = true;
                            offlineNpcMode = true;
                            spawnGhosts();
                            if (ghosts.size() != DEFAULT_NAOS || life.count() < DEFAULT_NAOS)
                                throw new IllegalStateException("Incorrect DNMS identity/ghost count");
                            ciInitialPositions.clear();
                            for (ArmorStandEntity entity : ghosts) ciInitialPositions.add(entity.getPos());
                            ciMovementStartTick = world.getTime();
                            LOG.info("DNMS_CI_REAL_BOOTSTRAP_OK identities=" + life.count()
                                + " ghosts=" + ghosts.size());
                            return 1;
                        } catch (Exception ex) {
                            LOG.severe("DNMS_CI_REAL_BOOTSTRAP_FAILED " + ex);
                            return 0;
                        }
                    })));
        }
        if (Boolean.getBoolean("dnms.ci.smoke")) {
            CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
                dispatcher.register(literal("dnms_ci_movement")
                    .requires(source -> source.hasPermissionLevel(4) && source.getEntity() == null)
                    .executes(context -> {
                        if (!recording || !freePlay || !lifeAiEnabled || !offlineNpcMode
                                || world == null || ciInitialPositions.size() != 24
                                || ghosts.size() != 24 || ciMovementStartTick < 0) {
                            LOG.severe("DNMS_CI_MOVEMENT_FAILED not_initialized");
                            return 0;
                        }
                        long elapsed = world.getTime() - ciMovementStartTick;
                        int moved = 0;
                        double maxDistance = 0;
                        for (int slot = 0; slot < 24; slot++) {
                            if (ghosts.get(slot).isRemoved()) {
                                LOG.severe("DNMS_CI_MOVEMENT_FAILED missing_entity=" + slot);
                                return 0;
                            }
                            double distance = ghosts.get(slot).getPos().distanceTo(ciInitialPositions.get(slot));
                            if (distance > 0.05) moved++;
                            maxDistance = Math.max(maxDistance, distance);
                        }
                        LOG.info("DNMS_CI_MOVEMENT_RESULT elapsed_ticks=" + elapsed
                            + " moved=" + moved + "/24 max_distance=" + maxDistance);
                        if (elapsed < 100 || moved == 0) {
                            LOG.severe("DNMS_CI_MOVEMENT_FAILED elapsed_ticks=" + elapsed
                                + " moved=" + moved);
                            return 0;
                        }
                        LOG.info("DNMS_CI_MOVEMENT_OK moved=" + moved + "/24");
                        return 1;
                    })));
        }
        // Exercise real world block mutations with the production harvesting/building methods.
        if (Boolean.getBoolean("dnms.ci.smoke")) {
            CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
                dispatcher.register(literal("dnms_ci_work")
                    .requires(source -> source.hasPermissionLevel(4) && source.getEntity() == null)
                    .executes(context -> {
                        try {
                            ServerWorld testWorld = context.getSource().getServer().getOverworld();
                            BlockPos spawn = testWorld.getSpawnPos();
                            // Keep the fixture above natural terrain and within the loaded spawn area.
                            BlockPos base = spawn.add(0, 12, 0);
                            for (int dx = -2; dx <= 3; dx++)
                                for (int dz = -2; dz <= 2; dz++) {
                                    BlockPos floor = base.add(dx, -1, dz);
                                    testWorld.setBlockState(floor, Blocks.STONE.getDefaultState(), 3);
                                    for (int dy = 0; dy <= 3; dy++)
                                        testWorld.setBlockState(base.add(dx, dy, dz),
                                            Blocks.AIR.getDefaultState(), 3);
                                }
                            BlockPos sourceLog = base.add(2, 0, 0);
                            BlockPos destination = base.add(-1, 0, -1);
                            testWorld.setBlockState(sourceLog, Blocks.OAK_LOG.getDefaultState(), 3);
                            NaoLifeStore.Nao worker = new NaoLifeStore.Nao();
                            worker.energy = 100;
                            worker.hasBuildAnchor = true;
                            worker.buildAnchorX = base.getX();
                            worker.buildAnchorY = base.getY();
                            worker.buildAnchorZ = base.getZ();
                            Vec3d origin = Vec3d.ofCenter(base);
                            if (!NaoWorkPatch.harvest(testWorld, origin, worker))
                                throw new IllegalStateException("harvest returned false");
                            if (!testWorld.getBlockState(sourceLog).isAir()
                                    || worker.collection != 1
                                    || worker.inventory.getOrDefault("minecraft:oak_log", 0) != 1)
                                throw new IllegalStateException("harvest world/inventory mismatch");
                            LOG.info("DNMS_CI_HARVEST_OK collected=1 source_cleared=true");
                            if (!NaoWorkPatch.build(testWorld, origin, worker))
                                throw new IllegalStateException("build returned false");
                            if (!testWorld.getBlockState(destination).isOf(Blocks.OAK_LOG)
                                    || worker.buildPlanStep != 1 || worker.building != 1
                                    || worker.inventory.getOrDefault("minecraft:oak_log", 0) != 0)
                                throw new IllegalStateException("build world/inventory mismatch");
                            LOG.info("DNMS_CI_BUILD_OK placed=1 inventory_consumed=true step=1");
                            return 1;
                        } catch (Exception ex) {
                            LOG.severe("DNMS_CI_WORK_FAILED " + ex);
                            return 0;
                        }
                    })));
        }
        // Dedicated CI smoke command: opt-in, console-only, never enabled in normal play.
        if (Boolean.getBoolean("dnms.ci.smoke")) {
            CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
                dispatcher.register(literal("dnms_ci_spawn")
                    .requires(source -> source.hasPermissionLevel(4)
                        && source.getEntity() == null)
                    .executes(context -> {
                        MinecraftServer server = context.getSource().getServer();
                        ServerWorld testWorld = server.getOverworld();
                        BlockPos origin = testWorld.getSpawnPos();
                        int spawned = 0;
                        for (int slot = 0; slot < 24; slot++) {
                            ArmorStandEntity stand = new ArmorStandEntity(EntityType.ARMOR_STAND, testWorld);
                            stand.refreshPositionAndAngles(origin.getX() + 0.5 + slot % 6,
                                origin.getY() + 2, origin.getZ() + 0.5 + slot / 6, 0, 0);
                            stand.setNoGravity(true);
                            stand.setCustomName(Text.literal("DNMS CI Nao " + (slot + 1)));
                            if (testWorld.spawnEntity(stand)) spawned++;
                        }
                        if (spawned != 24) {
                            LOG.severe("DNMS_CI_SPAWN_FAILED count=" + spawned);
                            return 0;
                        }
                        LOG.info("DNMS_CI_SPAWN_OK count=24");
                        return 1;
                    })));
        }
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
            dispatcher.register(literal("doppel")
                .requires(s -> s.hasPermissionLevel(2))
                .then(literal("dashboard")
                    .executes(c -> dashboard(c.getSource()))
                    .then(literal("bootstrap").executes(c -> bootstrapNaos(c.getSource())))
                    .then(literal("add").then(argument("count", IntegerArgumentType.integer(1, 256))
                        .executes(c -> addNaos(c.getSource(), IntegerArgumentType.getInteger(c, "count")))))
                    .then(literal("max").then(argument("count", IntegerArgumentType.integer(1, 256))
                        .executes(c -> setMaxNaos(c.getSource(), IntegerArgumentType.getInteger(c, "count")))))
                    .then(literal("profile").then(argument("slot", IntegerArgumentType.integer(1, 256))
                        .executes(c -> profile(c.getSource(), IntegerArgumentType.getInteger(c, "slot")))))
                    .then(literal("job").then(argument("slot", IntegerArgumentType.integer(1, 256))
                        .then(argument("value", StringArgumentType.word())
                            .executes(c -> updateProfile(c.getSource(), IntegerArgumentType.getInteger(c, "slot"), "job", StringArgumentType.getString(c, "value"))))))
                    .then(literal("home").then(argument("slot", IntegerArgumentType.integer(1, 256))
                        .then(argument("value", StringArgumentType.word())
                            .executes(c -> updateProfile(c.getSource(), IntegerArgumentType.getInteger(c, "slot"), "home", StringArgumentType.getString(c, "value"))))))
                    .then(literal("goal").then(argument("slot", IntegerArgumentType.integer(1, 256))
                        .then(argument("value", StringArgumentType.word())
                            .executes(c -> updateProfile(c.getSource(), IntegerArgumentType.getInteger(c, "slot"), "goal", StringArgumentType.getString(c, "value"))))))
                    .then(literal("relation").then(argument("from", IntegerArgumentType.integer(1, 256))
                        .then(argument("to", IntegerArgumentType.integer(1, 256))
                            .then(argument("role", StringArgumentType.word())
                                .executes(c -> relationshipRole(c.getSource(), IntegerArgumentType.getInteger(c, "from"), IntegerArgumentType.getInteger(c, "to"), StringArgumentType.getString(c, "role")))))))
                )
                .then(literal("start").executes(c -> start(c.getSource())))
                .then(literal("growth")
                    .then(literal("status").executes(c -> growthStatus(c.getSource())))
                    .then(literal("time").then(argument("clock", StringArgumentType.word())
                        .executes(c -> growthTime(c.getSource(), StringArgumentType.getString(c, "clock"))))))
                .then(literal("save").executes(c -> saveCommand(c.getSource())))
                .then(literal("day").then(literal("next")
                    .executes(c -> advanceFreePlayDay(c.getSource()))))
                .then(literal("physicalskill")
                    .then(literal("practice")
                        .then(argument("slot", IntegerArgumentType.integer(1, 256))
                            .then(argument("minutes", IntegerArgumentType.integer(1, 1440))
                                .executes(c -> physicalSkillPractice(c.getSource(),
                                    IntegerArgumentType.getInteger(c, "slot"),
                                    IntegerArgumentType.getInteger(c, "minutes"))))))
                    .then(literal("individual").executes(c -> physicalSkillModeSet(c.getSource(),
                        org.nagineco.doppel.core.PhysicalSkillSync.Mode.INDIVIDUAL)))
                    .then(literal("technique").executes(c -> physicalSkillModeSet(c.getSource(),
                        org.nagineco.doppel.core.PhysicalSkillSync.Mode.TECHNIQUE)))
                    .then(literal("full").executes(c -> physicalSkillModeSet(c.getSource(),
                        org.nagineco.doppel.core.PhysicalSkillSync.Mode.FULL)))
                    .then(literal("status").executes(c -> physicalSkillModeStatus(c.getSource()))))
                .then(literal("offline")
                    .then(literal("on").executes(c -> offlineNpcModeSet(c.getSource(), true)))
                    .then(literal("off").executes(c -> offlineNpcModeSet(c.getSource(), false)))
                    .then(literal("status").executes(c -> offlineNpcModeStatus(c.getSource()))))
                .then(literal("life")
                    .then(literal("on").executes(c -> lifeToggle(c.getSource(), true)))
                    .then(literal("off").executes(c -> lifeToggle(c.getSource(), false)))
                    .then(literal("status").executes(c -> lifeStatus(c.getSource()))))
                .then(literal("region")
                    .then(literal("on").then(argument("radius", IntegerArgumentType.integer(1, 12))
                        .executes(c -> regionOn(c.getSource(), IntegerArgumentType.getInteger(c, "radius")))))
                    .then(literal("off").executes(c -> regionOff(c.getSource()))))
                .then(literal("control")
                    .then(literal("select").then(argument("slot", IntegerArgumentType.integer(1, 256))
                        .executes(c -> controlNao(c.getSource(), IntegerArgumentType.getInteger(c, "slot")))))
                    .then(literal("release").executes(c -> controlRelease(c.getSource()))))
                .then(literal("plan").then(argument("slot", IntegerArgumentType.integer(1, 256))
                    .executes(c -> planStatus(c.getSource(), IntegerArgumentType.getInteger(c, "slot")))))
                .then(literal("growth").then(literal("propose")
                    .then(argument("slot", IntegerArgumentType.integer(1, 256))
                        .executes(c -> growthProposal(c.getSource(), IntegerArgumentType.getInteger(c, "slot"))))))
                .then(literal("social")
                    .then(literal("talk")
                        .then(argument("from", IntegerArgumentType.integer(1, 256))
                            .then(argument("to", IntegerArgumentType.integer(1, 256))
                                .executes(c -> socialTalk(c.getSource(),
                                    IntegerArgumentType.getInteger(c, "from"),
                                    IntegerArgumentType.getInteger(c, "to"))))))
                    .then(literal("status")
                        .then(argument("from", IntegerArgumentType.integer(1, 256))
                            .then(argument("to", IntegerArgumentType.integer(1, 256))
                                .executes(c -> socialStatus(c.getSource(),
                                    IntegerArgumentType.getInteger(c, "from"),
                                    IntegerArgumentType.getInteger(c, "to"))))))
                )
                .then(literal("mannequin")
                    .then(literal("animation")
                        .then(literal("on").executes(c -> appearanceAnimation(c.getSource(), true)))
                        .then(literal("off").executes(c -> appearanceAnimation(c.getSource(), false)))
                        .then(literal("status").executes(c -> say(c.getSource(),
                            "Stand-in walking animation " + (mannequinAnimation ? "ON" : "OFF"))))))
                .then(literal("appearance")
                    .then(literal("status").then(argument("slot", IntegerArgumentType.integer(1, 256))
                        .executes(c -> appearanceStatus(c.getSource(), IntegerArgumentType.getInteger(c, "slot")))))
                    .then(literal("set").then(argument("slot", IntegerArgumentType.integer(1, 256))
                        .then(argument("skinKey", StringArgumentType.word())
                            .executes(c -> appearanceSet(c.getSource(),
                                IntegerArgumentType.getInteger(c, "slot"),
                                StringArgumentType.getString(c, "skinKey")))))))
                .then(literal("reward")
                    .then(literal("on").executes(c -> rewardToggle(c.getSource(), true)))
                    .then(literal("off").executes(c -> rewardToggle(c.getSource(), false)))
                    .then(literal("status").executes(c -> say(c.getSource(), "Reward learning " + (rewardLearning ? "ON" : "OFF")))))
                .then(literal("recall").then(argument("slot", IntegerArgumentType.integer(1, 256))
                    .then(argument("topic", StringArgumentType.word())
                        .executes(c -> recallNao(c.getSource(), IntegerArgumentType.getInteger(c, "slot"),
                            StringArgumentType.getString(c, "topic"))))))
                .then(literal("person")
                    .then(literal("add").then(argument("id", StringArgumentType.word())
                        .executes(c -> personAdd(c.getSource(), StringArgumentType.getString(c, "id")))))
                    .then(literal("status").then(argument("id", StringArgumentType.word())
                        .executes(c -> personStatus(c.getSource(), StringArgumentType.getString(c, "id")))))
                    .then(literal("relate").then(argument("from", StringArgumentType.word())
                        .then(argument("to", StringArgumentType.word())
                            .then(argument("role", StringArgumentType.word())
                                .executes(c -> personRelate(c.getSource(),
                                    StringArgumentType.getString(c, "from"),
                                    StringArgumentType.getString(c, "to"),
                                    StringArgumentType.getString(c, "role")))))))
                    .then(literal("event").then(argument("from", StringArgumentType.word())
                        .then(argument("to", StringArgumentType.word())
                            .then(argument("detail", StringArgumentType.word())
                                .executes(c -> personEvent(c.getSource(),
                                    StringArgumentType.getString(c, "from"),
                                    StringArgumentType.getString(c, "to"),
                                    StringArgumentType.getString(c, "detail")))))))
                )
                .then(literal("resume").executes(c -> resume(c.getSource())))
                .then(literal("next").executes(c -> next(c.getSource())))
                .then(literal("status").executes(c -> status(c.getSource())))
                .then(literal("logs").executes(c -> showReports(c.getSource())))
                .then(literal("npc").then(literal("report").executes(c -> npcReport(c.getSource()))))
                .then(literal("select").then(argument("id", IntegerArgumentType.integer(1, 256))
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
        ServerTickEvents.END_SERVER_TICK.register(DoppelgangerMod::tryAutomaticResume);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (recording) {
                try {
                    ServerPlayerEntity p = owner == null ? null : server.getPlayerManager().getPlayer(owner);
                    if (p != null && p.getServerWorld() == world) saveSession();
                    else saveOfflineSession();
                } catch (IOException ex) { LOG.warning("Final DNMS checkpoint failed: " + ex.getMessage()); }
            }
        });
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
        lastIntegratedTrainingDay = Integer.MIN_VALUE;
        try { Files.deleteIfExists(mipCompletedFile()); }
        catch (IOException ex) { report("Could not reset MIP marker: " + ex.getMessage()); }
        growthObserved = Long.MIN_VALUE;
        growthDay = Long.MIN_VALUE;
        return say(src, "Day 1, loop 1/24. Play, then /doppel next.");
    }

    private static void tick(MinecraftServer server) {
        tickMIP(server);
        tickGrowth(server);
        if (!recording || world == null || owner == null) return;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(owner);
        boolean ownerPresent = player != null && player.getServerWorld() == world;
        if (!ownerPresent && !(offlineNpcMode && lifeAiEnabled)) return;
        if (lifeAiEnabled) dailyLifeEvents();
        lastTickWasAutomatic = playableAutoMove;
        if (ownerPresent && playableAutoMove && memories != null && !brains.isEmpty()
                && loopTick != lastAutoTick) {
            lastAutoTick = loopTick;
            int currentIndex = freePlay ? Math.min(selected, brains.size()-1) : tracks.size()-1;
            HabitBrain.Move choice = AutomaticMovementPatch.choose(brains, currentIndex, loopTick, memories, rewardLearning);
            AutomaticMovementPatch.move(player, choice);
            if (loopTick % 200 == 0) report("Playable Nao AMP selected " + choice);
        }
        if (ownerPresent) {
            observePlayable(player);
            if (freePlay && controlSession.selection().mode() == NaoControlSession.Mode.NPC
                    && memories != null && loopTick % 20 == 0) {
                int playableSlot = controlSession.selection().slot() - 1;
                if (playableSlot >= 0 && playableSlot < life.count()) {
                    Vec3d p = player.getPos();
                    memories.rememberEvent(playableSlot, day, loopTick, "playable_experience",
                        "position=" + p.x + "," + p.y + "," + p.z
                            + " goal=" + life.get(playableSlot).goal);
                    life.rememberPosition(playableSlot, p.x, p.y, p.z);
                }
            }
        }
        if (ownerPresent && freePlay && controlSession.selection().mode() == NaoControlSession.Mode.NPC) {
            int controlled = controlSession.selection().slot() - 1;
            if (controlled < ghosts.size() && !ghosts.get(controlled).isRemoved())
                ghosts.get(controlled).refreshPositionAndAngles(player.getX(), player.getY(),
                    player.getZ(), player.getYaw(), player.getPitch());
        }
        if (!freePlay && ownerPresent) {
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
            if (controlSession.selection().mode() == NaoControlSession.Mode.NPC
                    && controlSession.selection().slot() == i + 1) continue;
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
                if (loopTick % 1200 == 0) {
                    var personalPlan = NaoGoalPlanner.choosePersonal(nao, sharedKnowledge, time);
                    if (nao.energy >= 20) { nao.goal = personalPlan.primary(); nao.goalUntilTick = time + 1200; }
                    if (memories != null) memories.rememberEvent(i, day, loopTick,
                        "goal_plan", personalPlan.evidence());
                    try {
                        var recalled = NaoMemoryRecall.recall(memories.directory(), i + 1, nao.longTermGoal);
                        recalled.ifPresent(value -> nao.lastRecalledMemory = value);
                    } catch (IOException ex) { report("Recall error: " + ex.getMessage()); }
                }
                // Construction is a retained task, not a temporary COLLECT goal.
                if (nao.goal == NaoLifeStore.Goal.BUILD && !nao.constructionActive
                        && nao.buildPlanStep < org.nagineco.doppel.core.NaoBuildingPlan.starter().size()) {
                    nao.constructionActive = true;
                    if (!nao.hasBuildAnchor) {
                        BlockPos site = BlockPos.ofFloored(previous);
                        nao.hasBuildAnchor = true;
                        nao.buildAnchorX = site.getX();
                        nao.buildAnchorY = site.getY();
                        nao.buildAnchorZ = site.getZ();
                    }
                }
                if (nao.constructionActive && loopTick % 20 == 0 && nao.energy >= 20
                        && nao.goal != NaoLifeStore.Goal.SAFETY) {
                    int logs = nao.inventory == null ? 0 : nao.inventory.entrySet().stream()
                        .filter(e -> e.getKey() != null && e.getKey().endsWith("_log"))
                        .mapToInt(e -> Math.max(0, e.getValue() == null ? 0 : e.getValue())).sum();
                    boolean atSite = org.nagineco.doppel.core.NaoConstructionWorkflow.near(
                        previous.x, previous.z, nao.buildAnchorX + 0.5, nao.buildAnchorZ + 0.5);
                    var phase = org.nagineco.doppel.core.NaoConstructionWorkflow.next(
                        true, nao.buildPlanStep, logs, atSite);
                    nao.constructionPhase = phase.name();
                    switch (phase) {
                        case GATHER -> { nao.goal = NaoLifeStore.Goal.COLLECT; nao.goalUntilTick = time + 1200; }
                        case RETURN, BUILD -> { nao.goal = NaoLifeStore.Goal.BUILD; nao.goalUntilTick = time + 1200; }
                        case COMPLETE -> {
                            nao.constructionActive = false;
                            nao.goal = NaoLifeStore.Goal.EXPLORE;
                            nao.goalUntilTick = time + 1200;
                        }
                        default -> {}
                    }
                }
                if (nao.goal == NaoLifeStore.Goal.COLLECT && loopTick % 100 == 0
                        && nao.energy >= 20) {
                    // A skill trial gates execution; actual collection still requires a
                    // nearby exposed log and successful Minecraft block update.
                    var workSkill = physicalSkills != null && i < physicalSkills.count()
                        ? physicalSkills.state(i) : null;
                    long workAttempt = nao.actionAttempts == null ? 0L
                        : nao.actionAttempts.getOrDefault("COLLECT", 0);
                    boolean skillReady = org.nagineco.doppel.core.PhysicalWorkPerformance
                        .canExecuteCollection(workSkill, i, workAttempt);
                    boolean harvested = skillReady && NaoWorkPatch.harvest(world, previous, nao);
                    life.observeAction(i, NaoActionLearning.Action.COLLECT, harvested);
                    if (harvested && physicalSkills != null && i < physicalSkills.count()) {
                        physicalSkills.configure(physicalSkillMode, 1);
                        physicalSkills.practice(i, 1, 1, 0.25);
                    }
                    if (harvested) {
                    if (rewardLearning) NaoRlpPatch.record(nao, NaoLifeStore.Goal.COLLECT, 5);
                    report("Nao " + nao.number + " " + nao.lastOutcome);
                    if (memories != null) memories.rememberEvent(i, day, loopTick,
                        "npc_collect", nao.lastOutcome);
                    }
                }
                if (nao.goal == NaoLifeStore.Goal.BUILD && loopTick % 100 == 0) {
                    int remaining = Math.max(0,
                        org.nagineco.doppel.core.NaoBuildingPlan.starter().size() - nao.buildPlanStep);
                    int availableLogs = nao.inventory == null ? 0 : nao.inventory.entrySet().stream()
                        .filter(e -> e.getKey() != null && e.getKey().endsWith("_log"))
                        .mapToInt(e -> Math.max(0, e.getValue() == null ? 0 : e.getValue()))
                        .sum();
                    if (remaining == 0) {
                        nao.goal = NaoLifeStore.Goal.EXPLORE;
                        nao.goalUntilTick = time + 1200;
                    } else if (availableLogs == 0) {
                        nao.goal = NaoLifeStore.Goal.COLLECT;
                        nao.goalUntilTick = time + 1200;
                        nao.lastOutcome = "SHELTER NEEDS " + remaining + " LOGS";
                    }
                }
                if (nao.goal == NaoLifeStore.Goal.BUILD && loopTick % 100 == 0
                        && nao.energy >= 20) {
                    var buildSkill = physicalSkills != null && i < physicalSkills.count()
                        ? physicalSkills.state(i) : null;
                    long buildAttempt = nao.actionAttempts == null ? 0L
                        : nao.actionAttempts.getOrDefault("BUILD", 0);
                    boolean skillReady = org.nagineco.doppel.core.PhysicalBuildPerformance
                        .canBuild(buildSkill, i, buildAttempt);
                    boolean built = skillReady && NaoWorkPatch.build(world, previous, nao);
                    life.observeAction(i, NaoActionLearning.Action.BUILD, built);
                    if (built) {
                        if (physicalSkills != null && i < physicalSkills.count()) {
                            physicalSkills.configure(physicalSkillMode, 1);
                            physicalSkills.practice(i, 1, 1, 0.25);
                        }
                        if (rewardLearning) NaoRlpPatch.record(nao, NaoLifeStore.Goal.BUILD, 5);
                        report("Nao " + nao.number + " " + nao.lastOutcome);
                        if (memories != null) memories.rememberEvent(i, day, loopTick,
                            "npc_build", nao.lastOutcome);
                    }
                }
                HabitBrain.Move move = NaoLifeAI.move(nao, brains, i, loopTick,
                    memories, rewardLearning, sharedKnowledge, previous);
                if (nao.constructionActive && ("RETURN".equals(nao.constructionPhase)
                        || "GATHER".equals(nao.constructionPhase))) {
                    if (org.nagineco.doppel.core.NaoRouteSchedule.shouldRefresh(i, loopTick,
                            routeWaypoints[i] == null,
                            routeWaypoints[i] != null && (!routeWaypoints[i].phase().equals(nao.constructionPhase)
                                || (routeWaypoints[i].x() != Integer.MIN_VALUE
                                    && Math.abs(previous.x - routeWaypoints[i].x() - 0.5) < 0.15
                                    && Math.abs(previous.z - routeWaypoints[i].z() - 0.5) < 0.15)))) {
                    var startCell = new org.nagineco.doppel.core.NaoGridPathfinder.Cell(
                        (int)Math.floor(previous.x), (int)Math.floor(previous.z));
                    // In gathering mode, navigate toward the nearest exposed log.
                    // Stop within harvesting range instead of stepping into the tree.
                    var logTarget = "GATHER".equals(nao.constructionPhase)
                        ? NaoWorkPatch.findDistantLog(world, previous, 12) : java.util.Optional.<BlockPos>empty();
                    var goalCell = new org.nagineco.doppel.core.NaoGridPathfinder.Cell(
                        logTarget.map(BlockPos::getX).orElse(nao.buildAnchorX),
                        logTarget.map(BlockPos::getZ).orElse(nao.buildAnchorZ));
                    int floorY = (int)Math.floor(previous.y);
                    if (logTarget.isPresent()) {
                        BlockPos tree = logTarget.get();
                        // Choose an adjacent empty walkable cell, not the solid log itself.
                        BlockPos[] candidates = {tree.north(), tree.south(), tree.east(), tree.west()};
                        BlockPos chosen = null;
                        double best = Double.POSITIVE_INFINITY;
                        for (BlockPos candidate : candidates) {
                            BlockPos feet = new BlockPos(candidate.getX(), floorY, candidate.getZ());
                            if (!world.isChunkLoaded(feet) || !world.isChunkLoaded(feet.down())
                                    || !world.isAir(feet)
                                    || !world.getBlockState(feet.down()).isSolidBlock(world, feet.down()))
                                continue;
                            double dist = Vec3d.ofCenter(feet).squaredDistanceTo(previous);
                            if (dist < best) { best = dist; chosen = feet; }
                        }
                        if (chosen != null)
                            goalCell = new org.nagineco.doppel.core.NaoGridPathfinder.Cell(
                                chosen.getX(), chosen.getZ());
                    }
                    Vec3d worldAnchor = samples.get(0);
                    var route = org.nagineco.doppel.core.NaoGridPathfinder.find(
                        startCell, goalCell, (x, z) -> {
                            BlockPos feet = new BlockPos(x, floorY, z);
                            BlockPos support = feet.down();
                            return world.isChunkLoaded(feet) && world.isChunkLoaded(support)
                                && world.isAir(feet)
                                && world.getFluidState(feet).isEmpty()
                                && world.getFluidState(support).isEmpty()
                                && world.getBlockState(support).isSolidBlock(world, support)
                                && org.nagineco.doppel.core.MovementPolicy.withinAnchor(
                                    x + 0.5, previous.y, z + 0.5,
                                    worldAnchor.x, worldAnchor.y, worldAnchor.z);
                        }, 16);
                    if (!route.isEmpty()) {
                        var nextCell = route.get(0);
                        routeWaypoints[i] = new RouteWaypoint(nao.constructionPhase,
                            nextCell.x(), nextCell.z(), floorY);
                    } else {
                        // Negative cache prevents an unreachable destination from
                        // triggering another full A* scan on every movement tick.
                        routeWaypoints[i] = new RouteWaypoint(nao.constructionPhase,
                            Integer.MIN_VALUE, Integer.MIN_VALUE, floorY);
                        nao.lastOutcome = "GATHER".equals(nao.constructionPhase)
                            ? "GATHER_PATH_BLOCKED_OR_NO_LOG" : "RETURN_PATH_BLOCKED";
                    }
                    }
                    RouteWaypoint waypoint = routeWaypoints[i];
                    if (waypoint != null && waypoint.x() != Integer.MIN_VALUE) {
                        double dx = waypoint.x() + 0.5 - previous.x;
                        double dz = waypoint.z() + 0.5 - previous.z;
                        if (Math.abs(dx) > Math.abs(dz))
                            move = dx > 0 ? HabitBrain.Move.EAST : HabitBrain.Move.WEST;
                        else if (Math.abs(dz) > 0.01)
                            move = dz > 0 ? HabitBrain.Move.SOUTH : HabitBrain.Move.NORTH;
                        else move = HabitBrain.Move.STAY;
                    } else move = HabitBrain.Move.STAY;
                } else {
                    routeWaypoints[i] = null;
                }
                Vec3d baseProposed = org.nagineco.doppel.minecraft.MinecraftActionExecutor.proposedPosition(
                    previous, org.nagineco.doppel.minecraft.MinecraftActionExecutor.fromLegacyMove(move));
                // Preserve the movement direction and use a bounded skill-based stride.
                double baseDistance = baseProposed.distanceTo(previous);
                double stride = physicalSkills != null && i < physicalSkills.count()
                    ? org.nagineco.doppel.core.PhysicalMovementPerformance.stepDistance(physicalSkills.state(i))
                    : org.nagineco.doppel.core.PhysicalMovementPerformance.BASE_STEP;
                Vec3d proposed = baseDistance > 0
                    ? previous.add(baseProposed.subtract(previous).multiply(stride / baseDistance))
                    : previous;
                Vec3d anchor = samples.get(0);
                // Minecraft adapter supplies collision and danger; core owns the policy.
                boolean withinAnchor = org.nagineco.doppel.core.MovementPolicy.withinAnchor(
                    proposed.x, proposed.y, proposed.z, anchor.x, anchor.y, anchor.z);
                if (withinAnchor && world.isAir(BlockPos.ofFloored(proposed))
                        && !NaoLifeAI.danger(world, proposed)) pos = proposed;
                boolean moved = org.nagineco.doppel.core.MovementPolicy.moved(
                    previous.x, previous.y, previous.z, pos.x, pos.y, pos.z);
                if (loopTick % 20 == 0) {
                    NaoLifeAI.update(nao, moved);
                    if (rewardLearning && nao.goal == NaoLifeStore.Goal.EXPLORE)
                        NaoRlpPatch.record(nao, NaoLifeStore.Goal.EXPLORE, moved ? 1 : -1);
                    if (moved && nao.goal == NaoLifeStore.Goal.EXPLORE)
                        life.learn(i, NaoLifeStore.Goal.EXPLORE);
                    if (moved && physicalSkills != null && i < physicalSkills.count()) {
                        // Movement is measured from the accepted position, not an attempted step.
                        double minutes = org.nagineco.doppel.core.PhysicalMovementPerformance.practiceMinutes(
                            previous.distanceTo(pos));
                        physicalSkills.configure(physicalSkillMode, 1);
                        physicalSkills.practice(i, minutes, 1, 0.5);
                    }
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
            // Minecraft adapter applies the renderer-independent core pose.
            org.nagineco.doppel.minecraft.ArmorStandMotionAdapter.apply(
                ghost, previous, pos, loopTick, i, mannequinAnimation);
        }
        if (lifeAiEnabled && life != null && ghosts.size() >= 2 && loopTick % 200 == 0) {
            List<NaoLifeStore.Nao> participants = new ArrayList<>();
            for (int i = 0; i < ghosts.size() && i < life.count(); i++)
                if (!ghosts.get(i).isRemoved()) participants.add(life.get(i));
            for (NaoLifeStore.Nao receiver : participants) {
                if (!"BUILDER".equals(NaoCooperationPatch.role(receiver))) continue;
                int receiverIndex = receiver.number - 1;
                for (NaoLifeStore.Nao sender : participants) {
                    int senderIndex = sender.number - 1;
                    if (senderIndex >= ghosts.size() || receiverIndex >= ghosts.size()) continue;
                    if (ghosts.get(senderIndex).getPos().squaredDistanceTo(
                            ghosts.get(receiverIndex).getPos()) > 16) continue;
                    if (NaoCooperationPatch.transfer(sender, receiver)) {
                        if (rewardLearning) {
                            NaoRlpPatch.record(sender, NaoLifeStore.Goal.SOCIAL, 3);
                            NaoRlpPatch.record(receiver, NaoLifeStore.Goal.SOCIAL, 3);
                        }
                        report("Nao cooperation: " + sender.lastOutcome);
                        if (memories != null) {
                            memories.rememberEvent(senderIndex, day, loopTick, "npc_transfer", sender.lastOutcome);
                            memories.rememberEvent(receiverIndex, day, loopTick, "npc_receive", receiver.lastOutcome);
                        }
                        break;
                    }
                }
            }
        }
        autonomousSocialTick();
        long serverTick = server.getTicks();
        if (serverTick - lastCheckpointTick >= 100 || lastCheckpointTick == Long.MIN_VALUE) {
            try {
                if (ownerPresent) saveSession(); else saveOfflineSession();
                lastCheckpointTick = serverTick;
            }
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
            integrateTrainingMidnight();
            try { saveSession(); }
            catch (IOException ex) { return say(src, "Day not advanced because save failed: " + ex.getMessage()); }
            day++;
            loopTick = 0;
            world.setTimeOfDay(0);
            return say(src, "Day " + day + " started (NPC AI continues; playable selection retained).");
        }
        ServerPlayerEntity player = src.getServer().getPlayerManager().getPlayer(owner);
        if (player == null) return say(src, "Owner is offline.");
        integrateTrainingMidnight();
        // Default: clock + owner position only; bounded block rewind requires opt-in.
        if (regionRollbackEnabled && regionSnapshot != null) {
            try { regionSnapshot.restore(); }
            catch (RuntimeException ex) {
                return say(src, "Day not advanced: bounded region restore failed: " + ex.getMessage());
            }
        }
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
        // Preserve existing living stand-ins and their world positions across
        // training-loop growth; only create the newly required slots.
        int count = freePlay ? tracks.size() : tracks.size() - 1;
        while (ghosts.size() > count) {
            ArmorStandEntity surplus = ghosts.remove(ghosts.size() - 1);
            if (!surplus.isRemoved()) surplus.discard();
        }
        for (int i = 0; i < count; i++) {
            if (i < ghosts.size() && !ghosts.get(i).isRemoved()) continue;
            ArmorStandEntity stand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
            NaoLifeStore.Nao persisted = life != null && i < life.count() ? life.get(i) : null;
            Vec3d pos = persisted != null && persisted.hasPosition
                ? new Vec3d(persisted.posX, persisted.posY, persisted.posZ)
                : (i < ghosts.size() && ghosts.get(i).isRemoved()
                    ? ghosts.get(i).getPos()
                    : (tracks.get(i).isEmpty() ? Vec3d.ofCenter(dawn) : tracks.get(i).get(0)));
            stand.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
            stand.setNoGravity(true);
            stand.setInvulnerable(true);
            // Vanilla-only visual preview: an armed mannequin with a player head.
            // This is not a true humanoid player renderer or a loaded skin texture.
            stand.setShowArms(true);
            stand.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.PLAYER_HEAD));
            stand.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
            stand.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
            stand.equipStack(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
            stand.setCustomName(Text.literal("Doppel " + (i+1)));
            stand.setCustomNameVisible(true);
            world.spawnEntity(stand);
            if (i < ghosts.size()) ghosts.set(i, stand);
            else ghosts.add(stand);
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
        controlSession.release();
        ownerBeforeControl = null;
        regionSnapshot = null;
        regionRollbackEnabled = false;
        clearGhosts();
        recording = false;
        try { Files.deleteIfExists(sessionOwnerFile()); }
        catch (IOException ex) { LOG.warning("Could not clear auto-resume marker: " + ex.getMessage()); }
        freePlay = false;
        playableAutoMove = false;
        tracks.clear();
        brains.clear();
        physicalSkills = null;
        memories = null;
        owner = null;
        world = null;
        dawn = null;
        return say(src, "Stopped and saved. Resume with /doppel resume.");
    }

    private static int mipStatus(ServerCommandSource src) {
        return say(src, "MIP " + (mipEnabled ? "ON" : "OFF")
            + " | daily at 00:00"
            + " (tick " + mipScheduleTick + ")"
            + " | recent log entries: " + mipLog.size()
            + " | integrated index: " + (memories != null
                && Files.exists(memories.directory().resolve("nao_integrated_memory.md"))));
    }

    private static int parseMinecraftClock(String clock) {
        return org.nagineco.doppel.core.MinecraftClock.parse(clock);
    }

    private static String formatMinecraftClock(int tick) {
        return org.nagineco.doppel.core.MinecraftClock.format(tick);
    }

    private static int mipTimeStatus(ServerCommandSource src) {
        return say(src, "MIP scheduled daily at " + formatMinecraftClock(mipScheduleTick)
            + " (Minecraft tick " + mipScheduleTick + ").");
    }

    private static int mipTimeSet(ServerCommandSource src, String clock) {
        final int tick;
        try { tick = parseMinecraftClock(clock); }
        catch (IllegalArgumentException ex) { return say(src, ex.getMessage()); }
        try { writeAtomic(mipScheduleFile(), clock); }
        catch (IOException ex) { return say(src, "Could not save MIP schedule: " + ex.getMessage()); }
        mipScheduleTick = tick;
        scheduleInitialized = false;
        return say(src, "MIP scheduled at Minecraft " + clock + " (tick " + tick + ").");
    }

    private static void integrateTrainingMidnight() {
        int integrationKey = freePlay ? 1000 + day : tracks.size();
        if (!mipEnabled || memories == null || lastIntegratedTrainingDay == integrationKey) return;
        try {
            memories.flush();
            logMIP("Scheduled " + formatMinecraftClock(mipScheduleTick) + " (day " + day + "): "
                + integrateAndReplicate());
            if (sharedKnowledge != null) sharedKnowledge.refresh();
            writeAtomic(mipCompletedFile(), Integer.toString(integrationKey));
            lastIntegratedTrainingDay = integrationKey;
        } catch (IOException ex) {
            logMIP("Daily integration failed, will retry: " + ex.getMessage());
        }
    }

    private static void tickMIP(MinecraftServer server) {
        if (!mipEnabled || !recording || world == null || memories == null) return;
        long current = world.getTimeOfDay();
        long previous = lastObservedTime;
        lastObservedTime = current;
        if (!scheduleInitialized) {
            scheduleInitialized = true;
            // Catch up on startup/resume if the scheduled clock has passed.
            if (Math.floorMod(current, DAY_TICKS) >= mipScheduleTick) integrateTrainingMidnight();
            return;
        }
        long previousDay = Math.floorDiv(previous, DAY_TICKS);
        long currentDay = Math.floorDiv(current, DAY_TICKS);
        boolean crossedMidnight = currentDay > previousDay
            || (currentDay == previousDay && Math.floorMod(previous, DAY_TICKS) < mipScheduleTick
                && Math.floorMod(current, DAY_TICKS) >= mipScheduleTick);
        if (crossedMidnight || Math.floorMod(current, DAY_TICKS) >= mipScheduleTick)
            integrateTrainingMidnight();
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
                logMIP(integrateAndReplicate());
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
            String result = integrateAndReplicate();
            if (sharedKnowledge != null) sharedKnowledge.refresh();
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
