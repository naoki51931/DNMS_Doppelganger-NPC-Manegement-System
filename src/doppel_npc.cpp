#include <array>
#include <iostream>
#include <map>
#include <vector>
#include <algorithm>
#include <string>

constexpr int INITIAL_NAOS = 24;
constexpr int MAX_AGENTS = 256;
enum class Action { Explore, Chop, ReturnHome, Flee };
const char* name(Action a) {
    switch(a) {
        case Action::Explore: return "explore";
        case Action::Chop: return "chop";
        case Action::ReturnHome: return "return_home";
        case Action::Flee: return "flee";
    }
    return "unknown";
}
struct Situation {
    bool tree, night, enemy;
    bool operator<(const Situation& o) const {
        if (tree != o.tree) return tree < o.tree;
        if (night != o.night) return night < o.night;
        return enemy < o.enemy;
    }
};
struct HabitModel {
    std::map<Situation, std::array<int,4>> counts;
    void learn(Situation s, Action a) { counts[s][static_cast<int>(a)]++; }
    Action predict(Situation s) const {
        auto it = counts.find(s);
        if (it == counts.end()) return Action::Explore;
        const auto& v = it->second;
        return static_cast<Action>(std::distance(v.begin(), std::max_element(v.begin(),v.end())));
    }
};
struct Doppelganger {
    int id;
    bool npc = false;
    HabitModel habits;
    std::vector<Situation> episodicMemory;
    Action act(Situation s, Action humanChoice) {
        if (npc) return habits.predict(s);
        episodicMemory.push_back(s);
        habits.learn(s, humanChoice);
        return humanChoice;
    }
    void becomeNpc() {
        npc = true;
        episodicMemory.clear();
        episodicMemory.shrink_to_fit();
    }
};
Situation observe(int tick, int id) {
    return { (tick+id)%2==0, tick%5==4, (tick+id)%7==0 };
}
// Demo only: replace with keyboard/network input for a real human player.
Action demoHumanChoice(Situation s) {
    if (s.enemy) return Action::Flee;
    if (s.night) return Action::ReturnHome;
    if (s.tree) return Action::Chop;
    return Action::Explore;
}
int main() {
    std::vector<Doppelganger> agents;
    for (int round=0; round<INITIAL_NAOS; ++round) {
        agents.push_back({round+1, false, {}, {}});
        std::cout << "\nRound " << round+1 << ": human=" << round+1
                  << ", NPCs=" << round << '\n';
        for (int tick=0; tick<12; ++tick) {
            std::vector<Action> actions;
            for (auto& agent : agents) {
                Situation s = observe(tick,agent.id);
                actions.push_back(agent.act(s,demoHumanChoice(s)));
            }
            if (tick==0) {
                for (size_t i=0;i<agents.size();++i)
                    std::cout << "  agent " << agents[i].id << " ["
                              << (agents[i].npc?"NPC":"human") << "] "
                              << name(actions[i]) << '\n';
            }
            // Send actions to Minecraft server here.
        }
        agents.back().becomeNpc();
    }
    std::cout << "\nFinished: " << agents.size() << " learned NPCs.\n";
}
