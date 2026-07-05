package com.sms.service;

import com.sms.model.StoredFile;
import com.sms.model.StoredFileCategory;
import com.sms.repository.StoredFileRepository;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final MinioClient minioClient;
    private final StoredFileRepository storedFileRepository;

    @Value("${file.legacy-upload-dir:./uploads}")
    private String legacyUploadDir;

    @Value("${file.allowed-extensions:pdf,doc,docx,txt,zip,jpg,jpeg,png}")
    private String allowedExtensionsRaw;

    @Value("${storage.minio.bucket}")
    private String bucketName;

    @Value("${storage.minio.auto-create-bucket:true}")
    private boolean autoCreateBucket;

    private Path legacyRootLocation;
    private Set<String> allowedExtensions;

    @PostConstruct
    public void init() {
        this.legacyRootLocation = Paths.get(legacyUploadDir).toAbsolutePath().normalize();
        this.allowedExtensions = Arrays.stream(allowedExtensionsRaw.split(","))
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toSet());

        try {
            Files.createDirectories(legacyRootLocation);
            ensureBucket();
            log.info("MinIO bucket ready: {}", bucketName);
            log.info("历史本地附件目录: {}", legacyRootLocation);
        } catch (Exception e) {
            throw new RuntimeException("初始化文件存储失败", e);
        }
    }

    public String store(MultipartFile file, String subDir) {
        return store(file, subDir, StoredFileCategory.TEMP_UPLOAD, null, null, null, null);
    }

    public String store(
        MultipartFile file,
        String subDir,
        StoredFileCategory category,
        Long uploaderUserId,
        Long courseId,
        Long assignmentId,
        Long submissionId
    ) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("上传文件为空");
        }

        String originalName = StringCleaner.clean(file.getOriginalFilename());
        String ext = getExtension(originalName);
        if (ext.isEmpty() || !allowedExtensions.contains(ext)) {
            throw new RuntimeException("不支持的文件类型，仅允许: " + allowedExtensions);
        }

        String objectKey = normalizeSubDir(subDir) + "/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        String contentType = detectContentType(file);

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(contentType)
                    .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("文件上传到对象存储失败: " + e.getMessage(), e);
        }

        StoredFile metadata = StoredFile.builder()
            .storagePath(objectKey)
            .bucket(bucketName)
            .objectKey(objectKey)
            .originalName(originalName.isBlank() ? "file." + ext : originalName)
            .contentType(contentType)
            .size(file.getSize())
            .extension(ext)
            .uploaderUserId(uploaderUserId)
            .category(category == null ? StoredFileCategory.TEMP_UPLOAD : category)
            .courseId(courseId)
            .assignmentId(assignmentId)
            .submissionId(submissionId)
            .build();
        storedFileRepository.save(metadata);
        return objectKey;
    }

    public StoredFilePayload loadForDownload(String storagePath) {
        return loadPayload(storagePath);
    }

    public StoredFilePayload loadForPreview(String storagePath) {
        return loadPayload(storagePath);
    }

    public StoredFile getMetadata(String storagePath) {
        return storedFileRepository.findByStoragePath(storagePath).orElse(null);
    }

    public void bindStoredFile(String storagePath, StoredFileCategory category, Long courseId, Long assignmentId, Long submissionId) {
        storedFileRepository.findByStoragePath(storagePath).ifPresent(file -> {
            file.setCategory(category);
            file.setCourseId(courseId);
            file.setAssignmentId(assignmentId);
            file.setSubmissionId(submissionId);
            storedFileRepository.save(file);
        });
    }

    private StoredFilePayload loadPayload(String storagePath) {
        StoredFile metadata = storedFileRepository.findByStoragePath(storagePath).orElse(null);
        if (metadata != null) {
            try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(metadata.getBucket())
                    .object(metadata.getObjectKey())
                    .build()
            )) {
                byte[] bytes = stream.readAllBytes();
                Resource resource = new ByteArrayResource(bytes);
                return new StoredFilePayload(
                    resource,
                    metadata.getContentType() == null || metadata.getContentType().isBlank() ? "application/octet-stream" : metadata.getContentType(),
                    metadata.getOriginalName(),
                    metadata.getSize() == null ? bytes.length : metadata.getSize()
                );
            } catch (Exception e) {
                throw new RuntimeException("读取对象存储文件失败: " + e.getMessage(), e);
            }
        }

        return loadLegacyPayload(storagePath);
    }

    private StoredFilePayload loadLegacyPayload(String storagePath) {
        try {
            Path file = legacyRootLocation.resolve(storagePath).normalize();
            if (!file.startsWith(legacyRootLocation)) {
                throw new RuntimeException("非法的文件路径");
            }
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new RuntimeException("文件不存在或不可读: " + storagePath);
            }
            String contentType = Files.probeContentType(file);
            return new StoredFilePayload(
                resource,
                contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType,
                file.getFileName().toString(),
                Files.size(file)
            );
        } catch (MalformedURLException e) {
            throw new RuntimeException("文件路径错误: " + storagePath, e);
        } catch (IOException e) {
            throw new RuntimeException("读取历史本地文件失败: " + storagePath, e);
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!exists) {
            if (!autoCreateBucket) {
                throw new RuntimeException("MinIO bucket 不存在: " + bucketName);
            }
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    private String detectContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            return contentType;
        }
        String ext = getExtension(file.getOriginalFilename());
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "txt" -> "text/plain";
            default -> "application/octet-stream";
        };
    }

    private String normalizeSubDir(String subDir) {
        String normalized = (subDir == null || subDir.isBlank()) ? "uploads" : subDir.trim().replace('\\', '/');
        normalized = normalized.replaceAll("^/+", "").replaceAll("/+$", "");
        if (normalized.contains("..")) {
            throw new RuntimeException("非法的存储路径");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String getExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    static class StringCleaner {
        static String clean(String name) {
            if (name == null || name.isBlank()) {
                return "file";
            }
            return name.replaceAll("[\\\\/]", "_");
        }
    }
}
