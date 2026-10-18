package com.example.gas_delivery.repository;

import com.example.gas_delivery.entities.Caixa;
import com.example.gas_delivery.entities.StatusCaixa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CaixaRepository extends JpaRepository<Caixa, Long> {

    /** Caixa aberto no momento (só pode existir um). */
    Optional<Caixa> findFirstByStatusOrderByDataAberturaDesc(StatusCaixa status);

    /**
     * Histórico: todos os caixas, do mais recente para o mais antigo,
     * já trazendo o relatório congelado (evita LazyInitializationException).
     */
    @Query("select distinct c from Caixa c left join fetch c.resumo order by c.dataAbertura desc")
    List<Caixa> findAllComResumo();
}
