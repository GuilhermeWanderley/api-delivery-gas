package com.example.gas_delivery.controller;

import com.example.gas_delivery.dto.PedidoBotRequest;
import com.example.gas_delivery.entities.Pedido;
import com.example.gas_delivery.service.PedidoBotService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoBotController {

    private final PedidoBotService pedidoBotService;

    public PedidoBotController(PedidoBotService pedidoBotService) {
        this.pedidoBotService = pedidoBotService;
    }

    @PostMapping("/via-bot")
    public ResponseEntity<Pedido> criarPedidoViaBot(@Valid @RequestBody PedidoBotRequest request) {
        Pedido pedido = pedidoBotService.criarPedidoViaBot(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(pedido);
    }
}
