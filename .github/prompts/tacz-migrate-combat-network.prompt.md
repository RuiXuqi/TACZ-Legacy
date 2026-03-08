---
name: "TACZ Migrate Combat Network"
description: "Fix TACZ-Legacy combat logic: Burst fire mode cadence and misfire issues."
agent: "TACZ Migration"
argument-hint: "本轮主要负责修复 Burst/Auto 爆发开火模式无法连续射击以及偶尔卡壳的问题。"
---
修复 `TACZ` **战斗与网络层的开火模式（Fire Mode），尤其是 Burst（爆发/点射）相关的节奏断档和开火失败（卡死）问题**。

## ⚠️ 第二轮通报与新问题修复（本轮重点）
实机测试反馈发现了两个战斗流程上的严重问题，都和射击判定、频率控制以及状态同步有关：

1. **爆发开火（Burst）只听到一声开火**：
   - 玩家以 `TRIS-dyna GunsPack: IRAS` （5连发爆发模式）为例，选择了 Burst 模式后，按住或点击左键只发出一发子弹，也没有发出相应的连发声音。
   - **排查线索**：原版 TACZ 的 `LivingEntityShoot` 中，会有一个 `burstData` 或类似的计数器用于缓存剩余的点射发数和射击延迟（Cadence）。你必须确保：
     a) 每次触发 Burst 开火后，客户端与服务端都能将剩余发数记下。
     b) 在 Client / Server Tick 中，若 `burstData.hasNext()` 为真，需要强制在下一次允许射击的 tick 发起无需玩家点击的自动开火。

2. **某些枪（如 timeless50）无法开不出火且无报错**：
   - 这是个综合问题，有些枪按左键无论如何点都没反应。除了动画卡死，有可能是 **客户端的允许开火判断（如弹药、换弹、Draw冷却、保险装置，或者是服务端判定为非法状态将所有包吞掉）** 出了问题。
   - 检查 `canAttemptShoot` 的边界情况。
   - 检查对应枪械的 Fire Mode 定义在序列化时是否加载正确，如果没有则需要给予 Fallback 避免卡死。

## 默认关注范围
- `src/main/kotlin/com/tacz/legacy/common/infrastructure/mc/weapon/LivingEntityShoot.kt`
- `src/main/kotlin/com/tacz/legacy/common/infrastructure/mc/weapon/LivingEntityBurst.kt` (如果存在)
- `src/main/kotlin/com/tacz/legacy/common/infrastructure/mc/weapon/WeaponPlayerTickEventHandler.kt`
- `src/main/kotlin/com/tacz/legacy/client/gameplay/LegacyClientShootCoordinator.kt`

## 执行要求
- 验证以 Burst 模式点按一次鼠标，可以在游戏逻辑中无缝扣除 3 发或 5 发弹药（视枪械配置而定）。
- 确认由于射击频率或者弹药判断的不严谨导致的 `timeless50` 卡死不再出现。
