package com.example.gas_delivery.repository;

import com.example.gas_delivery.entities.StatusTransbordo;
import com.example.gas_delivery.entities.Transbordo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TransbordoRepository extends JpaRepository<Transbordo, Long> {

    List<Transbordo> findByStatusOrderByDataCriacaoAsc(StatusTransbordo status);

    long countByStatus(StatusTransbordo status);

    /** Evita duplicar o alerta se a mesma pessoa pedir várias vezes em sequência. */
    boolean existsByTelefoneAndStatusAndDataCriacaoAfter(
            String telefone,
            StatusTransbordo status,
            LocalDateTime limite
    );
}
