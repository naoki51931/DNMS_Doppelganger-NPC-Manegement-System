#include <array>
#include <algorithm>
#include <iostream>
#include <map>
#include <sstream>
#include <string>
#include <vector>

constexpr int INITIAL_NAOS = 24;
constexpr int MAX_CHARACTERS = 256;
constexpr int WORLD_SIZE = 16;

enum class Action { Wait, North, South, West, East, Gather, Flee, Count };
constexpr int ACTION_COUNT = static_cast<int>(Action::Count);

struct Situation {
    bool tree = false;
    bool night = false;
    bool danger = false;
    bool operator<(const Situation& b) const {
        if (tree != b.tree) return tree < b.tree;
        if (night != b.night) return night < b.night;
        return danger < b.danger;
    }
};

struct Habit {
    std::map<Situation, std::array<int, ACTION_COUNT>> frequencies;
    void learn(Situation s, Action a) {
        ++frequencies[s][static_cast<int>(a)];
    }
    Action choose(Situation s) const {
        auto it = frequencies.find(s);
        if (it == frequencies.end()) return Action::Wait;
        const auto& f = it->second;
        return static_cast<Action>(std::distance(f.begin(), std::max_element(f.begin(), f.end())));
    }
    int samples() const {
        int total = 0;
        for (const auto& entry : frequencies)
            for (int n : entry.second) total += n;
        return total;
    }
};

struct Character {
    int id = 0;
    int x = 0, z = 0, wood = 0;
    bool trained = false;
    Habit habit;
    std::vector<Situation> episodic;
};

class Simulation {
    std::array<Character, MAX_CHARACTERS> people{};
    int activeCount = INITIAL_NAOS;
    int trainedCount = 0;
    int controlled = 0;
    int tickCount = 0;
    bool freePlay = false;

    Situation observe(const Character& c) const {
        return { ((c.x * 3 + c.z) % 5 == 0), tickCount % 12 >= 7,
                 ((c.x + 2 * c.z + tickCount) % 13 == 0) };
    }
    static const char* actionName(Action a) {
        switch (a) {
            case Action::North: return "north";
            case Action::South: return "south";
            case Action::West: return "west";
            case Action::East: return "east";
            case Action::Gather: return "gather";
            case Action::Flee: return "flee";
            default: return "wait";
        }
    }
    void apply(Character& c, Action a, Situation before) {
        switch (a) {
            case Action::North: c.z = std::max(0, c.z - 1); break;
            case Action::South: c.z = std::min(WORLD_SIZE - 1, c.z + 1); break;
            case Action::West: c.x = std::max(0, c.x - 1); break;
            case Action::East: c.x = std::min(WORLD_SIZE - 1, c.x + 1); break;
            case Action::Gather: if (before.tree) ++c.wood; break;
            case Action::Flee: c.x = std::max(0, c.x - 1); break;
            default: break;
        }
    }
    void step(Action humanAction) {
        // Snapshot all observations before applying any action.
        std::array<Situation, MAX_CHARACTERS> observations{};
        std::array<Action, MAX_CHARACTERS> actions{};
        int active = freePlay ? activeCount : trainedCount + 1;
        for (int i = 0; i < active; ++i) {
            observations[i] = observe(people[i]);
            actions[i] = i == controlled ? humanAction
                        : people[i].habit.choose(observations[i]);
        }
        for (int i = 0; i < active; ++i) {
            Character& c = people[i];
            if (i == controlled && !freePlay) {
                c.habit.learn(observations[i], humanAction);
                c.episodic.push_back(observations[i]);
            }
            apply(c, actions[i], observations[i]);
        }
        ++tickCount;
        std::cout << "tick " << tickCount << " | #" << controlled+1
                  << " " << actionName(humanAction) << "\n";
    }
    void finishRound() {
        if (freePlay) {
            std::cout << "All 24 characters are already trained.\n";
            return;
        }
        Character& c = people[controlled];
        c.trained = true;
        c.episodic.clear(); // Forget the episode; retain habit policy.
        std::cout << "Character #" << c.id << " became NPC ("
                  << c.habit.samples() << " demonstrations).\n";
        ++trainedCount;
        if (trainedCount == INITIAL_NAOS) {
            freePlay = true;
            controlled = 0;
            std::cout << "24 NPCs ready! Switch freely with: select 1..24\n";
        } else {
            controlled = trainedCount;
            std::cout << "Now training character #" << controlled+1 << "\n";
        }
    }
    void show() const {
        std::cout << "tick=" << tickCount << " trained=" << trainedCount
                  << "/24 mode=" << (freePlay ? "free-play" : "training")
                  << " controlling=#" << controlled+1 << "\n";
        int active = freePlay ? MAX_CHARACTERS : trainedCount+1;
        for (int i=0; i<active; ++i) {
            const auto& c = people[i];
            std::cout << (i==controlled ? "* " : "  ") << "#" << c.id
                      << " (" << c.x << "," << c.z << ") wood=" << c.wood
                      << " learned=" << c.habit.samples()
                      << (i==controlled ? " PLAYER" : " NPC") << "\n";
        }
    }
public:
    Simulation() {
        for (int i=0; i<MAX_CHARACTERS; ++i) {
            people[i].id = i+1;
            people[i].x = i%WORLD_SIZE;
            people[i].z = i/WORLD_SIZE;
        }
    }
    void run() {
        std::cout << "Doppelganger NPC System - C++17 prototype\n"
                  << "Commands: w/a/s/d move, g gather, f flee, . wait,\n"
                  << "          next (finish training round), select N, status, help, quit\n"
                  << "Train each character with actions then type next.\n";
        std::string line;
        while (std::cout << "> " && std::getline(std::cin, line)) {
            if (line == "quit" || line == "q") break;
            if (line == "help") {
                std::cout << "w/a/s/d/g/f/. : perform action and tick NPCs\n"
                          << "next : finish current character training\n"
                          << "select N : control trained character (after all 24)\n"
                          << "status : show world state\n";
            } else if (line == "status") show();
            else if (line == "next") finishRound();
            else if (line.rfind("add ",0)==0) {
                std::istringstream in(line.substr(4));
                int n; std::string extra;
                if (!freePlay || !(in >> n) || (in >> extra) || n < 1 || n > MAX_CHARACTERS-activeCount)
                    std::cout << "Complete training first; valid add 1.." << MAX_CHARACTERS-activeCount << "\n";
                else {
                    for (int i=activeCount; i<activeCount+n; ++i) {
                        people[i].id=i+1; people[i].trained=true;
                        people[i].x=i%WORLD_SIZE; people[i].z=i/WORLD_SIZE;
                    }
                    activeCount+=n;
                    std::cout << "Active Naos: " << activeCount << "/256\n";
                }
            }
            else if (line.rfind("select ",0)==0) {
                std::istringstream in(line.substr(7));
                int n; std::string extra;
                if (!(in >> n) || (in >> extra) || n<1 || n>activeCount)
                    std::cout << "Select an active Nao\n";
                else if (!freePlay)
                    std::cout << "Complete all 24 training rounds first.\n";
                else {
                    controlled = n-1;
                    std::cout << "Now controlling #" << n << "\n";
                }
            } else if (line=="w") step(Action::North);
            else if (line=="s") step(Action::South);
            else if (line=="a") step(Action::West);
            else if (line=="d") step(Action::East);
            else if (line=="g") step(Action::Gather);
            else if (line=="f") step(Action::Flee);
            else if (line==".") step(Action::Wait);
            else if (!line.empty()) std::cout << "Unknown command; type help.\n";
        }
    }
};

int main() { Simulation{}.run(); }
