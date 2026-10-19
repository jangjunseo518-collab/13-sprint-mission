package com.sprint.mission.discodeit.entity;

import com.sprint.mission.discodeit.entity.base.BaseUpdatableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

@Entity
@Table(name = "read_statuses")
@Getter
public class ReadStatus extends BaseUpdatableEntity {

  @ManyToOne
  @JoinColumn(name = "user_id")
  private User user;

  @ManyToOne
  @JoinColumn(name = "channel_id")
  private Channel channel;
  private Instant lastReadAt;

  public ReadStatus() {
  }

  @Column(nullable = false)
  private boolean notificationEnabled;

  public ReadStatus(User user, Channel channel, Instant lastReadAt, boolean notificationEnabled) {
    this.user = user;
    this.channel = channel;
    this.lastReadAt = lastReadAt;
    this.notificationEnabled = notificationEnabled;
  }

  public void updateLastReadAt(Instant lastReadAt) {
    this.lastReadAt = lastReadAt;
  }

  public void updateNotificationEnabled(boolean notificationEnabled) {
    this.notificationEnabled = notificationEnabled;
  }

}
