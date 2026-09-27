package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import java.util.LinkedHashMap;
import java.util.Map;

import com.oscargabriel.financeapp.domain.model.ServiceHealth;
import com.oscargabriel.financeapp.domain.model.SystemStatus;

public record StatusResponse(String status, Map<String, String> services) {

    public static StatusResponse from(SystemStatus systemStatus) {
        Map<String, String> services = new LinkedHashMap<>();
        for (ServiceHealth service : systemStatus.services()) {
            services.put(service.name(), service.status().name());
        }
        return new StatusResponse(systemStatus.status().name(), services);
    }
}
