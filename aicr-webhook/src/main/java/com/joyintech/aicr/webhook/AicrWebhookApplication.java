package com.joyintech.aicr.webhook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Webhook 回调接收服务（部署单元 aicr-webhook :8081）。
 * 仅装配 base + security + api + common；禁止依赖 service / engine。
 */
@SpringBootApplication
public class AicrWebhookApplication {

    public static void main(String[] args) {
        SpringApplication.run(AicrWebhookApplication.class, args);
    }
}
