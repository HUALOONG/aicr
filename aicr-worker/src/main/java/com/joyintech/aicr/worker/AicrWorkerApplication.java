package com.joyintech.aicr.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 引擎执行层（部署单元 aicr-worker :8082）。
 * Kafka 消费入口，装配并驱动 engine 执行；禁止暴露 Web 控制器。
 */
@SpringBootApplication
public class AicrWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AicrWorkerApplication.class, args);
    }
}
