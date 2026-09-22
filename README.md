# Gas Delivery API

API em Spring Boot para gestão de pedidos de gás, com fluxo conversacional via WhatsApp, validação administrativa de CPF para o benefício **Gás do Povo**, despacho para entregadores e controle básico de caixa.

## Tecnologias

- Java 17
- Spring Boot (Web, Data JPA, Validation)
- Maven
- H2 Database (em memória)
- Lombok

## Como executar

1. Garanta que o ambiente está com **JDK 17**.
2. Na raiz do projeto, execute:

```bash
./mvnw spring-boot:run
```

Aplicação padrão: `http://localhost:8080`

## Configurações principais

Arquivo: `src/main/resources/application.properties`

- Banco H2:
  - `spring.datasource.url=jdbc:h2:mem:gasdb`
  - `spring.jpa.hibernate.ddl-auto=update`
  - Console: `http://localhost:8080/h2-console`
- Evolution API:
  - `evolution.api.base-url`
  - `evolution.api.instance`
  - `evolution.api.key`
- PIX:
  - `app.pix.key`

## Fluxo do bot (WhatsApp)

Webhook de entrada:

- `POST /api/webhook/whatsapp`

O bot coleta os dados em ordem linear e só cria o pedido no final:

1. Tipo de pedido
2. CPF (somente Gás do Povo)
3. Nome
4. Endereço
5. Forma de pagamento
6. Troco (se dinheiro) ou comprovante (se PIX)
7. Finalização e persistência

Regra de status ao criar pedido:

- **Pedido Normal**: `PENDENTE`
- **Gás do Povo**: `AGUARDANDO_VALIDACAO_CPF`

## Endpoints administrativos

Base: `/api/admin`

- `GET /api/admin/pedidos?status={STATUS}`  
  Lista pedidos (com filtro opcional por status).

- `PUT /api/admin/pedidos/{id}/despachar`  
  Despacha pedido para entregador.  
  Body:
  ```json
  { "entregadorId": 1 }
  ```

- `PUT /api/admin/pedidos/{id}/cancelar`  
  Cancela pedido.

- `PUT /api/admin/pedidos/{id}/aprovar-cpf`  
  Aprova CPF de pedido do Gás do Povo (status vai para `PENDENTE`).

- `PUT /api/admin/pedidos/{id}/reprovar-cpf`  
  Reprova CPF (status `CPF_REPROVADO`).

- `GET /api/admin/entregadores`  
  Lista entregadores ativos.

- `GET /api/admin/entregadores/caixa`  
  Retorna caixa do dia por entregador.

## Regras de negócio importantes

- Um pedido só pode ser despachado com:
  - endereço preenchido;
  - forma de pagamento preenchida.
- Pedidos do Gás do Povo ficam aguardando validação administrativa antes de entrar na fila normal de despacho.
- Ao aprovar CPF, o pedido já fica em `PENDENTE`, pronto para despacho.

## Estrutura resumida

- `controller/` — endpoints HTTP
- `service/` — regras de negócio e integração com Evolution API
- `entities/` — modelo JPA (`Cliente`, `Pedido`, `Entregador`, enums)
- `repository/` — acesso a dados com Spring Data JPA

