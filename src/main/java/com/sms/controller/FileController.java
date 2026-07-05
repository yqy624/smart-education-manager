package com.sms.controller;

import com.sms.dto.ApiResponse;
import com.sms.model.StoredFile;
import com.sms.model.StoredFileCategory;
import com.sms.model.User;
import com.sms.repository.UserRepository;
import com.sms.service.FileAccessService;
import com.sms.service.FileStorageService;
import com.sms.service.StoredFilePayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
@Tag(name = "Files", description = "附件上传、预览与下载接口")
@SecurityRequirement(name = "bearerAuth")
public class FileController {

    private final FileStorageService fileStorageService;
    private final FileAccessService fileAccessService;
    private final UserRepository userRepository;

    @Operation(summary = "上传附件", description = "上传作业相关附件，返回存储路径、原始文件名与文件大小。")
    @PostMapping("/upload")
    public ApiResponse<?> upload(
        @Parameter(description = "上传文件", required = true) @RequestParam("file") MultipartFile file,
        @Parameter(description = "文件分类，可选：SUBMISSION_ATTACHMENT / ASSIGNMENT_ATTACHMENT / TEMP_UPLOAD") @RequestParam(required = false) String category,
        @Parameter(hidden = true) Authentication auth
    ) {
        try {
            User currentUser = getCurrentUser(auth);
            StoredFileCategory resolvedCategory = fileAccessService.resolveCategory(category);
            String subDir = resolvedCategory == StoredFileCategory.ASSIGNMENT_ATTACHMENT ? "assignments" : "submissions";
            String path = fileStorageService.store(file, subDir, resolvedCategory, currentUser.getId(), null, null, null);
            StoredFile metadata = fileStorageService.getMetadata(path);
            return ApiResponse.ok("上传成功", Map.of(
                "path", path,
                "originalName", metadata != null ? metadata.getOriginalName() : (file.getOriginalFilename() == null ? "file" : file.getOriginalFilename()),
                "size", metadata != null && metadata.getSize() != null ? metadata.getSize() : file.getSize(),
                "fileId", metadata != null ? metadata.getId() : null,
                "contentType", metadata != null ? metadata.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "previewable", metadata != null && isPreviewable(metadata.getContentType())
            ));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @Operation(summary = "预览附件", description = "按存储路径预览附件，支持图片、PDF、文本等内联打开。")
    @GetMapping("/preview")
    public ResponseEntity<Resource> preview(
        @Parameter(description = "文件存储路径") @RequestParam String path,
        @Parameter(hidden = true) Authentication auth
    ) {
        User currentUser = getCurrentUser(auth);
        StoredFile metadata = fileStorageService.getMetadata(path);
        if (metadata != null) {
            fileAccessService.assertCanAccess(metadata, currentUser);
        }
        StoredFilePayload payload = fileStorageService.loadForPreview(path);
        return buildResponse(payload, true);
    }

    @Operation(summary = "下载附件", description = "按存储路径下载附件，可选传原始文件名用于兼容旧数据展示。")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "下载成功",
            content = @Content(mediaType = "application/octet-stream"))
    })
    @GetMapping("/download")
    public ResponseEntity<Resource> download(
        @Parameter(description = "文件存储路径") @RequestParam String path,
        @Parameter(description = "下载展示文件名，可选") @RequestParam(required = false) String name,
        @Parameter(hidden = true) Authentication auth
    ) {
        User currentUser = getCurrentUser(auth);
        StoredFile metadata = fileStorageService.getMetadata(path);
        if (metadata != null) {
            fileAccessService.assertCanAccess(metadata, currentUser);
        }
        StoredFilePayload payload = fileStorageService.loadForDownload(path);
        String fallbackName = (name == null || name.isBlank()) ? payload.originalName() : name;
        return buildResponse(new StoredFilePayload(payload.resource(), payload.contentType(), fallbackName, payload.size()), false);
    }

    private ResponseEntity<Resource> buildResponse(StoredFilePayload payload, boolean inline) {
        String originalName = payload.originalName() == null || payload.originalName().isBlank() ? "file" : payload.originalName();
        String encoded = URLEncoder.encode(originalName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(payload.contentType() == null || payload.contentType().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : payload.contentType()))
            .contentLength(payload.size())
            .header(HttpHeaders.CONTENT_DISPOSITION,
                (inline ? "inline" : "attachment") + "; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
            .body(payload.resource());
    }

    private User getCurrentUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
            .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    private boolean isPreviewable(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        return contentType.startsWith("image/")
            || "application/pdf".equalsIgnoreCase(contentType)
            || contentType.startsWith("text/");
    }
}
