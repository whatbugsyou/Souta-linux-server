package com.souta.linuxserver.config;

import cn.hutool.core.thread.NamedThreadFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
public class ThreadPoolConfig {
    @Bean("dialingPool")
    public ExecutorService dialingPool() {
        NamedThreadFactory namedThreadFactory = new NamedThreadFactory("dialing-pool-", false);
        return new ThreadPoolExecutor(100,
                100,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                namedThreadFactory);
    }

    @Bean("refreshPool")
    public ExecutorService flushLineRetryPool() {
        NamedThreadFactory namedThreadFactory = new NamedThreadFactory("refresh-pool-", false);
        return new ThreadPoolExecutor(100,
                100,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                namedThreadFactory);
    }

    @Bean("netPool")
    public ExecutorService netPool() {
        NamedThreadFactory namedThreadFactory = new NamedThreadFactory("net-pool-", false);
        return new ThreadPoolExecutor(20,
                20,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                namedThreadFactory);
    }

    @Bean("basePool")
    public ExecutorService basePool() {
        NamedThreadFactory namedThreadFactory = new NamedThreadFactory("base-pool-", false);
        return new ThreadPoolExecutor(20,
                20,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                namedThreadFactory);
    }

    @Bean("linePool")
    public ExecutorService linePool() {
        NamedThreadFactory namedThreadFactory = new NamedThreadFactory("line-pool-", false);
        return new ThreadPoolExecutor(100,
                100,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                namedThreadFactory);
    }

    @Bean("monitorPool")
    public ScheduledExecutorService monitorPool() {
        return Executors.newScheduledThreadPool(5);
    }


}
