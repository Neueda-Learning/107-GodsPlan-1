package com.godsplan.payments.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.admin")
public record SecurityProperties(String username, String password, String fullName) {}

