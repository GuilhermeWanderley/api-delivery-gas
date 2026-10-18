package com.example.gas_delivery.service;

import com.example.gas_delivery.entities.Configuracao;
import com.example.gas_delivery.repository.ConfiguracaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lê e atualiza a configuração global de preços (linha única id = 1).
 */
@Service
public class ConfiguracaoService {

    private final ConfiguracaoRepository repository;

    public ConfiguracaoService(ConfiguracaoRepository repository) {
        this.repository = repository;
    }

    /** Retorna a configuração atual; cria a padrão se ainda não existir. */
    @Transactional
    public Configuracao obter() {
        return repository.findById(Configuracao.ID_PADRAO)
                .orElseGet(() -> repository.save(new Configuracao()));
    }

    @Transactional
    public Configuracao atualizar(Configuracao dados) {
        Configuracao atual = obter();
        atual.setPrecoGasVista(dados.getPrecoGasVista());
        atual.setPrecoGasCredito(dados.getPrecoGasCredito());
        atual.setPrecoGasPovo(dados.getPrecoGasPovo());
        atual.setDescontoQuarta(dados.getDescontoQuarta());
        atual.setPromocaoQuartaAtiva(dados.isPromocaoQuartaAtiva());
        return repository.save(atual);
    }
}
