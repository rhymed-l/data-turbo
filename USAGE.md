# Data Turbo 使用指南

## 🚀 三步开始使用

### 第一步：添加依赖

在你的 Spring Boot 项目 `pom.xml` 中添加：

```xml

<dependency>
    <groupId>cn.rhymed</groupId>
    <artifactId>data-turbo</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 第二步：配置默认参数（可选）

在 `application.yml` 中配置默认参数（不配置则使用内置默认值）：

```yaml
data-turbo:
  batch-delete:
    primary-id: id              # 默认主键字段（不配置则为 null，会自动推断）
    fetch-size: 5000            # 默认每批次查询大小，默认 5000
    batch-size: 50000           # 默认每批次提交大小，默认 50000
    max-thread-count: 3         # 默认最大线程数，默认 3
```

**如果不配置**，将使用以下内置默认值：

- `primaryId`: `null`（自动推断为 "id" 或 "表别名.id"）
- `fetchSize`: `5000`
- `batchSize`: `50000`
- `maxThreadCount`: `3`

启动 Spring Boot 应用，查看日志：

```
BatchDeleteInterceptor 已自动注册到 SqlSessionFactory: DefaultSqlSessionFactory
Data Turbo 默认配置: primaryId=null, fetchSize=5000, batchSize=50000, maxThreadCount=3
```

看到这些日志说明拦截器已自动注册成功，并显示了当前使用的默认配置。

### 第三步：使用批量删除

有两种使用方式：

#### 方式一：使用 BatchDeleteHelper（最简单，使用默认配置）

```java
import cn.rhymed.data.turbo.BatchDeleteHelper;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    /**
     * 批量删除过期用户（使用默认配置）
     */
    public int batchDeleteExpiredUsers() {
        // 使用 BatchDeleteHelper 自动应用配置文件中的默认配置
        BatchDeleteHelper.execute(() -> {
            userMapper.deleteByStatus("expired");
        });

        // 或者传入自定义配置
        BatchDeleteConfig customConfig = BatchDeleteConfig.builder()
                .primaryId("user_id")
                .fetchSize(2000)
                .batchSize(10000)
                .maxThreadCount(8)
                .build();
        BatchDeleteHelper.execute(customConfig, () -> {
            userMapper.deleteByStatus("expired");
        });
    }
}
```

#### 方式二：手动设置上下文（灵活控制）

```java
import cn.rhymed.data.turbo.config.BatchDeleteConfig;
import cn.rhymed.data.turbo.context.BatchDeleteContext;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    /**
     * 批量删除过期用户（显式指定配置）
     */
    public int batchDeleteExpiredUsers() {
        // 1. 显式配置批量删除参数（覆盖默认值）
        BatchDeleteConfig config = BatchDeleteConfig.builder()
                .primaryId("id")           // 主键字段名
                .fetchSize(2000)           // 每次删除2000条（覆盖默认的1000）
                .batchSize(10000)          // 每10000条提交一次事务（覆盖默认的5000）
                .maxThreadCount(8)         // 使用8个线程（覆盖默认的4）
                .build();

        // 2. 设置到上下文
        BatchDeleteContext.setConfig(config);

        try {
            // 3. 执行删除（自动触发批量删除）
            return userMapper.deleteByStatus("expired");
        } finally {
            // 4. 清理上下文
            BatchDeleteContext.clearConfig();
        }
    }
}
```

**推荐做法**：

- 一般场景使用**方式一**（默认配置），在 `application.yml` 中统一管理
- 特殊场景使用**方式二**（显式配置），针对性优化

## 📝 完整示例

### Mapper 定义

```java
public interface UserMapper {
    /**
     * 根据状态删除用户
     * 注意：这是普通的 DELETE 语句，不需要特殊处理
     */
    @Delete("DELETE FROM user WHERE status = #{status}")
    int deleteByStatus(String status);

    /**
     * 根据条件删除
     */
    int deleteByCondition(@Param("condition") UserCondition condition);
}
```

```xml
<!-- UserMapper.xml -->
<mapper namespace="com.example.mapper.UserMapper">
    <delete id="deleteByCondition">
        DELETE FROM user
        WHERE 1=1
        <if test="condition.status != null">
            AND status = #{condition.status}
        </if>
        <if test="condition.createTime != null">
            AND create_time &lt; #{condition.createTime}
        </if>
    </delete>
</mapper>
```

### Service 实现

```java

@Service
@Slf4j
public class UserService {

    @Autowired
    private UserMapper userMapper;

    /**
     * 删除指定状态的用户
     */
    public int batchDeleteByStatus(String status) {
        BatchDeleteConfig config = BatchDeleteConfig.builder()
                .primaryId("id")
                .fetchSize(1000)
                .batchSize(5000)
                .maxThreadCount(4)
                .build();

        BatchDeleteContext.setConfig(config);

        try {
            int count = userMapper.deleteByStatus(status);
            log.info("成功删除 {} 个状态为 {} 的用户", count, status);
            return count;
        } catch (Exception e) {
            log.error("批量删除失败", e);
            throw e;
        } finally {
            BatchDeleteContext.clearConfig();
        }
    }

    /**
     * 删除指定时间之前的用户
     */
    public int batchDeleteBeforeDate(Date beforeDate) {
        BatchDeleteConfig config = BatchDeleteConfig.builder()
                .primaryId("id")
                .fetchSize(1500)
                .batchSize(10000)
                .maxThreadCount(6)
                .build();

        BatchDeleteContext.setConfig(config);

        try {
            UserCondition condition = new UserCondition();
            condition.setCreateTime(beforeDate);
            return userMapper.deleteByCondition(condition);
        } finally {
            BatchDeleteContext.clearConfig();
        }
    }

    /**
     * 删除所有测试数据
     */
    public int batchDeleteTestData() {
        BatchDeleteConfig config = BatchDeleteConfig.builder()
                .primaryId("id")
                .fetchSize(2000)
                .batchSize(10000)
                .maxThreadCount(8)
                .build();

        BatchDeleteContext.setConfig(config);

        try {
            return userMapper.deleteByStatus("test");
        } finally {
            BatchDeleteContext.clearConfig();
        }
    }
}
```

### Controller 调用

```java

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 批量删除过期用户
     */
    @DeleteMapping("/expired")
    public Result deleteExpiredUsers() {
        int count = userService.batchDeleteByStatus("expired");
        return Result.success("成功删除 " + count + " 个过期用户");
    }

    /**
     * 批量删除指定日期之前的用户
     */
    @DeleteMapping("/before/{date}")
    public Result deleteBeforeDate(@PathVariable String date) {
        Date beforeDate = DateUtils.parseDate(date);
        int count = userService.batchDeleteBeforeDate(beforeDate);
        return Result.success("成功删除 " + count + " 个用户");
    }
}
```

## 🎯 不同场景的配置建议

### 场景一：清理历史数据（100万条）

```java
BatchDeleteConfig config = BatchDeleteConfig.builder()
        .primaryId("id")
        .fetchSize(1500)        // 每次查1500条
        .batchSize(10000)       // 每10000条提交一次
        .maxThreadCount(6)      // 6个线程并发
        .build();
```

**预计耗时**：约 40-60 秒（取决于硬件和网络）

### 场景二：删除测试数据（10万条）

```java
BatchDeleteConfig config = BatchDeleteConfig.builder()
        .primaryId("id")
        .fetchSize(1000)        // 每次查1000条
        .batchSize(5000)        // 每5000条提交一次
        .maxThreadCount(4)      // 4个线程
        .build();
```

**预计耗时**：约 5-8 秒

### 场景三：大批量清理（1000万条）

```java
BatchDeleteConfig config = BatchDeleteConfig.builder()
        .primaryId("id")
        .fetchSize(2000)        // 每次查2000条
        .batchSize(20000)       // 每20000条提交一次
        .maxThreadCount(10)     // 10个线程（需要足够的连接池）
        .build();
```

**注意**：确保数据库连接池大小 >= maxThreadCount

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # 至少是 maxThreadCount 的 2 倍
```

**预计耗时**：约 8-12 分钟

## ⚠️ 重要提示

### 1. 不要在 @Transactional 中使用

```java
// ❌ 错误示例
@Transactional
public void deleteUsers() {
    BatchDeleteContext.setConfig(config);
    userMapper.deleteByStatus("expired");
}

// ✅ 正确示例
public void deleteUsers() {
    BatchDeleteContext.setConfig(config);
    userMapper.deleteByStatus("expired");
}
```

### 2. 确保数据库支持窗口函数

```sql
-- 测试数据库是否支持窗口函数
SELECT id, ROW_NUMBER() OVER (ORDER BY id) as rn
FROM user LIMIT 1;
```

- ✅ MySQL 8.0+
- ✅ PostgreSQL 9.0+
- ✅ Oracle 10g+
- ✅ SQL Server 2012+
- ❌ MySQL 5.7 及以下

### 3. 主键字段配置

```java
// 单表
.primaryId("id")

// 带表别名
.

primaryId("u.id")

// JOIN 查询（使用主表的主键）
.

primaryId("main_table.id")
```

### 4. 监控删除进度

查看应用日志，拦截器会详细记录整个执行过程：

```
2025-12-10 10:30:10.000  INFO --- ========================================
2025-12-10 10:30:10.001  INFO --- 批量删除拦截器启动
2025-12-10 10:30:10.002  INFO --- MappedStatement ID: com.example.mapper.UserMapper.deleteByStatus
2025-12-10 10:30:10.003  INFO --- 配置参数: primaryId=id, fetchSize=1000, batchSize=5000, maxThreadCount=4
2025-12-10 10:30:10.004  INFO --- 原始 SQL: DELETE FROM user WHERE status = ?
2025-12-10 10:30:10.005  INFO --- 开始查询分页信息...
2025-12-10 10:30:10.128  INFO --- 分页信息查询完成，共 50 页，耗时 123 ms
2025-12-10 10:30:10.129  INFO --- 数据量较大，启用多线程批量删除模式
2025-12-10 10:30:10.130  INFO --- 创建线程池，线程数=4, 总分页数=50, 每个线程处理约13页
2025-12-10 10:30:10.131  INFO --- 分配任务到线程 #1: 处理第 1 到第 13 页（共 13 页）
2025-12-10 10:30:10.132  INFO --- 分配任务到线程 #2: 处理第 14 到第 26 页（共 13 页）
2025-12-10 10:30:10.133  INFO --- 分配任务到线程 #3: 处理第 27 到第 39 页（共 13 页）
2025-12-10 10:30:10.134  INFO --- 分配任务到线程 #4: 处理第 40 到第 50 页（共 11 页）
2025-12-10 10:30:10.135  INFO --- 所有线程已启动，等待执行完成...
2025-12-10 10:30:10.136  INFO --- [pool-1-thread-1] 线程 #1 启动，开始处理 13 个分页
2025-12-10 10:30:10.137  INFO --- [pool-1-thread-2] 线程 #2 启动，开始处理 13 个分页
2025-12-10 10:30:10.138  INFO --- [pool-1-thread-3] 线程 #3 启动，开始处理 13 个分页
2025-12-10 10:30:10.139  INFO --- [pool-1-thread-4] 线程 #4 启动，开始处理 11 个分页
2025-12-10 10:30:11.234  INFO --- [pool-1-thread-1] 第 1 次事务提交，实际删除 5000 条，累计删除 5000 条
2025-12-10 10:30:11.345  INFO --- [pool-1-thread-2] 第 1 次事务提交，实际删除 5000 条，累计删除 5000 条
2025-12-10 10:30:11.456  INFO --- [pool-1-thread-3] 第 1 次事务提交，实际删除 5000 条，累计删除 5000 条
2025-12-10 10:30:11.567  INFO --- [pool-1-thread-4] 第 1 次事务提交，实际删除 5000 条，累计删除 5000 条
2025-12-10 10:30:12.678  INFO --- [pool-1-thread-1] 第 2 次事务提交，实际删除 5000 条，累计删除 10000 条
2025-12-10 10:30:12.789  INFO --- [pool-1-thread-2] 第 2 次事务提交，实际删除 5000 条，累计删除 10000 条
...
2025-12-10 10:30:15.370  INFO --- [pool-1-thread-1] 线程 #1 完成！处理了 13 页，共删除 13000 条记录，共提交 3 次事务，耗时 1234 ms
2025-12-10 10:30:15.393  INFO --- [pool-1-thread-2] 线程 #2 完成！处理了 13 页，共删除 13000 条记录，共提交 3 次事务，耗时 1256 ms
2025-12-10 10:30:15.384  INFO --- [pool-1-thread-3] 线程 #3 完成！处理了 13 页，共删除 13000 条记录，共提交 3 次事务，耗时 1245 ms
2025-12-10 10:30:15.337  INFO --- [pool-1-thread-4] 线程 #4 完成！处理了 11 页，共删除 11000 条记录，共提交 3 次事务，耗时 1198 ms
2025-12-10 10:30:15.400  INFO --- 所有线程执行完成，开始汇总结果...
2025-12-10 10:30:15.401  INFO --- 线程 #1 删除数量: 13000
2025-12-10 10:30:15.402  INFO --- 线程 #2 删除数量: 13000
2025-12-10 10:30:15.403  INFO --- 线程 #3 删除数量: 13000
2025-12-10 10:30:15.404  INFO --- 线程 #4 删除数量: 11000
2025-12-10 10:30:15.405  INFO --- ----------------------------------------
2025-12-10 10:30:15.406  INFO --- 批量删除统计: 使用 4 个线程，处理 50 个分页，总共删除 50000 条记录
2025-12-10 10:30:15.407  INFO --- ----------------------------------------
2025-12-10 10:30:15.408  INFO --- 批量删除全部完成！总删除 50000 条记录，总耗时 1456 ms (约 1.456 秒)
2025-12-10 10:30:15.409  INFO --- ========================================
```

**日志解读：**

- 可以清楚看到每个线程的启动、进度和完成情况
- 可以实时监控删除进度和事务提交情况
- 可以看到每个线程的性能数据（耗时、删除数量、提交次数）
- 最后会汇总所有线程的统计信息

**调整日志级别：**

```yaml
# application.yml
logging:
  level:
    cn.rhymed.data.turbo: INFO  # 关键进度信息
    # cn.rhymed.data.turbo: DEBUG  # 包含每页的详细信息
```

## 🔧 高级用法

### 动态配置参数

```java
public int batchDelete(String status, int dataSize) {
    // 根据数据量动态调整参数
    int threadCount;
    int batchSize;

    if (dataSize < 100000) {
        threadCount = 2;
        batchSize = 5000;
    } else if (dataSize < 1000000) {
        threadCount = 4;
        batchSize = 10000;
    } else {
        threadCount = 8;
        batchSize = 20000;
    }

    BatchDeleteConfig config = BatchDeleteConfig.builder()
            .primaryId("id")
            .fetchSize(1000)
            .batchSize(batchSize)
            .maxThreadCount(threadCount)
            .build();

    BatchDeleteContext.setConfig(config);

    try {
        return userMapper.deleteByStatus(status);
    } finally {
        BatchDeleteContext.clearConfig();
    }
}
```

### 封装工具方法

```java

@Component
public class BatchDeleteHelper {

    /**
     * 执行批量删除的通用方法
     */
    public <T> T executeBatchDelete(
            Supplier<T> deleteAction,
            String primaryId,
            int fetchSize,
            int batchSize,
            int maxThreadCount) {

        BatchDeleteConfig config = BatchDeleteConfig.builder()
                .primaryId(primaryId)
                .fetchSize(fetchSize)
                .batchSize(batchSize)
                .maxThreadCount(maxThreadCount)
                .build();

        BatchDeleteContext.setConfig(config);

        try {
            return deleteAction.get();
        } finally {
            BatchDeleteContext.clearConfig();
        }
    }

    /**
     * 使用默认配置执行批量删除
     */
    public <T> T executeBatchDeleteWithDefaults(Supplier<T> deleteAction) {
        return executeBatchDelete(deleteAction, "id", 1000, 5000, 4);
    }
}

// 使用示例
@Service
public class UserService {

    @Autowired
    private BatchDeleteHelper batchDeleteHelper;

    @Autowired
    private UserMapper userMapper;

    public int deleteExpiredUsers() {
        return batchDeleteHelper.executeBatchDeleteWithDefaults(
                () -> userMapper.deleteByStatus("expired")
        );
    }
}
```

## 🐛 故障排查

### 问题：拦截器没有生效

**检查步骤：**

1. 查看启动日志是否有：`BatchDeleteInterceptor 已自动注册`
2. 确认是否设置了 `BatchDeleteContext.setConfig(config)`
3. 确认删除操作确实执行了（不是直接返回 0）

### 问题：删除很慢

**优化建议：**

1. 增加 `maxThreadCount`（确保连接池够大）
2. 增加 `fetchSize` 和 `batchSize`
3. 检查主键字段是否有索引
4. 检查 WHERE 条件字段是否有索引

### 问题：内存溢出

**解决方案：**

1. 减小 `fetchSize`
2. 减小 `batchSize`
3. 增加 JVM 内存：`-Xmx2g`

---

更多问题请参考 [README.md](README.md) 的常见问题部分。
