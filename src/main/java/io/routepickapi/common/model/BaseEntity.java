package io.routepickapi.common.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

@Getter
@MappedSuperclass
public abstract class BaseEntity extends BaseTimeEntity {

    @CreatedBy // INSERT 시 작성자 자동 세팅 (AuditorAware가 값 제공)
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @LastModifiedBy // INSERT / UPDATE 시 수정자 자동 세팅
    @Column(name = "updated_by")
    private String updatedBy;

}
