package com.sprint.mission.discodeit.event;

import com.sprint.mission.discodeit.entity.Notification;
import com.sprint.mission.discodeit.entity.Role;
import com.sprint.mission.discodeit.repository.NotificationRepository;
import com.sprint.mission.discodeit.repository.ReadStatusRepository;
import com.sprint.mission.discodeit.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRequiredEventListener {

  private final ReadStatusRepository readStatusRepository;
  private final NotificationRepository notificationRepository;
  private final UserRepository userRepository;

  @Async
  @TransactionalEventListener
  @CacheEvict(value = "notifications", allEntries = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void on(MessageCreatedEvent event) {
    String channelName = event.channelName() != null ? event.channelName() : "DM";
    String title = event.authorName() + " (#" + channelName + ")";

    List<Notification> notifications = readStatusRepository
        .findAllByChannel_IdAndNotificationEnabledTrue(event.channelId())
        .stream()
        .map(rs -> rs.getUser().getId())
        .filter(receiverId -> !receiverId.equals(event.authorId()))   // 작성자 제외
        .map(receiverId -> new Notification(receiverId, title, event.content()))
        .toList();

    notificationRepository.saveAll(notifications);
    log.info("[메시지 알림 생성] messageId: {}, 수신자 수: {}",
        event.messageId(), notifications.size());
  }

  @Async
  @TransactionalEventListener
  @CacheEvict(value = "notifications", allEntries = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void on(RoleUpdatedEvent event) {
    String content = event.oldRole() + " -> " + event.newRole();

    notificationRepository.save(
        new Notification(event.userId(), "권한이 변경되었습니다.", content));
    log.info("[권한 변경 알림 생성] userId: {}", event.userId());
  }

  @CacheEvict(value = "notifications", allEntries = true)
  @EventListener
  public void on(S3UploadFailedEvent event) {
    String content = "RequestId: " + event.requestId()
        + "\nBinaryContentId: " + event.binaryContentId()
        + "\nError: " + event.errorMessage();

    List<Notification> notifications = userRepository.findAllByRole(Role.ADMIN).stream()
        .map(admin -> new Notification(admin.getId(), "S3 파일 업로드 실패", content))
        .toList();

    notificationRepository.saveAll(notifications);
    log.info("[S3 업로드 실패 알림 생성] binaryContentId: {}, 수신자 수: {}",
        event.binaryContentId(), notifications.size());
  }
}
