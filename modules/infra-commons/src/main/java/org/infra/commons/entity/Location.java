package org.infra.commons.entity;


import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@MappedSuperclass
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public abstract class Location extends BaseEntity {

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

    @Column(name = "state")
    String state;

    @Column(name = "country")
    String country;

    @Column(name = "pin_code", length = 10)
    String pinCode;
}
