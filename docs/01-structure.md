# 01 · 目录结构与模块职责

## 顶层：两个独立 Gradle 项目 + 一个共享协议模块

```
atmos/
├── settings.gradle.kts          # include("protocol", "server", "client")
├── build.gradle.kts             # 根：公共 java toolchain = 21
├── protocol/                    # 共享帧编解码（纯 Java，无 Bukkit / 无 MC 依赖）
├── server/                      # Purpur 插件（Bukkit/Paper API 唯一使用者）
├── client/                      # Fabric 纯客户端 mod
├── docs/                        # 设计三件套
└── README.md
```

`protocol` 不依赖任何框架：服务端只有裸 `byte[]`，Fabric 端把 `byte[]` 包进 `PacketByteBuf`。**两端共用同一个 `FrameCodec`**，从根上杜绝隐式序列化差异。

---

## protocol/（共享，两端复用）

```
protocol/src/main/java/dev/atmos/protocol/
├── AtmosProtocol.java       # 常量：MAGIC、PROTO_VERSION、MAX_PAYLOAD、通道名
├── Channel.java             # 通道枚举（atmos:hello / field / ...）
├── Frame.java               # 帧 POJO
├── FrameCodec.java          # 手工 encode/decode：magic|proto|type|seq|tick|len(VarInt)|body
├── VarInt.java              # MC VarInt 读写（与 Minecraft 协议一致）
├── Fixed.java               # ×100 定点 short <-> float（温度/气压）
├── Fragmenter.java          # 分片 + 重组（seq、total、crc、missing 位图）
├── Lz4.java                 # LZ4 压缩封装（两端同版本）
├── body/
│   ├── Hello.java  Welcome.java  Clock.java
│   ├── Field.java  Cells.java    Forecast.java
│   ├── Warn.java   Effect.java
│   └── Ack.java    Probe.java
└── util/
    └── Bytes.java           # big-endian 读取器/写入器边界校验
protocol/src/test/java/dev/atmos/protocol/
└── GoldenVectorTest.java    # 固定字节序列 → 对象 → 字节，断言两端一致；覆盖边界值
```

> **字节级一致性靠测试保证**：`GoldenVectorTest` 里写入若干硬编码 hex 串，服务端与 Fabric 端都必须能解出相同对象并再编码回同一 hex。

---

## server/（Bukkit/Paper 插件，唯一权威）

```
server/src/main/java/dev/atmos/server/
├── AtmosPlugin.java         # onEnable/onDisable：装配、调度、注册通道与命令
├── core/
│   ├── TickDriver.java      # 主时钟：异步演化 → 回主线程应用
│   ├── Budget.java          # 全局出站字节预算 + MSPT 采样 + 三级降级状态机
│   └── Scheduler.java       # 线程池/虚拟线程封装（仅纯计算与 IO）
├── climate/
│   ├── ClimateField.java    # 256²/512² 网格，七要素状态
│   ├── Noise.java           # 分层 simplex + 阻尼 + 阵风
│   ├── Front.java           # 风驱锋面传播，格间 20s 双线性过渡
│   ├── Cyclone.java         # 低压吸引子：识别→收敛→自演化
│   └── BiomeSampler.java    # 一次性克隆群系采样快照（异步只读）
├── forecast/
│   ├── ForecastEngine.java  # 7日分段推演 + 临近订正 + 置信度
│   └── Scoreboard.java      # 预报 vs 实况命中率统计
├── registry/
│   ├── WeatherDef.java      # 天气定义（触发/生命周期/速度/半径/预警提前量/效果）
│   └── WeatherRegistry.java # 从 weathers.yml 加载，配置驱动
├── effects/
│   ├── EffectEngine.java    # 伤害/作物/破坏/推力/雷击/庇护判定（全服务端）
│   └── Shelter.java         # 头顶遮挡 + 室内 = 安全区
├── netbridge/
│   ├── ChannelBridge.java   # registerOutgoing/IncomingChannel，收发 byte[]
│   ├── Session.java         # 每玩家状态：握手、seq、ack、限流计数
│   ├── RateLimiter.java     # 每玩家每秒限流 + 魔数/长度/版本白名单校验
│   └── FieldSender.java     # 视距+一格缓冲切片、delta 判定、1–2s 批发送
├── persist/
│   ├── SnapshotStore.java   # 年循环/干旱天数/积雪/土壤湿度 落盘续算
│   └── Codec.java           # 本地二进制快照（无网络端口）
├── api/
│   ├── AtmosAPI.java        # current(cell) / forecast(day)
│   └── event/               # ForecastPublishEvent / ExtremeWarningEvent /
│                            # WeatherCellSpawnEvent / ClimateTickEvent
├── cmd/
│   └── WxCommand.java       # /wx now|forecast|warn|radar|set|reload|verify|debug
├── hook/
│   ├── PapiHook.java  WorldGuardHook.java  DiscordHook.java  # 后两者默认关闭
│   └── Webhook.java         # 可选，默认关闭，仅出站 HTTPS，不监听
├── integration/
│   └── VanillaWeather.java  # setStorm 等基底 + Cause 区分 + doWeatherCycle 接管
└── resources/
    ├── plugin.yml           # api-version: 1.21.11
    ├── config.yml  biomes.yml  weathers.yml  forecast.yml  messages.yml
```

---

## client/（Fabric 1.21.11 纯客户端）

```
client/src/main/java/dev/atmos/client/
├── AtmosClient.java         # 入口：注册通道、Mixin 配置、资源加载
├── net/
│   ├── ClientBridge.java    # Identifier.of("atmos", "field") 等；byte[] <-> PacketByteBuf
│   ├── Handshake.java       # PLAY 阶段发 hello，收 welcome 判定 L2/L1
│   └── Reassembler.java     # 分片重组、丢片发 probe/ack
├── model/
│   ├── LocalField.java      # 客户端场缓存（只渲染用，不判定）
│   └── Interpolator.java    # 两帧快照间预测插值（借 atmos:clock 对齐）
├── render/
│   ├── mixin/               # WorldRenderer 降水、ClientWorld rainGradient/thunderGradient、
│                            #   云层/雾/天色/光照贴图定向压暗
│   ├── ParticleKit.java     # 龙卷风漏斗/冰雹/沙尘墙/地雾/斜雨幕/风线
│   └── ThunderAudio.java    # 按气旋方位 3D 定向雷声
├── hud/
│   ├── ForecastBook.java    # 预报书本 GUI（分页）
│   ├── Radar.java           # 雷达小地图
│   └── Overlay.java         # 预警横幅 + 温度风力条
└── resources/
    ├── fabric.mod.json
    ├── atmos.mixins.json
    └── assets/atmos/        # 粒子/音效/字体/着色器/语言文件，全部随 mod 打包
```

---

## 依赖方向（单向，禁止回流）

```
client ─┐
        ├──> protocol  (纯 Java，不含 MC/Bukkit)
server ─┘

client  ✗ 不依赖 server
server  ✗ 不依赖 client
server  ✗ 不依赖任何客户端 mod 接口
```

- 所有 Bukkit API 调用**只在主线程**执行；异步演化结果通过主线程队列 `apply`。
- Java 21 虚拟线程**只**用于纯计算与文件 IO，绝不碰 Bukkit 服务端对象。
