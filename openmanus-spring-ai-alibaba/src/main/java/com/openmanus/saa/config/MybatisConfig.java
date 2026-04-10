package com.openmanus.saa.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Plus 配置
 * 仅在 storage=mysql 时启用
 */
@Configuration
@ConditionalOnProperty(name = "openmanus.session.storage", havingValue = "mysql")
@MapperScan("com.openmanus.saa.service.session.mapper")
public class MybatisConfig {
}
