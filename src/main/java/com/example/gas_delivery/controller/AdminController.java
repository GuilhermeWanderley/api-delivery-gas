package com.example.gas_delivery.controller;

import com.example.gas_delivery.entities.Caixa;
import com.example.gas_delivery.entities.Configuracao;
import com.example.gas_delivery.entities.Entregador;
import com.example.gas_delivery.entities.Pedido;
import com.example.gas_delivery.entities.Status;
import com.example.gas_delivery.repository.EntregadorRepository;
import com.example.gas_delivery.repository.PedidoRepository;
import com.example.gas_delivery.service.AdminService;
import com.example.gas_delivery.service.CaixaService;
import com.example.gas_delivery.service.ConfiguracaoService;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final PedidoRepository pedidoRepository;
    private final EntregadorRepository entregadorRepository;
    private final AdminService adminService;
    private final CaixaService caixaService;
    private final ConfiguracaoService configuracaoService;

    public AdminController(
            PedidoRepository pedidoRepository,
            EntregadorRepository entregadorRepository,
            AdminService adminService,
            CaixaService caixaService,
            ConfiguracaoService configuracaoService
    ) {
        this.pedidoRepository = pedidoRepository;
        this.entregadorRepository = entregadorRepository;
        this.adminService = adminService;
        this.caixaService = caixaService;
        this.configuracaoService = configuracaoService;
    }

    @GetMapping("/pedidos")
    public ResponseEntity<List<Pedido>> listarPedidos(@RequestParam(required = false) Status status) {
        // Ordem de chegada (mais antigo primeiro) — fila FIFO de atendimento
        Sort ordemDeChegada = Sort.by(Sort.Direction.ASC, "dataCriacao");
        List<Pedido> pedidos = status == null
                ? pedidoRepository.findAll(ordemDeChegada)
                : pedidoRepository.findByStatusOrderByDataCriacaoAsc(status);
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
        return ResponseEntity.ok(adminService.cancelarPedido(id));
    }

    @PutMapping("/pedidos/{id}/entregue")
    public ResponseEntity<Pedido> marcarEntregue(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.atualizarPedidoParaEntregue(id));
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

    // ===================== CAIXA (turno de atendimento) =====================

    /**
     * Estado do caixa agora. O painel usa isso para avisar o operador
     * quando ele tenta atender sem ter aberto o caixa.
     */
    @GetMapping("/caixa/atual")
    public ResponseEntity<Map<String, Object>> caixaAtual() {
        Caixa aberto = caixaService.resumoDoTurnoAtual();
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("aberto", aberto != null);
        resposta.put("caixa", aberto);
        return ResponseEntity.ok(resposta);
    }

    /** Abre um novo turno de caixa. */
    @PostMapping("/caixa/abrir")
    public ResponseEntity<Caixa> abrirCaixa(@RequestBody(required = false) Map<String, String> body) {
        String abertoPor = body == null ? null : body.get("abertoPor");
        return ResponseEntity.status(HttpStatus.CREATED).body(caixaService.abrir(abertoPor));
    }

    /** Fecha o turno e congela o relatório. */
    @PostMapping("/caixa/fechar")
    public ResponseEntity<Caixa> fecharCaixa(@RequestBody(required = false) Map<String, String> body) {
        String observacao = body == null ? null : body.get("observacao");
        return ResponseEntity.ok(caixaService.fechar(observacao));
    }

    /** Histórico de turnos, com os relatórios congelados. */
    @GetMapping("/caixa/historico")
    public ResponseEntity<List<Caixa>> historicoCaixas() {
        return ResponseEntity.ok(caixaService.historico());
    }

    // ===================== CONFIGURAÇÃO (preços) =====================

    /** Lê a configuração global de preços (cria a padrão se ainda não existir). */
    @GetMapping("/configuracao")
    public ResponseEntity<Configuracao> obterConfiguracao() {
        return ResponseEntity.ok(configuracaoService.obter());
    }

    /** Atualiza os preços e a promoção de quarta-feira pelo painel. */
    @PutMapping("/configuracao")
    public ResponseEntity<Configuracao> atualizarConfiguracao(@RequestBody Configuracao dados) {
        return ResponseEntity.ok(configuracaoService.atualizar(dados));
    }
}
