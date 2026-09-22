package com.example.gas_delivery.repository;

import com.example.gas_delivery.entities.FormaPagamento;
import com.example.gas_delivery.entities.Pedido;
import com.example.gas_delivery.entities.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    List<Pedido> findByStatus(Status status);

    List<Pedido> findByEntregadorIdAndStatusAndDataAtualizacaoBetween(
            Long entregadorId,
            Status status,
            LocalDateTime inicio,
            LocalDateTime fim
    );

    @Query("""
            select coalesce(sum(p.valorTotal), 0)
            from Pedido p
            where p.entregador.id = :entregadorId
              and p.status = com.example.gas_delivery.entities.Status.ENTREGUE
              and p.formaPagamento in :formas
              and p.dataAtualizacao between :inicio and :fim
            """)
    BigDecimal somarCaixaDoDiaPorEntregador(
            @Param("entregadorId") Long entregadorId,
            @Param("formas") List<FormaPagamento> formas,
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );

    @Query("""
            select count(p) > 0
            from Pedido p
            where p.cliente.cpf = :cpf
              and p.tipoPedido = com.example.gas_delivery.entities.TipoPedido.GAS_DO_POVO
              and p.status = com.example.gas_delivery.entities.Status.ENTREGUE
              and p.dataCriacao >= :limite
            """)
    boolean existsPedidoGasDoPovoEntregueNoPeriodo(
            @Param("cpf") String cpf,
            @Param("limite") LocalDateTime limite
    );

    Optional<Pedido> findFirstByClienteTelefoneAndStatusInOrderByDataCriacaoDesc(
            String telefone,
            List<Status> status
    );
}
