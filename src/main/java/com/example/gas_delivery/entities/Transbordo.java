package com.example.gas_delivery.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Solicitação de atendimento humano.
 *
 * <p>Criada quando o cliente escolhe "falar com um atendente" no robô ou quando
 * manda um áudio (que o bot não consegue interpretar). O painel exibe essas
 * solicitações como alerta até alguém marcá-las como atendidas.
 */
@Entity
@Table(name = "transbordos")
@Getter
@Setter
@NoArgsConstructor
public class Transbordo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Telefone/remoteJid de quem pediu (normalizado só com dígitos quando possível). */
    @Column(nullable = false, length = 128)
    private String telefone;

    /** Nome do cliente, se já conhecido. */
    @Column(length = 255)
    private String nome;

    /** Por que pediu atendimento: ATENDENTE, AUDIO, etc. */
    @Column(nullable = false, length = 40)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusTransbordo status = StatusTransbordo.PENDENTE;

    @Column(nullable = false)
    private LocalDateTime dataCriacao;

    private LocalDateTime dataAtendimento;
}
