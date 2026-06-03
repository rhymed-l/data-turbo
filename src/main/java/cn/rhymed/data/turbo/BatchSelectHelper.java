package cn.rhymed.data.turbo;

import cn.rhymed.data.turbo.config.BatchSelectConfig;
import cn.rhymed.data.turbo.config.DataTurboProperties;
import cn.rhymed.data.turbo.context.BatchSelectContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 批量查询帮助类
 * <p>
 * 通过分批查询避免大数据量 OOM，同一事务内保证快照一致性（REPEATABLE READ），
 * 任一批次失败则整体失败（依赖 @Transactional 回滚）。
 * </p>
 * <p>使用示例：</p>
 * <pre>{@code
 * // 回调模式（省内存，推荐）
 * BatchSelectHelper.execute(() -> userMapper.list(), batch -> {
 *     batch.forEach(user -> sendEmail(user));
 * });
 *
 * // 收集模式（方便但内存占用大）
 * List<User> all = BatchSelectHelper.executeAndCollect(() -> userMapper.list());
 * }</pre>
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2026-06-01
 **/
public class BatchSelectHelper {

    private static DataTurboProperties properties;

    /**
     * 设置配置属性（由自动配置类调用）
     */
    public static void setProperties(DataTurboProperties dataTurboProperties) {
        properties = dataTurboProperties;
    }

    /**
     * 分批查询，每批数据通过回调处理（默认配置）
     *
     * @param query    查询方法，如 () -> userMapper.list()
     * @param consumer 每批数据的处理回调
     * @param <T>      数据类型
     */
    public static <T> void execute(Supplier<List<T>> query, Consumer<List<T>> consumer) {
        execute(null, query, consumer);
    }

    /**
     * 分批查询，每批数据通过回调处理（自定义配置）
     *
     * @param config   批量查询配置
     * @param query    查询方法，如 () -> userMapper.list()
     * @param consumer 每批数据的处理回调
     * @param <T>      数据类型
     */
    @SuppressWarnings("unchecked")
    public static <T> void execute(BatchSelectConfig config, Supplier<List<T>> query, Consumer<List<T>> consumer) {
        if (query == null) {
            throw new IllegalArgumentException("query 不能为空");
        }
        if (consumer == null) {
            throw new IllegalArgumentException("consumer 不能为空");
        }
        startBatchSelect(config);
        BatchSelectContext.setBatchConsumer((Consumer<List<?>>) (Consumer<?>) consumer);
        query.get();
    }

    /**
     * 分批查询并收集所有结果（默认配置）
     *
     * @param query 查询方法，如 () -> userMapper.list()
     * @param <T>   数据类型
     * @return 所有查询结果
     */
    public static <T> List<T> executeAndCollect(Supplier<List<T>> query) {
        return executeAndCollect(null, query);
    }

    /**
     * 分批查询并收集所有结果（自定义配置）
     *
     * @param config 批量查询配置
     * @param query  查询方法，如 () -> userMapper.list()
     * @param <T>    数据类型
     * @return 所有查询结果
     */
    public static <T> List<T> executeAndCollect(BatchSelectConfig config, Supplier<List<T>> query) {
        List<T> result = new ArrayList<>();
        execute(config, query, result::addAll);
        return result;
    }

    private static void startBatchSelect(BatchSelectConfig config) {
        if (config == null) {
            config = getDefaultConfig();
        }
        BatchSelectContext.setConfig(config);
    }

    /**
     * 获取默认配置（从配置文件或使用内置默认值）
     */
    private static BatchSelectConfig getDefaultConfig() {
        if (properties != null && properties.getBatchSelect() != null) {
            DataTurboProperties.BatchSelect bs = properties.getBatchSelect();
            return BatchSelectConfig.builder()
                    .primaryId(bs.getPrimaryId())
                    .fetchSize(bs.getFetchSize())
                    .build();
        }

        // 如果没有配置（非 Spring Boot 环境），使用内置默认值
        return BatchSelectConfig.builder()
                .primaryId(null)  // null 表示自动推断
                .fetchSize(5000)
                .build();
    }
}
