package org.infra.commons.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.infra.commons.dto.pojo.ApiError;
import org.infra.commons.dto.pojo.PageMetadata;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ApiResponse<T> {
    private String reqMsgId;
    @Builder.Default
    private String respMsgId = UUID.randomUUID().toString();
    private T content;

    @Builder.Default
    private int httpStatusCode = HttpStatus.OK.value();

    @Builder.Default
    private String statusCode = String.valueOf(HttpStatus.OK.value());

    @Builder.Default
    private String statusMessage = "Success";

    private List<ApiError> errors;
    private PageMetadata pages;

    // Constructor for non-paginated responses
    public ApiResponse(String respMsgId, T content, int httpStatusCode, String statusMessage, List<ApiError> errors) {
        this.respMsgId = respMsgId;
        this.content = content;
        this.httpStatusCode = httpStatusCode;
        this.statusMessage = statusMessage;
        this.errors = errors;
    }
}