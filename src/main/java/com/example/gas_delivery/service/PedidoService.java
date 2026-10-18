package com.example.gas_delivery.service;

import com.example.gas_delivery.dto.PedidoBotRequest;
import com.example.gas_delivery.entities.Caixa;
import com.example.gas_delivery.entities.Cliente;
import com.example.gas_delivery.entities.FormaPagamento;
import com.example.gas_delivery.entities.OrigemPedido;
import com.example.gas_delivery.entities.Pedido;
import com.example.gas_delivery.entities.Status;
import com.example.gas_delivery.entities.StatusCaixa;
import com.example.gas_delivery.entities.StatusPagamento;
import com.example.gas_delivery.entities.TipoPedido;
import com.example.gas_delivery.repository.CaixaRepository;
import com.example.gas_delivery.repository.ClienteRepository;
import com.example.gas_delivery.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Regras de criação de pedidos, compartilhadas pelo robô (Typebot) e pelo
 * lançamento manual do atendente — evitando que os dois fluxos divirjam.
 */
@Service
public class PedidoService {

    private static final String PREFIXO_BRASIL = "55";

    private static final int TAMANHO_MAXIMO_CHAVE_IDEMPOTENCIA = 128;

    private final ClienteRepository clienteRepository;
    private final PedidoRepository pedidoRepository;
    private final CaixaRepository caixaRepository;
    private final PrecoService precoService;

    public PedidoService(
            ClienteRepository clienteRepository,
            PedidoRepository pedidoRepository,
            CaixaRepository caixaRepository,
            PrecoService precoService
    ) {
        this.clienteRepository = clienteRepository;
        this.pedidoRepository = pedidoRepository;
        this.caixaRepository = caixaRepository;
        this.precoService = precoService;
    }

    /**
     * Pedido lançado pelo atendente (cliente idoso por telefone).
     * <p>
     * Diferente do robô, exige um caixa ABERTO: sem ele o operador não está
     * atendendo, então devolvemos conflito (409) com uma mensagem clara.
     */
    @Transactional
    public Pedido criarPedidoManual(PedidoBotRequest request) {
        validarRequest(request);

        Caixa caixaAberto = caixaRepository
                .findFirstByStatusOrderByDataAberturaDesc(StatusCaixa.ABERTO)
                .orElseThrow(() -> new IllegalStateException("Abra o caixa antes de lançar um pedido."));

        return criarPedido(request, OrigemPedido.MANUAL, caixaAberto, null);
    }

    /**
     * Monta e persiste um pedido aplicando as mesmas regras para qualquer
     * origem: preço, status inicial, CPF do Gás do Povo e situação do pagamento.
     *
     * @param caixa              turno ao qual o pedido será vinculado (pode ser nulo no fluxo do robô)
     * @param chaveIdempotencia  chave do robô (nula no lançamento manual)
     */
    @Transactional
    public Pedido criarPedido(
            PedidoBotRequest request,
            OrigemPedido origem,
            Caixa caixa,
            String chaveIdempotencia
    ) {
        validarRequest(request);

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

        pedido.setOrigem(origem);

        // PIX: aguarda comprovante. Dinheiro/cartão: nada a controlar.
        if (request.getFormaPagamento() == FormaPagamento.PIX) {
            pedido.setStatusPagamento(StatusPagamento.AGUARDANDO);
        } else {
            pedido.setStatusPagamento(StatusPagamento.NAO_APLICAVEL);
        }

        pedido.setValorTotal(precoService.calcularValorTotal(
                request.getTipoPedido(), request.getFormaPagamento()));

        pedido.setDataCriacao(LocalDateTime.now());
        pedido.setDataAtualizacao(LocalDateTime.now());
        pedido.setChaveIdempotencia(chaveIdempotencia);
        pedido.setCaixa(caixa);

        return pedidoRepository.save(pedido);
    }

    /** Normaliza a chave de idempotência (vazia vira {@code null}). */
    public String normalizarChaveIdempotencia(String chave) {
        if (chave == null || chave.isBlank()) {
            return null;
        }
        String limpa = chave.trim();
        return limpa.length() > TAMANHO_MAXIMO_CHAVE_IDEMPOTENCIA
                ? limpa.substring(0, TAMANHO_MAXIMO_CHAVE_IDEMPOTENCIA)
                : limpa;
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

    /** Valida os campos comuns aos dois fluxos (robô e manual). */
    public void validarRequest(PedidoBotRequest request) {
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
