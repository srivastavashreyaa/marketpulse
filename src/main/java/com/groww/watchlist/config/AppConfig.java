package com.groww.watchlist.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class AppConfig {

    /** Dedicated pool so a slow upstream can never starve the web server's request threads. */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService marketExecutor() {
        AtomicInteger n = new AtomicInteger();
        return Executors.newFixedThreadPool(12, r -> {
            Thread t = new Thread(r, "market-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }
}
