package com.example.gas_delivery.controller;

import com.example.gas_delivery.entities.Entregador;
import com.example.gas_delivery.entities.Pedido;
import com.example.gas_delivery.entities.Status;
import com.example.gas_delivery.repository.EntregadorRepository;
import com.example.gas_delivery.repository.PedidoRepository;
import com.example.gas_delivery.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final PedidoRepository pedidoRepository;
    private final EntregadorRepository entregadorRepository;
    private final AdminService adminService;

    public AdminController(
            PedidoRepository pedidoRepository,
            EntregadorRepository entregadorRepository,
            AdminService adminService
    ) {
        this.pedidoRepository = pedidoRepository;
        this.entregadorRepository = entregadorRepository;
        this.adminService = adminService;
    }

    @GetMapping("/pedidos")
    public ResponseEntity<List<Pedido>> listarPedidos(@RequestParam(required = false) Status status) {
        List<Pedido> pedidos = status == null ? pedidoRepository.findAll() : pedidoRepository.findByStatus(status);
        return ResponseEntity.ok(pedidos);
    }

    @PutMapping("/pedidos/{id}/despachar")
    public ResponseEntity<Pedido> despacharPedido(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body
    ) {
        Long entregadorId = body.get("entregadorId");
        if (entregadorId == null) {
            throw new IllegalArgumentException("Campo entregadorId é obrigatório.");
        }
        Pedido pedido = adminService.despacharPedido(id, entregadorId);
        return ResponseEntity.ok(pedido);
    }

    @PutMapping("/pedidos/{id}/cancelar")
    public ResponseEntity<Pedido> cancelarPedido(@PathVariable Long id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado: " + id));
        pedido.setStatus(Status.CANCELADO);
        Pedido salvo = pedidoRepository.save(pedido);
        return ResponseEntity.ok(salvo);
    }

    @PutMapping("/pedidos/{id}/aprovar-cpf")
    public ResponseEntity<Pedido> aprovarCpf(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.aprovarCpfGasDoPovo(id));
    }

    @PutMapping("/pedidos/{id}/reprovar-cpf")
    public ResponseEntity<Pedido> reprovarCpf(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.reprovarCpfGasDoPovo(id));
    }

    @GetMapping("/entregadores")
    public ResponseEntity<List<Entregador>> listarEntregadoresAtivos() {
        return ResponseEntity.ok(entregadorRepository.findByAtivoTrue());
    }

    @GetMapping("/entregadores/caixa")
    public ResponseEntity<Map<Long, BigDecimal>> caixaDoDiaPorEntregador() {
        return ResponseEntity.ok(adminService.calcularCaixaDoDiaPorEntregador());
    }
}
