package com.example.gas_delivery.controller;

import com.example.gas_delivery.dto.EntregadorRequestDTO;
import com.example.gas_delivery.entities.Entregador;
import com.example.gas_delivery.service.EntregadorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/entregadores")
public class EntregadorController {

    private final EntregadorService entregadorService;

    public EntregadorController(EntregadorService entregadorService) {
        this.entregadorService = entregadorService;
    }

    @PostMapping
    public ResponseEntity<Entregador> cadastrar(@Valid @RequestBody EntregadorRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(entregadorService.cadastrar(request));
    }

    @GetMapping
    public ResponseEntity<List<Entregador>> listar() {
        return ResponseEntity.ok(entregadorService.listarTodos());
    }

    @PutMapping("/{id}/ativar")
    public ResponseEntity<Entregador> ativar(@PathVariable Long id) {
        return ResponseEntity.ok(entregadorService.ativar(id));
    }

    @PutMapping("/{id}/desativar")
    public ResponseEntity<Entregador> desativar(@PathVariable Long id) {
        return ResponseEntity.ok(entregadorService.desativar(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        entregadorService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
