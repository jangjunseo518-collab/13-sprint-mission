package com.sprint.mission.discodeit.entity.base;

import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

@MappedSuperclass
@Getter
public abstract class BaseUpdatableEntity extends BaseEntity {

  @LastModifiedDate//객체 수정시 자동으로 시간 할당
  private Instant updatedAt;

}
