package cn.rhymed.data.turbo.interceptor;

import cn.rhymed.data.turbo.RowNumberSqlParser;
import cn.rhymed.data.turbo.config.BatchSelectConfig;
import cn.rhymed.data.turbo.config.PageConfig;
import cn.rhymed.data.turbo.context.BatchSelectContext;
import cn.rhymed.data.turbo.domain.PageResult;
import cn.rhymed.data.turbo.utils.MappedStatementUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.rhymed.data.turbo.constants.CommonConstants.CUSTOM_ROW_NUMBER_SQL_POSTFIX;

/**
 * 批量查询拦截器
 * <p>
 * 拦截 Executor.query，在同一个事务内分批查询数据并通过回调逐批处理，
 * 避免大数据量 OOM。依赖 REPEATABLE READ 保证快照一致性，
 * 依赖外层 @Transactional 保证原子性。
 * </p>
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2026-06-01
 **/
@Slf4j
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class BatchSelectInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        BatchSelectConfig config = BatchSelectContext.getConfig();
        // 只有获取到批量查询的配置才处理
        if (config == null) {
            return invocation.proceed();
        }

        Consumer<List<?>> batchConsumer = BatchSelectContext.getBatchConsumer();
        if (batchConsumer == null) {
            return invocation.proceed();
        }

        long startTime = System.currentTimeMillis();
        try {
            Object[] args = invocation.getArgs();
            MappedStatement ms = (MappedStatement) args[0];
            Object parameter = args[1];
            Executor executor = (Executor) invocation.getTarget();
            BoundSql boundSql = ms.getBoundSql(parameter);

            log.info("批量查询拦截器启动");
            log.info("配置参数: primaryId={}, fetchSize={}", config.getPrimaryId(), config.getFetchSize());
            log.info("原始 SQL: {}", boundSql.getSql());

            // 获取分页配置信息（通过窗口函数查询）
            List<PageResult> pageResults = doGetPageConfig(ms, parameter, executor, boundSql, config);
            // 获取到分页数据就可以清空上下文了
            BatchSelectContext.clearConfig();

            // 如果小于等于1页，直接执行原查询操作
            if (pageResults.size() <= 1) {
                log.info("数据量较小（<=1页），使用普通查询模式");
                List<?> result = (List<?>) invocation.proceed();
                batchConsumer.accept(result);
                long duration = System.currentTimeMillis() - startTime;
                log.info("查询完成，共 {} 条记录，耗时 {} ms", result.size(), duration);
                return Collections.emptyList();
            }

            // 逐页查询并回调处理（传入 executor 保证同一事务）
            int totalSize = doBatchSelect(ms, parameter, executor, boundSql, config, pageResults, batchConsumer);
            long duration = System.currentTimeMillis() - startTime;
            log.info("批量查询全部完成！总查询 {} 条记录，总耗时 {} ms (约 {} 秒)",
                    totalSize, duration, duration / 1000.0);
            return Collections.emptyList();
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("批量查询失败，已耗时 {} ms", duration, e);
            throw e;
        } finally {
            // 兜底再清空一次
            BatchSelectContext.clearConfig();
        }
    }

    /**
     * 逐页查询并回调处理（同一事务内串行执行）
     */
    @SuppressWarnings("unchecked")
    private int doBatchSelect(MappedStatement ms,
                              Object parameter,
                              Executor executor,
                              BoundSql boundSql,
                              BatchSelectConfig config,
                              List<PageResult> pageResults,
                              Consumer<List<?>> batchConsumer) throws Exception {

        int totalSize = 0;

        for (int i = 0; i < pageResults.size(); i++) {
            PageResult pageResult = pageResults.get(i);
            log.info("开始处理第 {}/{} 页: startKey={}, endKey={}, pageSize={}",
                    i + 1, pageResults.size(),
                    pageResult.getStartKey(), pageResult.getEndKey(), pageResult.getPageSize());

            // 构建带分页条件的查询 SQL
            PageConfig pageConfig = PageConfig.builder()
                    .primaryId(config.getPrimaryId())
                    .pageSize(config.getFetchSize())
                    .build();
            String selectSql = RowNumberSqlParser.getRowNumberPageSql(boundSql.getSql(), pageConfig, pageResult);
            log.debug("生成的查询 SQL: {}", selectSql);

            // 构建新的 BoundSql
            BoundSql selectBoundSql = new BoundSql(ms.getConfiguration(), selectSql,
                    getParameters(selectSql, boundSql.getParameterMappings()), parameter);
            copyAdditionalParameters(boundSql, selectBoundSql);

            // 创建新的 MappedStatement（保留原始结果映射）
            MappedStatement selectMs = MappedStatementUtils.copyFromMappedStatement(
                    ms,
                    ms.getId() + "_batch_select_" + pageResult.getPageNum(),
                    selectBoundSql);

            // 在同一事务内执行查询
            CacheKey cacheKey = executor.createCacheKey(selectMs, parameter, RowBounds.DEFAULT, selectBoundSql);
            List<?> batchData = executor.query(selectMs, parameter, RowBounds.DEFAULT, null, cacheKey, selectBoundSql);

            totalSize += batchData.size();
            log.info("第 {} 页查询完成，本批 {} 条，累计 {} 条",
                    i + 1, batchData.size(), totalSize);

            // 回调处理当前批次数据（异常会向上传播，触发 @Transactional 回滚）
            batchConsumer.accept(batchData);
        }

        return totalSize;
    }

    private List<PageResult> doGetPageConfig(MappedStatement ms,
                                             Object parameter,
                                             Executor executor,
                                             BoundSql boundSql,
                                             BatchSelectConfig config) throws Exception {
        long startTime = System.currentTimeMillis();

        MappedStatement customCountMs = null;
        try {
            customCountMs = ms.getConfiguration().getMappedStatement(ms.getId() + CUSTOM_ROW_NUMBER_SQL_POSTFIX);
        } catch (Exception e) {
            // ignore
        }

        List<PageResult> pageResults;

        if (customCountMs == null) {
            CacheKey countKey = executor.createCacheKey(ms, parameter, RowBounds.DEFAULT, boundSql);
            countKey.update(CUSTOM_ROW_NUMBER_SQL_POSTFIX);
            customCountMs = MappedStatementUtils.newRowNumberMappedStatement(ms);

            PageConfig pageConfigInfo = PageConfig.builder()
                    .primaryId(config.getPrimaryId())
                    .pageSize(config.getFetchSize())
                    .build();
            String countSql = RowNumberSqlParser.getRowNumberSql(boundSql.getSql(), pageConfigInfo);
            log.info("将 SELECT 语句转换为窗口函数分页查询");
            log.debug("生成的窗口函数 SQL: {}", countSql);

            BoundSql countBoundSql = new BoundSql(ms.getConfiguration(), countSql,
                    getParameters(countSql, boundSql.getParameterMappings()), parameter);
            copyAdditionalParameters(boundSql, countBoundSql);

            MappedStatement finalCountMs = customCountMs;
            pageResults = executor.query(finalCountMs, parameter, RowBounds.DEFAULT, null, countKey, countBoundSql);
        } else {
            pageResults = executor.query(customCountMs, parameter, RowBounds.DEFAULT, null);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("分页信息查询完成，共 {} 页，耗时 {} ms", pageResults.size(), duration);

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

    private List<ParameterMapping> getParameters(String sql, List<ParameterMapping> parameterMappings) {
        int sqlParamCount = countParameters(sql);
        int mappingCount = parameterMappings.size();

        if (log.isDebugEnabled()) {
            log.debug("参数映射分析: SQL中的'?'数量={}, ParameterMapping数量={}", sqlParamCount, mappingCount);
        }

        if (sqlParamCount == mappingCount) {
            return parameterMappings;
        }

        if (sqlParamCount != mappingCount) {
            log.debug("参数数量不一致: SQL中的'?'数量={}, ParameterMapping数量={}. " +
                            "这在使用动态SQL(如foreach)或参数复用时是正常的,将依赖MyBatis的additionalParameters机制处理",
                    sqlParamCount, mappingCount);
        }

        return parameterMappings;
    }

    private int countParameters(String sql) {
        Pattern pattern = Pattern.compile("\\?");
        Matcher matcher = pattern.matcher(sql);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * 复制 BoundSql 的额外参数（包括 foreach 生成的动态参数）
     */
    private void copyAdditionalParameters(BoundSql source, BoundSql target) {
        try {
            java.lang.reflect.Field additionalParametersField = BoundSql.class.getDeclaredField("additionalParameters");
            additionalParametersField.setAccessible(true);

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> sourceParams = (java.util.Map<String, Object>) additionalParametersField.get(source);

            if (sourceParams != null && !sourceParams.isEmpty()) {
                for (java.util.Map.Entry<String, Object> entry : sourceParams.entrySet()) {
                    target.setAdditionalParameter(entry.getKey(), entry.getValue());
                }
                log.debug("复制了 {} 个额外参数", sourceParams.size());
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.warn("无法复制 additionalParameters: {}", e.getMessage());
        }
    }
}
