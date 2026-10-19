package com.sprint.mission.discodeit.event;

import com.sprint.mission.discodeit.entity.BinaryContentStatus;
import com.sprint.mission.discodeit.service.BinaryContentService;
import com.sprint.mission.discodeit.storage.BinaryContentStorage;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class BinaryContentEventListener {

  private final BinaryContentStorage binaryContentStorage;
  private final BinaryContentService binaryContentService;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(BinaryContentCreatedEvent event) {
    UUID id = event.binaryContentId();
    log.info("파일 저장 시작 - id: {}", id);

    try {
      binaryContentStorage.put(id, event.bytes());
    } catch (Exception e) {
      log.error("파일 저장 실패 - id: {}", id, e);
      binaryContentService.updateStatus(id, BinaryContentStatus.FAIL);
      return;
    }

    binaryContentService.updateStatus(id, BinaryContentStatus.SUCCESS);
    log.info("파일 저장 완료 - id: {}", id);
  }
}
