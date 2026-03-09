# TACZ-Legacy 旧版第一人称枪模渲染参考

> 目的：说明 **旧版 / 89fc159 参考实现** 是如何
> 1. 抑制原版主手/副手物品渲染与 equip 动画
> 2. 接管第一人称枪模渲染
> 3. 将枪模从原始模型原点重定位到正确的屏幕中心透视位置
>
> 这份文档只描述当前已核实的旧版有效链路，不把未激活或后补的死代码当成真值。

## 1. 旧版真正生效的拦截点

在 `89fc159` 里，和第一人称枪模直接相关、且**确实在 mixins.tacz.json 中激活**的只有：

- `src/main/java/com/tacz/legacy/mixin/minecraft/client/ItemRendererMixin.java`
- `src/main/kotlin/com/tacz/legacy/client/event/FirstPersonRenderGunEvent.kt`
- `src/main/kotlin/com/tacz/legacy/client/event/FirstPersonRenderMatrices.kt`

对应 `src/main/resources/mixins.tacz.json`：

```json
{
  "mixins": ["LivingEntityMixin"],
  "client": ["client.ItemRendererMixin"]
}
```

这意味着：

- **旧版没有第二套隐藏的 EntityRenderer / ItemInHandRenderer mixin 在工作**。
- **旧版也没有真正接入 KeepingItemRenderer 的“保留上一把武器继续渲染”链路**。
- 抑制原版动画靠的是：
  1. `ItemRendererMixin` 压掉 equip 进度动画
  2. `FirstPersonRenderGunEvent` 直接取消 vanilla hand render，自己画枪

## 2. 旧版如何抑制原版“拿物品/切物品”动画

文件：`src/main/java/com/tacz/legacy/mixin/minecraft/client/ItemRendererMixin.java`

核心逻辑：在 `ItemRenderer.updateEquippedItem()` 的开头把主手 equip 进度钉死到 1，并把渲染中的主手物品强制设为当前枪。

```java
@Inject(method = "updateEquippedItem", at = @At("HEAD"))
private void cancelGunEquipAnimation(CallbackInfo ci) {
    EntityPlayerSP player = Minecraft.getMinecraft().player;
    if (player == null) {
        return;
    }
    ItemStack itemStack = player.getHeldItemMainhand();
    if (itemStack.getItem() instanceof IGun) {
        equippedProgressMainHand = 1.0f;
        prevEquippedProgressMainHand = 1.0f;
        itemStackMainHand = itemStack;
    }
}
```

### 这段代码实际抑制了什么？

它抑制的是 **vanilla 主手 equip animation**：也就是切换到武器时，物品先下沉再抬起的那段默认拿起动画。

### 它没有单独 patch 什么？

它**没有**逐个 patch “吃东西 / 拉弓 / 格挡 / 使用物品”这类 use animation。旧版真正压掉这些 vanilla 第一人称物品动作，不是靠这里，而是靠下一层：**直接取消原版 hand render**。

## 3. 旧版如何抑制原版“使用物品/手部渲染”动画

文件：`src/main/kotlin/com/tacz/legacy/client/event/FirstPersonRenderGunEvent.kt`

旧版在 `RenderSpecificHandEvent` 上接管第一人称渲染：

### 3.1 副手直接取消

```kotlin
if (event.hand != EnumHand.MAIN_HAND) {
    val mainItem = player.heldItemMainhand
    if (mainItem.item is IGun) {
        event.isCanceled = true
    }
    return
}
```

含义：

- 当主手拿的是枪时，**副手渲染也直接取消**。
- 这样可以避免 vanilla 副手或副手物品动作继续出现在画面里。

### 3.2 主手枪械改为完全自绘，并取消 vanilla render

```kotlin
val stack = event.itemStack
val iGun = stack.item as? IGun ?: return
...
model.render(stack)
...
event.isCanceled = true
```

含义：

- 一旦主手物品是 `IGun`，旧版就不再让原版 `ItemRenderer` 继续走自己的第一人称绘制流程。
- **vanilla 第一人称 use animation 之所以消失，不是因为它们分别被 patch 了，而是因为原版整条 hand/item render 被取消了。**
- 然后旧版自己决定保留哪些 vanilla 动作（例如 swing/equip 的基础位移），哪些完全不用。

> 2026-03-08 refit UI 实机补充：当前 Legacy 的生产态 `GunRefitScreen` 已不再在 GUI 层额外调用 `TACZGuiModelPreviewRenderer.renderStackPreview(...)` 或 fallback `itemRender.renderItemAndEffectIntoGUI(...)` 再画一把“屏幕内预览枪”。改装界面现在只保留第一人称手持枪模这条真实渲染链；screen 自身只负责附件槽、候选列表、属性图表与 tooltip 等 UI，不再额外叠一把悬浮枪模。

## 4. 旧版如何重定位枪模渲染位置

旧版重定位不是“写死一个平移常量”那么简单，而是一个顺序明确的矩阵链。

## 4.1 先套一层 vanilla first-person baseline

旧版在自绘前，先调用：

```kotlin
applyVanillaFirstPersonTransform(handSide, event.equipProgress, event.swingProgress)
```

内部是 1.12 原版第一人称持物的基础位移/挥动旋转：

```kotlin
private fun transformSideFirstPerson(handSide: EnumHandSide, equipProgress: Float) {
    val side = if (handSide == EnumHandSide.RIGHT) 1 else -1
    GlStateManager.translate(side * 0.56f, -0.52f + equipProgress * -0.6f, -0.72f)
}

private fun transformFirstPerson(handSide: EnumHandSide, swingProgress: Float) {
    val side = if (handSide == EnumHandSide.RIGHT) 1 else -1
    val swingSin = MathHelper.sin(swingProgress * swingProgress * Math.PI.toFloat())
    GlStateManager.rotate(side * (45.0f + swingSin * -20.0f), 0.0f, 1.0f, 0.0f)
    val swingRootSin = MathHelper.sin(MathHelper.sqrt(swingProgress) * Math.PI.toFloat())
    GlStateManager.rotate(side * swingRootSin * -20.0f, 0.0f, 0.0f, 1.0f)
    GlStateManager.rotate(swingRootSin * -80.0f, 1.0f, 0.0f, 0.0f)
    GlStateManager.rotate(side * -45.0f, 0.0f, 1.0f, 0.0f)
}
```

### 解释

这一步不是让原版来画枪，而是**只借用原版第一人称基础手持姿态**作为起点。

> 2026-03-08 实机补充：这一层 `applyVanillaFirstPersonTransform(...)` 只应被视为 **旧实现考古事实**，不能再直接当成今天生产链里的“永远都要保留”的步骤。对 `timeless50` 这类枪，继续无条件叠加 1.12 `transformSideFirstPerson + transformFirstPerson` 会把自定义枪模长期推向右下并拉远镜头，让画面看起来像“原版右下角小物件”。当前 Legacy 的生产修复已经移除了这层常驻 baseline，让 `idle_view` / `iron_view` positioning 节点重新主导第一人称构图；本文保留它，仅用于解释旧版为何当时能跑，以及为什么今天不能原样照抄。

## 4.2 再把 Bedrock 模型坐标系摆正

```kotlin
GlStateManager.translate(0.0f, 1.5f, 0.0f)
GlStateManager.rotate(180.0f, 0.0f, 0.0f, 1.0f)
```

### 解释

- vanilla 当前的渲染原点不是 Bedrock 枪模的原点
- Bedrock 枪模型坐标还是倒的
- 这两步是把枪模从 vanilla/item 原点移动到 Bedrock 模型原点，并把朝向翻正

如果少了这两步，枪模通常会：

- 出现在错误高度
- 左右/上下翻转
- 看起来像贴在手上某个奇怪位置，而不是屏幕中心的枪

## 4.3 用定位组逆矩阵把枪模推到屏幕中心正确位置

旧版最终真正决定枪口/机瞄/镜位位置的，不是前面的 vanilla baseline，而是：

```kotlin
applyFirstPersonPositioningTransform(model, stack, aimingProgress)
```

旧版 `89fc159` 的实现：

```kotlin
private fun applyFirstPersonPositioningTransform(model: BedrockGunModel, stack: ItemStack, aimingProgress: Float) {
    val transformMatrix = FirstPersonRenderMatrices.buildAimingPositioningTransform(
        idlePath = FirstPersonRenderMatrices.fromBedrockPath(model.idleSightPath),
        aimingPath = FirstPersonRenderMatrices.fromBedrockPath(model.resolveAimingViewPath(stack)),
        aimingProgress = aimingProgress,
    )
    GlStateManager.translate(0.0f, 1.5f, 0.0f)
    positioningMatrixBuffer.clear()
    transformMatrix.get(positioningMatrixBuffer)
    positioningMatrixBuffer.rewind()
    GL11.glMultMatrix(positioningMatrixBuffer)
    GlStateManager.translate(0.0f, -1.5f, 0.0f)
}
```

而 `FirstPersonRenderMatrices.buildPositioningNodeInverse()` 是核心：

```kotlin
internal fun buildPositioningNodeInverse(nodePath: List<PositioningNode>?): Matrix4f {
    val matrix = Matrix4f().identity()
    if (nodePath == null) {
        return matrix
    }
    for (i in nodePath.indices.reversed()) {
        val part = nodePath[i]
        matrix.rotateX(-part.xRot)
        matrix.rotateY(-part.yRot)
        matrix.rotateZ(-part.zRot)
        if (part.hasParent) {
            matrix.translate(-part.x / 16.0f, -part.y / 16.0f, -part.z / 16.0f)
        } else {
            matrix.translate(-part.x / 16.0f, 1.5f - part.y / 16.0f, -part.z / 16.0f)
        }
    }
    return matrix
}
```

### 解释

这一步本质上是：

- 从 `idleSightPath` / `ironSightPath` / scope view path 这类 **定位骨骼路径** 反推一个“把骨骼对齐到摄像机”的逆矩阵
- 再把这个逆矩阵乘到当前 OpenGL 模型矩阵上
- 于是枪模不再待在 JSON 原点，而是被“吸”到正确的屏幕中心/机瞄/镜位上

也就是说：

- `applyVanillaFirstPersonTransform()` 只给出一个 vanilla baseline
- **真正让枪模“居中且可瞄”的关键是 positioning matrix，而不是 baseline 常量**

## 5. 旧版对“所有 vanilla 动画”的真实处理边界

如果你要“彻底修复渲染问题”，要明确旧版是怎么处理的：

### 旧版确实抑制/绕开的部分
- 主手 equip animation（通过 `ItemRendererMixin`）
- 副手渲染（主手持枪时直接取消）
- 主手 vanilla item render/use animation（通过 `RenderSpecificHandEvent` 取消原版 render）
- vanilla hand/item 最终绘制结果（改为 `model.render(stack)`）

### 旧版仍然保留/复刻的部分
- baseline 手持姿态（`applyVanillaFirstPersonTransform`）
- swing/equip 参数输入（来自 event 的 `equipProgress` / `swingProgress`）

### 这意味着什么？
如果你想要 **完全不受原版手持动画影响**，那就不能只停在“取消原版 render”这一步；还要进一步检查：

1. 是否还在使用 `event.equipProgress` / `event.swingProgress`
2. 是否还在调用 `applyVanillaFirstPersonTransform()`
3. 是否需要在 ADS / inspect / reload 等阶段把 baseline transform 权重降到 0，改成纯自定义 transform

换句话说：

- 旧版做的是 **render takeover + equip suppression + custom repositioning**
- 旧版不是“把所有 vanilla 动画参数都归零”

## 6. 上游 TACZ 对应真值（帮助你补完缺口）

当前 Legacy 旧版没有完整移植、但上游 TACZ 明确存在的两点：

1. `TACZ/src/main/java/com/tacz/guns/mixin/client/ItemInHandRendererMixin.java`
   - 现代版 `tick()` 中同样把 `mainHandHeight/oMainHandHeight` 钉死到 `1.0f`
   - 还实现了 `KeepingItemRenderer`，支持切枪/收枪时“上一把枪继续渲染一段时间”
2. `TACZ/src/main/java/com/tacz/guns/client/event/FirstPersonRenderEvent.java`
   - 直接在 hand render 事件里调用自定义 renderer
   - 渲染后 `event.setCanceled(true)`

这说明 Legacy 要彻底修复第一人称渲染，最可靠的路径依然是：

- **继续坚持 total takeover**，不要退回 vanilla item renderer
- 用 mixin 压住 equip progress
- 用自定义矩阵链做重定位
- 需要“收枪时上一把还在屏幕里”的话，再把 `KeepingItemRenderer` 的真正实现接回到 `ItemRendererMixin`

## 7. 直接结论

如果你的目标是“彻底修复渲染问题”，旧版给出的可复用结论是：

1. **抑制原版动画，不是逐项 patch use animation，而是直接取消 vanilla hand render。**
2. **切枪拿起动画的抑制点在 `ItemRenderer.updateEquippedItem()`。**
3. **枪模重定位的核心不是常量平移，而是 `FirstPersonRenderMatrices.buildPositioningNodeInverse()` 生成的定位组逆矩阵。**
4. **如果仍然看到原版主手动作泄漏，先查是不是还在不受控地使用 `equipProgress/swingProgress`，以及是否还在无条件叠加 `applyVanillaFirstPersonTransform()`。**
5. **如果仍然看不到正确枪位，先查 positioning path（idle/aim/scope）和 Bedrock 坐标翻转步骤，而不是先改模型 JSON。**
