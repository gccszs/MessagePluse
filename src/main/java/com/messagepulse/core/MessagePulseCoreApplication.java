package com.messagepulse.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MessagePulse 2.0 - AI 时代消息基础设施
 * 核心应用程序启动类
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class MessagePulseCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessagePulseCoreApplication.class, args);
    }
}
