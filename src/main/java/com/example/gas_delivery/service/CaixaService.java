package com.example.gas_delivery.service;

import com.example.gas_delivery.entities.Caixa;
import com.example.gas_delivery.entities.Entregador;
import com.example.gas_delivery.entities.FormaPagamento;
import com.example.gas_delivery.entities.Pedido;
import com.example.gas_delivery.entities.ResumoCaixaEntregador;
import com.example.gas_delivery.entities.Status;
import com.example.gas_delivery.entities.StatusCaixa;
import com.example.gas_delivery.repository.CaixaRepository;
import com.example.gas_delivery.repository.EntregadorRepository;
import com.example.gas_delivery.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Regras do turno de caixa:
 * <ul>
 *   <li>O operador precisa <b>abrir</b> o caixa antes de atender;</li>
 *   <li>Ao <b>fechar</b>, o sistema congela um relatório do turno (histórico) e
 *       <b>liquida os entregadores</b> — cada um presta contas do dinheiro que
 *       estava carregando, e o respectivo saldo volta para zero;</li>
 *   <li>Enquanto aberto, os totais parciais podem ser consultados a qualquer momento.</li>
 * </ul>
 */
@Service
public class CaixaService {

    private final CaixaRepository caixaRepository;
    private final PedidoRepository pedidoRepository;
    private final EntregadorRepository entregadorRepository;

    public CaixaService(
            CaixaRepository caixaRepository,
            PedidoRepository pedidoRepository,
            EntregadorRepository entregadorRepository
    ) {
        this.caixaRepository = caixaRepository;
        this.pedidoRepository = pedidoRepository;
        this.entregadorRepository = entregadorRepository;
    }

    /** Existe algum caixa aberto agora? (usado para avisar o operador) */
    @Transactional(readOnly = true)
    public boolean haCaixaAberto() {
        return caixaRepository.findFirstByStatusOrderByDataAberturaDesc(StatusCaixa.ABERTO).isPresent();
    }

    @Transactional(readOnly = true)
    public Caixa caixaAberto() {
        return caixaRepository.findFirstByStatusOrderByDataAberturaDesc(StatusCaixa.ABERTO).orElse(null);
    }

    /** Resumo parcial do turno em andamento (ou {@code null} se o caixa estiver fechado). */
    @Transactional(readOnly = true)
    public Caixa resumoDoTurnoAtual() {
        Caixa aberto = caixaAberto();
        if (aberto == null) {
            return null;
        }
        preencherTotais(aberto, aberto.getDataAbertura(), LocalDateTime.now());
        return aberto;
    }

    @Transactional
    public Caixa abrir(String abertoPor) {
        if (haCaixaAberto()) {
            throw new IllegalStateException(
                    "Já existe um caixa aberto. Feche o turno atual antes de abrir um novo.");
        }
        Caixa caixa = new Caixa();
        caixa.setDataAbertura(LocalDateTime.now());
        caixa.setStatus(StatusCaixa.ABERTO);
        caixa.setAbertoPor(abertoPor);
        return caixaRepository.save(caixa);
    }

    @Transactional
    public Caixa fechar(String observacao) {
        Caixa caixa = caixaAberto();
        if (caixa == null) {
            throw new IllegalStateException("Nenhum caixa aberto para fechar.");
        }
        LocalDateTime fim = LocalDateTime.now();
        preencherTotais(caixa, caixa.getDataAbertura(), fim);
        liquidarEntregadores(caixa);
        caixa.setDataFechamento(fim);
        caixa.setStatus(StatusCaixa.FECHADO);
        if (observacao != null && !observacao.isBlank()) {
            caixa.setObservacao(observacao);
        }
        return caixaRepository.save(caixa);
    }

    @Transactional(readOnly = true)
    public List<Caixa> historico() {
        return caixaRepository.findAllComResumo();
    }

    // ------------------------------------------------------------------

    /**
     * Calcula os totais do período e grava o "relatório congelado"
     * (uma linha por entregador, separada por forma de pagamento).
     */
    void preencherTotais(Caixa caixa, LocalDateTime inicio, LocalDateTime fim) {
        List<Pedido> entregues = pedidoRepository
                .findByStatusAndDataAtualizacaoBetweenOrderByDataAtualizacaoAsc(Status.ENTREGUE, inicio, fim);

        Map<String, ResumoCaixaEntregador> porEntregador = new LinkedHashMap<>();
        BigDecimal geral = BigDecimal.ZERO;
        BigDecimal dinheiro = BigDecimal.ZERO;
        BigDecimal cartao = BigDecimal.ZERO;
        BigDecimal pix = BigDecimal.ZERO;

        for (Pedido pedido : entregues) {
            Entregador entregador = pedido.getEntregador();
            String chave = entregador != null ? String.valueOf(entregador.getId()) : "sem-entregador";
            String nome = entregador != null ? entregador.getNome() : "Sem entregador";

            ResumoCaixaEntregador linha = porEntregador.get(chave);
            if (linha == null) {
                linha = new ResumoCaixaEntregador();
                linha.setEntregadorId(entregador != null ? entregador.getId() : null);
                linha.setEntregadorNome(nome);
                porEntregador.put(chave, linha);
            }

            BigDecimal valor = pedido.getValorTotal() == null ? BigDecimal.ZERO : pedido.getValorTotal();
            FormaPagamento forma = pedido.getFormaPagamento();

            if (forma == FormaPagamento.DINHEIRO) {
                linha.setTotalDinheiro(linha.getTotalDinheiro().add(valor));
                dinheiro = dinheiro.add(valor);
            } else if (forma == FormaPagamento.CARTAO_CREDITO || forma == FormaPagamento.CARTAO_DEBITO) {
                linha.setTotalCartao(linha.getTotalCartao().add(valor));
                cartao = cartao.add(valor);
            } else if (forma == FormaPagamento.PIX) {
                linha.setTotalPix(linha.getTotalPix().add(valor));
                pix = pix.add(valor);
            }

            linha.setTotal(linha.getTotal().add(valor));
            linha.setQuantidadePedidos(linha.getQuantidadePedidos() + 1);
            geral = geral.add(valor);
        }

        caixa.getResumo().clear();
        for (ResumoCaixaEntregador linha : porEntregador.values()) {
            caixa.adicionarResumo(linha);
        }
        caixa.setTotalDinheiro(dinheiro);
        caixa.setTotalCartao(cartao);
        caixa.setTotalPix(pix);
        caixa.setTotalGeral(geral);
        caixa.setQuantidadePedidos(entregues.size());
    }

    /**
     * Liquida o turno: cada entregador presta contas do dinheiro que estava carregando.
     * <p>
     * O saldo ({@link Entregador#getCaixaAtual()}) é <b>zerado</b> e passa a integrar o
     * relatório congelado do caixa. Se houver saldo que o turno ainda não registrou
     * (entregas concluídas com o caixa fechado), a diferença é somada ao relatório para
     * que nenhum valor se perca na prestação de contas.
     */
    void liquidarEntregadores(Caixa caixa) {
        Map<Long, ResumoCaixaEntregador> porEntregadorId = new HashMap<>();
        for (ResumoCaixaEntregador linha : caixa.getResumo()) {
            if (linha.getEntregadorId() != null) {
                porEntregadorId.put(linha.getEntregadorId(), linha);
            }
        }

        for (Entregador entregador : entregadorRepository.findAll()) {
            BigDecimal carregado = entregador.getCaixaAtual() == null
                    ? BigDecimal.ZERO
                    : entregador.getCaixaAtual();

            if (carregado.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            ResumoCaixaEntregador linha = porEntregadorId.get(entregador.getId());
            BigDecimal jaRegistrado = linha == null
                    ? BigDecimal.ZERO
                    : linha.getTotalDinheiro().add(linha.getTotalCartao());

            BigDecimal diferenca = carregado.subtract(jaRegistrado);
            if (diferenca.compareTo(BigDecimal.ZERO) > 0) {
                if (linha == null) {
                    linha = new ResumoCaixaEntregador();
                    linha.setEntregadorId(entregador.getId());
                    linha.setEntregadorNome(entregador.getNome());
                    caixa.adicionarResumo(linha);
                    porEntregadorId.put(entregador.getId(), linha);
                }
                // Valor que ainda não estava no turno (ex.: entrega feita com o caixa fechado)
                linha.setTotalDinheiro(linha.getTotalDinheiro().add(diferenca));
                linha.setTotal(linha.getTotal().add(diferenca));
                caixa.setTotalDinheiro(caixa.getTotalDinheiro().add(diferenca));
                caixa.setTotalGeral(caixa.getTotalGeral().add(diferenca));
            }

            // Prestação de contas: o entregador repassa o dinheiro e o saldo volta a zero.
            entregador.setCaixaAtual(BigDecimal.ZERO);
            entregadorRepository.save(entregador);
        }
    }

    /** Lista vazia segura (evita null em respostas JSON). */
    static List<ResumoCaixaEntregador> vazio() {
        return new ArrayList<>();
    }
}
