package com.oscargabriel.financeapp.domain.exceptions.responses;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonProperty("errors")
    private List<ErrorDetail> errors;
}
