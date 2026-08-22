package org.example.delivery_service.dto.request;

import jakarta.validation.constraints.NotNull;

public record AssignCourierRequest(@NotNull Long livreurId) {
}
