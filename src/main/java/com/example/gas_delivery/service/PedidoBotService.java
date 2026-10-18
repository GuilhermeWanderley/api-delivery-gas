package com.example.gas_delivery.service;

import com.example.gas_delivery.dto.PedidoBotRequest;
import com.example.gas_delivery.entities.Cliente;
import com.example.gas_delivery.entities.Configuracao;
import com.example.gas_delivery.entities.FormaPagamento;
import com.example.gas_delivery.entities.Pedido;
import com.example.gas_delivery.entities.Status;
import com.example.gas_delivery.entities.TipoPedido;
import com.example.gas_delivery.repository.ClienteRepository;
import com.example.gas_delivery.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PedidoBotService {

    private static final Logger log = LoggerFactory.getLogger(PedidoBotService.class);

    private static final String PREFIXO_BRASIL = "55";

    private static final int TAMANHO_MAXIMO_CHAVE_IDEMPOTENCIA = 128;

    private final ClienteRepository clienteRepository;
    private final PedidoRepository pedidoRepository;
    private final ConfiguracaoService configuracaoService;

    public PedidoBotService(
            ClienteRepository clienteRepository,
            PedidoRepository pedidoRepository,
            ConfiguracaoService configuracaoService
    ) {
        this.clienteRepository = clienteRepository;
        this.pedidoRepository = pedidoRepository;
        this.configuracaoService = configuracaoService;
    }

    @Transactional
    public Pedido criarPedidoViaBot(PedidoBotRequest request) {
        validarRequest(request);

        // Idempotência: se o robô reenviar a mesma requisição (retry, duplo
        // clique, reconexão), devolvemos o pedido já criado em vez de duplicar.
        String chaveIdempotencia = normalizarChaveIdempotencia(request.getIdempotencyKey());
        if (chaveIdempotencia != null) {
            Optional<Pedido> existente = pedidoRepository.findByChaveIdempotencia(chaveIdempotencia);
            if (existente.isPresent()) {
                log.info("Requisição repetida do robô (chave '{}'): devolvendo o pedido #{} já existente.",
                        chaveIdempotencia, existente.get().getId());
                return existente.get();
            }
        }

        String telefone = normalizarTelefone(request.getTelefone());

        Cliente cliente = clienteRepository.findByTelefone(telefone)
                .orElseGet(() -> {
                    Cliente novo = new Cliente();
                    novo.setTelefone(telefone);
                    novo.setDataCadastro(LocalDateTime.now());
                    return novo;
                });

        cliente.setNome(request.getNome());
        cliente.setEnderecoPadrao(request.getEnderecoEntrega());

        if (request.getTipoPedido() == TipoPedido.GAS_DO_POVO) {
            if (request.getCpf() == null || request.getCpf().isBlank()) {
                throw new IllegalArgumentException("CPF é obrigatório para pedidos do tipo Gás do Povo.");
            }
            // A validação do CPF é feita manualmente pelo atendente; aqui apenas
            // normalizamos (só dígitos) para facilitar a conferência.
            cliente.setCpf(normalizarCpf(request.getCpf()));
        }

        Cliente clienteSalvo = clienteRepository.save(cliente);

        Pedido pedido = new Pedido();
        pedido.setCliente(clienteSalvo);
        pedido.setTipoPedido(request.getTipoPedido());
        pedido.setFormaPagamento(request.getFormaPagamento());
        pedido.setEnderecoEntrega(request.getEnderecoEntrega());
        pedido.setPrecisaTrocoPara(request.getPrecisaTrocoPara());

        pedido.setStatus(request.getTipoPedido() == TipoPedido.GAS_DO_POVO
                ? Status.AGUARDANDO_VALIDACAO_CPF
                : Status.PENDENTE);

        pedido.setValorTotal(calcularValorTotal(request.getTipoPedido(), request.getFormaPagamento()));

        pedido.setDataCriacao(LocalDateTime.now());
        pedido.setDataAtualizacao(LocalDateTime.now());
        // A restrição única no banco é a rede de segurança contra corrida.
        pedido.setChaveIdempotencia(chaveIdempotencia);

        return pedidoRepository.save(pedido);
    }

    /** Normaliza a chave de idempotência (vazia vira {@code null}). */
    private String normalizarChaveIdempotencia(String chave) {
        if (chave == null || chave.isBlank()) {
            return null;
        }
        String limpa = chave.trim();
        return limpa.length() > TAMANHO_MAXIMO_CHAVE_IDEMPOTENCIA
                ? limpa.substring(0, TAMANHO_MAXIMO_CHAVE_IDEMPOTENCIA)
                : limpa;
    }

    /**
     * Preço do pedido, lendo a configuração do banco (editável pelo painel):
     * <ul>
     *   <li><b>Gás do Povo</b>: valor fixo do programa;</li>
     *   <li><b>Gás comum</b>: preço à vista ou no crédito, com desconto
     *       automático de quarta-feira quando a promoção está ativa.</li>
     * </ul>
     */
    BigDecimal calcularValorTotal(TipoPedido tipo, FormaPagamento forma) {
        Configuracao cfg = configuracaoService.obter();
        if (tipo == TipoPedido.GAS_DO_POVO) {
            return cfg.getPrecoGasPovo();
        }
        BigDecimal base = forma == FormaPagamento.CARTAO_CREDITO
                ? cfg.getPrecoGasCredito()
                : cfg.getPrecoGasVista();
        if (ehQuartaFeira(LocalDate.now()) && cfg.isPromocaoQuartaAtiva()) {
            base = base.subtract(cfg.getDescontoQuarta());
        }
        return base;
    }

    /** Verifica se a data cai em uma quarta-feira (dia da promoção). */
    boolean ehQuartaFeira(LocalDate data) {
        return data != null && data.getDayOfWeek() == DayOfWeek.WEDNESDAY;
    }

    /**
     * Normaliza o telefone: remove caracteres não numéricos e garante o prefixo
     * "55" do Brasil. Assim "81999998888", "+5581999998888" e "5581999998888"
     * viram sempre o mesmo valor, evitando cliente duplicado.
     */
    private String normalizarTelefone(String telefone) {
        String limpo = telefone.replaceAll("[^0-9]", "");
        if (!limpo.startsWith(PREFIXO_BRASIL)) {
            limpo = PREFIXO_BRASIL + limpo;
        }
        return limpo;
    }

    private String normalizarCpf(String cpf) {
        return cpf == null ? null : cpf.replaceAll("[^0-9]", "");
    }

    private void validarRequest(PedidoBotRequest request) {
        if (request.getTelefone() == null || request.getTelefone().isBlank()) {
            throw new IllegalArgumentException("Telefone é obrigatório.");
        }
        if (request.getNome() == null || request.getNome().isBlank()) {
            throw new IllegalArgumentException("Nome é obrigatório.");
        }
        if (request.getEnderecoEntrega() == null || request.getEnderecoEntrega().isBlank()) {
            throw new IllegalArgumentException("Endereço de entrega é obrigatório.");
        }
        if (request.getFormaPagamento() == null) {
            throw new IllegalArgumentException("Forma de pagamento é obrigatória.");
        }
        if (request.getTipoPedido() == null) {
            throw new IllegalArgumentException("Tipo do pedido é obrigatório.");
        }
    }
}
