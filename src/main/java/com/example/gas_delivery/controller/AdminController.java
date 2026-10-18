package com.example.gas_delivery.controller;

import com.example.gas_delivery.dto.PedidoBotRequest;
import com.example.gas_delivery.dto.PedidoPagamentoRequest;
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
import com.example.gas_delivery.service.PedidoService;
import jakarta.validation.Valid;
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
    private final PedidoService pedidoService;

    public AdminController(
            PedidoRepository pedidoRepository,
            EntregadorRepository entregadorRepository,
            AdminService adminService,
            CaixaService caixaService,
            ConfiguracaoService configuracaoService,
            PedidoService pedidoService
    ) {
        this.pedidoRepository = pedidoRepository;
        this.entregadorRepository = entregadorRepository;
        this.adminService = adminService;
        this.caixaService = caixaService;
        this.configuracaoService = configuracaoService;
        this.pedidoService = pedidoService;
    }

    @GetMapping("/pedidos")
    public ResponseEntity<List<Pedido>> listarPedidos(
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) Boolean somenteTurnoAtual
    ) {
        // Kanban do turno atual: somente os pedidos do caixa ABERTO, ligados por
        // FK. Sem caixa aberto, o kanban fica vazio (não mostra pedidos antigos).
        if (Boolean.TRUE.equals(somenteTurnoAtual)) {
            Caixa caixaAberto = caixaService.caixaAberto();
            if (caixaAberto == null) {
                return ResponseEntity.ok(List.of());
            }
            List<Pedido> pedidos = status == null
                    ? pedidoRepository.findByCaixaIdOrderByIdDesc(caixaAberto.getId())
                    : pedidoRepository.findByCaixaIdAndStatusOrderByIdDesc(caixaAberto.getId(), status);
            return ResponseEntity.ok(pedidos);
        }

        // Pedido mais novo primeiro — o operador vê o que acabou de chegar no topo
        Sort ordemDeChegada = Sort.by(Sort.Direction.DESC, "id");
        List<Pedido> pedidos = status == null
                ? pedidoRepository.findAll(ordemDeChegada)
                : pedidoRepository.findByStatusOrderByIdDesc(status);
        return ResponseEntity.ok(pedidos);
    }

    /**
     * Lança um pedido manualmente (cliente idoso por telefone). Usa o mesmo
     * payload do robô e as mesmas regras de preço. Exige caixa aberto.
     */
    @PostMapping("/pedidos")
    public ResponseEntity<Pedido> criarPedidoManual(@Valid @RequestBody PedidoBotRequest request) {
        Pedido pedido = pedidoService.criarPedidoManual(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(pedido);
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

    /**
     * Atualiza a situação do pagamento (PIX). Só aceita as transições válidas
     * do fluxo: AGUARDANDO → COMPROVANTE_ENVIADO/CONFIRMADO,
     * COMPROVANTE_ENVIADO → CONFIRMADO e A_ESTORNAR → ESTORNADO.
     */
    @PutMapping("/pedidos/{id}/pagamento")
    public ResponseEntity<Pedido> atualizarPagamento(
            @PathVariable Long id,
            @RequestBody PedidoPagamentoRequest body
    ) {
        return ResponseEntity.ok(adminService.atualizarStatusPagamento(id, body.getStatus()));
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

    /**
     * Pedidos de um turno específico (histórico do caixa), do mais novo para o
     * mais antigo. Funciona também para turnos já fechados.
     */
    @GetMapping("/caixa/{id}/pedidos")
    public ResponseEntity<List<Pedido>> listarPedidosDoCaixa(@PathVariable Long id) {
        return ResponseEntity.ok(pedidoRepository.findByCaixaIdOrderByIdDesc(id));
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
