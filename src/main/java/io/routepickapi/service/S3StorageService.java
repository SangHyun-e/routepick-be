package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService {

    private static final int MAX_FILES = 30;
    private static final long MAX_FILE_SIZE = 15L * 1024 * 1024;

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
        if (bucketName == null || bucketName.isBlank()) {
            throw new CustomException(ErrorType.COMMON_INTERNAL, "S3 bucket 설정이 필요합니다.");
        }

        String folder = postId == null ? "temp" : String.valueOf(postId);
        List<UploadResult> results = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
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
}
