package com.sprint.mission.discodeit.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sprint.mission.discodeit.entity.Notification;
import com.sprint.mission.discodeit.entity.ReadStatus;
import com.sprint.mission.discodeit.entity.Role;
import com.sprint.mission.discodeit.entity.User;
import com.sprint.mission.discodeit.repository.NotificationRepository;
import com.sprint.mission.discodeit.repository.ReadStatusRepository;
import com.sprint.mission.discodeit.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationRequiredEventListenerTest {

  @Mock
  private ReadStatusRepository readStatusRepository;
  @Mock
  private NotificationRepository notificationRepository;
  @Mock
  private UserRepository userRepository;

  @InjectMocks
  private NotificationRequiredEventListener listener;

  @Captor
  private ArgumentCaptor<List<Notification>> notificationsCaptor;
  @Captor
  private ArgumentCaptor<Notification> notificationCaptor;

  private ReadStatus readStatusOf(UUID userId) {
    User user = mock(User.class);
    given(user.getId()).willReturn(userId);
    ReadStatus readStatus = mock(ReadStatus.class);
    given(readStatus.getUser()).willReturn(user);
    return readStatus;
  }

  private User adminOf(UUID userId) {
    User admin = mock(User.class);
    given(admin.getId()).willReturn(userId);
    return admin;
  }

  @Test
  @DisplayName("메시지 알림 - 작성자를 제외한 수신 대상에게 알림을 만든다")
  void onMessageCreated_excludesAuthor() {
    // given
    UUID channelId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    UUID receiverA = UUID.randomUUID();
    UUID receiverB = UUID.randomUUID();
    List<ReadStatus> readStatuses = List.of(
        readStatusOf(authorId), readStatusOf(receiverA), readStatusOf(receiverB));
    given(readStatusRepository.findAllByChannel_IdAndNotificationEnabledTrue(channelId))
        .willReturn(readStatuses);
    MessageCreatedEvent event = new MessageCreatedEvent(
        UUID.randomUUID(), channelId, "general", authorId, "홍길동", "안녕하세요");

    // when
    listener.on(event);

    // then
    verify(notificationRepository).saveAll(notificationsCaptor.capture());
    List<Notification> saved = notificationsCaptor.getValue();
    assertThat(saved).extracting(Notification::getReceiverId)
        .containsExactlyInAnyOrder(receiverA, receiverB);
    assertThat(saved).allSatisfy(n -> {
      assertThat(n.getTitle()).isEqualTo("홍길동 (#general)");
      assertThat(n.getContent()).isEqualTo("안녕하세요");
    });
  }

  @Test
  @DisplayName("메시지 알림 - 채널 이름이 없으면(DM) 제목에 DM을 쓴다")
  void onMessageCreated_privateChannelUsesDm() {
    // given
    UUID channelId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    UUID receiverId = UUID.randomUUID();
    List<ReadStatus> readStatuses = List.of(readStatusOf(receiverId));
    given(readStatusRepository.findAllByChannel_IdAndNotificationEnabledTrue(channelId))
        .willReturn(readStatuses);
    MessageCreatedEvent event = new MessageCreatedEvent(
        UUID.randomUUID(), channelId, null, authorId, "홍길동", "비밀 메시지");

    // when
    listener.on(event);

    // then
    verify(notificationRepository).saveAll(notificationsCaptor.capture());
    assertThat(notificationsCaptor.getValue()).hasSize(1);
    assertThat(notificationsCaptor.getValue().get(0).getTitle()).isEqualTo("홍길동 (#DM)");
  }

  @Test
  @DisplayName("권한 변경 알림 - 대상 사용자에게 변경 전후 권한을 담아 알림을 만든다")
  void onRoleUpdated_createsNotification() {
    // given
    UUID userId = UUID.randomUUID();
    RoleUpdatedEvent event = new RoleUpdatedEvent(userId, Role.USER, Role.CHANNEL_MANAGER);

    // when
    listener.on(event);

    // then
    verify(notificationRepository).save(notificationCaptor.capture());
    Notification saved = notificationCaptor.getValue();
    assertThat(saved.getReceiverId()).isEqualTo(userId);
    assertThat(saved.getTitle()).isEqualTo("권한이 변경되었습니다.");
    assertThat(saved.getContent()).isEqualTo("USER -> CHANNEL_MANAGER");
  }

  @Test
  @DisplayName("S3 업로드 실패 알림 - 모든 ADMIN에게 요청 정보를 담아 알림을 만든다")
  void onS3UploadFailed_notifiesAllAdmins() {
    // given
    UUID adminA = UUID.randomUUID();
    UUID adminB = UUID.randomUUID();
    List<User> admins = List.of(adminOf(adminA), adminOf(adminB));
    given(userRepository.findAllByRole(Role.ADMIN)).willReturn(admins);
    UUID binaryContentId = UUID.randomUUID();
    S3UploadFailedEvent event = new S3UploadFailedEvent(
        binaryContentId, "req-123", "access denied");

    // when
    listener.on(event);

    // then
    verify(notificationRepository).saveAll(notificationsCaptor.capture());
    List<Notification> saved = notificationsCaptor.getValue();
    assertThat(saved).extracting(Notification::getReceiverId)
        .containsExactlyInAnyOrder(adminA, adminB);
    assertThat(saved).allSatisfy(n -> {
      assertThat(n.getTitle()).isEqualTo("S3 파일 업로드 실패");
      assertThat(n.getContent()).contains("req-123")
          .contains(binaryContentId.toString())
          .contains("access denied");
    });
  }

  @Test
  @DisplayName("S3 업로드 실패 알림 - ADMIN이 없으면 빈 목록을 저장한다")
  void onS3UploadFailed_noAdmins() {
    // given
    given(userRepository.findAllByRole(Role.ADMIN)).willReturn(List.of());
    S3UploadFailedEvent event = new S3UploadFailedEvent(UUID.randomUUID(), "req-1", "error");

    // when
    listener.on(event);

    // then
    verify(notificationRepository).saveAll(notificationsCaptor.capture());
    assertThat(notificationsCaptor.getValue()).isEmpty();
  }
}