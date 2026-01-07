package com.sales;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class SalesSystemApplication {

    public static void main(String[] args) {
        System.setProperty("hadoop.home.dir", "D:\\hadoop\\winutils-master\\winutils-master\\hadoop-3.3.5");
        SpringApplication.run(SalesSystemApplication.class, args);
    }
}
