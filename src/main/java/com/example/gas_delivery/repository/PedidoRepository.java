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

    /** Pedidos criados com o caixa fechado (órfãos) — adotados quando um novo caixa abre. */
    java.util.List<Pedido> findByCaixaIsNullAndStatusIn(java.util.Collection<Status> statuses);
    List<Pedido> findByStatus(Status status);

    /** Pedidos de um status, do mais novo para o mais antigo. */
    List<Pedido> findByStatusOrderByIdDesc(Status status);

    boolean existsByEntregadorId(Long entregadorId);

    /** Pedidos entregues dentro de um turno (para o fechamento de caixa). */
    List<Pedido> findByStatusAndDataAtualizacaoBetweenOrderByDataAtualizacaoAsc(
            Status status,
            LocalDateTime inicio,
            LocalDateTime fim
    );

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

    /** Usado para garantir idempotência das requisições vindas do robô. */
    Optional<Pedido> findByChaveIdempotencia(String chaveIdempotencia);

    /** Pedidos de um turno de caixa (kanban do turno atual), do mais novo para o mais antigo. */
    List<Pedido> findByCaixaIdOrderByIdDesc(Long caixaId);

    /** Pedidos de um turno de caixa filtrados por status, do mais novo para o mais antigo. */
    List<Pedido> findByCaixaIdAndStatusOrderByIdDesc(Long caixaId, Status status);
}
