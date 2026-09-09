package com.joyintech.aicr.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 管理控制台 RPC 入口（部署单元 aicr-web :8080）。
 * 装配 service + security + api + base + common；禁止依赖 engine。
 */
@SpringBootApplication
public class AicrWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(AicrWebApplication.class, args);
    }
}
