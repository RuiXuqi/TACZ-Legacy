# TACZ-Legacy 瞄准镜 (Scope) 渲染迁移方案

## 1. 原版 (TACZ) 渲染机制分析

在 TACZ 中，处于 Aim (ADS) 状态的瞄准镜渲染并不是将模型简单叠加在枪支之上，而是通过**OpenGL 模板测试 (Stencil Buffer)** 构建的一个多阶段渲染管线。主要流程如下：

### A. 骨骼节点严格分层
`BedrockAttachmentModel` 在加载时会提取针对瞄准镜特化的骨骼节点：
- `scope_view` / `scope_body` / `ocular_ring`
- `ocular`（镜孔/镜片所在面）
- `division`（准星分划板/红点）

### B. 渲染时序倒置与模板写入 (Stencil Masking)
在绘制主枪体之前，优先渲染 Scope 的 `ocular` (镜片) 模型：
1. 关闭颜色和深度写入：`RenderSystem.colorMask(false, false, false, false)`
2. 开启模板写入，为不同的镜孔依次写入增量模板值（如 `1`, `2` 等）：`RenderSystem.stencilFunc(GL11.GL_GREATER, i + 1, 0xFF)`

### C. 枪体遮罩裁切 (Body Culling)
主枪体渲染时：
- 设置模板测试：`RenderSystem.stencilFunc(GL11.GL_EQUAL, 0, 0xFF)`
- **效果**：枪体渲染到与镜片重叠的屏幕区域时，像素会被硬件丢弃。完美防止枪管或机匣内部在开镜时穿模挡住视野。

### D. 视野张开与准星动态渲染 (Dynamic Reticle)
使用 `Tesselator` 绘制动态分划板：
1. 根据玩家的开镜进度 (Aiming Progress, 0.0~1.0)，计算出动态的圆形半径。
2. 使用 `GL_INVERT` 模板操作，在 `ocular` 范围内通过 `Triangle Fan` 画圆，限制后续渲染只在张开的视野圈内生效。
3. 最后，重新启用颜色写入，在此遮罩下渲染 `division` 节点（准星）。

---

## 2. TACZL (1.12.2) 迁移方案与重构难点

在 1.12.2 Forge 环境下的 `LegacyGunItemStackRenderer` 中实现该机制，需要解决以下根本痛点：

### 痛点 (1)：Framebuffer 开启 Stencil 附件
Minecraft 1.12.2 默认的主 Framebuffer Object (FBO) 仅分配了 24-bit 的 Depth 格式（没有 8-bit Stencil）。直接调用 `GL11.glEnable(...)` 无效。
**解决方案**：
在客户端初始化或由于窗口变动重建时，强制激活主 FBO 的模板缓冲：
```java
Minecraft.getMinecraft().getFramebuffer().enableStencil();
```

### 痛点 (2)：GeckoLib/LegacyGeoModel 节点解析
现在的渲染是基于根模型将所有骨架递归渲染完毕（“一锅炖”）。我们需要提取外部模型内部的特定层次。
**解决方案**：
修改或包装现在的模型解析结果：
遍历外部配件 GeoModel 定义的 `IBone`，抽取 `ocularNodes`、`divisionNodes`、`scopeBodyNode`，在 `ExternalAttachmentRenderContext` 中单独分类并提供独立的 `renderOcular()` 等方法。

### 痛点 (3)：重排 `LegacyGunItemStackRenderer` 渲染管线
不能在挂点处立即渲染附属物品。必须将整个物品渲染流切分为多个 Pass。
**重构的管线顺序（伪代码）：**
```kotlin
fun renderItem(...) {
    // 1. 预先解析所有挂载件并得到绝对变换矩阵 (如 scope_pos)
    val attachmentContexts = resolveAllAttachments()
    val scopeContext = attachmentContexts.findByType(SCOPE)

    // 2. Pass 1: Stencil Mask 构建
    if (scopeContext != null && scopeAiming) {
        GL11.glEnable(GL11.GL_STENCIL_TEST)
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT)
        
        setupStencilWriteState() // 关闭 Color/Depth 写入
        scopeContext.renderOcularStencils() // 仅渲染镜孔部位写入 Stencil ID
        restoreColorDepthState()
    }

    // 3. Pass 2: 主枪体渲染
    if (scopeContext != null) {
        GL11.glStencilFunc(GL11.GL_EQUAL, 0, 0xFF) // 枪身不能覆盖镜孔
    }
    renderGunBody(..., excludedNodes = listOf(scopeContext))

    // 4. Pass 3: 镜筒与准星渲染
    if (scopeContext != null) {
        GL11.glStencilFunc(GL11.GL_EQUAL, 0, 0xFF)
        scopeContext.renderScopeBody() // 渲染瞄具外壳
        
        applyDynamicReticleMask() // 计算开镜进度并反转 Stencil 绘制圆
        scopeContext.renderDivisions() // 渲染准星

        GL11.glDisable(GL11.GL_STENCIL_TEST)
    }
}
```

### 痛点 (4)：状态隔离与兼容性
1.12.2 的 `GlStateManager` 没有暴露完整的 Stencil 功能，必须混用 `GL11.*` 方法。
考虑到光影（Optifine/Oculus）也深度利用了 Stencil/Depth 缓冲区，我们在渲染开始和结束时必须**保存且恢复当前的 Stencil State（或者进行合理的位掩码区隔）**。

## 3. 后续工作流建议
1. 确认主渲染管线的架构升级方向（将一锅炖改为 RenderPass 机制）。
2. 在 `LegacyGunItemStackRenderer` 中引入 Stencil / Stencil Mask 能力类。
3. 从外部配件库的模型解析出特殊的骨架节点。
4. 拼装重排整体渲染顺序。
