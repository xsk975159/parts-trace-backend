package com.kewei.parts;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 零部件溯源系统启动类
 *
 * @author kewei
 * @since 2024-01-15
 */
@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
@MapperScan({"com.kewei.parts.security", "com.kewei.parts.module.system.mapper", "com.kewei.parts.module.identity.mapper", "com.kewei.parts.module.parts.mapper", "com.kewei.parts.module.trace.mapper", "com.kewei.parts.module.anomaly.mapper"})
public class PartsTraceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PartsTraceApplication.class, args);
    }
}





