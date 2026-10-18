package com.example.gas_delivery.service;

import com.example.gas_delivery.dto.EntregadorRequestDTO;
import com.example.gas_delivery.entities.Entregador;
import com.example.gas_delivery.repository.EntregadorRepository;
import com.example.gas_delivery.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntregadorServiceTest {

    @Mock
    private EntregadorRepository repository;

    @Mock
    private PedidoRepository pedidoRepository;

    private EntregadorService service;

    @BeforeEach
    void setUp() {
        service = new EntregadorService(repository, pedidoRepository);
    }

    @Test
    @DisplayName("Deve cadastrar entregador ativo")
    void deveCadastrarEntregador() {
        when(repository.findByTelefone("5581888888888")).thenReturn(Optional.empty());
        when(repository.save(any(Entregador.class))).thenAnswer(inv -> inv.getArgument(0));

        Entregador resultado = service.cadastrar(new EntregadorRequestDTO("Motoboy", "5581888888888"));

        assertEquals("Motoboy", resultado.getNome());
        assertTrue(resultado.isAtivo());
        verify(repository).save(any(Entregador.class));
    }

    @Test
    @DisplayName("Deve rejeitar telefone duplicado")
    void deveRejeitarTelefoneDuplicado() {
        when(repository.findByTelefone("5581888888888")).thenReturn(Optional.of(new Entregador()));

        assertThrows(IllegalArgumentException.class,
                () -> service.cadastrar(new EntregadorRequestDTO("Motoboy", "5581888888888")));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Deve desativar entregador")
    void deveDesativar() {
        Entregador existente = new Entregador();
        existente.setId(1L);
        existente.setAtivo(true);
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        when(repository.save(any(Entregador.class))).thenAnswer(inv -> inv.getArgument(0));

        Entregador resultado = service.desativar(1L);

        assertFalse(resultado.isAtivo());
    }

    @Test
    @DisplayName("Deve ativar entregador")
    void deveAtivar() {
        Entregador existente = new Entregador();
        existente.setId(1L);
        existente.setAtivo(false);
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        when(repository.save(any(Entregador.class))).thenAnswer(inv -> inv.getArgument(0));

        Entregador resultado = service.ativar(1L);

        assertTrue(resultado.isAtivo());
    }
}
