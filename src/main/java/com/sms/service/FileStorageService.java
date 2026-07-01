package com.sms.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文件存储服务。
 * 【新增文件 - 模块4：作业附件上传/下载】
 *
 * 安全设计：
 *  1. 后缀白名单校验（application.yml 的 file.allowed-extensions），拒绝可执行/脚本类文件；
 *  2. 用 UUID 重命名存储，杜绝文件名注入、覆盖与中文乱码；原始文件名单独保存用于下载时还原；
 *  3. 存储路径基于规范化的根目录，下载时校验目标路径必须位于根目录内，防止路径穿越（../）；
 *  4. 按子目录（如 submissions）隔离不同业务的文件。
 */
@Slf4j
@Service
public class FileStorageService {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${file.allowed-extensions:pdf,doc,docx,txt,zip,jpg,jpeg,png}")
    private String allowedExtensionsRaw;

    private Path rootLocation;
    private Set<String> allowedExtensions;

    @PostConstruct
    public void init() {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.allowedExtensions = Arrays.stream(allowedExtensionsRaw.split(","))
                .map(String::trim).map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        try {
            Files.createDirectories(rootLocation);
            log.info("文件存储根目录: {}", rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("无法创建文件存储目录: " + rootLocation, e);
        }
    }

    // PLACEHOLDER_STORE
    /**
     * 存储一个上传文件到指定子目录。
     * @param file       上传文件
     * @param subDir     业务子目录（如 "submissions"）
     * @return 相对存储路径（形如 submissions/uuid.ext），存库用
     */
    public String store(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("上传文件为空");
        }
        String original = StringCleaner.clean(file.getOriginalFilename());
        String ext = getExtension(original);
        if (ext.isEmpty() || !allowedExtensions.contains(ext)) {
            throw new RuntimeException("不支持的文件类型，仅允许: " + allowedExtensions);
        }

        try {
            // 目标子目录，规范化后必须仍在根目录内
            Path targetDir = rootLocation.resolve(subDir).normalize();
            if (!targetDir.startsWith(rootLocation)) {
                throw new RuntimeException("非法的存储路径");
            }
            Files.createDirectories(targetDir);

            // UUID 重命名，保留扩展名
            String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Path targetFile = targetDir.resolve(storedName).normalize();

            try (var in = file.getInputStream()) {
                Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return subDir + "/" + storedName;
        } catch (IOException e) {
            throw new RuntimeException("文件存储失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按相对路径加载文件为可下载资源。
     * 会再次校验路径未越界（防止存库的路径被篡改）。
     */
    public Resource loadAsResource(String relativePath) {
        try {
            Path file = rootLocation.resolve(relativePath).normalize();
            if (!file.startsWith(rootLocation)) {
                throw new RuntimeException("非法的文件路径");
            }
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new RuntimeException("文件不存在或不可读: " + relativePath);
        } catch (MalformedURLException e) {
            throw new RuntimeException("文件路径错误: " + relativePath, e);
        }
    }

    /** 取小写扩展名（不含点），无扩展名返回空串 */
    private String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }

    /** 简单清理文件名中的路径分隔符，防注入 */
    static class StringCleaner {
        static String clean(String name) {
            if (name == null) return "file";
            return name.replaceAll("[\\\\/]", "_");
        }
    }
}
