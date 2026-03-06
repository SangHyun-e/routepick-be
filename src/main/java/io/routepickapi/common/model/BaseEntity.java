package io.routepickapi.common.model;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity extends BaseTimeEntity {

    @CreatedBy // INSERT 시 작성자 자동 세팅 (AuditorAware가 값 제공)
    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    @LastModifiedBy // INSERT / UPDATE 시 수정자 자동 세팅
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

}
