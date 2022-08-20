package com.example.springbot_reborn.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Configuration
@PropertySource("application.properties")
@Data
public class BotConfig {

    @Value("${BOT_NAME}")
    private String bot_name;

    @Value("${BOT_TOKEN}")
    private String bot_token;
}
