package com.example.gas_delivery.controller;

import com.example.gas_delivery.entities.Transbordo;
import com.example.gas_delivery.service.TransbordoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Transbordo: quando o cliente pede atendimento humano.
 *
 * <ul>
 *   <li>{@code POST /api/atendimento/solicitar} — chamado pelo Typebot (X-Api-Key)</li>
 *   <li>{@code GET /api/admin/atendimento/pendentes} — painel</li>
 *   <li>{@code PUT /api/admin/atendimento/{id}/atender} — painel</li>
 * </ul>
 */
@RestController
@CrossOrigin(origins = "*")
public class TransbordoController {

    private final TransbordoService service;

    public TransbordoController(TransbordoService service) {
        this.service = service;
    }

    /** Entrada do robô. */
    @PostMapping("/api/atendimento/solicitar")
    public ResponseEntity<Map<String, Object>> solicitar(@RequestBody(required = false) Map<String, String> body) {
        String telefone = body == null ? null : body.get("telefone");
        String nome = body == null ? null : body.get("nome");
        String motivo = body == null ? null : body.get("motivo");

        Transbordo t = service.solicitar(telefone, nome, motivo);
        if (t == null) {
            return ResponseEntity.ok(Map.of("status", "DUPLICADO",
                    "mensagem", "Já existe uma solicitação pendente recente."));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", t.getId(),
                "status", t.getStatus().name(),
                "mensagem", "Solicitação registrada. Um atendente vai falar com você."));
    }

    /** Painel: lista as solicitações aguardando alguém. */
    @GetMapping("/api/admin/atendimento/pendentes")
    public ResponseEntity<List<Transbordo>> pendentes() {
        return ResponseEntity.ok(service.pendentes());
    }

    /** Painel: quantas estão pendentes (para o badge/alert). */
    @GetMapping("/api/admin/atendimento/pendentes/contador")
    public ResponseEntity<Map<String, Long>> contador() {
        return ResponseEntity.ok(Map.of("pendentes", service.contarPendentes()));
    }

    /** Painel: marca como atendida. */
    @PutMapping("/api/admin/atendimento/{id}/atender")
    public ResponseEntity<Transbordo> atender(@PathVariable Long id) {
        return ResponseEntity.ok(service.atender(id));
    }
}
