package com.messagepulse.core.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI messagePulseOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MessagePulse API")
                        .description("AI 时代消息基础设施 - 核心平台 API")
                        .version("2.0.0")
                        .contact(new Contact()
                                .name("MessagePulse Team")
                                .url("https://github.com/gccszs/MessagePluse")
                                .email("support@messagepulse.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Development Server"),
                        new Server()
                                .url("https://api.messagepulse.com")
                                .description("Production Server")))
                .addSecurityItem(new SecurityRequirement().addList("ApiKey"))
                .schemaRequirement("ApiKey", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-API-Key")
                        .description("API Key for authentication"));
    }
}
