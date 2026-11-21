#include <array>
#include <iostream>
#include <vector>
#include <string>
#include <algorithm>

// Standalone prototype: deterministic one-day rewind and 24 recorded timelines.
// Minecraft world snapshots, real input and server ticks need a mod/API adapter.
constexpr int PEOPLE=24, TICKS_PER_DAY=12, WORLD_WIDTH=16;
enum class Action { Wait, Left, Right, Chop };
struct Actor { int x=0, wood=0; };
struct World { std::array<Actor,PEOPLE> actors{}; std::array<bool,WORLD_WIDTH> trees{}; int day=1; };
using Timeline=std::array<Action,TICKS_PER_DAY>;
struct DayRecords { std::array<Timeline,PEOPLE> tracks{}; std::array<bool,PEOPLE> recorded{}; };
const char* label(Action a) {
    switch(a){case Action::Left:return "left";case Action::Right:return "right";
    case Action::Chop:return "chop";default:return "wait";}
}
Action scriptedHuman(int player,int tick) {
    // Replace with recorded real player input from Minecraft.
    if(tick%4==0)return Action::Right;
    if(tick%4==1)return Action::Chop;
    if(tick%4==2 && player%2==0)return Action::Left;
    return Action::Wait;
}
void apply(World& world,int id,Action action) {
    Actor& a=world.actors[id];
    if(action==Action::Left)a.x=std::max(0,a.x-1);
    if(action==Action::Right)a.x=std::min(WORLD_WIDTH-1,a.x+1);
    if(action==Action::Chop && world.trees[a.x]) {
        world.trees[a.x]=false;
        ++a.wood;
    }
}
World makeWorld() {
    World w;
    w.trees.fill(true);
    for(int i=0;i<PEOPLE;++i)w.actors[i].x=i%WORLD_WIDTH;
    return w;
}
DayRecords recordSameDay(const World& dawn) {
    DayRecords data;
    for(int p=0;p<PEOPLE;++p) {
        World replay=dawn; // Reset world, date and all actor positions.
        for(int tick=0;tick<TICKS_PER_DAY;++tick) {
            Action a=scriptedHuman(p,tick);
            data.tracks[p][tick]=a;
            apply(replay,p,a);
        }
        data.recorded[p]=true;
        std::cout<<"Day "<<dawn.day<<" loop "<<p+1<<"/24 recorded\n";
    }
    return data;
}
World mergeDay(World dawn,const DayRecords& data) {
    for(bool complete:data.recorded)if(!complete)return dawn;
    // All 24 actions are replayed in each tick. Same-tree conflicts
    // are resolved deterministically by lower actor index first.
    for(int tick=0;tick<TICKS_PER_DAY;++tick)
        for(int p=0;p<PEOPLE;++p)
            apply(dawn,p,data.tracks[p][tick]);
    ++dawn.day;
    return dawn;
}
int main(int argc,char** argv) {
    int days=3;
    if(argc>1) {
        try { days=std::stoi(argv[1]); }
        catch(...) { std::cerr<<"Invalid days\n";return 1; }
    }
    if(days<1 || days>3650) { std::cerr<<"Days must be 1..3650\n";return 1; }
    World world=makeWorld();
    for(int d=0;d<days;++d) {
        World dawn=world;
        DayRecords records=recordSameDay(dawn);
        world=mergeDay(dawn,records);
        std::cout<<"Committed Day "<<world.day-1<<"; now Day "<<world.day<<"\n";
    }
    for(int i=0;i<PEOPLE;++i)
        std::cout<<"NPC "<<i+1<<" x="<<world.actors[i].x
                 <<" wood="<<world.actors[i].wood<<"\n";
}
