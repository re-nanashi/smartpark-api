package com.smartpark.api.shared.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RedissonConfig {
    @Value("${redis.host:localhost}")
    private String redisHost;

    @Value("${redis.port:6379}")
    private int redisPort;

    @Value("${redis.connect-timeout-ms:1000}")
    private int connectTimeoutMs;

    @Value("${redis.operation-timeout-ms:1000}")
    private int operationTimeoutMs;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort)
                .setConnectTimeout(connectTimeoutMs)
                .setTimeout(operationTimeoutMs)
                .setRetryAttempts(0)         // fail-fast: no internal retry
                .setRetryInterval(0)
                .setConnectionMinimumIdleSize(10)
                .setConnectionPoolSize(64);  // sized for 10-20k RPS surge

        log.info("Configuring Redisson client → redis://{}:{}", redisHost, redisPort);
        return Redisson.create(config);
    }
}