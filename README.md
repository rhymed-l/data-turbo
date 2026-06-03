# Data Turbo - MyBatis 批量数据处理工具

基于 MyBatis 拦截器实现的高性能批量数据处理工具，支持批量删除、批量更新和批量查询，适用于大数据量操作场景。

> 📖 **详细使用指南**：完整的配置说明、使用示例和最佳实践请查看 [USAGE.md](USAGE.md)

## 核心特性

- ✅ **自动配置**：Spring Boot 项目开箱即用，无需手动注册
- ✅ **批量删除**：多线程并发删除、分批提交事务，提升删除效率
- ✅ **批量更新**：多线程并发更新、分批提交事务，提升更新效率
- ✅ **批量查询**：分批查询 + 回调处理，避免大数据量 OOM，保证快照一致性
- ✅ **窗口函数分页**：使用 ROW_NUMBER() 窗口函数智能分页
- ✅ **零侵入设计**：通过拦截器实现，无需修改现有 Mapper 代码
- ✅ **线程安全**：删除/更新每个线程独立 SqlSession 和 Transaction

## 快速开始

### 1. 添加依赖

将 `data-turbo` 添加到你的项目依赖中：

```xml

<dependency>
    <groupId>cn.rhymed</groupId>
    <artifactId>data-turbo</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. ✨ 自动配置（推荐）

**Spring Boot 项目无需任何配置，拦截器会自动注册！**

只需引入依赖，Spring Boot 会自动扫描并注册 `BatchDeleteInterceptor`、`BatchUpdateInterceptor` 和 `BatchSelectInterceptor`。

启动日志会显示：

```
BatchDeleteInterceptor 已自动注册到 SqlSessionFactory: DefaultSqlSessionFactory
BatchUpdateInterceptor 已自动注册到 SqlSessionFactory: DefaultSqlSessionFactory
BatchSelectInterceptor 已自动注册到 SqlSessionFactory: DefaultSqlSessionFactory
```

#### 2.1 可选：自定义默认配置

在 `application.yml` 中配置默认参数：

```yaml
data-turbo:
  batch-delete:
    primary-id: id              # 默认主键字段（不配置则为 null，会自动推断）
    fetch-size: 5000            # 默认每批次查询大小，默认 5000
    batch-size: 50000           # 默认每批次提交大小，默认 50000
    max-thread-count: 3         # 默认最大线程数，默认 3
  batch-update:
    primary-id: id
    fetch-size: 5000
    batch-size: 50000
    max-thread-count: 3
  batch-select:
    primary-id: id              # 默认主键字段
    fetch-size: 5000            # 默认每批次查询大小
```

**内置默认值**（不配置时使用）：

- `primaryId`: `null`（自动推断为 "id" 或 "表别名.id"）
- `fetchSize`: `5000`
- `batchSize`: `50000`
- `maxThreadCount`: `3`

**注意**：

- 这些是**默认值**，当调用 `BatchDeleteHelper.execute()` 时不传参数会使用这些默认值
- 仍然可以在代码中显式指定配置来覆盖默认值

### 3. 手动注册（非 Spring Boot 项目）

如果你的项目不是 Spring Boot，需要手动注册拦截器：

```java
import cn.rhymed.data.turbo.interceptor.BatchDeleteInterceptor;
import org.apache.ibatis.session.SqlSessionFactory;

public class MyBatisConfig {

    public void configureSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
        BatchDeleteInterceptor interceptor = new BatchDeleteInterceptor(sqlSessionFactory);
        sqlSessionFactory.getConfiguration().addInterceptor(interceptor);
    }
}
```

或者在 MyBatis 配置文件中：

```xml

<configuration>
    <plugins>
        <plugin interceptor="cn.rhymed.data.turbo.interceptor.BatchDeleteInterceptor">
            <!-- 注意：需要通过构造函数注入 SqlSessionFactory，可能需要自定义实现 -->
        </plugin>
    </plugins>
</configuration>
```

### 4. 使用批量删除

在需要批量删除的地方使用 `BatchDeleteContext` 设置配置：

```java
import cn.rhymed.data.turbo.config.BatchDeleteConfig;
import cn.rhymed.data.turbo.context.BatchDeleteContext;

public class UserService {

    @Autowired
    private UserMapper userMapper;

    public void batchDeleteUsers() {
        // 设置批量删除配置
        BatchDeleteConfig config = BatchDeleteConfig.builder()
                .primaryId("id")           // 主键字段名
                .fetchSize(1000)           // 每次查询/删除的数据量
                .batchSize(5000)           // 每删除5000条提交一次事务
                .maxThreadCount(4)         // 最多使用4个线程
                .build();

        BatchDeleteContext.setConfig(config);

        try {
            // 执行删除操作（自动触发批量删除拦截器）
            userMapper.deleteByCondition(someCondition);
        } finally {
            // 清理上下文（拦截器会自动清理，这里是保险起见）
            BatchDeleteContext.clearConfig();
        }
    }
}
```

## 配置参数说明

### BatchDeleteConfig

| 参数             | 类型     | 必填 | 说明                                 |
|----------------|--------|----|------------------------------------|
| primaryId      | String | 是  | 主键字段名，用于分页排序（例如：`"id"` 或 `"t.id"`） |
| fetchSize      | int    | 是  | 每批次查询/删除的数据量，建议 500-2000           |
| batchSize      | int    | 是  | 每删除多少条数据提交一次事务，建议 5000-10000       |
| maxThreadCount | int    | 是  | 最大并发线程数，建议 2-8，取决于数据库连接池大小         |

### 参数配置建议

```java
// 小数据量（10万以内）
BatchDeleteConfig.builder()
    .

primaryId("id")
    .

fetchSize(1000)
    .

batchSize(5000)
    .

maxThreadCount(2)
    .

build();

// 中等数据量（10万-100万）
BatchDeleteConfig.

builder()
    .

primaryId("id")
    .

fetchSize(1500)
    .

batchSize(10000)
    .

maxThreadCount(4)
    .

build();

// 大数据量（100万以上）
BatchDeleteConfig.

builder()
    .

primaryId("id")
    .

fetchSize(2000)
    .

batchSize(10000)
    .

maxThreadCount(8)
    .

build();
```

## 工作原理

### 执行流程

```
1. 设置 BatchDeleteConfig 到 ThreadLocal
   ↓
2. 执行 DELETE 语句（被拦截器捕获）
   ↓
3. 使用窗口函数查询分页信息
   SELECT
     floor((row_num - 1) / fetchSize) AS page_num,
     min(id) AS start_key,
     max(id) AS end_key,
     count(*) AS page_size
   FROM (
     SELECT id, row_number() OVER (ORDER BY id) AS row_num
     FROM your_table
     WHERE your_conditions
   ) t
   GROUP BY page_num
   ↓
4. 将分页任务分配给多个线程
   ↓
5. 每个线程独立执行删除
   - 使用 BETWEEN start_key AND end_key 删除数据
   - 累计删除数量达到 batchSize 时提交事务
   ↓
6. 汇总所有线程的删除结果
```

### 关键技术点

1. **窗口函数分页**
   ```sql
   -- 原始 DELETE 语句
   DELETE FROM user WHERE status = 'inactive'

   -- 转换为分页查询（获取分页范围）
   SELECT
     floor((row_num - 1) / 1000) AS page_num,
     min(id) AS start_key,
     max(id) AS end_key
   FROM (
     SELECT id, row_number() OVER (ORDER BY id) AS row_num
     FROM user WHERE status = 'inactive'
   ) t
   GROUP BY page_num

   -- 实际删除 SQL（每个分页范围）
   DELETE FROM user
   WHERE status = 'inactive'
     AND id BETWEEN start_key AND end_key
   ORDER BY id
   ```

2. **多线程并发删除**
    - 每个线程使用独立的 `SqlSession` 和 `Transaction`
    - 线程安全，互不干扰

3. **批量事务提交**
    - 使用 `ExecutorType.BATCH` 模式
    - 累计删除 `batchSize` 条记录后提交事务
    - 避免长事务锁表

## 使用场景

### ✅ 适用场景

- 大批量数据删除（几十万到千万级）
- 需要避免长事务锁表
- 数据库支持窗口函数（MySQL 8.0+, PostgreSQL, Oracle, SQL Server）
- 有自增主键或唯一递增字段

### ❌ 不适用场景

- 小数据量删除（几千条以内，直接删除更快）
- 数据库不支持窗口函数（MySQL 5.7 及以下）
- 没有自增主键或唯一递增字段
- 需要严格的事务一致性（本工具会分批提交）

## Mapper 示例

### 基础用法

```java
public interface UserMapper {
    // 普通的删除方法，无需特殊处理
    @Delete("DELETE FROM user WHERE status = #{status}")
    int deleteByStatus(String status);

    // 带条件的删除
    int deleteByCondition(@Param("condition") UserCondition condition);
}
```

```xml
<!-- UserMapper.xml -->
<delete id="deleteByCondition">
    DELETE FROM user
    WHERE status = #{condition.status}
    <if test="condition.createTime != null">
        AND create_time &lt; #{condition.createTime}
    </if>
</delete>
```

### 使用示例

```java

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    /**
     * 批量删除过期用户
     */
    public int batchDeleteExpiredUsers() {
        BatchDeleteConfig config = BatchDeleteConfig.builder()
                .primaryId("id")
                .fetchSize(1000)
                .batchSize(5000)
                .maxThreadCount(4)
                .build();

        BatchDeleteContext.setConfig(config);

        try {
            return userMapper.deleteByStatus("expired");
        } finally {
            BatchDeleteContext.clearConfig();
        }
    }

    /**
     * 批量删除指定时间之前的日志
     */
    public int batchDeleteOldLogs(Date beforeDate) {
        BatchDeleteConfig config = BatchDeleteConfig.builder()
                .primaryId("log_id")       // 指定主键字段
                .fetchSize(2000)
                .batchSize(10000)
                .maxThreadCount(6)
                .build();

        BatchDeleteContext.setConfig(config);

        try {
            LogCondition condition = new LogCondition();
            condition.setCreateTime(beforeDate);
            return logMapper.deleteByCondition(condition);
        } finally {
            BatchDeleteContext.clearConfig();
        }
    }
}
```

## 注意事项

### 1. 主键字段配置

```java
// ✅ 单表查询
.primaryId("id")

// ✅ 带表别名
.

primaryId("u.id")

// ❌ 不指定（会自动尝试使用 "id"，可能失败）
.

primaryId(null)
```

### 2. 数据库连接池配置

确保数据库连接池大小 >= maxThreadCount：

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # 应该 >= maxThreadCount
```

### 3. 事务管理

- 拦截器会自动管理事务，**不要**在外层使用 `@Transactional`
- 删除操作会分批提交，不保证原子性

```java
// ❌ 错误用法：外层使用 @Transactional
@Transactional
public void batchDelete() {
    BatchDeleteContext.setConfig(config);
    userMapper.deleteByStatus("expired");
}

// ✅ 正确用法：不使用 @Transactional
public void batchDelete() {
    BatchDeleteContext.setConfig(config);
    userMapper.deleteByStatus("expired");
}
```

### 4. 异常处理

任何线程删除失败都会导致整个操作失败，已提交的事务不会回滚：

```java
try{
        BatchDeleteContext.setConfig(config);

int count = userMapper.deleteByStatus("expired");
    log.

info("成功删除 {} 条记录",count);
}catch(
Exception e){
        log.

error("批量删除失败，部分数据可能已删除",e);
// 处理异常...
}finally{
        BatchDeleteContext.

clearConfig();
}
```

### 5. 数据库兼容性

| 数据库        | 支持版本  | 说明                    |
|------------|-------|-----------------------|
| MySQL      | 8.0+  | 需要支持窗口函数 ROW_NUMBER() |
| PostgreSQL | 9.0+  | 完全支持                  |
| Oracle     | 10g+  | 完全支持                  |
| SQL Server | 2012+ | 完全支持                  |
| MySQL 5.7  | ❌     | 不支持窗口函数               |

## 批量查询（Batch Select）

批量查询通过分批加载数据 + 回调处理的方式，**避免大数据量 OOM**。所有分页查询在**同一事务内**串行执行，依赖 MySQL
REPEATABLE READ 保证快照一致性。

### 核心设计

| 特性         | 说明                                        |
|------------|-------------------------------------------|
| **快照一致性**  | 同一事务内 REPEATABLE READ，所有分页数据 == 事务开始时刻的数据 |
| **原子性**    | 任意一批回调抛异常 → `@Transactional` 回滚整个事务       |
| **避免 OOM** | 每批数据加载到内存、处理完、GC 回收，不会一次性加载全量数据           |

### 使用方式

#### 方式一：回调处理（推荐，省内存）

```java
import cn.rhymed.data.turbo.BatchSelectHelper;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    @Transactional  // 必须在事务中使用，保证快照一致性和原子性
    public void processAllUsers() {
        // 使用默认配置，分批查询并通过回调处理
        BatchSelectHelper.execute(() -> userMapper.list(), batch -> {
            // batch 是当前这一批的数据，处理完即释放
            batch.forEach(user -> sendEmail(user));
        });
    }

    @Transactional
    public void processWithCustomConfig() {
        // 自定义配置
        BatchSelectConfig config = BatchSelectConfig.builder()
                .primaryId("user_id")
                .fetchSize(1000)
                .build();

        BatchSelectHelper.execute(config, () -> userMapper.list(), batch -> {
            batch.forEach(user -> userMapper.updateStatus(user.getId(), "processed"));
        });
    }
}
```

#### 方式二：收集全部结果（方便但内存占用大）

```java
@Transactional
public void processAllUsers() {
    // 收集所有结果到一个 List（大数据量时慎用）
    List<User> allUsers = BatchSelectHelper.executeAndCollect(() -> userMapper.list());
    // 然后做业务处理
}
```

### 与原始查询的等价性

```java
// 原始写法（大数据量会 OOM）
List<User> all = userMapper.list();
all.forEach(user -> sendEmail(user));

// 批量查询写法（等价于上面，但不会 OOM）
BatchSelectHelper.execute(() -> userMapper.list(), batch -> {
    batch.forEach(user -> sendEmail(user));
});
```

**数据保证：**

- 回调处理的总数据量 = 一次查询出来的全部数据
- 数据内容完全一致（同一事务快照读）
- 唯一区别：返回值是 `void`，数据通过回调流式处理

### BatchSelectConfig 参数

| 参数        | 类型     | 必填 | 说明              |
|-----------|--------|----|-----------------|
| primaryId | String | 否  | 主键字段名，不配置则自动推断  |
| fetchSize | int    | 否  | 每批次查询大小，默认 5000 |

### ⚠️ 批量查询注意事项

1. **必须在 `@Transactional` 中使用**：保证快照一致性和原子性
2. **回调中的异常会触发整体回滚**：任意一批处理失败 → 事务回滚 → 整体失败
3. **不支持多线程**：为保证事务一致性，所有分页在同一连接内串行执行

## 性能参考

测试环境：MySQL 8.0，8 核 CPU，数据库连接池大小 20

| 数据量   | 不使用拦截器 | 使用拦截器（4线程） | 提升   |
|-------|--------|------------|------|
| 10万条  | 15秒    | 5秒         | 3倍   |
| 50万条  | 90秒    | 25秒        | 3.6倍 |
| 100万条 | 210秒   | 55秒        | 3.8倍 |

*实际性能取决于硬件配置、网络延迟、索引设计等因素*

## 常见问题

### Q1: 为什么删除没有走批量拦截器？

**A:** 检查以下几点：

1. 是否调用了 `BatchDeleteContext.setConfig(config)`
2. 拦截器是否正确注册：
    - Spring Boot 项目：查看启动日志是否有 "BatchDeleteInterceptor 已自动注册"
    - 非 Spring Boot：确认手动注册代码是否执行
3. 查看日志，确认拦截器被触发

### Q1.1: 如何禁用自动配置？

**A:** 如果你想禁用自动配置，可以在 Spring Boot 主类上排除：

```java

@SpringBootApplication(exclude = {DataTurboAutoConfiguration.class})
public class Application {
    static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

或在 `application.yml` 中配置：

```yaml
spring:
  autoconfigure:
    exclude:
      - cn.rhymed.data.turbo.config.DataTurboAutoConfiguration
```

### Q2: 如何查看执行进度？

**A:** 查看日志输出，拦截器会详细记录整个执行过程：

```
========================================
批量删除拦截器启动
MappedStatement ID: com.example.mapper.UserMapper.deleteByStatus
配置参数: primaryId=id, fetchSize=1000, batchSize=5000, maxThreadCount=4
原始 SQL: DELETE FROM user WHERE status = ?
开始查询分页信息...
分页信息查询完成，共 50 页，耗时 123 ms
数据量较大，启用多线程批量删除模式
创建线程池，线程数=4, 总分页数=50, 每个线程处理约13页
分配任务到线程 #1: 处理第 1 到第 13 页（共 13 页）
分配任务到线程 #2: 处理第 14 到第 26 页（共 13 页）
分配任务到线程 #3: 处理第 27 到第 39 页（共 13 页）
分配任务到线程 #4: 处理第 40 到第 50 页（共 11 页）
所有线程已启动，等待执行完成...
[pool-1-thread-1] 线程 #1 启动，开始处理 13 个分页
[pool-1-thread-2] 线程 #2 启动，开始处理 13 个分页
[pool-1-thread-3] 线程 #3 启动，开始处理 13 个分页
[pool-1-thread-4] 线程 #4 启动，开始处理 11 个分页
[pool-1-thread-1] 第 1 次事务提交，实际删除 5000 条，累计删除 5000 条
[pool-1-thread-2] 第 1 次事务提交，实际删除 5000 条，累计删除 5000 条
[pool-1-thread-3] 第 1 次事务提交，实际删除 5000 条，累计删除 5000 条
[pool-1-thread-4] 第 1 次事务提交，实际删除 5000 条，累计删除 5000 条
[pool-1-thread-1] 第 2 次事务提交，实际删除 5000 条，累计删除 10000 条
[pool-1-thread-2] 第 2 次事务提交，实际删除 5000 条，累计删除 10000 条
...
[pool-1-thread-1] 线程 #1 完成！处理了 13 页，共删除 13000 条记录，共提交 3 次事务，耗时 1234 ms
[pool-1-thread-2] 线程 #2 完成！处理了 13 页，共删除 13000 条记录，共提交 3 次事务，耗时 1256 ms
[pool-1-thread-3] 线程 #3 完成！处理了 13 页，共删除 13000 条记录，共提交 3 次事务，耗时 1245 ms
[pool-1-thread-4] 线程 #4 完成！处理了 11 页，共删除 11000 条记录，共提交 3 次事务，耗时 1198 ms
所有线程执行完成，开始汇总结果...
线程 #1 删除数量: 13000
线程 #2 删除数量: 13000
线程 #3 删除数量: 13000
线程 #4 删除数量: 11000
----------------------------------------
批量删除统计: 使用 4 个线程，处理 50 个分页，总共删除 50000 条记录
----------------------------------------
批量删除全部完成！总删除 50000 条记录，总耗时 1456 ms (约 1.456 秒)
========================================
```

**日志级别说明：**

- `INFO`：关键进度信息（启动、完成、提交事务等）
- `DEBUG`：详细的分页信息和每页处理情况

### Q3: 删除失败后如何回滚？

**A:** 批量删除使用分批提交，**无法全局回滚**。如果需要严格的事务一致性，不应该使用此工具。

### Q4: 可以用于 UPDATE 操作吗？

**A:** 支持。使用 `BatchUpdateHelper.execute()` 即可，用法与批量删除一致。

### Q5: 批量查询为什么要用回调而不是直接返回 List？

**A:** 批量查询的核心目标是**避免 OOM**。如果返回 List，所有数据最终还是会加载到内存中，失去了分批的意义。通过回调（Consumer），每批数据处理完即可被
GC 回收。

### Q6: fetchSize 和 batchSize 有什么区别？

**A:**

- `fetchSize`: 窗口函数分页大小，决定每个 PageResult 的数据范围（影响分页数量）
- `batchSize`: 事务提交阈值，累计删除/更新这么多数据后提交一次事务（影响事务大小）

建议配置：`batchSize >= fetchSize`，通常设置为 `fetchSize` 的 5-10 倍。

## 📚 文档导航

| 文档                     | 说明                               |
|------------------------|----------------------------------|
| [README.md](README.md) | 项目概览、快速开始、工作原理、FAQ               |
| [USAGE.md](USAGE.md)   | **详细使用指南**：完整配置说明、使用示例、场景建议、故障排查 |

## 版本历史

### v1.0.5 (2026-06-01)

- ✅ 新增批量查询（BatchSelectHelper）：分批查询 + 回调处理，避免大数据量 OOM
- ✅ 批量查询保证快照一致性（同一事务 REPEATABLE READ）
- ✅ 批量查询保证原子性（@Transactional 回滚）

### v1.0.4 (2026-01-15)

- ✅ 新增批量更新（BatchUpdateHelper）：多线程并发更新

### v1.0.0 (2025-12-10)

- ✅ 初始版本发布
- ✅ 支持多线程批量删除
- ✅ 支持分批事务提交
- ✅ 支持窗口函数智能分页
- ✅ Spring Boot 自动配置（开箱即用）
- ✅ 兼容 Spring Boot 2.x 和 3.x

## License

MIT License

## 联系作者

- Author: rhymed.liu
- Email: me@rhymed.cn

---

**⚠️ 使用前请充分测试，并做好数据备份！**
