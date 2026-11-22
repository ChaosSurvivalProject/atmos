# Atmos —— Minecraft 1.21.11 真实气象系统（C/S 协同）

> 服务端权威的连续大气场 + Fabric 纯客户端渲染。一个端口，零外部依赖，零客户端判定。

**当前状态：设计阶段（Phase 0 未开始）。** 本仓库已交付【目录结构 + 协议契约表 + 分阶段计划】，等待项目方确认后再逐模块提交实现代码。详见 [`docs/`](docs/)。

---

## 一句话架构

```
[Purpur 1.21.11 插件]  ── Custom Payload (atmos:*, 复用 25565 TCP) ──>  [Fabric 纯客户端 mod]
  · 唯一权威：气象场/预报/气旋/判定/持久化                                  · 只渲染 + 插值 + HUD + ack
  · 服务端不装任何 mod                                                      · 上报一律不可信
```

- 服务端：单个 Bukkit/Paper 插件，`api-version: 1.21.11`，Java 21。
- 客户端：Fabric 1.21.11 **纯客户端** mod，服务端不装任何 mod。
- 共享：`protocol/` 模块，两端复用字节级编解码，用 golden vector 单测钉死一致性。

---

## 部署方式（规划，代码完成后可执行）

1. **服务端**：将 `server/build/libs/atmos-server-<ver>.jar` 放入 `plugins/`，重启。首次启动生成 `config.yml / biomes.yml / weathers.yml / forecast.yml / messages.yml`。
2. **客户端**：将 `client/build/libs/atmos-client-<ver>.jar` 放入 Fabric 1.21.11 的 `mods/`。
3. **无需开放任何新端口**（见下方核对清单）。
4. **代理**：若经 Velocity，见「代理注意事项」。

> 模块 A/B 与"强制 mod"开关决定了未装 mod 玩家的体验，务必先读「模式 A/B 差异」。

---

## 端口核对清单（硬性验收项 #1）

部署后用 `ss -tlnp` 或 `netstat -tlnp` 核对，**期望结果只有 25565**：

```bash
ss -tlnp | grep java
# 期望：仅 0.0.0.0:25565（或服务器配置的单一端口）
# 禁止出现：任何 0.0.0.0:其他端口 / 127.0.0.1:其他端口
```

本方案在代码层面对以下模式**全部禁用**，CR 时逐项 grep 确认零命中：

| 禁止项 | 检查关键字 | 约定 |
|---|---|---|
| 额外监听端口 | `ServerSocket`, `DatagramSocket`, `bind(` | 零出现 |
| 内嵌 Web/WS | `HttpServer`, `Undertow`, `Netty ServerBootstrap`, `WebSocket` | 零出现 |
| 独立雷达/预报服务 | 任何独立进程 | 不存在 |
| RCON 当数据通道 | RCON 仅 MC 自带，不复用 | 不用于 atmos |
| 数据库/Redis 对外端口 | `server.port` 之类 | 仅本地文件持久化（SQLite/H2 文件），无网络端口 |
| 资源包 URL | `setResourcePack(String url)` | 禁用；资产随 mod 打包 |
| 出站 HTTPS | 仅"真实世界对齐"可选开关，默认 `false` | 只出站不监听 |

---

## 强制 mod 开关（config.yml → `network.require-mod`）

| 取值 | 行为 | 未装 mod 玩家 |
|---|---|---|
| `false`（默认） | 允许 vanilla 玩家进服，走 L1 降级层 | 看到原版天气 + BossBar 预警，不掉线 |
| `true` | 仍**不踢人**（硬约束），但反复友好提示并只给最基础 L1 | 能玩，提示"安装 mod 获得完整体验" |

> 无论取值如何：**绝不踢人、绝不崩溃**。握手失败一律自动降级 L1。

---

## 模式 A / 模式 B 差异（`weather.base-mode`）

| | 模式 A（推荐，默认） | 模式 B |
|---|---|---|
| 全局原版雨/雷 | **保留**作基底 | **关闭**（`setStorm(false)` 常驻） |
| vanilla 玩家 | 仍有全局天气 | **看不到任何降水** |
| mod 玩家 | 客户端把渲染**覆盖**为局地气象场 | 局地场即唯一降水来源 |
| 观感一致性 | 同屏需调参对齐（本方案提供对齐预设） | 最统一，但惩罚未装 mod 者 |
| 适用 | 混合玩家群 | 全员装 mod 的硬核服 |

关键：接管天气后关闭 `doWeatherCycle`，用 `setStorm/setThundering/setWeatherDuration/setClearWeatherDuration` 作基底，监听 `WeatherChangeEvent`/`ThunderChangeEvent` 的 `Cause` 区分 `PLUGIN / COMMAND / 原版循环`，避免与其他天气插件互抢。模式 A/B **必须显式配置**，无默认隐式行为。

---

## 代理注意事项

- **Velocity**：启动参数加 `-Dvelocity.max-plugin-message-payload-size=1048576`，否则大包被截。
- **BungeeCord**：**无法**解除单包上限。本方案保证在 **30 KiB（32767 B）单包**下工作——所有 field/forecast 均分片 + seq + ack，未注册通道按 32767 B 上限设计。
- 握手放在 **PLAY 阶段**：1.21 配置阶段停留超过约 15 秒会被客户端断开；配置阶段协商仅作后期增强且只传几十字节。

---

## 降级行为（L0 / L1 / L2）

| 级别 | 触发 | 表现 |
|---|---|---|
| **L2 完整** | mod 握手成功 | 局地场、粒子、定向雷声、雷达、预报 GUI |
| **L1 基础** | 无 mod / 握手失败 / 协议不符 / 端口超限 | 原版天气 + BossBar/ActionBar 预警 + 原版粒子音效；服务端判定照常 |
| **L0 最小** | payload 异常 / 限流触发 / MSPT 超预算 | 只保预警与关键基底，停发 field 细节 |

降级原则：**异常只降级，绝不踢人、绝不崩溃**。所有客户端上报（hello 之外）视为不可信数据，不参与任何判定。

---

## 验收标准（代码完成后逐项自测并在此打勾）

> 以下为验收清单，当前均为 `[ ]`，实现完成后由维护者实测勾选并附日志/截图。

1. [ ] `ss -tlnp` 只有 25565 一个监听端口，无任何临时/额外监听。
2. [ ] 未装 mod 的 vanilla 客户端可正常进服、看到 L1 天气与 BossBar 预警，不掉线。
3. [ ] 装 mod 与未装 mod 玩家同屏时观感一致（模式 A）。
4. [ ] 断链/乱序/超长 payload 只降级不踢人。
5. [ ] 7 日预报会订正、有置信度、能出命中率报表。
6. [ ] 极端天气有提前预警 + 庇护判定，室内完全安全。
7. [ ] 满载下 MSPT 未明显劣化，带宽有上限（全局出站字节预算生效）。

---

## 文档索引

| 文档 | 内容 |
|---|---|
| [`docs/01-structure.md`](docs/01-structure.md) | 目录结构 + 每个模块职责 |
| [`docs/02-protocol.md`](docs/02-protocol.md) | 协议契约表（帧格式 / 通道 / 字节布局 / 分片 / 限流 / 预算） |
| [`docs/03-phases.md`](docs/03-phases.md) | 分阶段计划（Phase 0–8 + 每阶段验收） |

## 许可

GPL-3.0（与组织内多数项目一致）。© 2026 ChaosSurvivalProject · 星穹旅驿。
