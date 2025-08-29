package io.routepickapi.common.model;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class) // @CreatedDate / @LastModifiedDate 값을 자동으로 채우는 리스너 연결
public abstract class BaseTimeEntity {

    @CreatedDate // 처음 INSERT 시, 자동 세팅
    @Column(name = "created_at", updatable = false, nullable = false) // 업데이트 시 값 변경 금지, NULL 허용X
    private LocalDateTime createdAt;

    @LastModifiedDate // INSERT / UPDATE 시, 자동 세팅
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
