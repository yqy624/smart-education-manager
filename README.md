# SmartEducationManager Dist

智慧教育管理系统的 Windows 便携发布包仓库，内置 Java 运行时，下载后可直接启动。

## 仓库定位

这是一个发布包仓库，不是完整的 Java 源码仓库。

仓库主要用于：

- 存放可直接运行的 Windows 分发版本
- 提供启动脚本、运行配置和使用说明
- 方便下载、部署、演示和备份

如果你想在 GitHub 上展示源码、开发过程和模块设计，建议后续再单独维护一个源码仓库。

## 包含内容

- `education-manager.jar`：系统主程序
- `runtime/`：内置 Java 运行时，无需用户额外安装 JDK
- `config/application.yml`：运行配置文件
- `bin/`：PowerShell 辅助脚本
- `start.bat` / `stop.bat`：启动与停止入口
- `README.txt`：面向普通 Windows 用户的本地说明

更详细的目录说明见 `PROJECT_STRUCTURE.md`。

## 运行环境

- Windows 10 / 11
- 本机或局域网可访问的 MySQL
- 默认访问地址：`http://localhost:8080`

## 快速开始

1. 打开 `config/application.yml`。
2. 按实际环境修改数据库连接和 JWT 密钥。
3. 双击 `start.bat` 或 `启动系统.bat`。
4. 在浏览器中访问 `http://localhost:8080`。
5. 停止时双击 `stop.bat` 或 `停止系统.bat`。

## 关键配置

提交到公开仓库时，配置文件中应只保留占位值，不要保留真实凭据。

```yml
spring:
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/student_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:change-me}

jwt:
  secret: ${JWT_SECRET:ChangeThisJwtSecretBeforeProductionUse_AtLeast32CharsLong}
```

## 常用操作

- 启动系统：`start.bat`
- 停止系统：`stop.bat`
- 检查状态：`检查状态.bat`
- 打包发布：`打包发布.bat`
- 创建桌面快捷方式：`安装到桌面.bat`

## 仓库维护建议

- 保留 `logs/` 和 `uploads/` 在 `.gitignore` 中
- 不要提交真实数据库密码、JWT 密钥和生产环境配置
- 当前仓库包含较大的二进制文件，适合做发布包仓库
- 如果后续版本继续增大，建议考虑 GitHub Releases 或 Git LFS

## 适合当前仓库的管理方式

推荐做法：

- 当前仓库继续保留为 `dist` 仓库
- 后续如有源码，再单独建立 `smart-education-manager` 源码仓库
- 发布包通过源码仓库打包后同步到当前仓库，或发布到 Releases

## License

本项目采用 MIT License，详见 `LICENSE`。
