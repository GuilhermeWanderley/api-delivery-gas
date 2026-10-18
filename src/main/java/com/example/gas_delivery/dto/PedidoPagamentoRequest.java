package com.example.gas_delivery.dto;

import com.example.gas_delivery.entities.StatusPagamento;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Corpo de {@code PUT /api/admin/pedidos/{id}/pagamento}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PedidoPagamentoRequest {

    private StatusPagamento status;
}
