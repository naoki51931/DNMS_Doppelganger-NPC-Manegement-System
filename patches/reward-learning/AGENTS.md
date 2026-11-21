# Reward Learning Patch guidance

<!-- DNMS_PATCH_BILINGUAL_CURRENT_BEGIN -->

## 日本語：開発ルール
RLPは任意で初期OFFを維持する。直近の手動移動と結果の時間的な関連を因果関係と断定しない。AMPの自動移動に報酬を与えず、アイテム消費を自動的に悪い結果としない。報酬は個別の奈央の記憶に保存し、再開時の二重計上を避ける。生活AIの目標報酬も単純な増分平均であり、Q学習とは呼ばない。プレイアブルと24人のNPCは共通の `nao_personality.md` を使い続ける。Java 21 / Fabric 1.21.1のビルドと実機動作を区別して検証する。

## English: Developer Rules
Keep RLP opt-in and OFF by default. Never claim that temporal association between manual movement and outcome proves causation. Do not reward AMP-generated movement or treat item consumption as inherently negative. Persist rewards in individual Nao journals and avoid double-counting on resume. Life-AI goal rewards are simple incremental averages, not Q-learning. The playable character and all NPCs must share `nao_personality.md`. Verify Java 21 / Fabric 1.21.1 builds separately from in-game runtime.

<!-- DNMS_PATCH_BILINGUAL_CURRENT_END -->


Keep reward learning opt-in and OFF by default. Do not infer causal certainty from the most recent manual movement. Do not reward AMP-generated movements or treat item loss as intrinsically bad. Store reward records in each individual Nao journal and restore them without double-counting ordinary movement observations. The playable character and all 24 NPCs must continue using the same authoritative shared personality file. Keep documentation consistent with command behavior. Verify Java 21 / Fabric 1.21.1 build and runtime before claiming compatibility.
