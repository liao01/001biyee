package com.jiawa.lyw.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 独立装配外部地图服务配置，避免业务模块通过彼此的基础设施共享凭据或超时策略。 */
@Configuration
@ConditionalOnProperty(prefix = "app.map.amap", name = "base-url")
@EnableConfigurationProperties(AmapWebServiceProperties.class)
public class AmapWebServiceConfiguration {
}
