package com.example.gas_delivery.repository;

import com.example.gas_delivery.entities.Entregador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EntregadorRepository extends JpaRepository<Entregador, Long> {
    List<Entregador> findByAtivoTrue();

    Optional<Entregador> findByTelefone(String telefone);
}
