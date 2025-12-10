package cn.rhymed.data.turbo.interceptor;

import cn.rhymed.data.turbo.RowNumberSqlParser;
import cn.rhymed.data.turbo.config.BatchDeleteConfig;
import cn.rhymed.data.turbo.config.PageConfig;
import cn.rhymed.data.turbo.context.BatchDeleteContext;
import cn.rhymed.data.turbo.domain.PageResult;
import cn.rhymed.data.turbo.utils.MappedStatementUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.rhymed.data.turbo.constants.CommonConstants.CUSTOM_ROW_NUMBER_SQL_POSTFIX;

/**
 * 批量删除拦截器
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-09 23:25
 **/
@Slf4j
@Intercepts(
        {
                @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
        }
)
@RequiredArgsConstructor
public class BatchDeleteInterceptor implements Interceptor {


    private final SqlSessionFactory sqlSessionFactory;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        BatchDeleteConfig batchDeleteConfig = BatchDeleteContext.getConfig();
        // 只有获取到批量删除的配置才处理
        if (batchDeleteConfig == null) {
            return invocation.proceed();
        }

        long startTime = System.currentTimeMillis();
        try {
            Object[] args = invocation.getArgs();
            MappedStatement ms = (MappedStatement) args[0];
            Object parameter = args[1];
            Executor executor = (Executor) invocation.getTarget();
            BoundSql boundSql = ms.getBoundSql(parameter);

            log.info("批量删除拦截器启动");
            log.info("配置参数: primaryId={}, fetchSize={}, batchSize={}, maxThreadCount={}",
                    batchDeleteConfig.getPrimaryId(),
                    batchDeleteConfig.getFetchSize(),
                    batchDeleteConfig.getBatchSize(),
                    batchDeleteConfig.getMaxThreadCount());
            log.info("原始 SQL: {}", boundSql.getSql());

            //获取分页配置信息（通过窗口函数查询）
            List<PageResult> pageResults = doGetPageConfig(ms, parameter, executor, boundSql, batchDeleteConfig);
            // 获取到分页数据就可以清空上下文了
            BatchDeleteContext.clearConfig();

            // 如果小于等于1页，直接执行原删除操作
            if (pageResults.size() <= 1) {
                log.info("数据量较小（<=1页），使用普通删除模式");
                int result = (int) invocation.proceed();
                long duration = System.currentTimeMillis() - startTime;
                log.info("删除完成，共删除 {} 条记录，耗时 {} ms", result, duration);
                return result;
            }

            // 执行多线程批量删除
            int result = doBatchDelete(ms, parameter, boundSql, batchDeleteConfig, pageResults);
            long duration = System.currentTimeMillis() - startTime;
            log.info("批量删除全部完成！总删除 {} 条记录，总耗时 {} ms (约 {} 秒)",
                    result, duration, duration / 1000.0);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("批量删除失败，已耗时 {} ms", duration, e);
            throw e;
        } finally {
            // 这里是兜底再清空一次
            BatchDeleteContext.clearConfig();
        }
    }

    private int doBatchDelete(MappedStatement ms,
                              Object parameter,
                              BoundSql boundSql,
                              BatchDeleteConfig batchDeleteConfig,
                              List<PageResult> pageResults) throws Exception {
        int poolSize = Math.min(pageResults.size(), batchDeleteConfig.getMaxThreadCount());
        ExecutorService executorService = Executors.newFixedThreadPool(poolSize);
        List<CompletableFuture<Integer>> futures = new ArrayList<>();

        // 将 PageResult 分配到各个线程，每个线程处理多个 PageResult
        int pageSize = pageResults.size();
        int pagePerThread = (pageSize + poolSize - 1) / poolSize; // 向上取整

        for (int i = 0; i < poolSize; i++) {
            int startIdx = i * pagePerThread;
            int endIdx = Math.min(startIdx + pagePerThread, pageSize);
            if (startIdx >= pageSize) {
                break;
            }
            List<PageResult> threadPages = pageResults.subList(startIdx, endIdx);
            final int threadIndex = i + 1;

            log.info("分配任务到线程 #{}: 处理第 {} 到第 {} 页（共 {} 页）",
                    threadIndex, startIdx + 1, endIdx, threadPages.size());

            CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                long threadStartTime = System.currentTimeMillis();
                String threadName = Thread.currentThread().getName();
                log.info("[{}] 线程 #{} 启动，开始处理 {} 个分页", threadName, threadIndex, threadPages.size());

                // 每个线程使用独立的 SqlSession，不自动提交
                try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH, false)) {
                    int totalDeleted = 0;
                    int uncommittedCount = 0;
                    int processedPages = 0;
                    int commitCount = 0;

                    // 获取 SqlSession 的 Executor
                    Executor threadExecutor = getExecutor(sqlSession);

                    for (PageResult pageResult : threadPages) {
                        processedPages++;
                        log.debug("[{}] 处理第 {}/{} 页: startKey={}, endKey={}, pageSize={}",
                                threadName, processedPages, threadPages.size(),
                                pageResult.getStartKey(), pageResult.getEndKey(), pageResult.getPageSize());

                        // 构建带分页条件的删除 SQL
                        PageConfig pageConfig = PageConfig.builder()
                                .primaryId(batchDeleteConfig.getPrimaryId())
                                .pageSize(batchDeleteConfig.getFetchSize())
                                .build();
                        String deleteSql = RowNumberSqlParser.getRowNumberPageSql(boundSql.getSql(), pageConfig, pageResult);
                        log.debug("[{}] 生成的删除 SQL: {}", threadName, deleteSql);
                        BoundSql deleteBoundSql = new BoundSql(ms.getConfiguration(), deleteSql, boundSql.getParameterMappings(), parameter);

                        // 创建新的 MappedStatement 用于执行删除
                        MappedStatement deleteMs = MappedStatementUtils.copyFromMappedStatement(ms, ms.getId() + "_batch_delete_" + pageResult.getPageNum(), deleteBoundSql);

                        // 执行删除操作（直接使用 Executor，不需要注册 MappedStatement）
                        threadExecutor.update(deleteMs, parameter);

                        // 估算本次删除影响的行数（用于判断是否需要提交）
                        int estimatedAffected = pageResult.getPageSize() != null ? pageResult.getPageSize() : batchDeleteConfig.getFetchSize();
                        uncommittedCount += estimatedAffected;

                        // 按 batchSize 提交事务
                        if (uncommittedCount >= batchDeleteConfig.getBatchSize()) {
                            commitCount++;
                            // 刷新批次并获取实际影响行数
                            List<BatchResult> batchResults = sqlSession.flushStatements();
                            int actualAffected = countAffectedRows(batchResults);
                            totalDeleted += actualAffected;
                            sqlSession.commit();
                            log.info("[{}] 第 {} 次事务提交，实际删除 {} 条，累计删除 {} 条",
                                    threadName, commitCount, actualAffected, totalDeleted);
                            uncommittedCount = 0;
                        }
                    }

                    // 提交剩余的删除操作
                    if (uncommittedCount > 0) {
                        commitCount++;
                        List<BatchResult> batchResults = sqlSession.flushStatements();
                        int actualAffected = countAffectedRows(batchResults);
                        totalDeleted += actualAffected;
                        sqlSession.commit();
                        log.info("[{}] 最终事务提交，实际删除 {} 条，累计删除 {} 条",
                                threadName, actualAffected, totalDeleted);
                    }

                    long threadDuration = System.currentTimeMillis() - threadStartTime;
                    log.info("[{}] 线程 #{} 完成！处理了 {} 页，共删除 {} 条记录，共提交 {} 次事务，耗时 {} ms",
                            threadName, threadIndex, processedPages, totalDeleted, commitCount, threadDuration);

                    return totalDeleted;
                } catch (Exception e) {
                    long threadDuration = System.currentTimeMillis() - threadStartTime;
                    log.error("[{}] 线程 #{} 执行失败，已耗时 {} ms", threadName, threadIndex, threadDuration, e);
                    throw new RuntimeException("批量删除失败", e);
                }
            }, executorService);

            futures.add(future);
        }

        log.info("所有线程已启动，等待执行完成...");

        // 等待所有任务完成
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        try {
            allOf.get();
            log.info("所有线程执行完成，开始汇总结果...");
        } catch (Exception e) {
            log.error("批量删除任务执行失败", e);
            throw new RuntimeException("批量删除任务执行失败", e);
        } finally {
            executorService.shutdown();
        }

        // 汇总所有线程的删除数量
        int totalDeleted = 0;
        for (int i = 0; i < futures.size(); i++) {
            int threadDeleted = futures.get(i).get();
            totalDeleted += threadDeleted;
            log.info("线程 #{} 删除数量: {}", i + 1, threadDeleted);
        }

        log.info("----------------------------------------");
        log.info("批量删除统计: 使用 {} 个线程，处理 {} 个分页，总共删除 {} 条记录",
                poolSize, pageSize, totalDeleted);
        log.info("----------------------------------------");
        return totalDeleted;
    }

    private List<PageResult> doGetPageConfig(MappedStatement ms,
                                             Object parameter,
                                             Executor executor,
                                             BoundSql boundSql,
                                             BatchDeleteConfig batchDeleteConfig) throws Exception {
        long startTime = System.currentTimeMillis();

        MappedStatement customCountMs = null;
        try {
            customCountMs = ms.getConfiguration().getMappedStatement(ms.getId() + CUSTOM_ROW_NUMBER_SQL_POSTFIX);
        } catch (Exception e) {
            //ignore
        }

        List<PageResult> pageResults;

        if (customCountMs == null) {
            CacheKey countKey = executor.createCacheKey(ms, parameter, RowBounds.DEFAULT, boundSql);
            countKey.update(CUSTOM_ROW_NUMBER_SQL_POSTFIX);
            //根据当前的 ms 创建一个返回值为 PageResult 类型的 ms
            customCountMs = MappedStatementUtils.newRowNumberMappedStatement(ms);
            //调用方言获取 row number sql
            PageConfig pageConfigInfo = PageConfig.builder()
                    .primaryId(batchDeleteConfig.getPrimaryId())
                    .pageSize(batchDeleteConfig.getFetchSize())
                    .build();
            String countSql = RowNumberSqlParser.getRowNumberSql(boundSql.getSql(), pageConfigInfo);
            log.info("将 DELETE 语句转换为查询分页的 SELECT 语句");
            log.debug("生成的窗口函数 SQL: {}", countSql);

            // 这里分页后会去掉参数 所以重新解析参数设置
            BoundSql countBoundSql =
                    new BoundSql(ms.getConfiguration(), countSql, this.getParameters(countSql, boundSql.getParameterMappings()), parameter);
            MappedStatement finalCountMs = customCountMs;
            pageResults = executor.query(finalCountMs, parameter, RowBounds.DEFAULT, null, countKey, countBoundSql);
        } else {
            pageResults = executor.query(customCountMs, parameter, RowBounds.DEFAULT, null);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("分页信息查询完成，共 {} 页，耗时 {} ms", pageResults.size(), duration);

        // 打印分页详情
        if (log.isDebugEnabled() && !pageResults.isEmpty()) {
            log.debug("分页详情:");
            for (int i = 0; i < Math.min(pageResults.size(), 5); i++) {
                PageResult pr = pageResults.get(i);
                log.debug("  第 {} 页: startKey={}, endKey={}, pageSize={}",
                        i + 1, pr.getStartKey(), pr.getEndKey(), pr.getPageSize());
            }
            if (pageResults.size() > 5) {
                log.debug("  ... 还有 {} 页", pageResults.size() - 5);
            }
        }

        return pageResults;
    }


    /**
     * 获取 SqlSession 的 Executor
     */
    private Executor getExecutor(SqlSession sqlSession) {
        try {
            // 通过反射获取 SqlSession 的 executor 字段
            java.lang.reflect.Field executorField = sqlSession.getClass().getDeclaredField("executor");
            executorField.setAccessible(true);
            return (Executor) executorField.get(sqlSession);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get executor from SqlSession", e);
        }
    }

    /**
     * 计算 BatchResult 中实际影响的行数
     */
    private int countAffectedRows(List<BatchResult> batchResults) {
        int total = 0;
        for (BatchResult batchResult : batchResults) {
            int[] updateCounts = batchResult.getUpdateCounts();
            for (int count : updateCounts) {
                if (count > 0) {
                    total += count;
                }
            }
        }
        return total;
    }

    private List<ParameterMapping> getParameters(String sql, List<ParameterMapping> parameterMappings) {
        int diff = parameterMappings.size() - countParameters(sql);
        if (diff == 0) {
            return parameterMappings;
        }

        List<ParameterMapping> result;
        for (result = new ArrayList<>(parameterMappings.size()); diff < parameterMappings.size(); diff++) {
            result.add(parameterMappings.get(diff));
        }
        return result;
    }

    private int countParameters(String sql) {
        // 定义正则表达式来匹配 ?
        Pattern pattern = Pattern.compile("\\?");
        Matcher matcher = pattern.matcher(sql);

        int count = 0;
        while (matcher.find()) {
            count++;
        }

        return count;
    }
}
