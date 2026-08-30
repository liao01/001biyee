package com.jiawa.lyw.config;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.ResourcePropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProductionMyBatisConfigurationTests {

    @Test
    void productionProfileMapsSnakeCaseQueryColumnsToCamelCaseResponseFields() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(
                new ResourcePropertySource("production", "classpath:application-prod.properties")
        );

        MybatisProperties properties = Binder.get(environment)
                .bind("mybatis", MybatisProperties.class)
                .orElseThrow(() -> new AssertionError("MyBatis production configuration is missing"));

        assertNotNull(properties.getConfiguration(),
                "Production must configure MyBatis column-name mapping");
        assertEquals(Boolean.TRUE, properties.getConfiguration().getMapUnderscoreToCamelCase());
    }
}
