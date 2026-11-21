# Doppelganger NPC System

C++17 prototype for a Minecraft-inspired world of 24 doppelganger characters.

## Integrated simulator (recommended)

`src/doppel_integrated.cpp` combines **24 training rounds**, habit learning, NPC autonomy, episodic-memory clearing and unlimited character switching.

```bash
g++ -std=c++17 -O2 -Wall -Wextra src/doppel_integrated.cpp -o doppel
./doppel
```

1. Control character #1 using `w/a/s/d` (movement), `g` (gather), `f` (flee), or `.` (wait).
2. Type `next` to finish the round: the character becomes an NPC using a frequency-based learned policy. Its temporary episode log is cleared, but its learned habits and world state remain.
3. Repeat through all 24 characters. Earlier NPCs act once per command/tick while the current character is controlled.
4. After all 24 rounds, type `select 1` through `select 24` to take control of any character. The other 23 act as NPCs; switching is unlimited.
5. Type `status` to inspect all characters, or `quit` to exit.

Each NPC's learned policy maps a simplified observation (tree/night/danger) to its most frequently demonstrated action. If a situation was never demonstrated, the NPC waits. The world is a small simulated grid, not Minecraft itself.

**Limitations:** this is a console prototype; it does not connect to Minecraft, use real keyboard/mouse events, save progress to disk, or implement pathfinding/complex imitation learning. NPC actions advance when the player enters an action, not continuously in background time. In free-play, player actions do not update the NPC habit policy.

## Earlier standalone examples

- `src/doppel_npc.cpp`: demonstration-based NPC conversion using scripted human choices.
- `src/doppel_switch.cpp`: 24-character switching with fixed sample habits.

## Roadmap

- Minecraft integration (mod or compatible server API)
- Persist world state, learned habits, and current playable character
- Real observations/actions and better behavior imitation
- Optional continuous tick loop, navigation and inventory systems

This simulation models behavior, not subjective consciousness.
