# SmartEducationManager

智慧教育管理系统源码库，基于 Java 17、Spring Boot 3 和 Maven 构建。

## 仓库定位

这是项目的源码仓库，用于保存后端服务、页面模板、静态资源、配置示例和部署说明。

仓库主要用于：

- 维护 Java / Spring Boot 源码
- 记录开发、构建和部署方式
- 生成可运行的 JAR 包
- 从源码构建 `dist/` 目录中的 Windows 安装包/便携版

`dist/` 是本地构建出的发布目录，属于安装包版本，不作为源码内容提交到 Git。

## 项目结构

- `src/`：应用源码、页面模板和静态资源
- `docs/`：部署和维护说明
- `pom.xml`：Maven 项目配置
- `Dockerfile`：容器镜像构建文件
- `dist/`：本地生成的 Windows 发布包目录，已在 `.gitignore` 中忽略
- `target/`：Maven 构建产物，已在 `.gitignore` 中忽略

## 运行环境

- JDK 17
- Maven 3.8+
- MySQL

默认访问地址：`http://localhost:8080`

## 本地运行

1. 配置数据库连接和 JWT 密钥。
2. 执行：

```bash
mvn spring-boot:run
```

也可以先构建 JAR：

```bash
mvn clean package
java -jar target/education-manager-1.0.0.jar
```

## 关键配置

公开源码仓库中只应保留占位值，不要提交真实数据库密码、JWT 密钥和生产环境配置。

```yml
spring:
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/student_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:change-me}

jwt:
  secret: ${JWT_SECRET:ChangeThisJwtSecretBeforeProductionUse_AtLeast32CharsLong}
```

## 部署

部署说明见 `docs/deployment.md`。

Windows 安装包/便携版应由源码构建生成，并输出到本地 `dist/` 目录或作为 GitHub Releases 附件发布。

## License

本项目采用 MIT License，详见 `LICENSE`。
