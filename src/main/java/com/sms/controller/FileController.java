package com.sms.controller;

import com.sms.dto.ApiResponse;
import com.sms.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 文件上传/下载接口。
 * 【新增文件 - 模块4：作业附件上传/下载】
 *
 * 上传：POST /api/files/upload (multipart)  -> 返回 {path, originalName}
 *   前端拿到 path 后，连同作业内容一起调用 /api/student/assignments/{id}/submit-with-file
 *   （或把 path 存到表单再提交）。这里上传与业务解耦，便于复用。
 * 下载：GET /api/files/download?path=...&name=...  -> 以原文件名返回附件流
 *
 * 权限：所有登录用户可上传/下载（学生交附件、师生下载附件）。
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
public class FileController {

    private final FileStorageService fileStorageService;

    /** 上传附件，返回存储路径与原始文件名 */
    @PostMapping("/upload")
    public ApiResponse<?> upload(@RequestParam("file") MultipartFile file) {
        try {
            String path = fileStorageService.store(file, "submissions");
            return ApiResponse.ok("上传成功", Map.of(
                "path", path,
                "originalName", file.getOriginalFilename() == null ? "file" : file.getOriginalFilename(),
                "size", file.getSize()
            ));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 下载附件。path 为存库的相对路径，name 为展示给用户的原始文件名（可选） */
    @GetMapping("/download")
    public ResponseEntity<Resource> download(@RequestParam String path,
                                             @RequestParam(required = false) String name) {
        Resource resource = fileStorageService.loadAsResource(path);
        String downloadName = (name == null || name.isBlank())
            ? resource.getFilename() : name;
        // 用 RFC 5987 编码，兼容中文文件名
        String encoded = URLEncoder.encode(downloadName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
            .body(resource);
    }
}
