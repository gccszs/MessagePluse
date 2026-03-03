# 安全设计

## 安全架构概览

MessagePulse 2.0 的安全设计覆盖以下层面：

```
┌─────────────────────────────────────────────────────────┐
│  1. API Key 认证                                         │
│     - 身份验证                                           │
│     - Key 生命周期管理                                   │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│  2. 权限控制                                             │
│     - Scope 细粒度权限                                   │
│     - 渠道级别权限                                       │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│  3. 租户隔离                                             │
│     - 数据隔离                                           │
│     - 资源隔离                                           │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│  4. 安全检查链                                           │
│     - API Key → 权限 → 租户 → 配额 → 内容安全          │
└─────────────────────────────────────────────────────────┘
```

## API Key 认证

### API Key 实体

```java
@Entity
@Table(name = "api_keys")
public class ApiKey {
    private Long id;
    private String keyId;           // ak_xxxxxxxxxxxxxxxx
    private String keySecret;       // BCrypt 加密存储
    private String tenantId;
    private String name;
    private Set<String> scopes;     // ["sms:send", "email:send"]
    private Set<String> allowedChannels;  // ["sms", "email"]
    private RateLimitConfig rateLimit;
    private Boolean active;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
}
```

### API Key 格式

```
Authorization: Bearer ak_abc123def456.keySecret789
```

- **keyId**: `ak_` 前缀 + 32 位随机字符串
- **keySecret**: 客户端持有明文，服务端存储 BCrypt 哈希

### 认证流程

```
1. 请求到达
   ↓
2. ApiKeyAuthenticationFilter 拦截
   ↓
3. 从 Authorization Header 提取 API Key
   ↓
4. 解析 keyId 和 keySecret
   ↓
5. 查询 API Key（Redis 缓存 + MySQL）
   ↓
6. BCrypt 验证 keySecret
   ↓
7. 检查 API Key 状态：
   - active == true?
   - 未过期?
   ↓
8. 创建 ApiKeyAuthentication 放入 SecurityContext
   ↓
9. 更新 lastUsedAt
```

### 认证过滤器实现

```java
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyRepository apiKeyRepository;
    private final RedisTemplate<String, ApiKey> redisCache;
    private final PasswordEncoder passwordEncoder;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorized(response, "Missing Authorization header");
            return;
        }

        String credentials = authHeader.substring(7);
        String[] parts = credentials.split("\\.", 2);

        if (parts.length != 2) {
            sendUnauthorized(response, "Invalid API Key format");
            return;
        }

        String keyId = parts[0];
        String keySecret = parts[1];

        // 查询 API Key（先查缓存，再查数据库）
        ApiKey apiKey = findApiKey(keyId);

        if (apiKey == null) {
            sendUnauthorized(response, "API Key not found");
            return;
        }

        // 验证 keySecret
        if (!passwordEncoder.matches(keySecret, apiKey.getKeySecret())) {
            sendUnauthorized(response, "Invalid API Key secret");
            return;
        }

        // 检查状态
        if (!apiKey.isActive()) {
            sendUnauthorized(response, "API Key is disabled");
            return;
        }

        if (apiKey.getExpiresAt() != null
            && apiKey.getExpiresAt().isBefore(LocalDateTime.now())) {
            sendUnauthorized(response, "API Key has expired");
            return;
        }

        // 设置认证信息
        ApiKeyAuthentication authentication = new ApiKeyAuthentication(
            apiKey.getKeyId(),
            apiKey.getTenantId(),
            apiKey.getScopes(),
            apiKey.getAllowedChannels()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 更新最后使用时间（异步）
        updateLastUsedAt(apiKey);

        filterChain.doFilter(request, response);
    }

    private ApiKey findApiKey(String keyId) {
        // 1. 查 Redis 缓存
        String cacheKey = "apikey:" + keyId;
        ApiKey cached = redisCache.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 2. 查 MySQL
        ApiKey apiKey = apiKeyRepository.findByKeyId(keyId).orElse(null);
        if (apiKey != null) {
            // 写入缓存，TTL 5 分钟
            redisCache.opsForValue().set(cacheKey, apiKey, Duration.ofMinutes(5));
        }

        return apiKey;
    }
}
```

## 权限控制

### Scope 定义

```java
public class Scopes {
    public static final String MESSAGE_SEND = "message:send";
    public static final String MESSAGE_QUERY = "message:query";
    public static final String MESSAGE_REVOKE = "message:revoke";
    public static final String APIKEY_MANAGE = "apikey:manage";
    public static final String SKILL_MANAGE = "skill:manage";
    public static final String TEMPLATE_MANAGE = "template:manage";
    public static final String BILLING_VIEW = "billing:view";
}
```

### @RequireScope 注解

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireScope {
    String value();
}
```

**使用示例**：

```java
@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    @PostMapping
    @RequireScope("message:send")
    public ResponseEntity<SendMessageResponse> sendMessage(
        @RequestBody SendMessageRequest request
    ) {
        // 只有拥有 "message:send" scope 的 API Key 才能调用
    }

    @GetMapping("/{messageId}/status")
    @RequireScope("message:query")
    public ResponseEntity<MessageStatusResponse> getStatus(
        @PathVariable String messageId
    ) {
        // 只有拥有 "message:query" scope 的 API Key 才能调用
    }
}
```

### 权限检查拦截器

```java
@Aspect
@Component
public class ScopeCheckAspect {

    @Before("@annotation(requireScope)")
    public void checkScope(RequireScope requireScope) {
        ApiKeyAuthentication auth = (ApiKeyAuthentication)
            SecurityContextHolder.getContext().getAuthentication();

        if (auth == null) {
            throw new UnauthorizedException("Not authenticated");
        }

        if (!auth.getScopes().contains(requireScope.value())) {
            throw new ForbiddenException(
                "Insufficient scope: " + requireScope.value()
            );
        }
    }
}
```

### 渠道级别权限

API Key 可以限制只允许使用特定渠道：

```java
// 在消息发送时检查渠道权限
public void checkChannelPermission(
    ApiKeyAuthentication auth,
    List<String> requestedChannels
) {
    Set<String> allowedChannels = auth.getAllowedChannels();

    for (String channel : requestedChannels) {
        if (!allowedChannels.contains(channel)) {
            throw new ForbiddenException(
                "Channel not allowed: " + channel
            );
        }
    }
}
```

## 租户隔离

### 数据隔离

所有数据表都包含 `tenant_id` 字段，查询时自动添加租户条件：

```java
@Entity
@Table(name = "messages")
public class Message {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @PrePersist
    protected void onCreate() {
        // 自动设置租户 ID
        ApiKeyAuthentication auth = (ApiKeyAuthentication)
            SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            this.tenantId = auth.getTenantId();
        }
    }
}
```

### 查询隔离

```java
@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

    // 所有查询都需要带 tenantId 条件
    List<Message> findByTenantIdAndStatus(String tenantId, MessageStatus status);

    Optional<Message> findByMessageIdAndTenantId(String messageId, String tenantId);

    Page<Message> findByTenantIdAndCreatedAtBetween(
        String tenantId,
        LocalDateTime start,
        LocalDateTime end,
        Pageable pageable
    );
}
```

### 全局租户过滤器

```java
@Component
public class TenantFilter implements Filter {

    @Override
    public void doFilter(
        ServletRequest request,
        ServletResponse response,
        FilterChain chain
    ) throws IOException, ServletException {

        ApiKeyAuthentication auth = (ApiKeyAuthentication)
            SecurityContextHolder.getContext().getAuthentication();

        if (auth != null) {
            // 设置当前线程的租户上下文
            TenantContext.setCurrentTenant(auth.getTenantId());
        }

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}

public class TenantContext {
    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();

    public static void setCurrentTenant(String tenantId) {
        currentTenant.set(tenantId);
    }

    public static String getCurrentTenant() {
        return currentTenant.get();
    }

    public static void clear() {
        currentTenant.remove();
    }
}
```

## 安全检查链

消息发送前的完整安全检查流程：

```
1. API Key 验证
   ↓ 验证通过
2. 租户渠道权限检查
   - tenant_123 是否允许使用 sms?
   - tenant_123 是否允许使用 feishu?
   ↓ 权限通过
3. Recipient 授权检查
   - 该 AI 系统是否有权限向该用户发送?
   - 用户是否订阅了该类型通知?
   ↓ 授权通过
4. 配额检查
   - 租户短信配额是否充足?
   - 是否触发限流?
   ↓ 通过
5. 内容安全检查
   - 短信敏感词过滤
   - 反垃圾邮件检查
   ↓ 通过
6. 发送消息
```

### 代码实现

```java
@Service
public class SecurityCheckChain {

    private final ApiKeyService apiKeyService;
    private final RateLimiterService rateLimiterService;

    public void check(SendMessageRequest request, ApiKeyAuthentication auth) {
        // 1. 渠道权限检查
        checkChannelPermission(auth, request.getRouting().getChannels());

        // 2. 配额检查
        if (!rateLimiterService.allowRequest(
            auth.getKeyId(),
            auth.getTenantId(),
            request.getRouting().getChannels()
        )) {
            throw new RateLimitExceededException("Rate limit exceeded");
        }

        // 3. 内容安全检查（可选）
        // contentSafetyService.check(request.getContent());
    }
}
```

## API Key 生命周期管理

### 创建

```java
@Service
public class ApiKeyService {

    public CreateApiKeyResponse createApiKey(CreateApiKeyRequest request) {
        // 生成 keyId 和 keySecret
        String keyId = "ak_" + generateRandomString(32);
        String rawKeySecret = generateRandomString(48);

        // BCrypt 加密 keySecret
        String encodedSecret = passwordEncoder.encode(rawKeySecret);

        // 保存到数据库
        ApiKey apiKey = new ApiKey();
        apiKey.setKeyId(keyId);
        apiKey.setKeySecret(encodedSecret);
        apiKey.setTenantId(request.getTenantId());
        apiKey.setScopes(request.getScopes());
        apiKey.setAllowedChannels(request.getAllowedChannels());
        apiKey.setActive(true);

        apiKeyRepository.save(apiKey);

        // 返回时包含明文 keySecret（仅此一次）
        return new CreateApiKeyResponse(keyId, rawKeySecret);
    }
}
```

### 撤销

```java
public void revokeApiKey(String keyId) {
    ApiKey apiKey = apiKeyRepository.findByKeyId(keyId)
        .orElseThrow(() -> new NotFoundException("API Key not found"));

    apiKey.setActive(false);
    apiKeyRepository.save(apiKey);

    // 清除缓存
    redisCache.delete("apikey:" + keyId);
}
```

### 过期管理

```java
@Scheduled(fixedRate = 3600000) // 每小时检查
public void checkExpiredApiKeys() {
    List<ApiKey> expiredKeys = apiKeyRepository.findByActiveAndExpiresAtBefore(
        true, LocalDateTime.now()
    );

    for (ApiKey key : expiredKeys) {
        key.setActive(false);
        apiKeyRepository.save(key);
        redisCache.delete("apikey:" + key.getKeyId());
        log.info("API Key expired: {}", key.getKeyId());
    }
}
```

## 安全最佳实践

### 1. Key Secret 安全

- 使用 BCrypt 加密存储，不存明文
- 仅在创建时返回一次
- 客户端需安全存储

### 2. 传输安全

- 生产环境强制使用 HTTPS
- API Key 通过 Header 传输，避免 URL 泄露

### 3. 日志安全

- 日志中不记录完整的 API Key
- 只记录 keyId 的前 8 位

### 4. 限流防护

- API Key 级别限流
- 租户级别限流
- 渠道级别限流
- 防止恶意调用

### 5. 审计日志

- 记录所有 API Key 操作
- 记录认证失败事件
- 记录权限检查失败事件

## 与其他知识文档的关系

- **系统架构** → `02-architecture.md`：安全在接入层的位置
- **API 设计** → `05-api-design.md`：API 认证方式
- **数据库设计** → `04-database-design.md`：api_keys 表结构
- **监控设计** → `10-monitoring.md`：安全相关的监控指标
