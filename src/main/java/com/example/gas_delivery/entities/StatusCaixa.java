package com.example.gas_delivery.entities;

/**
 * Situação de um caixa (turno de atendimento).
 * O operador precisa abrir o caixa antes de atender os pedidos e fecha no fim do turno.
 */
public enum StatusCaixa {
    ABERTO,
    FECHADO
}
