# MessagePulse 2.0 - AI 时代消息基础设施

> 消息脉动，让消息传递如此简单
> 一次集成，多渠道通达；智能向导，新平台即插即用。

---

## 📖 项目简介

MessagePulse 2.0 是面向 AI 时代的统一消息分发平台，核心价值是**解耦 AI 系统与消息渠道**。

### 核心特性

- 🔌 **插件化架构**：每个渠道独立 Skill，按需激活
- 🔀 **完全解耦**：AI 系统无需关心具体渠道
- 📦 **易于扩展**：新增渠道 = 新增 Skill
- 🤖 **智能向导**：Guide Skill 辅助接入新平台
- 🛡️ **安全可靠**：API Key 认证、租户隔离、消息状态机
- 📊 **可观测性**：完整的监控和链路追踪

---

## 🎯 设计理念

### 传统模式（复杂）
```
OpenClaw → 短信插件（复杂）
         → 邮件插件（复杂）
         → 飞书插件（复杂）
```

### MessagePulse 方案（解耦）
```
OpenClaw → MessagePulse → 短信/邮件/飞书/微信...
```

---

## 📚 文档

- **[完整设计文档](docs/plans/2026-03-02-messagepulse-2.0-design.md)** - 60+ 页完整架构设计
- **[文档导航](docs/README.md)** - 文档中心导航
- **[开发反馈日志](docs/feedback/development-feedback.md)** - 开发过程中的问题和解决方案

---

## 🏗️ 系统架构

```
┌─────────────────────────────────────────────────────────┐
│  AI 系统层                                               │
│  OpenClaw | Cursor | Claude Code | Custom AI Agents    │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│  MessagePulse Core (核心平台)                            │
│  - API 认证与授权                                        │
│  - 消息路由引擎                                          │
│  - 去重引擎（三级去重）                                  │
│  - 撤回引擎                                              │
│  - 一致性引擎                                            │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│  Channel Skills (独立进程，按需部署)                      │
│  短信 Skill | 邮件 Skill | 飞书 Skill | 微信 Skill ... │
└─────────────────────────────────────────────────────────┘
```

---

## 🚀 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| 核心框架 | Spring Boot | 3.x |
| 消息队列 | Apache Kafka | 3.x |
| 数据库 | MySQL | 8.x |
| 缓存 | Redis | 7.x |
| 监控 | Prometheus + Grafana | - |
| 链路追踪 | SkyWalking / Zipkin | - |
| 容器化 | Docker + Compose | - |

---

## 📊 性能指标

| 指标 | 目标值 |
|------|--------|
| 接口层 QPS | 12000+ |
| 接口延迟 P99 | < 20ms |
| 消息重复率 | < 0.01% |
| 消息成功率 | > 99% |
| 内存占用 | ~8GB |

---

## 🛠️ 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Docker & Docker Compose
- MySQL 8.x
- Redis 7.x
- Kafka 3.x

### 本地开发

```bash
# 克隆仓库
git clone https://github.com/gccszs/MessagePluse.git
cd MessagePluse

# 启动基础设施
docker-compose up -d

# 启动 MessagePulse Core
cd core
mvn spring-boot:run

# 启动 Channel Skills（示例）
cd skills/sms-skill
mvn spring-boot:run
```

---

## 📋 开发路线图

- [x] Phase 0: 完整设计文档
- [ ] Phase 1: 核心框架搭建
- [ ] Phase 2: API 认证授权
- [ ] Phase 3: Kafka 集成
- [ ] Phase 4: 去重引擎
- [ ] Phase 5: Channel Skills
- [ ] Phase 6: 监控系统
- [ ] Phase 7: 压力测试
- [ ] Phase 8: 文档完善

---

## 🤝 贡献指南

我们欢迎任何形式的贡献！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

---

## 📝 许可证

本项目采用 Apache License 2.0 许可证 - 详见 [LICENSE](LICENSE) 文件

---

## 📞 联系方式

- GitHub: https://github.com/gccszs/MessagePluse
- Issues: https://github.com/gccszs/MessagePluse/issues

---

## 🌟 致谢

感谢所有为 MessagePulse 做出贡献的开发者！

---

**MessagePulse 2.0** - 让 AI 系统的消息分发更简单 🚀
