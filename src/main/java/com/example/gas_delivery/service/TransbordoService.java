package com.example.gas_delivery.service;

import com.example.gas_delivery.entities.StatusTransbordo;
import com.example.gas_delivery.entities.Transbordo;
import com.example.gas_delivery.repository.TransbordoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Registra e gerencia pedidos de atendimento humano (transbordo).
 *
 * <p>Usado quando o cliente escolhe "falar com um atendente" no robô ou quando
 * envia um áudio — situações em que a automação não resolve.
 */
@Service
public class TransbordoService {

    private static final Logger log = LoggerFactory.getLogger(TransbordoService.class);

    /** Janela de deduplicação: evita 3 alertas se o cliente insistir em 2 minutos. */
    private static final long MINUTOS_DEDUPLICACAO = 5;

    public static final String MOTIVO_ATENDENTE = "ATENDENTE";
    public static final String MOTIVO_AUDIO = "AUDIO";

    private final TransbordoRepository repository;

    public TransbordoService(TransbordoRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Transbordo solicitar(String telefone, String nome, String motivo) {
        String telefoneLimpo = normalizar(telefone);
        String motivoFinal = (motivo == null || motivo.isBlank()) ? MOTIVO_ATENDENTE : motivo.trim();

        LocalDateTime limite = LocalDateTime.now().minusMinutes(MINUTOS_DEDUPLICACAO);
        if (repository.existsByTelefoneAndStatusAndDataCriacaoAfter(
                telefoneLimpo, StatusTransbordo.PENDENTE, limite)) {
            log.info("Transbordo já pendente para {} nos últimos {} min — não duplicando.",
                    telefoneLimpo, MINUTOS_DEDUPLICACAO);
            return null;
        }

        Transbordo t = new Transbordo();
        t.setTelefone(telefoneLimpo);
        t.setNome(nome == null || nome.isBlank() ? null : nome.trim());
        t.setMotivo(motivoFinal);
        t.setStatus(StatusTransbordo.PENDENTE);
        t.setDataCriacao(LocalDateTime.now());

        Transbordo salvo = repository.save(t);
        log.warn("[TRANSBORDO] atendimento humano solicitado — motivo={} telefone={} id={}",
                motivoFinal, telefoneLimpo, salvo.getId());
        return salvo;
    }

    @Transactional(readOnly = true)
    public List<Transbordo> pendentes() {
        return repository.findByStatusOrderByDataCriacaoAsc(StatusTransbordo.PENDENTE);
    }

    @Transactional(readOnly = true)
    public long contarPendentes() {
        return repository.countByStatus(StatusTransbordo.PENDENTE);
    }

    @Transactional
    public Transbordo atender(Long id) {
        Transbordo t = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Solicitação não encontrada: " + id));
        if (t.getStatus() != StatusTransbordo.PENDENTE) {
            throw new IllegalStateException("Esta solicitação já foi finalizada.");
        }
        t.setStatus(StatusTransbordo.ATENDIDO);
        t.setDataAtendimento(LocalDateTime.now());
        return repository.save(t);
    }

    /** Mantém o identificador original (pode ser @lid ou @s.whatsapp.net). */
    private String normalizar(String telefone) {
        if (telefone == null) {
            throw new IllegalArgumentException("Telefone é obrigatório para solicitar atendimento.");
        }
        String limpo = telefone.trim();
        if (limpo.isEmpty()) {
            throw new IllegalArgumentException("Telefone é obrigatório para solicitar atendimento.");
        }
        return limpo.length() > 128 ? limpo.substring(0, 128) : limpo;
    }
}
