package cn.rhymed.data.turbo.config;

import cn.rhymed.data.turbo.BatchDeleteHelper;
import cn.rhymed.data.turbo.BatchUpdateHelper;
import cn.rhymed.data.turbo.interceptor.BatchDeleteInterceptor;
import cn.rhymed.data.turbo.interceptor.BatchUpdateInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;

/**
 * Data Turbo 自动配置类
 * 自动注册 BatchDeleteInterceptor 和 BatchUpdateInterceptor 到所有 SqlSessionFactory
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10
 **/
@Slf4j
@Configuration
@ConditionalOnClass({SqlSessionFactory.class, BatchDeleteInterceptor.class})
public class DataTurboAutoConfiguration {

    @Resource
    private List<SqlSessionFactory> sqlSessionFactories;

    /**
     * 注册配置属性
     */
    @Bean
    @ConfigurationProperties(prefix = "data-turbo")
    public DataTurboProperties dataTurboProperties() {
        return new DataTurboProperties();
    }

    @PostConstruct
    public void addInterceptors() {
        // 初始化配置
        DataTurboProperties properties = dataTurboProperties();
        BatchDeleteHelper.setProperties(properties);
        BatchUpdateHelper.setProperties(properties);

        if (sqlSessionFactories == null || sqlSessionFactories.isEmpty()) {
            log.warn("未找到 SqlSessionFactory，拦截器未注册");
            return;
        }

        for (SqlSessionFactory sqlSessionFactory : sqlSessionFactories) {
            // 注册批量删除拦截器
            BatchDeleteInterceptor deleteInterceptor = new BatchDeleteInterceptor(sqlSessionFactory);
            sqlSessionFactory.getConfiguration().addInterceptor(deleteInterceptor);
            log.info("BatchDeleteInterceptor 已自动注册到 SqlSessionFactory: {}",
                    sqlSessionFactory.getClass().getSimpleName());

            // 注册批量更新拦截器
            BatchUpdateInterceptor updateInterceptor = new BatchUpdateInterceptor(sqlSessionFactory);
            sqlSessionFactory.getConfiguration().addInterceptor(updateInterceptor);
            log.info("BatchUpdateInterceptor 已自动注册到 SqlSessionFactory: {}",
                    sqlSessionFactory.getClass().getSimpleName());
        }

        // 打印批量删除配置信息
        log.info("BatchDelete 默认配置: primaryId={}, fetchSize={}, batchSize={}, maxThreadCount={}",
                properties.getBatchDelete().getPrimaryId(),
                properties.getBatchDelete().getFetchSize(),
                properties.getBatchDelete().getBatchSize(),
                properties.getBatchDelete().getMaxThreadCount());

        // 打印批量更新配置信息
        log.info("BatchUpdate 默认配置: primaryId={}, fetchSize={}, batchSize={}, maxThreadCount={}",
                properties.getBatchUpdate().getPrimaryId(),
                properties.getBatchUpdate().getFetchSize(),
                properties.getBatchUpdate().getBatchSize(),
                properties.getBatchUpdate().getMaxThreadCount());
    }
}
