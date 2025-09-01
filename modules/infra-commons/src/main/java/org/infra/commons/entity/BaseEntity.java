package org.infra.commons.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;


/**
 * Base entity class that provides common auditing fields and optimistic locking.
 * Uses Spring Data JPA auditing annotations to automatically populate created and updated metadata.
 */


// Enables automatic population of audit fields via Spring Data's auditing infrastructure.
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@ToString
@MappedSuperclass
@SuperBuilder
@NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public abstract class BaseEntity {

    /**
     * Timestamp of entity creation.
     * Automatically set once during entity persist.
     * Stored as UTC Instant in the database.
     */
    @CreatedDate
    @Column(name = "created_date", nullable = false, updatable = false)
    Instant createdDate;

    /**
     * Timestamp of last entity update.
     * Automatically updated on each entity modification.
     * Stored as UTC Instant in the database.
     */
    @LastModifiedDate
    @Column(name = "updated_date", nullable = false)
    Instant updatedDate;

    /**
     * Identifier of the user who created the entity.
     * Automatically set once during entity persist.
     */
    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    String createdBy;

    /**
     * Identifier of the user who last modified the entity.
     * Automatically updated on each modification.
     */
    @LastModifiedBy
    @Column(name = "updated_by")
    String updatedBy;

    /**
     * Version field used for optimistic locking to prevent lost updates during concurrent modifications.
     * Managed automatically by JPA/Hibernate.
     */
    @Version
    @Column(name = "version", nullable = false)
    Long version;
}
