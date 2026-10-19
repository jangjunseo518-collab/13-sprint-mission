package com.sprint.mission.discodeit.storage.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sprint.mission.discodeit.event.S3UploadFailedEvent;
import com.sprint.mission.discodeit.storage.BinaryContentStorage;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@SpringJUnitConfig(S3BinaryContentStorageRetryTest.TestConfig.class)
class S3BinaryContentStorageRetryTest {

  // 설정 클래스(TestConfig)가 같은 mock을 쓰도록 static으로 둔다.
  private static final S3Client S3_CLIENT = mock(S3Client.class);
  private static final ApplicationEventPublisher EVENT_PUBLISHER =
      mock(ApplicationEventPublisher.class);

  /**
   * AWS에 연결하지 않도록 getS3Client()만 가짜 클라이언트로 바꾼 테스트용 저장소
   */
  static class TestableS3BinaryContentStorage extends S3BinaryContentStorage {

    private final S3Client s3Client;

    TestableS3BinaryContentStorage(ApplicationEventPublisher publisher, S3Client s3Client) {
      super("ap-northeast-2", "test-bucket", 600, publisher);
      this.s3Client = s3Client;
    }

    @Override
    public S3Client getS3Client() {
      return s3Client;
    }
  }

  @Configuration
  @EnableRetry(proxyTargetClass = true)
  static class TestConfig {

    @Bean
    S3BinaryContentStorage s3BinaryContentStorage() {
      return new TestableS3BinaryContentStorage(EVENT_PUBLISHER, S3_CLIENT);
    }
  }

  @Autowired
  private BinaryContentStorage storage;

  @BeforeEach
  void setUp() {
    reset(S3_CLIENT, EVENT_PUBLISHER);
  }

  @Test
  @DisplayName("업로드 성공 - 첫 시도에 성공하면 재시도하지 않고 실패 이벤트도 없다")
  void put_success_firstAttempt() {
    // given
    UUID id = UUID.randomUUID();
    when(S3_CLIENT.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());

    // when
    UUID result = storage.put(id, new byte[]{1, 2, 3});

    // then
    assertThat(result).isEqualTo(id);
    verify(S3_CLIENT, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    verifyNoInteractions(EVENT_PUBLISHER);
  }

  @Test
  @DisplayName("업로드 재시도 - 두 번 실패해도 세 번째에 성공하면 정상 반환하고 실패 이벤트는 없다")
  void put_success_afterRetries() {
    // given
    UUID id = UUID.randomUUID();
    when(S3_CLIENT.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenThrow(SdkClientException.create("일시적 오류 1"))
        .thenThrow(SdkClientException.create("일시적 오류 2"))
        .thenReturn(PutObjectResponse.builder().build());

    // when
    UUID result = storage.put(id, new byte[]{1, 2, 3});

    // then
    assertThat(result).isEqualTo(id);
    verify(S3_CLIENT, times(3)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    verifyNoInteractions(EVENT_PUBLISHER);
  }

  @Test
  @DisplayName("업로드 최종 실패 - 3번 시도 후 실패 이벤트를 발행하고 예외를 다시 던진다")
  void put_fail_afterMaxAttempts() {
    // given
    UUID id = UUID.randomUUID();
    when(S3_CLIENT.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenThrow(SdkClientException.create("업로드 실패"));

    // when & then - 예외가 호출한 쪽까지 전달되어야 BinaryContent를 FAIL로 바꿀 수 있다.
    assertThatThrownBy(() -> storage.put(id, new byte[]{1, 2, 3}))
        .isInstanceOf(SdkClientException.class);

    verify(S3_CLIENT, times(3)).putObject(any(PutObjectRequest.class), any(RequestBody.class));

    ArgumentCaptor<S3UploadFailedEvent> captor = ArgumentCaptor.forClass(S3UploadFailedEvent.class);
    verify(EVENT_PUBLISHER, times(1)).publishEvent(captor.capture());
    assertThat(captor.getValue().binaryContentId()).isEqualTo(id);
    assertThat(captor.getValue().errorMessage()).contains("업로드 실패");
  }
}