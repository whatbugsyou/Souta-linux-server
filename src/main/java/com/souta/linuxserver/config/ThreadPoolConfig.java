package com.souta.linuxserver.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ThreadPoolConfig {
    @Bean("dialingPool")
    public ExecutorService dialingPool(){
        ThreadFactoryBuilder threadFactoryBuilder = new ThreadFactoryBuilder().setNameFormat("dialing-pool-%d");
        return new ThreadPoolExecutor(100,
                100,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                threadFactoryBuilder.build());
    }

    @Bean("refreshPool")
    public ExecutorService flushLineRetryPool(){
        ThreadFactoryBuilder threadFactoryBuilder = new ThreadFactoryBuilder().setNameFormat("refresh-pool-%d");
        return new ThreadPoolExecutor(100,
                100,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                threadFactoryBuilder.build());
    }

    @Bean("netPool")
    public ExecutorService netPool(){
        ThreadFactoryBuilder threadFactoryBuilder = new ThreadFactoryBuilder().setNameFormat("net-pool-%d");
        return new ThreadPoolExecutor(20,
                20,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                threadFactoryBuilder.build());
    }

    @Bean("basePool")
    public ExecutorService basePool(){
        ThreadFactoryBuilder threadFactoryBuilder = new ThreadFactoryBuilder().setNameFormat("net-pool-%d");
        return new ThreadPoolExecutor(20,
                20,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                threadFactoryBuilder.build());
    }
    @Bean("linePool")
    public ExecutorService linePool(){
        ThreadFactoryBuilder threadFactoryBuilder = new ThreadFactoryBuilder().setNameFormat("line-pool-%d");
        return new ThreadPoolExecutor(100,
                100,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                threadFactoryBuilder.build());
    }
}
