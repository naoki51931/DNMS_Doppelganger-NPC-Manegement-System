package org.nagineco.doppel.core;
/** World-compatible scheduling and skill growth, independent of the engine. */
public final class WorldProgressPolicy {
 private WorldProgressPolicy(){}
 public static boolean integrationDue(boolean enabled,long tick,int scheduledTick,long lastDay){
  return enabled&&tick%24000>=scheduledTick&&lastDay!=tick/24000;
 }
 public static PhysicalSkillSync.Skill grow(PhysicalSkillSync.Skill k,boolean explore,boolean collect){
  return new PhysicalSkillSync.Skill(Math.min(100,k.technique()+(explore?0:0.2)),Math.min(100,k.coordination()+0.1),Math.min(100,k.proficiency()+(explore?0:0.2)),Math.min(100,k.strength()+(collect?0.1:0)),Math.min(100,k.agility()+(explore?0.1:0)),Math.min(100,k.endurance()+0.05));
 }
}
