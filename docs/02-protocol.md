# 02 · 双端协议契约表

> 命名空间统一 `atmos:*`。Bukkit 写 `"atmos:field"`，Fabric 写 `Identifier.of("atmos", "field")`。
> 帧格式两端**手工对齐**，不得依赖各自框架隐式序列化。

---

## 2.1 帧格式（公共头）

```
magic(4B) | proto(1B) | type(1B) | seq(2B) | tick(8B) | len(VarInt) | body
```

| 字段 | 长度 | 编码 | 说明 |
|---|---|---|---|
| `magic` | 4 B | ASCII 大端固定 `A T M S` (`0x41 0x54 0x4D 0x53`) | 魔数校验，不符直接丢弃并降级 |
| `proto` | 1 B | unsigned byte | 协议版本，白名单校验（当前 `1`） |
| `type` | 1 B | unsigned byte | 消息类型，见 2.2 |
| `seq` | 2 B | **big-endian** unsigned short | 单调递增序号，用于乱序/丢片检测 |
| `tick` | 8 B | **big-endian** signed long | 服务端游戏刻，插值对齐基准 |
| `len` | VarInt | MC VarInt（1–5 B） | `body` 字节数，校验与实际一致 |
| `body` | `len` B | 各类型自定义，见 2.3 | 温度/气压 ×100 定点存 short |

- 整数一律 **big-endian**；`len` 用 MC VarInt。
- 定点：`temp_short = round(temp_c × 100)`；`pressure_short = round(hPa × 100)`。解码反向 `/100f`。
- 上限：**单包 ≤ 30 KiB（32767 B）**（未注册通道上限）。超出必须分片。

---

## 2.2 通道 / 消息类型表

| 方向 | 通道 | type | 内容 | 时机 |
|---|---|---|---|---|
| C→S | `atmos:hello` | `0x01` | mod 版本、协议版本、能力位、渲染档位 | 进服 PLAY 阶段一次 |
| S→C | `atmos:welcome` | `0x02` | 网格尺寸、同步频率、模式 A/B、是否强制 mod | 收到 hello 后 |
| S→C | `atmos:clock` | `0x03` | 游戏刻 + 单调时钟，供插值对齐 | 定期（如每 20t） |
| S→C | `atmos:field` | `0x04` | 玩家视距内网格切片，**增量 delta** | 每 1–2 秒一批 |
| S→C | `atmos:cells` | `0x05` | 气旋/锋面单体：中心、半径、强度、移动矢量、生命周期 | 随 field 批或变化时 |
| S→C | `atmos:forecast` | `0x06` | 7 日分段预报 + 置信度 + 预警等级 | 每日滚动 / 订正时 |
| S→C | `atmos:warn` / `atmos:effect` | `0x07`/`0x08` | 预警、方位雷声、一次性特效 | **事件驱动立即发** |
| C→S | `atmos:ack` / `atmos:probe` | `0x09`/`0x0A` | 分片确认、丢包补片；可选本地观测（仅统计，不可信） | 收到 field 后 / 丢片时 |

> 握手闸门：以 `atmos:hello` 到达为**发送闸门**；未握手/握手失败 → 自动降级 L1，友好提示，**绝不踢人、绝不崩溃**。

---

## 2.3 body 字节布局（逐类型）

> 记法：`u8/u16/u32/u64` 无符号 big-endian；`i16/i32/i64` 有符号 big-endian；`str` = VarInt 长度 + UTF-8；`f32` = IEEE754 big-endian。

### `0x01 hello`（C→S）
| 字段 | 类型 | 含义 |
|---|---|---|
| modVersion | str | 如 `0.1.0` |
| protocolVersion | u8 | 客户端协议版本 |
| capabilityBits | u32 | 位掩码：粒子/雷声/HUD/雷达/着色器…… |
| renderTier | u8 | 0 低 / 1 中 / 2 高（影响下发切片密度） |

### `0x02 welcome`（S→C）
| 字段 | 类型 | 含义 |
|---|---|---|
| gridSize | u16 | 256 或 512 |
| syncPeriodTicks | u16 | field 批发送间隔（tick） |
| baseMode | u8 | 0=A / 1=B |
| forceMod | u8 | 0/1 |
| acceptedProto | u8 | 服务端采纳的协议版本 |

### `0x03 clock`（S→C）
| 字段 | 类型 | 含义 |
|---|---|---|
| gameTick | i64 | 服务端当前 tick |
| monotonicMs | i64 | 单调毫秒（插值用，不依赖墙钟） |

### `0x04 field`（S→C，核心，分片）
| 字段 | 类型 | 含义 |
|---|---|---|
| fragIndex | u16 | 当前片序号 |
| fragTotal | u16 | 总片数 |
| originX / originZ | i16 / i16 | 切片左上角网格坐标 |
| width / height | u8 / u8 | 本片覆盖格数 |
| deltaMaskLen | u16 | 变化位图长度（bit=1 表示该格有更新） |
| payload | bytes | LZ4(量化后的格数据序列) |

每格量化（仅 delta 位图为 1 的格）：
`temp(i16 ×100) · humidity(u8) · pressure(i16 ×100) · trend(i16) · dew(i16 ×100) · windU(i8) · windV(i8) · cloud(u8) · vis(u16)`
→ 单位格 16 B；LZ4 后通常远小于原始。

### `0x05 cells`（S→C）
| 字段 | 类型 | 含义 |
|---|---|---|
| count | u8 | 单体个数 |
| per cell | … | id(i32) · kind(u8: cyclone/front) · centerX(f32) · centerZ(f32) · radius(f32) · intensity(f32) · velX(f32) · velZ(f32) · phase(u8: 生成/增强/成熟/消散) |

### `0x06 forecast`（S→C，分片）
| 字段 | 类型 | 含义 |
|---|---|---|
| days | u8 | 天数（3/7/14） |
| segmentsPerDay | u8 | 分段数（凌晨/上午/午后/夜间…） |
| per seg | … | kind(u8) · tempLo(i16×100) · tempHi(i16×100) · precipProb(u8) · windLevel(u8) · vis(u16) · confidence(u8) · warnLevel(u8) |

### `0x07 warn`（S→C，即时）
| 字段 | 类型 | 含义 |
|---|---|---|
| warnLevel | u8 | 蓝/黄/橙/红 = 0/1/2/3 |
| cellId | i32 | 关联单体 |
| leadTicks | u32 | 提前量（tick） |
| messageKey | str | messages.yml 键 |

### `0x08 effect`（S→C，即时）
| 字段 | 类型 | 含义 |
|---|---|---|
| effectId | u8 | 定向雷声/一次性特效枚举 |
| worldX/Y/Z | f32 | 世界坐标（用于 3D 定向） |
| param | u32 | 附加参数 |

### `0x09 ack` / `0x0A probe`（C→S）
| 消息 | 字段 |
|---|---|
| ack | sessionSeq(u16) · receivedFragBitmap(bytes) |
| probe | 本地观测样本（仅用于命中率统计；**服务端不据此判定任何天气/伤害**） |

---

## 2.4 分片 / 重组 / 补片

1. 发送端 `Fragmenter`：body 压缩后若 > 上限，切成 `fragTotal` 片，每片带 `seq` 与 `fragIndex`。
2. 接收端 `Reassembler`：按 `seq + fragTotal` 聚合，收齐校验 crc 后交解码；缺片发 `ack`（含 receivedFragBitmap）。
3. 发送端只**补缺失片**（选择性重传），不重发整批。
4. 乱序：按 seq 排序；重复 seq 丢弃。
5. 任何重组失败 → 该批丢弃 + L1 降级，不踢人。

---

## 2.5 校验与安全

| 校验 | 规则 | 失败处理 |
|---|---|---|
| 魔数 | 必须 `ATMS` | 丢弃 |
| 协议版本 | `proto` ∈ 白名单（当前 {1}） | 握手失败 → L1 |
| 长度 | `len` 与实际 body 一致，且 ≤ 32767 | 丢弃 + 限流计数 |
| 每玩家限流 | 每秒 C→S 包数上限（配置，默认如 20/s） | 超限丢弃 + 降级；不踢人 |
| 来源不可信 | hello 之外所有 C→S 数据 | 只用于渲染/统计，不进判定 |

---

## 2.6 出站字节预算与 MSPT 自适应

全局出站字节预算（如 64 KiB/s 可配），MSPT 升高时按序降级：

```
MSPT > t1 → 降网格分辨率（512→256→128）
MSPT > t2 → 降同步频率（1s→2s→4s）
MSPT > t3 → 只保 warn/effect 预警，停发 field
```

优先级队列：`warn/effect`（即时） > `clock` > `forecast` > `cells` > `field`。预算不足时低优先级先让路。
