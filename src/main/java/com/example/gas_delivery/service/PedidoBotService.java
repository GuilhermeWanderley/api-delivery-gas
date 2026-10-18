package com.example.gas_delivery.service;

import com.example.gas_delivery.dto.PedidoBotRequest;
import com.example.gas_delivery.entities.Caixa;
import com.example.gas_delivery.entities.FormaPagamento;
import com.example.gas_delivery.entities.OrigemPedido;
import com.example.gas_delivery.entities.Pedido;
import com.example.gas_delivery.entities.StatusCaixa;
import com.example.gas_delivery.entities.TipoPedido;
import com.example.gas_delivery.repository.CaixaRepository;
import com.example.gas_delivery.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Recebe os pedidos finalizados pelo robô (Typebot). A montagem do pedido é
 * compartilhada com o lançamento manual em {@link PedidoService}; aqui ficam
 * apenas as particularidades do robô: idempotência e envio da chave PIX.
 */
@Service
public class PedidoBotService {

    private static final Logger log = LoggerFactory.getLogger(PedidoBotService.class);

    private final PedidoRepository pedidoRepository;
    private final CaixaRepository caixaRepository;
    private final ConfiguracaoService configuracaoService;
    private final WhatsappNotificationService whatsappNotificationService;
    private final PedidoService pedidoService;

    public PedidoBotService(
            PedidoRepository pedidoRepository,
            CaixaRepository caixaRepository,
            ConfiguracaoService configuracaoService,
            WhatsappNotificationService whatsappNotificationService,
            PedidoService pedidoService
    ) {
        this.pedidoRepository = pedidoRepository;
        this.caixaRepository = caixaRepository;
        this.configuracaoService = configuracaoService;
        this.whatsappNotificationService = whatsappNotificationService;
        this.pedidoService = pedidoService;
    }

    @Transactional
    public Pedido criarPedidoViaBot(PedidoBotRequest request) {
        pedidoService.validarRequest(request);

        // Idempotência: se o robô reenviar a mesma requisição (retry, duplo
        // clique, reconexão), devolvemos o pedido já criado em vez de duplicar.
        String chaveIdempotencia = pedidoService.normalizarChaveIdempotencia(request.getIdempotencyKey());
        if (chaveIdempotencia != null) {
            Optional<Pedido> existente = pedidoRepository.findByChaveIdempotencia(chaveIdempotencia);
            if (existente.isPresent()) {
                log.info("Requisição repetida do robô (chave '{}'): devolvendo o pedido #{} já existente.",
                        chaveIdempotencia, existente.get().getId());
                return existente.get();
            }
        }

        // Turno de caixa: o kanban só mostra o caixa ABERTO. Sem caixa aberto o
        // pedido nasce com caixa nulo (não é erro) e não aparece no turno atual.
        Caixa caixaAberto = caixaRepository
                .findFirstByStatusOrderByDataAberturaDesc(StatusCaixa.ABERTO)
                .orElse(null);

        Pedido pedidoSalvo = pedidoService.criarPedido(
                request, OrigemPedido.BOT, caixaAberto, chaveIdempotencia);

        // PIX de pedido comum: manda a chave e pede o comprovante na hora.
        // Nunca para GAS_DO_POVO — nesse caso só depois da aprovação do CPF.
        try {
            if (pedidoSalvo.getFormaPagamento() == FormaPagamento.PIX
                    && pedidoSalvo.getTipoPedido() == TipoPedido.NORMAL) {
                String chavePix = configuracaoService.obter().getChavePix();
                if (chavePix != null && !chavePix.isBlank()) {
                    whatsappNotificationService.enviarChavePix(pedidoSalvo, chavePix);
                }
            }
        } catch (Exception ex) {
            log.error("Falha ao enviar a chave PIX do pedido #{}: {}. "
                    + "O pedido foi criado normalmente.", pedidoSalvo.getId(), ex.getMessage(), ex);
        }

        return pedidoSalvo;
    }
}
