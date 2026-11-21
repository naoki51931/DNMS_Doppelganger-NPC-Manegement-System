# Reward Learning Patch (RLP)

<!-- DNMS_PATCH_BILINGUAL_CURRENT_BEGIN -->

## 日本語ガイド（現行仕様）
RLPは奈央の行動結果に報酬を付ける任意パッチで、初期値はOFFです。`/doppel reward on|off|status` で設定し、`config/doppelganger/reward-learning.enabled` に保存します。手動移動に続くアイテム獲得は正、被ダメージは負、アイテム消費は中立として記録します。AMPの自動移動を人間の報酬学習対象にしません。生活AIでは原木採集、探索移動、NPC間の資源受け渡しにも限定的な目標報酬を使います。個別の学習結果は保存されますが、単純な時系列上の関連付けであり、因果関係を証明するものでもQ学習でもありません。共通人格は変更しません。

## English Guide (Current Behavior)
RLP is optional signed outcome learning and defaults OFF. Configure with `/doppel reward on|off|status`; the setting persists in `config/doppelganger/reward-learning.enabled`. Item gains after manual movement are positive, damage is negative, and item consumption is neutral. AMP-generated movements are excluded from human reward attribution. The life-AI layer also uses limited goal rewards for log harvesting, exploration movement and NPC resource transfers. Individual outcomes persist, but these are temporal associations and incremental goal estimates—not causal proof or Q-learning. The shared personality remains unchanged.

<!-- DNMS_PATCH_BILINGUAL_CURRENT_END -->


Optional signed outcome learning for DNMS (Fabric 1.21.1 / Java 21). **OFF by default**.

- `/doppel reward on`: enable movement reward attribution and reward-aware decisions.
- `/doppel reward off`: disable reward attribution and reward-aware decisions.
- `/doppel reward status`: show current setting.

The toggle persists in `config/doppelganger/reward-learning.enabled`. While enabled, the latest **manually observed** movement is associated with item gains (+count) and health damage (-health points). Item consumption/loss is neutral. Reward events are stored in each Nao's Markdown journal and reloaded on start/resume. Reward-aware shared decisions are used for NPCs and AMP. AMP-generated movement is not rewarded.

This is a simple temporal association, not causal inference. The reward patch does not change `nao_personality.md`: playable Nao and all 24 NPCs continue to use one shared personality. The patch has not been build-tested in Minecraft.
