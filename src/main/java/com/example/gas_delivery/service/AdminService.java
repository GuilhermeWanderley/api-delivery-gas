package com.example.gas_delivery.service;

import com.example.gas_delivery.entities.Configuracao;
import com.example.gas_delivery.entities.Entregador;
import com.example.gas_delivery.entities.FormaPagamento;
import com.example.gas_delivery.entities.Pedido;
import com.example.gas_delivery.entities.Status;
import com.example.gas_delivery.entities.StatusPagamento;
import com.example.gas_delivery.repository.EntregadorRepository;
import com.example.gas_delivery.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private static final EnumSet<FormaPagamento> FORMAS_QUE_ENTRAM_NO_CAIXA =
            EnumSet.of(FormaPagamento.DINHEIRO, FormaPagamento.CARTAO_CREDITO, FormaPagamento.CARTAO_DEBITO);

    private final PedidoRepository pedidoRepository;
    private final EntregadorRepository entregadorRepository;
    private final WhatsappNotificationService whatsappNotificationService;
    private final ConfiguracaoService configuracaoService;

    public AdminService(
            PedidoRepository pedidoRepository,
            EntregadorRepository entregadorRepository,
            WhatsappNotificationService whatsappNotificationService,
            ConfiguracaoService configuracaoService
    ) {
        this.pedidoRepository = pedidoRepository;
        this.entregadorRepository = entregadorRepository;
        this.whatsappNotificationService = whatsappNotificationService;
        this.configuracaoService = configuracaoService;
    }

    @Transactional
    public Pedido despacharPedido(Long pedidoId, Long entregadorId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado: " + pedidoId));

        if (pedido.getEnderecoEntrega() == null
                || pedido.getEnderecoEntrega().isBlank()
                || pedido.getFormaPagamento() == null) {
            throw new IllegalStateException("Não é possível despachar um pedido com endereço ou forma de pagamento em branco. O cliente ainda não finalizou o pedido.");
        }

        if (pedido.getStatus() != Status.PENDENTE) {
            throw new IllegalStateException(
                    "Apenas pedidos com status PENDENTE podem ser despachados. "
                            + "Pedidos do Gás do Povo precisam do CPF aprovado antes.");
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
        whatsappNotificationService.notificarMudancaDeStatus(
                pedidoSalvo.getCliente().getTelefone(), pedidoSalvo.getStatus());
        return pedidoSalvo;
    }

    @Transactional
    public Pedido cancelarPedido(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado: " + pedidoId));

        if (pedido.getStatus() == Status.ENTREGUE) {
            throw new IllegalStateException("Pedido já entregue não pode ser cancelado.");
        }
        if (pedido.getStatus() == Status.CANCELADO) {
            throw new IllegalStateException("Pedido já está cancelado.");
        }

        pedido.setStatus(Status.CANCELADO);
        // Pedido já pago (ou com comprovante em análise) precisa de estorno:
        // fica A_ESTORNAR até o atendente devolver o dinheiro e marcar ESTORNADO.
        if (pedido.getStatusPagamento() == StatusPagamento.CONFIRMADO
                || pedido.getStatusPagamento() == StatusPagamento.COMPROVANTE_ENVIADO) {
            pedido.setStatusPagamento(StatusPagamento.A_ESTORNAR);
        }
        pedido.setDataAtualizacao(LocalDateTime.now());
        Pedido pedidoSalvo = pedidoRepository.save(pedido);
        whatsappNotificationService.notificarMudancaDeStatus(
                pedidoSalvo.getCliente().getTelefone(), pedidoSalvo.getStatus());
        return pedidoSalvo;
    }

    @Transactional
    public Pedido atualizarStatusPagamento(Long pedidoId, StatusPagamento novoStatus) {
        if (novoStatus == null) {
            throw new IllegalArgumentException("Status de pagamento é obrigatório.");
        }

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado: " + pedidoId));

        StatusPagamento atual = pedido.getStatusPagamento();

        // Dinheiro/cartão (ou pedido legado sem controle de pagamento): não há
        // comprovante PIX a transicionar.
        if (atual == StatusPagamento.NAO_APLICAVEL) {
            throw new IllegalArgumentException("Este pedido não é PIX.");
        }
        if (atual == null) {
            throw new IllegalArgumentException("Transição de pagamento inválida.");
        }

        boolean transicaoPermitida = switch (atual) {
            case AGUARDANDO -> novoStatus == StatusPagamento.COMPROVANTE_ENVIADO
                    || novoStatus == StatusPagamento.CONFIRMADO;
            case COMPROVANTE_ENVIADO -> novoStatus == StatusPagamento.CONFIRMADO;
            case A_ESTORNAR -> novoStatus == StatusPagamento.ESTORNADO;
            default -> false;
        };

        if (!transicaoPermitida) {
            throw new IllegalArgumentException("Transição de pagamento inválida.");
        }

        pedido.setStatusPagamento(novoStatus);
        pedido.setDataAtualizacao(LocalDateTime.now());
        return pedidoRepository.save(pedido);
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
        Pedido pedidoSalvo = pedidoRepository.save(pedido);
        whatsappNotificationService.notificarMudancaDeStatus(
                pedidoSalvo.getCliente().getTelefone(), pedidoSalvo.getStatus());

        // CPF aprovado: se for PIX e houver chave cadastrada, envia a chave
        // e pede o comprovante. Falha no envio não desfaz a aprovação.
        if (pedidoSalvo.getFormaPagamento() == FormaPagamento.PIX) {
            try {
                Configuracao configuracao = configuracaoService.obter();
                String chavePix = configuracao == null ? null : configuracao.getChavePix();
                if (chavePix != null && !chavePix.isBlank()) {
                    whatsappNotificationService.enviarChavePix(pedidoSalvo, chavePix);
                }
            } catch (Exception ex) {
                log.error("Falha ao enviar a chave PIX do pedido #{} após aprovar o CPF: {}. "
                        + "A aprovação foi mantida.", pedidoSalvo.getId(), ex.getMessage(), ex);
            }
        }

        return pedidoSalvo;
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
        Pedido pedidoSalvo = pedidoRepository.save(pedido);
        whatsappNotificationService.notificarMudancaDeStatus(
                pedidoSalvo.getCliente().getTelefone(), pedidoSalvo.getStatus());
        return pedidoSalvo;
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
        whatsappNotificationService.notificarMudancaDeStatus(
                pedidoSalvo.getCliente().getTelefone(), pedidoSalvo.getStatus());

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
