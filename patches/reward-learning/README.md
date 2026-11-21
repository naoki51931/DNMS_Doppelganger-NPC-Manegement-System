# Reward Learning Patch (RLP)

Optional signed outcome learning for DNMS (Fabric 1.21.1 / Java 21). **OFF by default**.

- `/doppel reward on`: enable movement reward attribution and reward-aware decisions.
- `/doppel reward off`: disable reward attribution and reward-aware decisions.
- `/doppel reward status`: show current setting.

The toggle persists in `config/doppelganger/reward-learning.enabled`. While enabled, the latest **manually observed** movement is associated with item gains (+count) and health damage (-health points). Item consumption/loss is neutral. Reward events are stored in each Nao's Markdown journal and reloaded on start/resume. Reward-aware shared decisions are used for NPCs and AMP. AMP-generated movement is not rewarded.

This is a simple temporal association, not causal inference. The reward patch does not change `nao_personality.md`: playable Nao and all 24 NPCs continue to use one shared personality. The patch has not been build-tested in Minecraft.
