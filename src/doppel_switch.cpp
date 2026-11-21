#include <array>
#include <iostream>
#include <string>
#include <limits>

constexpr int INITIAL_NAOS = 24;
constexpr int COUNT = 256;
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
    int activeCount = INITIAL_NAOS;
    int selected = 0;
    characters[selected].mode = Mode::Human;
    std::cout << "24-initial / 256-max doppelganger simulator\n"
              << "Commands: 1-256 switch, add N, d/a move, w collect, t tick, q quit\n";
    while (true) {
        std::cout << "\nPlaying #" << characters[selected].id << " > ";
        std::string command;
        if (!(std::cin >> command)) break;
        if (command == "q") break;
        if (command == "t") {
            for (int i=0;i<activeCount;++i) {
                if (characters[i].mode == Mode::NPC) characters[i].actNPC();
                const auto& c=characters[i];
                std::cout << "#" << c.id << (c.mode == Mode::Human ? " HUMAN" : " NPC")
                          << " x=" << c.x << " wood=" << c.wood << '\n';
            }
        } else if (command == "d" || command == "a" || command == "w") {
            characters[selected].actHuman(command[0]);
            for (int i=0;i<activeCount;++i) if (characters[i].mode == Mode::NPC) characters[i].actNPC();
        } else if (command == "add") {
            int n=0;
            if (!(std::cin >> n) || n<1 || n>COUNT-activeCount) {
                std::cin.clear();
                std::cin.ignore(std::numeric_limits<std::streamsize>::max(), '\n');
                std::cout << "Invalid add count.\n";
            } else {
                activeCount+=n;
                std::cout << "Active: " << activeCount << "/256\n";
            }
        } else {
            try {
                size_t pos = 0;
                int n = std::stoi(command, &pos);
                if (pos != command.size() || n < 1 || n > activeCount) {
                    std::cout << "Choose an active Nao (1..256).\n";
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
