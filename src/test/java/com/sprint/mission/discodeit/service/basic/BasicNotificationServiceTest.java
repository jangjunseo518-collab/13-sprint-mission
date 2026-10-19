package com.sprint.mission.discodeit.service.basic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sprint.mission.discodeit.dto.response.NotificationDto;
import com.sprint.mission.discodeit.entity.Notification;
import com.sprint.mission.discodeit.exception.notification.NotificationAccessDeniedException;
import com.sprint.mission.discodeit.exception.notification.NotificationNotFoundException;
import com.sprint.mission.discodeit.mapper.NotificationMapper;
import com.sprint.mission.discodeit.repository.NotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BasicNotificationServiceTest {

  @Mock
  private NotificationRepository notificationRepository;
  @Mock
  private NotificationMapper notificationMapper;

  @InjectMocks
  private BasicNotificationService notificationService;

  @Test
  @DisplayName("알림 조회 성공 - 수신자의 알림 목록을 DTO로 반환한다")
  void findAllByReceiverId_success() {
    // given
    UUID receiverId = UUID.randomUUID();
    Notification notification = new Notification(receiverId, "제목", "내용");
    NotificationDto dto = new NotificationDto(
        UUID.randomUUID(), Instant.now(), receiverId, "제목", "내용");

    given(notificationRepository.findAllByReceiverIdOrderByCreatedAtDesc(receiverId))
        .willReturn(List.of(notification));
    given(notificationMapper.toDto(notification)).willReturn(dto);

    // when
    List<NotificationDto> result = notificationService.findAllByReceiverId(receiverId);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).title()).isEqualTo("제목");
    assertThat(result.get(0).receiverId()).isEqualTo(receiverId);
  }

  @Test
  @DisplayName("알림 삭제 성공 - 본인의 알림이면 삭제된다")
  void delete_success() {
    // given
    UUID notificationId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    Notification notification = new Notification(requesterId, "제목", "내용");
    given(notificationRepository.findById(notificationId)).willReturn(Optional.of(notification));

    // when
    notificationService.delete(notificationId, requesterId);

    // then
    verify(notificationRepository).delete(notification);
  }

  @Test
  @DisplayName("알림 삭제 실패 - 존재하지 않는 알림이면 NotificationNotFoundException이 발생한다")
  void delete_fail_notFound() {
    // given
    UUID notificationId = UUID.randomUUID();
    given(notificationRepository.findById(notificationId)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> notificationService.delete(notificationId, UUID.randomUUID()))
        .isInstanceOf(NotificationNotFoundException.class);

    verify(notificationRepository, never()).delete(any(Notification.class));
  }

  @Test
  @DisplayName("알림 삭제 실패 - 다른 사람의 알림이면 NotificationAccessDeniedException이 발생한다")
  void delete_fail_accessDenied() {
    // given
    UUID notificationId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();
    Notification notification = new Notification(ownerId, "제목", "내용");
    given(notificationRepository.findById(notificationId)).willReturn(Optional.of(notification));

    // when & then
    assertThatThrownBy(() -> notificationService.delete(notificationId, otherUserId))
        .isInstanceOf(NotificationAccessDeniedException.class);

    verify(notificationRepository, never()).delete(any(Notification.class));
  }
}