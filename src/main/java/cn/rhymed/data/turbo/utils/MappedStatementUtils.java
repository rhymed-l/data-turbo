package cn.rhymed.data.turbo.utils;

import cn.rhymed.data.turbo.domain.PageResult;
import org.apache.ibatis.mapping.*;

import java.util.ArrayList;
import java.util.List;

import static cn.rhymed.data.turbo.constants.CommonConstants.CUSTOM_ROW_NUMBER_SQL_POSTFIX;

/**
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:43
 **/
public class MappedStatementUtils {

    private static final List<ResultMapping> EMPTY_RESULT_MAPPING = new ArrayList<>(0);


    public static MappedStatement newRowNumberMappedStatement(MappedStatement ms) {
        MappedStatement.Builder builder =
                new MappedStatement.Builder(ms.getConfiguration(), ms.getId() + CUSTOM_ROW_NUMBER_SQL_POSTFIX,
                        ms.getSqlSource(), ms.getSqlCommandType());
        builder.resource(ms.getResource());
        builder.fetchSize(ms.getFetchSize());
        builder.statementType(ms.getStatementType());
        builder.keyGenerator(ms.getKeyGenerator());
        if (ms.getKeyProperties() != null && ms.getKeyProperties().length != 0) {
            StringBuilder keyProperties = new StringBuilder();
            for (String keyProperty : ms.getKeyProperties()) {
                keyProperties.append(keyProperty).append(",");
            }
            keyProperties.delete(keyProperties.length() - 1, keyProperties.length());
            builder.keyProperty(keyProperties.toString());
        }
        builder.timeout(ms.getTimeout());
        builder.parameterMap(ms.getParameterMap());
        //count查询返回值int
        List<ResultMap> resultMaps = new ArrayList<>();
        ResultMap resultMap =
                new ResultMap.Builder(ms.getConfiguration(), ms.getId(), PageResult.class, EMPTY_RESULT_MAPPING).build();
        resultMaps.add(resultMap);
        builder.resultMaps(resultMaps);
        builder.resultSetType(ms.getResultSetType());
        builder.cache(ms.getCache());
        builder.flushCacheRequired(ms.isFlushCacheRequired());
        builder.useCache(ms.isUseCache());
        return builder.build();
    }

    /**
     * 复制 MappedStatement，使用新的 id 和 BoundSql
     */
    public static MappedStatement copyFromMappedStatement(MappedStatement ms, String newMsId, BoundSql boundSql) {
        MappedStatement.Builder builder = new MappedStatement.Builder(
                ms.getConfiguration(),
                newMsId,
                new BoundSqlSqlSource(boundSql),
                ms.getSqlCommandType()
        );
        builder.resource(ms.getResource());
        builder.fetchSize(ms.getFetchSize());
        builder.statementType(ms.getStatementType());
        builder.keyGenerator(ms.getKeyGenerator());
        if (ms.getKeyProperties() != null && ms.getKeyProperties().length != 0) {
            builder.keyProperty(String.join(",", ms.getKeyProperties()));
        }
        if (ms.getKeyColumns() != null && ms.getKeyColumns().length != 0) {
            builder.keyColumn(String.join(",", ms.getKeyColumns()));
        }
        builder.timeout(ms.getTimeout());
        builder.parameterMap(ms.getParameterMap());
        builder.resultMaps(ms.getResultMaps());
        builder.resultSetType(ms.getResultSetType());
        builder.cache(ms.getCache());
        builder.flushCacheRequired(ms.isFlushCacheRequired());
        builder.useCache(ms.isUseCache());
        return builder.build();
    }

    /**
     * 自定义 SqlSource，直接返回给定的 BoundSql
     */
    private static class BoundSqlSqlSource implements SqlSource {
        private final BoundSql boundSql;

        public BoundSqlSqlSource(BoundSql boundSql) {
            this.boundSql = boundSql;
        }

        @Override
        public BoundSql getBoundSql(Object parameterObject) {
            return boundSql;
        }
    }
}
