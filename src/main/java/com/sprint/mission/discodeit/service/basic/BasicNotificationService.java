package com.sprint.mission.discodeit.service.basic;

import com.sprint.mission.discodeit.dto.response.NotificationDto;
import com.sprint.mission.discodeit.entity.Notification;
import com.sprint.mission.discodeit.exception.notification.NotificationAccessDeniedException;
import com.sprint.mission.discodeit.exception.notification.NotificationNotFoundException;
import com.sprint.mission.discodeit.mapper.NotificationMapper;
import com.sprint.mission.discodeit.repository.NotificationRepository;
import com.sprint.mission.discodeit.service.NotificationService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BasicNotificationService implements NotificationService {

  private final NotificationRepository notificationRepository;
  private final NotificationMapper notificationMapper;

  @Cacheable(value = "notifications", key = "#receiverId")
  @Override
  @Transactional(readOnly = true)
  public List<NotificationDto> findAllByReceiverId(UUID receiverId) {
    return notificationRepository.findAllByReceiverIdOrderByCreatedAtDesc(receiverId)
        .stream()
        .map(notificationMapper::toDto)
        .toList();
  }

  @Override
  @CacheEvict(value = "notifications", key = "#requesterId")
  @Transactional
  public void delete(UUID notificationId, UUID requesterId) {
    Notification notification = notificationRepository.findById(notificationId)
        .orElseThrow(() -> new NotificationNotFoundException(notificationId));

    if (!notification.getReceiverId().equals(requesterId)) {
      throw new NotificationAccessDeniedException(notificationId);
    }

    notificationRepository.delete(notification);
  }
}
