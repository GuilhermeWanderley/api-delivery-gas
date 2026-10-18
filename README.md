# Gas Delivery — API (Binho do Gás)

Backend do sistema de pedidos de gás. A **conversa no WhatsApp é conduzida pelo
Typebot**; quando o cliente confirma, o Typebot chama esta API, que grava o
pedido, controla o fluxo operacional (validação de CPF do Gás do Povo, despacho,
entrega), avisa o cliente pelo WhatsApp e gerencia o **caixa por turno**.

> 📚 **Novo por aqui?** Leia o **[APRENDENDO.md](APRENDENDO.md)** — ele explica
> cada tecnologia usada e em que ordem estudar.
> 🚀 **Vai publicar?** Leia o **[PRODUCAO.md](PRODUCAO.md)**.

---

## 1. Arquitetura

```
   Cliente (WhatsApp)
          │
          ▼
   Evolution API (8090)  ──►  Typebot (nuvem)
          ▲                         │
          │ notificações            │ POST /api/pedidos/via-bot
          │                         ▼
   ┌──────────────────────────────────────────────┐
   │  Esta API (Spring Boot, 8080)                │
   │   ├── PostgreSQL (5433)  ── schema via Flyway│
   │   └── Painel React (5173 dev / nginx prod)   │
   └──────────────────────────────────────────────┘
```

- A API **não fala com o WhatsApp direto** — quem fala é a Evolution API.
- A API e a Evolution conversam pela **rede interna do Docker**
  (`http://evolution-api:8080`), então nenhuma porta precisa ficar exposta.
- O **painel** consome a API por HTTP com login.

---

## 2. Tecnologias (o que é cada uma e por que está aqui)

| Tecnologia | O que é | Por que usamos |
|---|---|---|
| **Java 17** | Linguagem | Base de tudo no backend |
| **Spring Boot 3.3** | Framework que "monta" a aplicação | Servidor HTTP, injeção de dependência, configuração |
| **Spring Web** | Camada REST | Transforma métodos Java em endpoints HTTP |
| **Spring Data JPA / Hibernate** | ORM | Escrever `repository.findAll()` em vez de SQL |
| **PostgreSQL 16** | Banco relacional | Dados de verdade (pedidos, clientes, caixas) |
| **H2** | Banco em memória | Rodar/testar sem subir Postgres |
| **Flyway** | Versionador de schema | O banco evolui por migrações versionadas, não "na sorte" |
| **Spring Security** | Autenticação/autorização | Protege o painel e o endpoint do robô |
| **Lombok** | Gerador de código | `@Getter`/`@Setter` em vez de escrever à mão |
| **Maven** (`./mvnw`) | Build e dependências | Compila, roda testes, empacota |
| **Docker / Compose** | Empacotamento | "Roda igual em qualquer máquina" |
| **Evolution API** | Gateway não-oficial do WhatsApp | Enviar/receber mensagens |
| **Typebot** | Construtor de fluxo conversacional | A conversa com o cliente |
| **ngrok** | Túnel público temporário | Fazer a nuvem (Typebot) alcançar sua máquina em dev |

---

## 3. Como rodar

### Desenvolvimento (H2 em memória, sem Docker)

```bash
./mvnw spring-boot:run
```

- API: `http://localhost:8080`
- Console do banco H2: `http://localhost:8080/h2-console`
  (JDBC URL `jdbc:h2:mem:gasdb`, usuário `sa`, sem senha)
- Login do painel: `admin` / `binho-admin` (padrão de desenvolvimento)

### Docker (Postgres de verdade — é assim que roda em produção)

```bash
cp .env.example .env      # preencha as chaves
sg docker -c "docker compose up -d --build"
```

- API: `http://127.0.0.1:8080`
- Postgres: `127.0.0.1:5433`
- Ver logs: `sg docker -c "docker logs -f gas-delivery-app-1"`

> ⚠️ Se aparecer `permission denied ... docker.sock`, seu usuário não está no
> grupo `docker`: `sudo usermod -aG docker $USER` (e entre de novo na sessão).

### Testes

```bash
./mvnw test          # roda tudo
./mvnw test -Dtest=CaixaServiceTest   # roda uma classe
```

Hoje são **81 testes**. Eles rodam com H2, sem precisar de Docker.

---

## 4. Mapa do código (onde fica cada coisa)

```
src/main/java/com/example/gas_delivery/
├── GasDeliveryApplication.java   ← ponto de entrada (main)
├── config/
│   ├── SecurityConfig.java       ← login do painel, CORS, regras de acesso
│   └── RestTemplateConfig.java   ← cliente HTTP p/ Evolution (com timeout)
├── security/
│   ├── BotApiKeyFilter.java      ← exige X-Api-Key no endpoint do robô
│   └── ValidadorSegurancaProducao.java ← trava de subida insegura
├── controller/                   ← recebem HTTP e devolvem JSON
│   ├── PedidoBotController.java  ← /api/pedidos/via-bot (chamado pelo Typebot)
│   ├── AdminController.java      ← /api/admin/** (painel)
│   ├── EntregadorController.java ← /api/entregadores/**
│   ├── PedidoController.java
│   └── ClienteController.java
├── service/                      ← REGRAS DE NEGÓCIO ficam aqui
│   ├── PedidoBotService.java     ← cria pedido do robô + idempotência
│   ├── AdminService.java         ← despachar/entregar/cancelar/aprovar CPF
│   ├── CaixaService.java         ← abrir/fechar turno + liquidação
│   ├── ConfiguracaoService.java  ← preços e promoção de quarta
│   ├── EntregadorService.java
│   └── WhatsappNotificationService.java ← enviar mensagem (retry/backoff)
├── entities/                     ← classes que viram tabelas
│   ├── Pedido, Cliente, Entregador, Caixa, ResumoCaixaEntregador, Configuracao
│   └── Status, TipoPedido, FormaPagamento, StatusCaixa (enums)
├── repository/                   ← interfaces de acesso a dados
├── dto/                          ← formato dos dados que entram/saem
└── exceptions/                   ← tratamento centralizado de erro

src/main/resources/
├── application.properties         ← config de dev (H2)
├── application-docker.properties  ← config de Docker/Postgres
└── db/migration/                  ← migrações do banco (Flyway)
```

**A regra de ouro da organização:** `controller` só recebe/valida a chamada →
`service` decide o que fazer → `repository` salva/lê. Se você colocar regra de
negócio no controller, fica difícil testar.

---

## 5. Regras de negócio

### Preços (configuráveis pelo painel)

| Item | Valor |
|---|---|
| Gás comum (P13) à vista (dinheiro, PIX, débito) | `precoGasVista` — padrão **R$ 150,00** |
| Gás comum no cartão de crédito | `precoGasCredito` — padrão **R$ 155,00** |
| Gás do Povo | `precoGasPovo` — padrão **R$ 20,00** |
| Promoção de quarta-feira | `descontoQuarta` (padrão R$ 10) sobre o gás comum |

Os preços ficam na tabela `configuracoes` (linha única) e são editados na aba
**Configurações** do painel — **sem precisar mexer no código nem reiniciar**.

### Status do pedido

| Evento | Status resultante |
|---|---|
| Pedido normal criado | `PENDENTE` |
| Pedido Gás do Povo criado | `AGUARDANDO_VALIDACAO_CPF` |
| CPF aprovado | `PENDENTE` |
| CPF reprovado | `CPF_REPROVADO` |
| Despachado | `DESPACHADO` |
| Entregue | `ENTREGUE` |
| Cancelado | `CANCELADO` |

Transições permitidas: só `PENDENTE` pode ser despachado; só `DESPACHADO` pode
virar `ENTREGUE`; `ENTREGUE` não pode ser cancelado.

### Caixa por turno

1. O operador **abre o caixa** no início do dia (não é possível abrir dois).
2. Enquanto aberto, o painel mostra o resumo parcial (dinheiro/cartão/PIX).
3. Ao **fechar**, o sistema:
   - congela um relatório por entregador (histórico permanente);
   - **zera o saldo** (`caixaAtual`) de cada entregador — o dinheiro foi
     repassado. Se sobrou valor que o turno não registrou (entrega feita com o
     caixa fechado), ele entra no relatório para não se perder.
4. O painel avisa em faixa âmbar quando o caixa está fechado.

### Dinheiro no caixa

Entram no acerto do entregador apenas pedidos `ENTREGUE` pagos em **dinheiro ou
cartão**. **PIX cai direto na conta e não entra no caixa físico.**

---

## 6. Endpoints

Todas as rotas `/api/**` exigem login (HTTP Basic), **exceto** o webhook do robô.

### Entrada do robô
- `POST /api/pedidos/via-bot` — cria o pedido. Protegido pelo header
  `X-Api-Key` (quando `BOT_API_KEY` está definida).
  Aceita `idempotencyKey` opcional para não duplicar pedido reenviado.

### Pedidos (`/api/admin`)
- `GET /api/admin/pedidos?status={STATUS}` — lista (ordem de chegada)
- `PUT /api/admin/pedidos/{id}/aprovar-cpf` · `/reprovar-cpf`
- `PUT /api/admin/pedidos/{id}/despachar` — corpo `{ "entregadorId": 2 }`
- `PUT /api/admin/pedidos/{id}/entregue`
- `PUT /api/admin/pedidos/{id}/cancelar`

### Caixa (`/api/admin/caixa`)
- `GET /atual` — `{ aberto, caixa }`
- `POST /abrir` — corpo `{ "abertoPor": "..." }`
- `POST /fechar` — corpo `{ "observacao": "..." }`
- `GET /historico` — turnos fechados com relatório congelado

### Configuração de preços (`/api/admin/configuracao`)
- `GET` — lê os preços atuais
- `PUT` — atualiza (mesmo corpo do `GET`)

### Entregadores (`/api/entregadores`)
- `GET` · `POST` · `PUT /{id}/ativar` · `PUT /{id}/desativar` · `DELETE /{id}`

### Infra
- `GET /actuator/health` — **público**, usado pelos healthchecks do Docker

---

## 7. Segurança

| Camada | Como funciona |
|---|---|
| **Painel** | HTTP Basic + senha em BCrypt (`APP_ADMIN_USUARIO` / `APP_ADMIN_SENHA`) |
| **Robô** | Header `X-Api-Key` com `BOT_API_KEY` (vazio = aberto, só em dev) |
| **CORS** | Restrito a `APP_CORS_ORIGINS` (nunca `*`) |
| **Produção** | `APP_AMBIENTE=producao` faz a app **recusar subir** insegura |
| **Rede** | Todas as portas publicadas em `127.0.0.1` |

Detalhes e como trocar a senha: veja o **[PRODUCAO.md](PRODUCAO.md)**.

---

## 8. Banco de dados e migrações

O schema é versionado em `src/main/resources/db/migration/`:

| Versão | O que faz |
|---|---|
| `V1__schema_inicial.sql` | Schema completo (baseline) |
| `V2__idempotencia_pedido.sql` | Coluna `chave_idempotencia` + índice único |

**Para alterar o schema:** crie `V3__descricao.sql`, reinicie e confira:

```bash
sg docker -c "docker exec gas-delivery-db-1 psql -U gas -d gasdelivery \
  -c 'SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank'"
```

> ⚠️ **Nunca edite uma migração já aplicada** — o Flyway guarda um checksum e
> recusa subir. Crie a próxima versão.

---

## 9. Receitas do dia a dia

**Ver os logs da API**
```bash
sg docker -c "docker logs -f gas-delivery-app-1"
sg docker -c "docker logs gas-delivery-app-1 2>&1 | grep -i erro"
```

**Entrar no banco**
```bash
sg docker -c "docker exec -it gas-delivery-db-1 psql -U gas -d gasdelivery"
# dentro: \dt (tabelas) · \d pedidos (estrutura) · SELECT * FROM pedidos;
```

**Rodar só os testes de uma área**
```bash
./mvnw test -Dtest='CaixaServiceTest'
```

**Mudar um preço** → aba **Configurações** do painel (ou `PUT /api/admin/configuracao`).

**Adicionar um campo novo numa tabela**
1. Crie `V3__meu_campo.sql` com o `ALTER TABLE`.
2. Adicione o campo na entidade (`entities/`).
3. Reinicie — o `ddl-auto=validate` avisa se a entidade e o banco saírem de sincronia.

**Reconectar o WhatsApp**
```bash
curl -s "http://127.0.0.1:8090/instance/connect/botgas" -H "apikey: $EVOLUTION_API_KEY"
```
Ou abra o manager: `http://127.0.0.1:8090/manager`

---

## 10. Problemas comuns

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| Painel pede login em loop | Credencial errada | Confira `APP_ADMIN_USUARIO`/`APP_ADMIN_SENHA` no `.env` |
| `401` no painel | Sessão antiga | Clique em **Sair** e entre de novo |
| Bot não responde | Sessão do WhatsApp caiu | `GET /instance/connectionState/botgas` → precisa ser `open` |
| Pedido não chega | Endpoint do robô com chave errada | Veja se `BOT_API_KEY` bate com o header do Typebot |
| `403` estranho no navegador | CORS | Adicione a origem em `APP_CORS_ORIGINS` |
| App não sobe em produção | `APP_AMBIENTE=producao` + config fraca | Leia a mensagem de erro — ela diz o que corrigir |
| Cliente não recebe notificação | WhatsApp desconectado ou 401 | `docker logs ... | grep "Falha ao enviar"` |
| Pedido duplicado | Typebot não envia `idempotencyKey` | Adicione o campo no bloco HTTP |

---

## 11. Glossário

| Termo | Significado |
|---|---|
| **Endpoint** | Uma URL que a API expõe (ex.: `POST /api/pedidos/via-bot`) |
| **Webhook** | Uma URL que OUTRO sistema chama quando algo acontece |
| **DTO** | Objeto que carrega dados entre camadas (o formato do JSON) |
| **Entidade** | Classe Java que representa uma tabela |
| **Repository** | Interface que faz as consultas ao banco |
| **Migration** | Script versionado que altera o schema do banco |
| **Idempotência** | Chamar a mesma operação duas vezes dá o mesmo resultado |
| **Backoff** | Esperar cada vez mais entre tentativas |
| **CORS** | Regra do navegador sobre qual site pode chamar a API |
| **HTTP Basic** | Autenticação simples: usuário:senha codificados no header |
| **BCrypt** | Algoritmo que transforma a senha em hash irreversível |
| **Healthcheck** | Endpoint que diz "estou vivo" — o Docker usa para reiniciar |
| **Volume** | Pasta do Docker que sobrevive ao container ser recriado |

---

## 12. Estrutura de pastas (visão rápida)

```
gas-delivery/
├── README.md              ← este arquivo
├── APRENDENDO.md          ← trilha de estudo das tecnologias
├── PRODUCAO.md            ← deploy, backup, TLS, monitoramento
├── .env.example           ← modelo das variáveis de ambiente
├── docker-compose.yml     ← app + Postgres
├── pom.xml                ← dependências (Maven)
└── src/
    ├── main/java/...      ← código
    ├── main/resources/    ← config + migrações
    └── test/java/...      ← testes (81)
```
