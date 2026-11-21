#include <array>
#include <iostream>
#include <string>
#include <limits>

constexpr int COUNT = 24;
enum class Mode { NPC, Human };
struct Character {
    int id;
    Mode mode = Mode::NPC;
    int x = 0;
    int wood = 0;
    int preferredAction = 0; // 0: explore, 1: collect wood
    void actNPC() {
        if (preferredAction == 1) ++wood;
        else ++x;
    }
    void actHuman(char command) {
        if (command == 'd') ++x;
        else if (command == 'a') --x;
        else if (command == 'w') ++wood;
    }
};
int main() {
    std::array<Character, COUNT> characters{};
    for (int i = 0; i < COUNT; ++i) {
        characters[i].id = i + 1;
        characters[i].preferredAction = i % 2;
    }
    int selected = 0;
    characters[selected].mode = Mode::Human;
    std::cout << "24-character doppelganger simulator\n"
              << "Commands: 1-24 switch, d/a move, w collect, t tick, q quit\n";
    while (true) {
        std::cout << "\nPlaying #" << characters[selected].id << " > ";
        std::string command;
        if (!(std::cin >> command)) break;
        if (command == "q") break;
        if (command == "t") {
            for (auto& c : characters) {
                if (c.mode == Mode::NPC) c.actNPC();
                std::cout << "#" << c.id << (c.mode == Mode::Human ? " HUMAN" : " NPC")
                          << " x=" << c.x << " wood=" << c.wood << '\n';
            }
        } else if (command == "d" || command == "a" || command == "w") {
            characters[selected].actHuman(command[0]);
            for (auto& c : characters) if (c.mode == Mode::NPC) c.actNPC();
        } else {
            try {
                size_t pos = 0;
                int n = std::stoi(command, &pos);
                if (pos != command.size() || n < 1 || n > COUNT) {
                    std::cout << "Choose a number from 1 to 24.\n";
                    continue;
                }
                characters[selected].mode = Mode::NPC;
                selected = n - 1;
                characters[selected].mode = Mode::Human;
                std::cout << "Now controlling #" << n << '\n';
            } catch (...) { std::cout << "Unknown command.\n"; }
        }
    }
}
