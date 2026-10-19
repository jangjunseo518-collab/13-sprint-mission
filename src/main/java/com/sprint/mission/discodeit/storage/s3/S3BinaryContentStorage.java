package com.sprint.mission.discodeit.storage.s3;

import com.sprint.mission.discodeit.config.MDCLoggingFilter;
import com.sprint.mission.discodeit.dto.response.BinaryContentDto;
import com.sprint.mission.discodeit.event.S3UploadFailedEvent;
import com.sprint.mission.discodeit.storage.BinaryContentStorage;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Slf4j
@Component
@ConditionalOnProperty(name = "discodeit.storage.type", havingValue = "s3")
public class S3BinaryContentStorage implements BinaryContentStorage {

  private final String region;
  private final String bucket;
  private final long presignedUrlExpiration;
  private final ApplicationEventPublisher eventPublisher;

  public S3BinaryContentStorage(
      @Value("${discodeit.storage.s3.region}") String region,
      @Value("${discodeit.storage.s3.bucket}") String bucket,
      @Value("${discodeit.storage.s3.presigned-url-expiration:600}") long presignedUrlExpiration,
      ApplicationEventPublisher eventPublisher) {
    this.region = region;
    this.bucket = bucket;
    this.presignedUrlExpiration = presignedUrlExpiration;
    this.eventPublisher = eventPublisher;
  }

  @Retryable(
      retryFor = SdkException.class,
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000, multiplier = 2)
  )
  @Override
  public UUID put(UUID id, byte[] bytes) {
    log.info("[S3 업로드 시도] binaryContentId: {}", id);
    getS3Client().putObject(
        PutObjectRequest.builder()
            .bucket(bucket)
            .key(id.toString())
            .build(),
        RequestBody.fromBytes(bytes)
    );
    return id;
  }

  @Recover
  public UUID recover(SdkException e, UUID id, byte[] bytes) {
    log.error("[S3 업로드 최종 실패] binaryContentId: {}", id, e);

    eventPublisher.publishEvent(
        new S3UploadFailedEvent(id, MDC.get(MDCLoggingFilter.REQUEST_ID), e.getMessage()));

    throw e;   // 반드시 다시 던져야 함
  }

  @Override
  public InputStream get(UUID id) {
    return getS3Client().getObject(
        GetObjectRequest.builder()
            .bucket(bucket)
            .key(id.toString())
            .build()
    );
  }

  @Override
  public ResponseEntity<Void> download(BinaryContentDto binaryContentDto) {
    String presignedUrl = generatePresignedUrl(
        binaryContentDto.id().toString(),
        binaryContentDto.contentType()
    );

    return ResponseEntity.status(HttpStatus.FOUND)
        .header(HttpHeaders.LOCATION, presignedUrl)
        .build();
  }

  public S3Client getS3Client() {
    return S3Client.builder()
        .region(Region.of(region))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }

  private String generatePresignedUrl(String key, String contentType) {
    try (S3Presigner presigner = S3Presigner.builder()
        .region(Region.of(region))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build()) {

      GetObjectRequest getObjectRequest = GetObjectRequest.builder()
          .bucket(bucket)
          .key(key)
          .responseContentType(contentType)
          .build();

      GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
          .signatureDuration(Duration.ofSeconds(presignedUrlExpiration))
          .getObjectRequest(getObjectRequest)
          .build();

      return presigner.presignGetObject(presignRequest).url().toString();
    }
  }
}