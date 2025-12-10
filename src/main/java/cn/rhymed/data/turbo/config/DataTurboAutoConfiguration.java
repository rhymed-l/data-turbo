package cn.rhymed.data.turbo.config;

import cn.rhymed.data.turbo.BatchDeleteHelper;
import cn.rhymed.data.turbo.interceptor.BatchDeleteInterceptor;
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
 * 自动注册 BatchDeleteInterceptor 到所有 SqlSessionFactory
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
    public void addBatchDeleteInterceptor() {
        // 初始化配置
        DataTurboProperties properties = dataTurboProperties();
        BatchDeleteHelper.setProperties(properties);

        if (sqlSessionFactories == null || sqlSessionFactories.isEmpty()) {
            log.warn("未找到 SqlSessionFactory，BatchDeleteInterceptor 未注册");
            return;
        }

        for (SqlSessionFactory sqlSessionFactory : sqlSessionFactories) {
            BatchDeleteInterceptor interceptor = new BatchDeleteInterceptor(sqlSessionFactory);
            sqlSessionFactory.getConfiguration().addInterceptor(interceptor);
            log.info("BatchDeleteInterceptor 已自动注册到 SqlSessionFactory: {}",
                    sqlSessionFactory.getClass().getSimpleName());
        }

        // 打印配置信息
        log.info("Data Turbo 默认配置: primaryId={}, fetchSize={}, batchSize={}, maxThreadCount={}",
                properties.getBatchDelete().getPrimaryId(),
                properties.getBatchDelete().getFetchSize(),
                properties.getBatchDelete().getBatchSize(),
                properties.getBatchDelete().getMaxThreadCount());
    }
}
