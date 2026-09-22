package com.example.gas_delivery.repository;

import com.example.gas_delivery.entities.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findByTelefone(String telefone);
}
