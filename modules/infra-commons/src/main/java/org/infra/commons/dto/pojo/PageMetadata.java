package org.infra.commons.dto.pojo;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PageMetadata {
    Long totalElements;
    Integer totalPages;
    Integer pageNumber;
    Integer pageSize;
}