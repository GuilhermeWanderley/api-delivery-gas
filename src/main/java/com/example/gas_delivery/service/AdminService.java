package com.example.gas_delivery.service;

import com.example.gas_delivery.entities.Entregador;
import com.example.gas_delivery.entities.FormaPagamento;
import com.example.gas_delivery.entities.Pedido;
import com.example.gas_delivery.entities.Status;
import com.example.gas_delivery.repository.EntregadorRepository;
import com.example.gas_delivery.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminService {

    private static final EnumSet<FormaPagamento> FORMAS_QUE_ENTRAM_NO_CAIXA =
            EnumSet.of(FormaPagamento.DINHEIRO, FormaPagamento.CARTAO_CREDITO, FormaPagamento.CARTAO_DEBITO);

    private final PedidoRepository pedidoRepository;
    private final EntregadorRepository entregadorRepository;
    private final EvolutionApiClientService evolutionApiClientService;

    public AdminService(
            PedidoRepository pedidoRepository,
            EntregadorRepository entregadorRepository,
            EvolutionApiClientService evolutionApiClientService
    ) {
        this.pedidoRepository = pedidoRepository;
        this.entregadorRepository = entregadorRepository;
        this.evolutionApiClientService = evolutionApiClientService;
    }

    @Transactional
    public Pedido despacharPedido(Long pedidoId, Long entregadorId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado: " + pedidoId));

        if (pedido.getEnderecoEntrega() == null
                || pedido.getEnderecoEntrega().isBlank()
                || pedido.getFormaPagamento() == null) {
            throw new IllegalStateException("Não é possível despachar um pedido com endereço ou forma de pagamento em branco. O cliente ainda não finalizou o pedido no WhatsApp.");
        }

        if (pedido.getStatus() != Status.PENDENTE && pedido.getStatus() != Status.AGUARDANDO_VALIDACAO_CPF) {
            throw new IllegalStateException("Apenas pedidos pendentes podem ser despachados.");
        }

        Entregador entregador = entregadorRepository.findById(entregadorId)
                .orElseThrow(() -> new IllegalArgumentException("Entregador não encontrado: " + entregadorId));

        if (!entregador.isAtivo()) {
            throw new IllegalStateException("Entregador inativo não pode receber despacho.");
        }

        pedido.setEntregador(entregador);
        pedido.setStatus(Status.DESPACHADO);
        pedido.setDataAtualizacao(LocalDateTime.now());

        Pedido pedidoSalvo = pedidoRepository.save(pedido);
        evolutionApiClientService.enviarFichaEntregador(pedidoSalvo);
        return pedidoSalvo;
    }

    @Transactional
    public Pedido aprovarCpfGasDoPovo(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado: " + pedidoId));

        if (pedido.getStatus() != Status.AGUARDANDO_VALIDACAO_CPF) {
            throw new IllegalStateException("Somente pedidos aguardando validação de CPF podem ser aprovados.");
        }

        pedido.setStatus(Status.PENDENTE);
        pedido.setDataAtualizacao(LocalDateTime.now());
        return pedidoRepository.save(pedido);
    }

    @Transactional
    public Pedido reprovarCpfGasDoPovo(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado: " + pedidoId));

        if (pedido.getStatus() != Status.AGUARDANDO_VALIDACAO_CPF) {
            throw new IllegalStateException("Somente pedidos aguardando validação de CPF podem ser reprovados.");
        }

        pedido.setStatus(Status.CPF_REPROVADO);
        pedido.setDataAtualizacao(LocalDateTime.now());
        return pedidoRepository.save(pedido);
    }

    @Transactional
    public Pedido atualizarPedidoParaEntregue(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado: " + pedidoId));

        if (pedido.getStatus() != Status.DESPACHADO) {
            throw new IllegalStateException("Somente pedidos despachados podem ser marcados como entregues.");
        }

        pedido.setStatus(Status.ENTREGUE);
        pedido.setDataAtualizacao(LocalDateTime.now());
        Pedido pedidoSalvo = pedidoRepository.save(pedido);

        Entregador entregador = pedidoSalvo.getEntregador();
        if (entregador != null && FORMAS_QUE_ENTRAM_NO_CAIXA.contains(pedidoSalvo.getFormaPagamento())) {
            BigDecimal caixaAtualizado = entregador.getCaixaAtual().add(pedidoSalvo.getValorTotal());
            entregador.setCaixaAtual(caixaAtualizado);
            entregadorRepository.save(entregador);
        }

        return pedidoSalvo;
    }

    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> calcularCaixaDoDiaPorEntregador() {
        LocalDateTime inicioDoDia = LocalDate.now().atStartOfDay();
        LocalDateTime fimDoDia = LocalDate.now().atTime(LocalTime.MAX);

        Map<Long, BigDecimal> caixaPorEntregador = new LinkedHashMap<>();
        List<Entregador> entregadores = entregadorRepository.findAll();

        for (Entregador entregador : entregadores) {
            BigDecimal total = pedidoRepository.somarCaixaDoDiaPorEntregador(
                    entregador.getId(),
                    FORMAS_QUE_ENTRAM_NO_CAIXA.stream().toList(),
                    inicioDoDia,
                    fimDoDia
            );
            caixaPorEntregador.put(entregador.getId(), total);
        }

        return caixaPorEntregador;
    }
}
