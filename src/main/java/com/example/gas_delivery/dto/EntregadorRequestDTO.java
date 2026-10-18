package com.example.gas_delivery.dto;

import jakarta.validation.constraints.NotBlank;

public record EntregadorRequestDTO(
        @NotBlank(message = "Nome é obrigatório.") String nome,
        @NotBlank(message = "Telefone é obrigatório.") String telefone
) {}
