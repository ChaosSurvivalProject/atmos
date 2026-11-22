# 03 · 分阶段计划

> 原则：**先骨架与契约，后演化与判定，最后表现层与打磨**。每阶段有明确产出与验收，确认一段再写一段，不一次性倾倒实现。

---

## Phase 0 · 骨架与协议基座（地基）
**产出**
- 三个 Gradle 模块根配置（toolchain 21）、`protocol` 常量/枚举/POJO。
- `FrameCodec` 手工编解码 + `VarInt`/`Fixed`/`Bytes` 工具。
- `GoldenVectorTest`：硬编码 hex ↔ 对象 ↔ hex，覆盖 magic/边界/定点/最大长度。
- `plugin.yml`（api-version 1.21.11）、`fabric.mod.json` 最小可加载壳。

**验收**：`./gradlew test` 全绿；服务端能加载插件、客户端能进 Fabric 世界（无功能）。

---

## Phase 1 · 链路与握手（打通一个端口）
**产出**
- 服务端 `ChannelBridge`/`Session`/`RateLimiter`；客户端 `ClientBridge`/`Handshake`/`Reassembler`。
- `atmos:hello` → `welcome` → `clock` 握手闸门；L1/L2 判定。
- `Fragmenter` 分片 + ack 补片 + 乱序处理 + 30 KiB 上限验证。

**验收**：装 mod 客户端收到 welcome 并进入 L2；未装 mod 玩家正常进服走 L1；断链/超长包只降级不踢人（对应验收 #2/#4）。

---

## Phase 2 · 服务端气候内核（权威场，无渲染）
**产出**
- `ClimateField` 256²/512² 七要素网格；`Noise` 分层 simplex + 阻尼 + 阵风。
- `Front` 风驱锋面传播（20s 双线性过渡）；`Cyclone` 低压吸引子识别与收敛。
- `BiomeSampler` 一次性克隆群系快照（异步只读，不碰实时区块）。
- `ClimateTickDriver`：异步演化 → 主线程 apply；`doWeatherCycle` 接管与基底衔接。

**验收**：无客户端也能在内存/日志中观测到连续场演化；异步不触碰 Bukkit 对象（静态检查 + 日志）；MSPT 基线记录。

---

## Phase 3 · 场同步与客户端渲染（看得见）
**产出**
- `FieldSender`：视距+一格缓冲切片、delta 判定、1–2s 批发送；`cells` 单体下发。
- 客户端 `LocalField`/`Interpolator`（借 clock 插值）；`mixin` 接管降水/雨雷梯度/云雾天色光照。
- `ParticleKit` 基础粒子（斜雨幕、风线）；模式 A 对齐预设。

**验收**：装 mod 玩家看到局地场；模式 A 下装/未装 mod 同屏观感一致（验收 #3）。

---

## Phase 4 · 天气预报（会变的预报）
**产出**
- `ForecastEngine` 7 日分段推演 + 临近订正 + 置信度；`Scoreboard` 命中率统计。
- `forecast` 分片下发；`/wx forecast` 书本 GUI；BossBar/ActionBar 倒计时。
- 气象站方块 + 告示牌、雷达小地图、`AtmosAPI.forecast(day)`、`ForecastPublishEvent`。

**验收**：预报错逐日订正、有置信度、能出命中率报表（验收 #5）。

---

## Phase 5 · 天气注册表与效果（玩法落地）
**产出**
- `weathers.yml` 配置驱动注册表（普通/强/极端三档全部天气定义）。
- `EffectEngine`：逐项开关（推力/视野/作物/玻璃树叶/积雪/雷击引燃/冰雹/庇护/船矿车失控/火焰熄灭）。
- `Shelter` 庇护判定（头顶遮挡 + 室内 = 安全，只惩罚暴露实体与作物）。
- `warn/effect` 即时事件、提前预警、`ExtremeWarningEvent`、`WeatherCellSpawnEvent`。

**验收**：极端天气提前预警；室内完全安全、室外按配置惩罚（验收 #6）。

---

## Phase 6 · 降级、持久化、预算（稳定性）
**产出**
- L0/L1/L2 完整降级状态机；未装 mod 玩家 L1 体验打磨（原版粒子/音效/BossBar）。
- `SnapshotStore` 年循环/干旱/积雪/土壤湿度落盘续算。
- `Budget` 全局出站字节预算 + MSPT 三级自适应（降分辨率→降频率→只保预警）。

**验收**：满载 MSPT 未明显劣化、带宽有上限（验收 #7）；重启后续算正确。

---

## Phase 7 · 命令 / API / Hook / 文档
**产出**
- `/wx now|forecast|warn|radar|set|reload|verify|debug` 权限分级。
- `AtmosAPI.current(cell)`；`ClimateTickEvent` 等自定义事件；PAPI/WorldGuard/Discord（默认关）。
- 可选 Webhook（默认关、仅出站 HTTPS、不监听）；`verify` 自检端口与协议。
- README 验收清单逐项实测勾选 + 日志/截图。

**验收**：全部 7 项验收通过并写入 README。

---

## Phase 8 · 打磨与发布
**产出**
- 性能回归、极端场景压测、配置文档补全、Release 构建脚本。
- 组织仓库打 tag、发布 notes。

---

## 依赖关系（不可跳步）

```
Phase 0 ──> Phase 1 ──> Phase 2 ──> Phase 3
                          │           │
                          └──> Phase 4（依赖场）
                          └──> Phase 5（依赖场 + 链路）
                      Phase 1,2,3,4,5 ──> Phase 6 ──> Phase 7 ──> Phase 8
```

- Phase 2 之前不写任何渲染（没有权威场就没有可渲染之物）。
- Phase 3 之前不写 HUD/粒子细节。
- Phase 6 的稳定性必须在功能齐全后统一验证。
