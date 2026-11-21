# Doppelganger NPC System

A C++17 prototype inspired by a Minecraft-style world containing 24 doppelganger characters.

## Concept

- Play each character during a training round.
- Learn a simple habit policy from observed situations and actions.
- Convert completed characters into autonomous NPCs, clearing their short-term episodic history while retaining learned habits.
- Select any of 24 characters as the playable character; the previous one resumes NPC behavior.
- Preserve simulated position and collected wood when switching.

## Current prototype

This repository contains **two separate console demos**, not yet a Minecraft mod or server plugin.

- `src/doppel_npc.cpp`: 24 training rounds, a frequency-based habit learner, and conversion to NPC. Human input is simulated by `demoHumanChoice`.
- `src/doppel_switch.cpp`: interactive switching among 24 characters. NPC habits are currently fixed alternating behaviors; this demo is **not yet integrated** with the learner.

## Build

Requires a C++17 compiler:

```bash
g++ -std=c++17 -O2 src/doppel_npc.cpp -o doppel_npc
g++ -std=c++17 -O2 src/doppel_switch.cpp -o doppel_switch
./doppel_npc
./doppel_switch
```

In the switching demo, enter `1`–`24` to switch, `a`/`d` to move, `w` to collect wood, `t` to advance NPCs, and `q` to quit.

## Planned work

1. Integrate habit learning and character switching into one runtime.
2. Record real player actions and world observations.
3. Persist per-character policies and world state.
4. Connect to a Minecraft-compatible server or mod API.
5. Add continuous simulation, navigation, inventory and richer action policies.

This prototype does not claim to model consciousness or transfer subjective experience.
