package org.infra.commons.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.MappedSuperclass;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

/**
 * Embeddable address details for reuse across entities.
 */
@Embeddable
@Getter @Setter
@ToString
@MappedSuperclass
@SuperBuilder
@NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Address extends BaseEntity {
    @Column(name = "line1", nullable = false)
    String line1;

    @Column(name = "line2")
    String line2;

    @Column(name = "line3")
    String line3;

    @Column(name = "landmark")
    String landmark;

    @Column(name = "city", nullable = false)
    String city;

    @Column(name = "district")
    String district;

    @Column(name = "state", length = 120)
    String state;

    @Column(name = "country", length = 120)
    String country;

    @Column(name = "pin_code", length = 10)
    String pinCode;
}
