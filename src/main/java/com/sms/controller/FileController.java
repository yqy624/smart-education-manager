package com.sms.controller;

import com.sms.dto.ApiResponse;
import com.sms.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
@Tag(name = "Files", description = "附件上传与下载接口")
@SecurityRequirement(name = "bearerAuth")
public class FileController {

    private final FileStorageService fileStorageService;

    @Operation(summary = "上传附件", description = "上传作业相关附件，返回存储路径、原始文件名与文件大小。")
    @PostMapping("/upload")
    public ApiResponse<?> upload(@Parameter(description = "上传文件", required = true)
                                 @RequestParam("file") MultipartFile file) {
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

    @Operation(summary = "下载附件", description = "按存储路径下载附件，可选传原始文件名用于下载展示。")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "下载成功",
            content = @Content(mediaType = "application/octet-stream"))
    })
    @GetMapping("/download")
    public ResponseEntity<Resource> download(@Parameter(description = "文件存储路径") @RequestParam String path,
                                             @Parameter(description = "下载展示文件名，可选") @RequestParam(required = false) String name) {
        Resource resource = fileStorageService.loadAsResource(path);
        String downloadName = (name == null || name.isBlank())
            ? resource.getFilename() : name;
        String encoded = URLEncoder.encode(downloadName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
            .body(resource);
    }
}
