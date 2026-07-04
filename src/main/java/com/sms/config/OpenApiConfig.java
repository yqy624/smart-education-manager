package com.sms.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI educationManagerOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Smart Education Manager API")
                .version("1.0.0")
                .description("智慧教育管理系统在线接口文档，覆盖认证、学生、教师、管理员、文件和通知等核心接口。"))
            .addServersItem(new Server().url("http://localhost:8080").description("本地开发环境"))
            .components(new Components().addSecuritySchemes("bearerAuth",
                new SecurityScheme()
                    .name("Authorization")
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .in(SecurityScheme.In.HEADER)))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
