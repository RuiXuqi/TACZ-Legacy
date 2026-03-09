# TACZ 专用音频引擎设计方案

## 背景

`TACZ-Legacy` 当前的枪包音频仍然绑定在 `Minecraft 1.12.2` 原版声音系统上，真实链路如下：

1. `TACZClientAssetManager.reload()` 从 display 与 animation 资源里收集 `soundResources`
2. `GunPackSoundResourcePack.installOrUpdate(soundResources)` 把枪包音频暴露为 `IResourcePack`
3. 资源域变化时调用 `minecraft.refreshResources()`
4. `ObjectAnimationSoundChannel` 与 `ServerMessageSound` 都通过 `GunSoundPlayManager.playClientSound(...)` 提交播放
5. `GunSoundPlayManager` 的默认 backend 创建 `GunSoundInstance`，最终走 `Minecraft.getSoundHandler().playSound(...)`

这条链路有三个致命问题：

- **枪包音频兼容性受制于 1.12 老声音栈**：新枪包或更高版本生成的音频资源一旦与原版解码/资源刷新行为不兼容，就可能导致播放失败甚至卡住。
- **资源刷新成本过高且不可控**：只要枪包音频域变化，当前实现就可能触发 `refreshResources()`；这对 focused smoke、CI 和快速迭代都很危险。
- **音频验证能力过弱**：现在最多只能在“播放时”看有没有报错，缺少 manifest、probe、preflight、静默 backend 这类能让 smoke 稳定运行的机制。

## 当前落地状态（2026-03-08）

本方案的**阶段 A/B/C 已完成落地**，当前仓库状态不再只是设计稿：

- `GunSoundPlayManager -> TACZAudioRuntime` 的统一 facade 已建立，动画音效与 `ServerMessageSound` 都经统一入口提交。
- reload 阶段已具备 manifest / probe / preflight 能力，能在 focused smoke 中输出音频清单、兼容性与请求来源摘要。
- `diagnostic` / `null` backend 已可用于 smoke / CI，focused smoke 仍可在不真实播放声音的情况下验证音频链路。
- `6302dff23e380f4e7b1d54395723f9d4bfc04277` 之前那套可用的“预解码 + 直出 OpenAL”思路已经恢复为当前主路径：新增 `TACZOpenALSoundEngine`，在资源 reload 后直接把 pack-backed `ogg` 通过 JOrbis 解成 PCM 并缓存到 OpenAL buffer，真实枪包音频不再依赖 `SoundHandler + CodecJOrbis`。
- `legacy-minecraft` / `legacy` / `minecraft` 属性别名当前都解析到恢复后的 `direct_openal` 主后端；旧 `SoundHandler` 路线改为显式 `vanilla-minecraft` 调试回退，`GunPackSoundResourcePack` 也只在这一显式回退模式下启用。
- 2026-03-08 本轮重新检索时发现当前工作树曾被**部分破坏/回退**：`TACZOpenALSoundEngine` 文件仍在，但 `TACZAudioTypes`、`TACZAudioRuntime`、`GunSoundPlayManager`、`ClientProxy` 的关键接线被退回到旧 `legacy-minecraft -> SoundHandler` 主链。本轮已把这些接线重新补回，当前工作树重新回到“direct OpenAL 为默认可听主链”的状态。

### 本轮实机验证结论（2026-03-08）

针对“**工作区代码库部分内容被破坏，音频代码需要重新验证**”这一轮要求，已经完成四层验证：

1. **代码重审**：确认 animation keyframe 链 `ObjectAnimationSoundChannel -> GunSoundPlayManager -> TACZAudioRuntime` 仍完整，真正回退的是 backend 末端接线
2. **主源码编译**：`./gradlew --no-daemon compileKotlin compileJava`
3. **坏状态复现 smoke**：`build/smoke-tests/runclient-focused-smoke-20260308-190244.log`
4. **修复后实机 smoke**：`build/smoke-tests/runclient-focused-smoke-20260308-190637.log`

结论已经更新为：

- 当前工作树在修复前，的确已经重新退回旧 `SoundHandler` 主链：坏状态 smoke `runclient-focused-smoke-20260308-190244.log` 明确打出 `backend=legacy_minecraft`，随后再次触发 `CodecJOrbis` / `Unable to acquire inputstream in method 'initialize'`，并以 `runClient` 退出码 `137` 结束。
- 本轮已把默认可听主链重新拉回 `direct_openal`，并重新注册了 client tick 音频钩子，让 `TACZOpenALSoundEngine` 的 deferred reload / source cleanup 真正在运行时生效。
- `./gradlew --no-daemon compileKotlin compileJava` 已通过，证明本轮音频主链改动在当前工作树中能够正确编译。
- 重新执行定向 Gradle 测试时，`compileTestKotlin` 仍被**无关音频**的并行测试阻塞（`LegacyClientShootCoordinatorBurstStateTest` 与 `MathUtilFovTest` 的 unresolved）；但此前属于音频回退所致的 unresolved —— `DIRECT_OPENAL`、`VANILLA_MINECRAFT`、`setLegacyResourceResolverForTesting` —— 已全部消失，说明音频 API/enum/runtime 接口已经重新对齐。
- 修复后的 focused smoke `runclient-focused-smoke-20260308-190637.log` 已实机打出：
  - `OpenAL direct audio ready: loaded=1515 failed=0 totalBuffers=1515`
  - `Audio manifest ready: 1829 sounds (...), backend=direct_openal`
   - `[FocusedSmoke] AUDIO_PLAYBACK_OBSERVED location=tacz:hk_mp5a5/hk_mp5a5_shoot class=TACZOpenALSoundHandle`
   - `tacz:hk_mp5a5/hk_mp5a5_shoot`、`tacz:hk_mp5a5/hk_mp5a5_inspect_raise`、`tacz:hk_mp5a5/hk_mp5a5_inspect_cloth`、`tacz:hk_mp5a5/hk_mp5a5_reload_magout` 等请求均以 `backend=direct_openal result=SUBMITTED_TO_BACKEND` 进入真实后端
   - `minecraft:p02_sm_mpapa5_raise`、`minecraft:weap_mpapa5_fire_first_plr_01` 这类缺失引用会在 direct backend 下被安全 `DROPPED`，不再把客户端拖死
  - 最终 `[FocusedSmoke] PASS animation=true projectile=true explosion=true ...`
- 对同一份 smoke log 的关键字复核已确认：**此前会把客户端拖死的 `CodecJOrbis` / `Error reading the header` / `Unable to acquire inputstream` 已不再出现。**

### 当前仍未完成的内容

- `minecraft:*` 命名但实际上在 pack / classpath 中缺失的动画音效，仍会在 manifest 中被标成 `MISSING` 并在 direct backend 下 `DROPPED`；这类引用需要继续做分类处置，而不是继续把它们喂给旧声音栈。
- normalize / cache 策略当前已经足以支撑 smoke 与主链播放，但长期仍可继续优化（例如更细粒度的缓存生命周期与资源统计）。
- dedicated backend 的设计文档已从“待实现”切换为“已落地，需要继续文档化与边界收口”。

### vanilla fallback 防卡死补丁仍然保留

尽管主链已经切到 `direct_openal`，`TACZAudioRuntime` 对显式 `vanilla-minecraft` fallback 仍保留提交前防护：

- `SUPPORTED_OGG_VORBIS`：允许继续提交到 `vanilla-minecraft`
- `MISSING` / `UNTRACKED`：只有当 `Minecraft 1.12.2` 的资源管理器能够真实解析 `sounds/<path>.ogg` 时才允许提交；否则直接 `DROPPED`
- `INVALID_OGG_CAPTURE` / `INVALID_VORBIS_IDENTIFICATION` / `IO_ERROR`：直接 `DROPPED`

这意味着旧 `SoundHandler` 路径仍可作为调试回退，但已经不会再是默认 smoke / 默认可听主链。

### 提交状态语义约定

为避免诊断输出误导，runtime 记录里的提交状态现约定为：

- `SUBMITTED_TO_BACKEND`：请求已交给某个 playback backend；**这不是“已经被人耳确认听到”的证明**
- `RECORDED_ONLY`：仅记录/预检，不尝试真实播放（`diagnostic` / `null`）
- `DROPPED`：运行时未能交给后端

这一区分很重要：它让 focused smoke 能明确回答“runtime 有没有收到请求”，同时避免把 legacy `SoundHandler` 下游的异步失败误记成“已播出”。

也就是说：本阶段已经不只是“能诊断音频链是否收到请求”，而是**已经重新把当前工作树的可听主链恢复为 direct OpenAL，并再次用 in-world smoke 证明最终播放节点被调用**。后续工作重点不再是“实现 dedicated backend”，而是继续收口缺失引用分类、缓存生命周期和诊断文档。

### 本轮验证补充

- `get_errors` 对本轮音频改动文件返回 `No errors found`
- `./gradlew --no-daemon compileKotlin compileJava` 通过；其中 `TACZAudioRuntime.kt` 只有一条无害的 Kotlin safe-call warning
- 重新执行 `./gradlew --no-daemon test --tests '*TACZOpenALSoundEngineTest' --tests '*GunSoundParityTest' --tests '*TACZAudioRuntimeTest'` 时，当前阻塞已缩小为**与音频无关**的并行测试文件：`LegacyClientShootCoordinatorBurstStateTest.kt` 与 `MathUtilFovTest.kt`
- focused smoke 运行过程中仍会看到 Forge 1.12 对现代 jar 的 ASM 扫描警告，但这些警告在本轮并未阻止进入世界、触发动画/射击或拿到最终 `PASS`；因此它们不再构成当前音频路径的阻塞

因此，这一轮可以确认的是：

- **当前工作树里被回退的 direct OpenAL 主链已经重新接回并通过主源码编译**
- **最终播放 marker（`AUDIO_PLAYBACK_OBSERVED`）已在修复后的 in-world smoke 中再次出现**
- **旧 `CodecJOrbis` 崩溃链已被本轮“先复现、后修复、再复测”完整关闭**

## 设计目标

1. **TACZ gun-pack 音频与 Minecraft 原版环境音分离**
   - 原版环境、方块、UI 等音效继续由 Minecraft 自己管理。
   - TACZ 枪包音频走专用运行时，不再依赖 `GunPackSoundResourcePack` + `SoundHandler` 作为主路径。

2. **focused smoke / CI 可在无真实音频设备下稳定运行**
   - 必须支持 `NullAudioBackend` 或等价静默后端。
   - 必须支持 preflight / diagnostic 模式，能在不真实播放声音的情况下验证：资源是否存在、是否可被专用引擎接收、哪些资源仍不兼容。

3. **统一动画音效与网络广播音效入口**
   - `ObjectAnimationSoundChannel`
   - `ServerMessageSound.Handler`
   - 这两类调用都应经过同一音频 facade / runtime / backend 管理。

4. **保留可回退路径，但不再让它阻塞主链**
   - 过渡期允许保留 `legacy-minecraft` fallback backend。
   - 但 TACZ 的主验证、focused smoke、未来默认路径，应指向专用后端而不是原版声音栈。

5. **优先可维护、可诊断、可渐进迁移**
   - 先把边界、清单、预检、后端切换做好，再逐步替换实际播放。
   - 避免一次性重写所有调用点或把整套音频系统塞进 Mixin。

## 非目标

- 不替换整个 Minecraft 音频系统。
- 不手工批量改写枪包音频资源来掩盖兼容问题。
- 不重写动画状态机、战斗或 GUI 本体逻辑。
- 不要求第一阶段就覆盖所有可能的音频编码；第一阶段更重要的是**稳定性、可诊断性和可迁移性**。

## 现状确认与关键事实

### 当前统一入口已经存在

`GunSoundPlayManager` 已经具备一个基础的 backend seam：

- `SoundPlaybackBackend playbackBackend`
- `setPlaybackBackendForTesting(...)`
- `resetPlaybackBackendForTesting()`

这意味着我们**不需要再发明第二个公开播放入口**，可以直接把它升级为真正的运行时桥。

### 当前真正触发播放的 Legacy 调用点很少

在 `TACZ-Legacy` 当前代码中，直接调用 `GunSoundPlayManager.playClientSound(...)` 的核心路径只有：

- `src/main/java/com/tacz/legacy/api/client/animation/ObjectAnimationSoundChannel.java`
- `src/main/kotlin/com/tacz/legacy/common/network/message/event/ServerMessageSound.kt`

而且这两条路径**都不消费返回值**。这说明：

- 第一阶段可以继续保留现有 facade 形式，不必因为句柄抽象而一次性重写所有调用方。
- 若后续确实需要 stop/pause/query handle，再把返回值抽象成 `GunAudioHandle` 也不会产生大规模回归。

### 当前最危险的点不是“播放 API”，而是“资源刷新与解码前置能力缺失”

真正阻塞 smoke 的并不是 `playClientSound(...)` 这一个函数本身，而是：

- `GunPackSoundResourcePack.installOrUpdate(...)`
- `minecraft.refreshResources()`
- 对资源格式/可解码性的预检缺失

因此迁移顺序必须是：**先清单/预检/后端切换，再逐步拿掉 legacy 资源桥依赖**。

## 建议架构

## 1. 总体分层

建议把新系统拆成 5 层：

1. **Facade 层**：`GunSoundPlayManager`
2. **Runtime 层**：`TACZAudioRuntime` / `TACZAudioEngine`
3. **Asset Manifest / Probe 层**：音频清单、探测、预检、诊断
4. **Decode / Normalize / Cache 层**：解码、规格统一、缓存
5. **Backend 层**：`null` / `legacy-minecraft` / `dedicated-openal` / `diagnostic`

### 分层边界

- **Facade 层**：保持现有调用点不散裂；动画和网络只认这一个入口。
- **Runtime 层**：决定当前启用哪个 backend，维护 listener snapshot、运行配置、reload 生命周期。
- **Manifest / Probe 层**：在 reload 时回答“引用了什么、能不能用、为什么不能用”。
- **Decode / Cache 层**：把原始资源转换成专用 backend 更容易消费的规范格式，例如 PCM buffer 或统一缓存条目。
- **Backend 层**：真正负责静默、记录、fallback 或 dedicated playback。

## 2. 关键接口建议

### 2.1 Facade：保留 `GunSoundPlayManager`

短期不建议创建第二套对外 API。推荐保留：

- `GunSoundPlayManager.playClientSound(...)`
- `GunSoundPlayManager.applyClientDistanceMix(...)`

但把内部职责调整为：

- facade 只负责参数兜底与兼容入口
- 真正的资源解析、backend 路由、listener/tick 交给 `TACZAudioRuntime`

### 2.2 Runtime：新增 `TACZAudioRuntime`

建议新增专门运行时，职责至少包含：

- `reload(manifest)`：接收客户端资源 reload 后的音频清单
- `play(request)`：接收播放请求并路由到 backend
- `tick(listenerSnapshot)`：更新 listener/camera 位姿、回收已完成 source
- `shutdown()`：客户端退出或 world unload 时释放资源
- `diagnostics()`：导出可用于 smoke/日志的汇总信息

建议的 request 数据最少包括：

- `soundId`
- `entityId` / 实体跟随信息（可空）
- `world position`（若有）
- `volume`
- `pitch`
- `distance`
- `category/source`（animation/server/builtin 等）
- `spatial` / `loop` / `mono policy` 等策略位

### 2.3 Backend：统一 `TACZAudioBackend`

建议定义 backend 抽象，例如：

- `initialize()`
- `submit(request, asset)`
- `tick(listenerSnapshot)`
- `stopAll()`
- `close()`
- `modeName()`
- `healthSnapshot()`

建议至少有 3 类实现：

1. `NullAudioBackend`
   - 什么都不播，但记录请求数、资源命中、失败原因。
   - focused smoke / CI 默认优先使用。

2. `LegacyMinecraftAudioBackend`
   - 兼容过渡实现。
   - 内部仍可委托 `GunSoundInstance` / `SoundHandler`。
   - 不应再作为 focused smoke 的默认 backend。

3. `DedicatedOpenALAudioBackend`
   - 目标实现。
   - 直接使用专用 source/buffer 管理 TACZ gun-pack 音频。
   - 不依赖 `SoundHandler` 或 `IResourcePack` 资源桥作为主路径。

> 说明：若本轮只够落到 `Null + Legacy + Manifest/Probe`，也可以先完成第一阶段；但设计目标仍应明确指向 dedicated backend。

## 3. 资源清单、Probe 与预检

### 3.1 为什么 manifest 是第一优先级

当前系统直到播放时才知道资源能不能被消费，这对 smoke 来说太晚了。新系统必须在 reload 后就能回答：

- 哪些 sound id 被 display / animation / server sound 引用
- 每个 sound id 对应到哪个 pack 文件
- 原始资源是否存在
- 原始资源的容器/编码是否可识别
- 当前 backend 是否能接收该资源

### 3.2 建议的数据结构

建议新增类似如下的结构（命名可调整）：

- `TACZAudioAssetManifest`
- `TACZAudioAssetDescriptor`
- `TACZAudioProbeResult`
- `TACZAudioDiagnosticsSnapshot`

每个 descriptor 至少记录：

- `soundId`
- `originType`（display sound / animation keyframe / server sound）
- `sourcePack`
- `resourcePath`
- `exists`
- `byteSize`
- `containerType`
- `decodeStatus`
- `fallbackEligible`
- `notes / error`

### 3.3 集成点

建议把 manifest 构建放在 `TACZClientAssetManager.reload()` 的后半段：

- display sound map 收集音频
- animation sound keyframe 收集音频
- 对每个 `soundId` 调用 pack asset resolver 做探测
- 把结果传给 `TACZAudioRuntime.reload(manifest)`

### 3.4 Smoke 输出建议

focused smoke 应至少输出：

- `audio.manifest.total`
- `audio.manifest.missing`
- `audio.manifest.unsupported`
- `audio.backend.mode`
- `audio.submit.count`
- `audio.submit.failed`

必要时可导出到 `build/smoke-tests/` 下的 JSON 或文本摘要。

## 4. Decode / Normalize / Cache

### 4.1 为什么需要专门的 decode 层

即使资源路径能找到，也不代表可以被 1.12 原版顺利解码。专用后端需要自己的“可控解码链”，否则只是把问题从 `SoundHandler` 搬到了别处。

### 4.2 建议策略

- 资源读取统一经 `GunPackAssetLocator` / `TACZClientAssetManager.openPackAsset(...)` 完成
- decode 层负责把原始资源变成 backend 可接受的标准形式
- normalize 层可做：
  - sample rate 统一
  - bit depth 统一
  - mono/stereo 策略
  - 可选的 downmix
- cache 层负责：
  - 内存缓存（快速复用）
  - 可选磁盘缓存（减少重复 decode）

### 4.3 缓存位置建议

建议优先放到仓库运行时生成目录，例如：

- `build/tacz-audio-cache/`
- 或 `run/tacz-audio-cache/`

选择原则：

- 不污染源码目录
- 可以安全清理
- smoke 与 runClient 都能访问

### 4.4 第三方库策略

首选原则：

- 优先 **Java 8 可用、纯 Java、许可证明确** 的解码方案
- 若必须引入原生依赖，必须明确说明平台分发与 CI 成本

因此建议：

1. 第一阶段先完成 manifest / probe / backend 抽象 / null backend
2. 第二阶段再评估最合适的 dedicated decode 方案
3. 不要在方案尚未定型前就把整个工程绑死在复杂 native 依赖上

## 5. Listener、空间化与音量策略

专用 backend 一旦不再依赖 `SoundHandler`，就需要自己维护 listener 信息。建议：

- 每帧或每 tick 从 `Minecraft` / 当前 camera / player 读取 listener snapshot
- backend 使用统一 snapshot 进行空间化更新
- 现有 `applyClientDistanceMix(...)` 可在过渡期继续复用，但长期最好让 dedicated backend 自己负责衰减策略

对 gun-pack 音效的策略建议：

- 第一人称 / 本地玩家关键动画音效：允许 non-spatial 或近距离优先策略
- 其他实体枪声、爆炸枪声：走 spatial
- 若资源是 stereo 但请求 spatial，可记录诊断并执行可控降级（例如 downmix mono）

## 6. focused smoke / CI 方案

## 6.1 新的运行模式

建议支持 JVM 属性切换，例如：

- `-Dtacz.audio.backend=null`
- `-Dtacz.audio.backend=legacy`
- `-Dtacz.audio.backend=dedicated`
- `-Dtacz.audio.preflight=true`
- `-Dtacz.audio.preflight.strict=true`

其中：

- `null`：不真实播放，只统计与验证链路
- `legacy`：兼容 fallback
- `dedicated`：专用 backend
- `preflight.strict=true`：只要存在 missing/unsupported 资源就把 smoke 判定为失败

## 6.2 脚本改造建议

`scripts/runclient_focused_smoke.sh` 建议增加音频相关参数透传，至少允许：

- 默认用 `null + preflight`
- 可选切到 `dedicated`
- 在日志里输出独立 audio 段的 PASS/FAIL marker

例如可以在 focused smoke runtime 中新增：

- `[FocusedSmoke][Audio] backend=null manifest=42 missing=0 unsupported=0`
- `[FocusedSmoke][Audio] submit animation=3 server=1 failed=0`

再由总 PASS/FAIL 汇总判定。

## 7. 渐进迁移路线

### 阶段 A：先把边界切开

目标：

- 保留 `GunSoundPlayManager` facade
- 新增 `TACZAudioRuntime` 与 backend 抽象
- 增加配置项和运行模式

交付标准：

- 代码里已经能在 facade 层切换 backend
- 不再把“测试 seam”仅仅视为单元测试 hack，而是正式运行时能力

### 阶段 B：manifest / probe / null backend 先落地

目标：

- reload 时构建音频清单
- 能记录 missing / unsupported
- focused smoke 默认走 null backend + preflight

交付标准：

- smoke 不再因为真实音频播放链卡死
- 日志/诊断能回答资源兼容性状态

### 阶段 C：引入 dedicated backend（可先不替换全部）

目标：

- 让 TACZ gun-pack 音频可以在不依赖 `SoundHandler` 的情况下被播放
- 至少覆盖 animation keyframe sound 与 `ServerMessageSound`

交付标准：

- 两条路径都经由 dedicated runtime/backend
- focused smoke 可切 dedicated 模式做验证

### 阶段 D：去掉主链上的 resource-pack bridge 依赖

目标：

- `GunPackSoundResourcePack` 不再是 TACZ gun-pack 音频主链的必要条件
- `refreshResources()` 不再因为枪包音频而成为常态操作

交付标准：

- 默认 TACZ 音频主路径不依赖 `IResourcePack` 注入
- legacy backend 仅作为 fallback 或调试选项保留

### 阶段 E：清理与强化

目标：

- 完善缓存策略
- 明确不兼容资源报告
- 补 focused smoke / 单测 / 文档

交付标准：

- 音频链路具备稳定的可诊断性
- 后续动画 / render / combat Agent 可以把“有没有声、为什么没声”直接定位到 manifest/backend/probe，而不是重新挖声音栈

## 8. 对现有代码的推荐落点

### 继续沿用的文件

- `src/main/java/com/tacz/legacy/client/sound/GunSoundPlayManager.java`
- `src/main/java/com/tacz/legacy/api/client/animation/ObjectAnimationSoundChannel.java`
- `src/main/kotlin/com/tacz/legacy/common/network/message/event/ServerMessageSound.kt`
- `src/main/kotlin/com/tacz/legacy/client/resource/TACZClientAssetManager.kt`

### 逐步边缘化的文件

- `src/main/java/com/tacz/legacy/client/sound/GunPackSoundResourcePack.java`
- `src/main/java/com/tacz/legacy/client/sound/GunSoundInstance.java`

它们可以在过渡期保留，但不应继续被视为 TACZ 专用音频的长期核心。

### 建议新增目录

可新增类似结构（命名可调整）：

- `src/main/java/com/tacz/legacy/client/audio/runtime/**`
- `src/main/java/com/tacz/legacy/client/audio/backend/**`
- `src/main/java/com/tacz/legacy/client/audio/asset/**`
- `src/main/java/com/tacz/legacy/client/audio/decode/**`
- `src/main/java/com/tacz/legacy/client/audio/diagnostic/**`

## 9. 验收标准

满足以下条件，才算这条方案真正落地：

1. focused smoke 在默认配置下不再因为 TACZ gun-pack 音频卡死
2. `ObjectAnimationSoundChannel` 与 `ServerMessageSound` 都经统一 runtime/backend 提交
3. reload 后能得到 manifest / probe 结果，而不是只有播放时日志
4. TACZ gun-pack 音频主链不再必须依赖 `refreshResources()`
5. 能明确列出仍不兼容的资源，而不是沉默失败

## 10. 给后续 Agent 的实施建议

- **先做阶段 A/B，再做阶段 C**。不要一上来就重写整套 dedicated playback 却没有 manifest / probe / smoke 兜底。
- **优先解决“稳定性与可诊断性”**。没有这层，后续动画/战斗 Agent 一旦反馈“没声音”，仍然只能靠猜。
- **不要把音频问题转化为资源批量手改**。那会破坏枪包兼容的总体目标。
- **如需改 public API，先确认调用方数量**。当前 `playClientSound(...)` 的返回值在 Legacy 主链中并未被消费，因此可以渐进演进，而不是一次性大改。

## 11. 与其他 Prompt 的边界

- `tacz-stage-render-animation-first-person.prompt.md`
  - 负责“动画为什么没播、状态机为什么没动、sound keyframe 为什么没被推进”。
  - 当它确认“sound keyframe 已推进，但底层音频后端/兼容层失败”时，应交给本方案。

- `tacz-stage-foundation-client-hooks.prompt.md`
  - 负责 shared registration、focused smoke 入口与共享守门。
  - 若需要接 focused smoke 参数透传或诊断 marker，可配合，但不应接管音频后端本体。

- `tacz-migrate-data-pack.prompt.md`
  - 负责 sound id / display / animation 资源引用是否被 runtime 正确保留。
  - 负责“引用是否存在”，不是“底层如何播放”。

这个边界要保持清晰：**本方案负责 TACZ 专用音频运行时，不替别的轨道吞掉业务 feature。**
