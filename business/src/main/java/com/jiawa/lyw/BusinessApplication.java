package com.jiawa.lyw;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.ConfigurableEnvironment;

@Slf4j
@SpringBootApplication
@MapperScan("com.jiawa.lyw.mapper")
public class BusinessApplication {

    public static void main(String[] args) {
        ConfigurableEnvironment environment = SpringApplication.run(BusinessApplication.class, args).getEnvironment();
        log.info("启动成功!!!!");
        log.info("测试地址:http://localhost:{}{}/hello"
                , environment.getProperty("server.port")
                ,environment.getProperty("server.servlet.context-path"));

    }

}
