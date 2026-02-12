package io.routepickapi.controller;

import io.routepickapi.service.S3StorageService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/uploads")
@Validated
public class UploadController {

    private final S3StorageService storageService;

    @Operation(summary = "이미지 업로드", description = "S3에 이미지 업로드")
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ImageUploadResponse> uploadImages(
        @RequestParam("files") List<MultipartFile> files,
        @RequestParam(value = "postId", required = false) @Min(1) Long postId
    ) {
        List<S3StorageService.UploadResult> uploaded = storageService.uploadImages(files, postId);
        List<ImageInfo> images = uploaded.stream()
            .map(result -> new ImageInfo(result.key(), result.url(), result.size()))
            .toList();
        return ResponseEntity.ok(new ImageUploadResponse(images));
    }

    @Operation(summary = "이미지 삭제", description = "S3 이미지 삭제")
    @DeleteMapping("/images")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteImage(@RequestParam("key") String key) {
        storageService.deleteImage(key);
        return ResponseEntity.noContent().build();
    }

    public record ImageUploadResponse(List<ImageInfo> images) {

    }

    public record ImageInfo(String key, String url, long size) {

    }
}
