package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService {

    private static final int MAX_FILES = 30;
    private static final long MAX_FILE_SIZE = 15L * 1024 * 1024;
    private static final long MAX_TOTAL_SIZE = 100L * 1024 * 1024;
    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/webp",
        "image/gif"
    );
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        ".jpg",
        ".jpeg",
        ".png",
        ".webp",
        ".gif"
    );
    private static final String SUPPORTED_FORMAT_LABEL = "JPG, PNG, WEBP, GIF";
    private static final Pattern IMAGE_SRC_PATTERN = Pattern.compile("src\\s*=\\s*['\"]([^'\"]+)['\"]",
        Pattern.CASE_INSENSITIVE);

    private final S3Client s3Client;

    @Value("${aws.s3.bucket:}")
    private String bucketName;

    public List<UploadResult> uploadImages(List<MultipartFile> files, Long postId) {
        if (files == null || files.isEmpty()) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "업로드할 파일이 없습니다.");
        }
        if (files.size() > MAX_FILES) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT,
                "이미지는 최대 30개까지 업로드할 수 있습니다.");
        }
        long totalSize = files.stream()
            .filter(file -> file != null && !file.isEmpty())
            .mapToLong(MultipartFile::getSize)
            .sum();
        if (totalSize > MAX_TOTAL_SIZE) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT,
                "전체 이미지는 최대 100MB까지 업로드할 수 있습니다.");
        }
        if (bucketName == null || bucketName.isBlank()) {
            throw new CustomException(ErrorType.COMMON_INTERNAL, "S3 bucket 설정이 필요합니다.");
        }

        String folder = postId == null ? "temp" : String.valueOf(postId);
        List<UploadResult> results = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            if (!isSupportedFile(file)) {
                throw new CustomException(ErrorType.COMMON_INVALID_INPUT,
                    "지원하지 않는 이미지 형식입니다. (" + SUPPORTED_FORMAT_LABEL + ")");
            }
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new CustomException(ErrorType.COMMON_INVALID_INPUT,
                    "파일 크기는 최대 15MB까지 업로드할 수 있습니다.");
            }

            String extension = getExtension(file.getOriginalFilename(), file.getContentType());
            String key = String.format("posts/%s/%s%s", folder, UUID.randomUUID(), extension);

            try (InputStream inputStream = file.getInputStream()) {
                PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();
                s3Client.putObject(request, RequestBody.fromInputStream(inputStream, file.getSize()));
                String url = s3Client.utilities().getUrl(builder -> builder.bucket(bucketName).key(key))
                    .toExternalForm();
                results.add(new UploadResult(key, url, file.getSize()));
            } catch (IOException | S3Exception e) {
                log.error("S3 upload failed", e);
                throw new CustomException(ErrorType.COMMON_INTERNAL, "이미지 업로드에 실패했습니다.");
            }
        }
        return results;
    }

    public void deleteImage(String key) {
        String normalizedKey = normalizeKey(key);
        if (bucketName == null || bucketName.isBlank()) {
            throw new CustomException(ErrorType.COMMON_INTERNAL, "S3 bucket 설정이 필요합니다.");
        }

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(normalizedKey)
                .build());
        } catch (S3Exception e) {
            log.error("S3 delete failed: key={}", normalizedKey, e);
            throw new CustomException(ErrorType.COMMON_INTERNAL, "이미지 삭제에 실패했습니다.");
        }
    }

    public void deleteImagesFromContent(String content) {
        List<String> keys = extractKeysFromContent(content);
        if (keys.isEmpty()) {
            return;
        }
        for (String key : keys) {
            deleteImage(key);
        }
    }

    private String getExtension(String originalFilename, String contentType) {
        if (originalFilename != null) {
            int dot = originalFilename.lastIndexOf('.');
            if (dot >= 0 && dot < originalFilename.length() - 1) {
                return originalFilename.substring(dot).toLowerCase(Locale.ROOT);
            }
        }
        if (contentType != null) {
            if (contentType.contains("png")) return ".png";
            if (contentType.contains("webp")) return ".webp";
            if (contentType.contains("gif")) return ".gif";
        }
        return ".jpg";
    }

    public record UploadResult(String key, String url, long size) {

    }

    private boolean isSupportedFile(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null && SUPPORTED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            return true;
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            return false;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return false;
        }
        String ext = filename.substring(dot).toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.contains(ext);
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "삭제할 이미지 키가 필요합니다.");
        }
        String normalized = key.trim();
        if (!normalized.startsWith("posts/")) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "삭제할 이미지 키가 올바르지 않습니다.");
        }
        return normalized;
    }

    private List<String> extractKeysFromContent(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        Set<String> keys = new LinkedHashSet<>();
        Matcher matcher = IMAGE_SRC_PATTERN.matcher(content);
        while (matcher.find()) {
            String url = matcher.group(1);
            String key = extractKeyFromUrl(url);
            if (key != null && key.startsWith("posts/")) {
                keys.add(key);
            }
        }
        return new ArrayList<>(keys);
    }

    private String extractKeyFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || bucketName == null || bucketName.isBlank()) {
                return null;
            }
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return null;
            }

            String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
            String bucketPrefix = bucketName + "/";

            if (host.startsWith(bucketName + ".s3.") || host.startsWith(bucketName + ".s3-")) {
                return normalizedPath;
            }
            if ((host.startsWith("s3.") || host.startsWith("s3-")) && normalizedPath.startsWith(
                bucketPrefix)) {
                return normalizedPath.substring(bucketPrefix.length());
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
        return null;
    }
}
