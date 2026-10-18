package com.example.gas_delivery.service;

import com.example.gas_delivery.dto.EntregadorRequestDTO;
import com.example.gas_delivery.entities.Entregador;
import com.example.gas_delivery.repository.EntregadorRepository;
import com.example.gas_delivery.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EntregadorService {

    private final EntregadorRepository repository;
    private final PedidoRepository pedidoRepository;

    public EntregadorService(EntregadorRepository repository, PedidoRepository pedidoRepository) {
        this.repository = repository;
        this.pedidoRepository = pedidoRepository;
    }

    @Transactional
    public Entregador cadastrar(EntregadorRequestDTO request) {
        if (repository.findByTelefone(request.telefone()).isPresent()) {
            throw new IllegalArgumentException("Já existe entregador com este telefone.");
        }
        Entregador entregador = new Entregador();
        entregador.setNome(request.nome());
        entregador.setTelefone(request.telefone());
        entregador.setAtivo(true);
        return repository.save(entregador);
    }

    @Transactional(readOnly = true)
    public List<Entregador> listarTodos() {
        return repository.findAll();
    }

    @Transactional
    public Entregador ativar(Long id) {
        return alterarAtivo(id, true);
    }

    @Transactional
    public Entregador desativar(Long id) {
        return alterarAtivo(id, false);
    }

    private Entregador alterarAtivo(Long id, boolean ativo) {
        Entregador entregador = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Entregador não encontrado: " + id));
        entregador.setAtivo(ativo);
        return repository.save(entregador);
    }

    @Transactional
    public void excluir(Long id) {
        Entregador entregador = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Entregador não encontrado: " + id));
        if (pedidoRepository.existsByEntregadorId(id)) {
            throw new IllegalStateException(
                    "Não é possível excluir este entregador: ele possui pedidos vinculados. Desative-o em vez disso.");
        }
        repository.delete(entregador);
    }
}
