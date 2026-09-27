package com.oscargabriel.financeapp.domain.exceptions.responses;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorDetail implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonProperty("code")
    private String code;

    @JsonProperty("description")
    private String description;

    @JsonProperty("field")
    private String field;

    public static ErrorDetail of(String code, String description, String field) {
        return new ErrorDetail(code, description, field);
    }
}
