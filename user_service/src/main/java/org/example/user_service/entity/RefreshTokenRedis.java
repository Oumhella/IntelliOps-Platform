package org.example.user_service.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.io.Serializable;
@Getter
@Setter
@RedisHash("RefreshToken")
public class RefreshTokenRedis implements Serializable {

    @Id
    private String id;

    private Long userId;

    @TimeToLive
    private Long timeToLiveInSeconds;

    public RefreshTokenRedis() {}

    public RefreshTokenRedis(String id, Long userId, Long timeToLiveInSeconds) {
        this.id = id;
        this.userId = userId;
        this.timeToLiveInSeconds = timeToLiveInSeconds;
    }
}
