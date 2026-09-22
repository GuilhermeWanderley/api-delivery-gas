package com.example.gas_delivery.service;

import com.example.gas_delivery.entities.Cliente;
import com.example.gas_delivery.entities.FormaPagamento;
import com.example.gas_delivery.entities.Pedido;
import com.example.gas_delivery.entities.Status;
import com.example.gas_delivery.entities.TipoPedido;
import com.example.gas_delivery.repository.ClienteRepository;
import com.example.gas_delivery.repository.PedidoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BotMessageProcessorService {

    private static final BigDecimal VALOR_GAS_DO_POVO = new BigDecimal("20.00");

    private final ClienteRepository clienteRepository;
    private final PedidoRepository pedidoRepository;
    private final EvolutionApiClientService evolutionApiClientService;
    private final Map<String, BotSessionState> sessoes = new ConcurrentHashMap<>();
    private final BigDecimal valorP13Normal;

    public BotMessageProcessorService(
            ClienteRepository clienteRepository,
            PedidoRepository pedidoRepository,
            EvolutionApiClientService evolutionApiClientService,
            @Value("${app.preco.p13:120.00}") BigDecimal valorP13Normal
    ) {
        this.clienteRepository = clienteRepository;
        this.pedidoRepository = pedidoRepository;
        this.evolutionApiClientService = evolutionApiClientService;
        this.valorP13Normal = valorP13Normal;
    }

    @Transactional
    public String processarMensagem(String telefone, String mensagem) {
        if (telefone == null || telefone.isBlank()) {
            throw new IllegalArgumentException("Telefone é obrigatório.");
        }

        String texto = mensagem == null ? "" : mensagem.trim();
        String textoNorm = normalizar(texto);

        BotSessionState estado = sessoes.computeIfAbsent(telefone, ignored -> {
            BotSessionState novoEstado = BotSessionState.novo(telefone);
            novoEstado.etapa(EtapaBot.AGUARDANDO_TIPO_PEDIDO);
            return novoEstado;
        });

        if ("cancelar".equals(textoNorm)) {
            return cancelarPedidoPendente(estado, telefone);
        }

        return switch (estado.etapa()) {
            case AGUARDANDO_TIPO_PEDIDO -> tratarTipoPedido(estado, textoNorm);
            case AGUARDANDO_NOME -> tratarNome(estado, texto);
            case AGUARDANDO_CPF -> tratarCpf(estado, textoNorm);
            case AGUARDANDO_ENDERECO -> tratarEndereco(estado, texto);
            case AGUARDANDO_FORMA_PAGAMENTO -> tratarFormaPagamento(estado, telefone, textoNorm);
            case AGUARDANDO_TROCO -> tratarTroco(estado, textoNorm);
            case AGUARDANDO_COMPROVANTE_PIX -> finalizarPedido(estado, telefone);
        };
    }

    private String tratarTipoPedido(BotSessionState estado, String textoNorm) {
        if (textoNorm.isBlank() || "menu".equals(textoNorm) || "oi".equals(textoNorm) || "ola".equals(textoNorm)) {
            return mensagemBoasVindas();
        }

        if (textoNorm.contains("normal") || "1".equals(textoNorm)) {
            estado.tipoPedido(TipoPedido.NORMAL);
            estado.etapa(EtapaBot.AGUARDANDO_NOME);
            return "Perfeito! Qual é o seu nome?";
        }

        if (textoNorm.contains("gas do povo") || textoNorm.contains("gasdo povo")
                || textoNorm.contains("gasdopovo") || textoNorm.contains("povo") || "2".equals(textoNorm)) {
            estado.tipoPedido(TipoPedido.GAS_DO_POVO);
            estado.etapa(EtapaBot.AGUARDANDO_CPF);
            return "Informe seu CPF para o Gás do Povo (somente números).";
        }

        return "Não entendi sua opção.\n" + mensagemBoasVindas();
    }

    private String tratarNome(BotSessionState estado, String texto) {
        if (texto == null || texto.isBlank()) {
            return "Por favor, informe seu nome para continuar.";
        }
        estado.nome(texto.trim());

        estado.etapa(EtapaBot.AGUARDANDO_ENDERECO);
        return "Informe o endereço completo (nome/rua/número/bairro/ponto de referência).";
    }

    private String tratarCpf(BotSessionState estado, String textoNorm) {
        String cpf = textoNorm.replaceAll("[^0-9]", "");
        if (cpf.length() != 11) {
            return "CPF inválido. Envie um CPF com 11 dígitos.";
        }

        estado.cpf(cpf);
        estado.etapa(EtapaBot.AGUARDANDO_NOME);
        return "Perfeito! Qual é o seu nome?";
    }

    private String tratarEndereco(BotSessionState estado, String texto) {
        if (texto == null || texto.isBlank()) {
            return "Por favor, informe o endereço completo.";
        }
        estado.enderecoEntrega(texto.trim());
        estado.etapa(EtapaBot.AGUARDANDO_FORMA_PAGAMENTO);
        return "Forma de pagamento:\n1 - Dinheiro\n2 - Cartão de Crédito\n3 - Cartão de Débito\n4 - PIX";
    }

    private String tratarFormaPagamento(BotSessionState estado, String telefone, String textoNorm) {
        FormaPagamento formaPagamento = parseFormaPagamento(textoNorm);
        if (formaPagamento == null) {
            return "Forma de pagamento inválida. Responda com: 1, 2, 3 ou 4.";
        }

        estado.formaPagamento(formaPagamento);

        if (formaPagamento == FormaPagamento.DINHEIRO) {
            estado.etapa(EtapaBot.AGUARDANDO_TROCO);
            return "Precisa de troco? Se sim, informe o valor. Ex: 100,00. Se não, responda: NAO";
        }

        if (formaPagamento == FormaPagamento.PIX) {
            evolutionApiClientService.enviarChavePix(telefone);
            estado.etapa(EtapaBot.AGUARDANDO_COMPROVANTE_PIX);
            return "Chave PIX enviada. Quando pagar, envie o comprovante para finalizar o pedido.";
        }

        return finalizarPedido(estado, telefone);
    }

    private String tratarTroco(BotSessionState estado, String textoNorm) {
        if ("nao".equals(textoNorm) || "não".equals(textoNorm)) {
            estado.precisaTrocoPara(null);
            return finalizarPedido(estado, null);
        }

        try {
            String normalizado = textoNorm.replace(".", "").replace(",", ".");
            BigDecimal valorTroco = new BigDecimal(normalizado);
            if (valorTroco.compareTo(BigDecimal.ZERO) <= 0) {
                return "Valor de troco inválido. Informe um valor positivo ou responda NAO.";
            }
            estado.precisaTrocoPara(valorTroco);
            return finalizarPedido(estado, null);
        } catch (NumberFormatException ex) {
            return "Não entendi o valor de troco. Exemplo válido: 100,00 ou responda NAO.";
        }
    }

    private String finalizarPedido(BotSessionState estado, String telefone) {
        String tel = telefone == null ? estado.telefone() : telefone;
        if (tel == null || tel.isBlank()) {
            throw new IllegalStateException("Telefone ausente para finalização do pedido.");
        }

        Pedido salvo = criarPedidoCompleto(tel, estado);

        estado.ultimoPedidoId(salvo.getId());
        resetarSessao(estado);
        if (salvo.getTipoPedido() == TipoPedido.GAS_DO_POVO) {
            return "Tudo anotado! Seu pedido com o benefício do Gás do Povo foi enviado para análise. Assim que a equipe aprovar, ele sairá para entrega!";
        }
        return "Pedido confirmado! O motoboy já está a caminho.";
    }

    public void atualizarEstadoCliente(String telefone, String novoEstado) {
        BotSessionState estado = sessoes.computeIfAbsent(telefone, BotSessionState::novo);
        estado.etapa(EtapaBot.valueOf(novoEstado));
    }

    @Transactional
    private String cancelarPedidoPendente(BotSessionState estado, String telefone) {
        Long ultimoPedidoId = estado.ultimoPedidoId();
        if (ultimoPedidoId == null) {
            return "Não encontrei pedido pendente para cancelamento.";
        }

        Pedido pedido = pedidoRepository.findById(ultimoPedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado: " + ultimoPedidoId));

        if (!pedido.getCliente().getTelefone().equals(telefone)) {
            throw new IllegalStateException("Pedido não pertence ao telefone informado.");
        }

        if (pedido.getStatus() != Status.PENDENTE) {
            return "Seu pedido já está em andamento e não pode ser cancelado por aqui.";
        }

        pedido.setStatus(Status.CANCELADO);
        pedido.setDataAtualizacao(LocalDateTime.now());
        pedidoRepository.save(pedido);
        return "Pedido " + pedido.getId() + " cancelado com sucesso.";
    }

    private FormaPagamento parseFormaPagamento(String textoNorm) {
        if ("1".equals(textoNorm) || textoNorm.contains("dinheiro")) {
            return FormaPagamento.DINHEIRO;
        }
        if ("2".equals(textoNorm) || textoNorm.contains("credito")) {
            return FormaPagamento.CARTAO_CREDITO;
        }
        if ("3".equals(textoNorm) || textoNorm.contains("debito")) {
            return FormaPagamento.CARTAO_DEBITO;
        }
        if ("4".equals(textoNorm) || textoNorm.contains("pix")) {
            return FormaPagamento.PIX;
        }
        return null;
    }

    private String mensagemBoasVindas() {
        return "Olá, seja bem-vindo! Como deseja pedir seu gás hoje? \n"
                + "1 - Pedido Normal \n"
                + "2 - Gás do Povo (Requer aprovação de CPF e taxa de R$ 20)";
    }

    private String normalizar(String texto) {
        return texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
    }

    private void resetarSessao(BotSessionState estado) {
        Long ultimoPedidoId = estado.ultimoPedidoId();
        String telefone = estado.telefone();
        estado.etapa(EtapaBot.AGUARDANDO_TIPO_PEDIDO);
        estado.tipoPedido(null);
        estado.nome(null);
        estado.cpf(null);
        estado.enderecoEntrega(null);
        estado.formaPagamento(null);
        estado.precisaTrocoPara(null);
        estado.telefone(telefone);
        estado.ultimoPedidoId(ultimoPedidoId);
    }

    private Pedido criarPedidoCompleto(String telefone, BotSessionState estado) {
        Cliente cliente = clienteRepository.findByTelefone(telefone).orElseGet(Cliente::new);
        if (cliente.getId() == null) {
            cliente.setTelefone(telefone);
            cliente.setDataCadastro(LocalDateTime.now());
        }
        cliente.setNome(estado.nome());
        cliente.setEnderecoPadrao(estado.enderecoEntrega());
        if (estado.tipoPedido() == TipoPedido.GAS_DO_POVO) {
            cliente.setCpf(estado.cpf());
        }
        Cliente clienteSalvo = clienteRepository.save(cliente);

        Pedido pedido = new Pedido();
        pedido.setCliente(clienteSalvo);
        pedido.setStatus(estado.tipoPedido() == TipoPedido.GAS_DO_POVO ? Status.AGUARDANDO_VALIDACAO_CPF : Status.PENDENTE);
        pedido.setTipoPedido(estado.tipoPedido());
        pedido.setFormaPagamento(estado.formaPagamento());
        pedido.setPrecisaTrocoPara(estado.precisaTrocoPara());
        pedido.setEnderecoEntrega(estado.enderecoEntrega());
        pedido.setValorTotal(estado.tipoPedido() == TipoPedido.GAS_DO_POVO ? VALOR_GAS_DO_POVO : valorP13Normal);
        pedido.setDataCriacao(LocalDateTime.now());
        pedido.setDataAtualizacao(LocalDateTime.now());
        return pedidoRepository.save(pedido);
    }

    private enum EtapaBot {
        AGUARDANDO_TIPO_PEDIDO,
        AGUARDANDO_NOME,
        AGUARDANDO_CPF,
        AGUARDANDO_ENDERECO,
        AGUARDANDO_FORMA_PAGAMENTO,
        AGUARDANDO_TROCO,
        AGUARDANDO_COMPROVANTE_PIX
    }

    private static final class BotSessionState {
        private EtapaBot etapa = EtapaBot.AGUARDANDO_TIPO_PEDIDO;
        private TipoPedido tipoPedido;
        private String nome;
        private String cpf;
        private String enderecoEntrega;
        private FormaPagamento formaPagamento;
        private BigDecimal precisaTrocoPara;
        private String telefone;
        private Long ultimoPedidoId;

        private static BotSessionState novo(String telefone) {
            BotSessionState state = new BotSessionState();
            state.telefone = telefone;
            return state;
        }

        private EtapaBot etapa() {
            return etapa;
        }

        private void etapa(EtapaBot etapa) {
            this.etapa = etapa;
        }

        private TipoPedido tipoPedido() {
            return tipoPedido;
        }

        private void tipoPedido(TipoPedido tipoPedido) {
            this.tipoPedido = tipoPedido;
        }

        private String nome() {
            return nome;
        }

        private void nome(String nome) {
            this.nome = nome;
        }

        private String cpf() {
            return cpf;
        }

        private void cpf(String cpf) {
            this.cpf = cpf;
        }

        private String enderecoEntrega() {
            return enderecoEntrega;
        }

        private void enderecoEntrega(String enderecoEntrega) {
            this.enderecoEntrega = enderecoEntrega;
        }

        private FormaPagamento formaPagamento() {
            return formaPagamento;
        }

        private void formaPagamento(FormaPagamento formaPagamento) {
            this.formaPagamento = formaPagamento;
        }

        private BigDecimal precisaTrocoPara() {
            return precisaTrocoPara;
        }

        private void precisaTrocoPara(BigDecimal precisaTrocoPara) {
            this.precisaTrocoPara = precisaTrocoPara;
        }

        private String telefone() {
            return telefone;
        }

        private void telefone(String telefone) {
            this.telefone = telefone;
        }

        private Long ultimoPedidoId() {
            return ultimoPedidoId;
        }

        private void ultimoPedidoId(Long ultimoPedidoId) {
            this.ultimoPedidoId = ultimoPedidoId;
        }
    }
}
