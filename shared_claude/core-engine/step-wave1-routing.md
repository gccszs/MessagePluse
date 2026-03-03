# Core Engine - Wave 1: 路由引擎 + 状态机

## 完成时间
2026-03-03

## 创建的文件

### 路由引擎 (engine/routing/)
| 文件 | 说明 |
|------|------|
| `RoutingEngine.java` | 路由接口，`List<String> route(MessageEvent event)` |
| `ExplicitRouter.java` | 显式路由，直接返回 `routingConfig.explicitChannels` |
| `ImplicitRouter.java` | 隐式路由，根据 RecipientInfo 字段推断渠道（email→EMAIL, phone→SMS, userId→IN_APP） |
| `AutoRouter.java` | 自动路由，支持 FAILOVER（首个渠道）/ LOAD_BALANCE（轮询）/ BROADCAST（全部） |
| `DefaultRoutingEngine.java` | @Primary 实现，根据 routingConfig.mode 分发到对应路由器 |

### 状态机 (engine/statemachine/)
| 文件 | 说明 |
|------|------|
| `StateTransitionValidator.java` | 定义合法状态转换表，PENDING→ROUTING, ROUTING→SENDING/FAILED, SENDING→SENT/FAILED, FAILED→SENDING(重试), any→REVOKED |
| `MessageStateMachine.java` | 调用 validator 验证转换，非法转换抛 `MessagePulseException(INVALID_STATE_TRANSITION)` |

### Service (service/)
| 文件 | 说明 |
|------|------|
| `MessageStateService.java` | @Transactional，验证转换→创建 MessageState 记录→更新 Message 状态 |

## 修改的文件
| 文件 | 修改 |
|------|------|
| `enums/ErrorCode.java` | 新增 `INVALID_STATE_TRANSITION(8000, "Invalid state transition")` |

## 编译结果
`mvn compile -Dcheckstyle.skip=true` → BUILD SUCCESS (71 source files)
