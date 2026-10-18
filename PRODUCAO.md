# Binho do Gás — Guia de Produção

Documento operacional: o que fazer para tirar o sistema da máquina de
desenvolvimento e colocá-lo rodando com segurança para a cliente.

---

## 1. Arquitetura

```
WhatsApp ──► Evolution API (8090) ──► Typebot (cloud) ──► POST /api/pedidos/via-bot
                  ▲                                              │
                  │ notificações (retry + backoff)               ▼
             gas-delivery (8080) ◄──── Postgres 16 (5433) ──── painel React
```

| Serviço | Porta | Observação |
|---|---|---|
| gas-delivery (API) | 8080 | Spring Boot 3.3 / Java 17 |
| Postgres | 5433 | volume `pgdata` |
| Evolution API | 8090 | stack separada (`~/evolution-api`) |
| Painel (dev) | 5173 | em produção é servido estático |

---

## 2. Segredos e variáveis de ambiente

**Regra:** nenhum segredo no código. Tudo vem do `.env` (que está no `.gitignore`).

| Variável | Para que serve | Obrigatória em produção |
|---|---|---|
| `DB_PASSWORD` | senha do Postgres | ✅ |
| `EVOLUTION_API_KEY` | autentica na Evolution API | ✅ |
| `APP_AMBIENTE` | `producao` ativa o **bloqueio de subida insegura** | ✅ no servidor |
| `APP_ADMIN_USUARIO` | login do painel | ✅ |
| `APP_ADMIN_SENHA` | senha do painel (mín. 12 caracteres) | ✅ (troque o padrão!) |
| `BOT_API_KEY` | chave que o Typebot envia em `X-Api-Key` | ✅ |
| `APP_CORS_ORIGINS` | domínio(s) do painel liberados no CORS | ✅ |
| `EVOLUTION_TIMEOUT_*` / `EVOLUTION_MAX_TENTATIVAS` | resiliência da integração | opcional |

Gerar chaves fortes:

```bash
openssl rand -hex 32     # use como BOT_API_KEY
openssl rand -base64 24  # use como APP_ADMIN_SENHA e DB_PASSWORD
```

⚠️ **Nunca** versione o `.env`. O `.env.example` é o modelo público.

---

## 3. Segurança já implementada

- **Painel protegido** por HTTP Basic (Spring Security, senha com BCrypt).
  Sem credencial → `401`. Sessão stateless.
- **Endpoint do robô** (`POST /api/pedidos/via-bot`) protegido por chave
  compartilhada no header `X-Api-Key`. Se `BOT_API_KEY` estiver vazio, o
  endpoint fica aberto e um **aviso é registrado no startup** — só use assim
  localmente.
- **CORS restrito** a `APP_CORS_ORIGINS` (sem `*`). Origem não autorizada → `403`.
- **Idempotência**: requisições repetidas do robô não geram pedido duplicado
  (campo `idempotencyKey` + restrição única no banco).
- **Retry com backoff exponencial** nas notificações de WhatsApp (3 tentativas);
  erros 4xx não são repetidos (não adianta) e 429 é aguardado.
- **Timeouts** de conexão (3s) e leitura (8s) na chamada à Evolution API —
  evita thread presa.
- `/actuator/health` continua público (usado pelos healthchecks do Docker).

### Trava de subida insegura (`APP_AMBIENTE=producao`)

Com `APP_AMBIENTE=producao`, a aplicação **se recusa a iniciar** se encontrar:

- `APP_ADMIN_SENHA` vazia, igual ao padrão antigo de desenvolvimento ou com
  menos de 12 caracteres;
- `BOT_API_KEY` vazia ou com menos de 24 caracteres;
- `APP_CORS_ORIGINS` apontando apenas para `localhost`/`127.0.0.1`.

A mensagem de erro lista exatamente o que corrigir. É proposital: **melhor não
subir do que subir exposto.**

```bash
# no servidor
APP_AMBIENTE=producao
```

Se `APP_ADMIN_SENHA` ficar vazia, o sistema **gera uma senha aleatória** e a
imprime no log — nunca deixa o painel com senha vazia (senha vazia seria aceita
no login). A senha muda a cada reinício, então defina a variável.

### Portas: já vêm fechadas

No `docker-compose.yml` todas as portas estão publicadas apenas em
`127.0.0.1` — ou seja, **não respondem para a internet** mesmo com o firewall
liberado:

| Serviço | Porta |
|---|---|
| gas-delivery (API) | `127.0.0.1:8080` |
| Postgres do sistema | `127.0.0.1:5433` |
| Evolution API | `127.0.0.1:8090` |
| Postgres da Evolution | `127.0.0.1:5432` |
| Redis da Evolution | `127.0.0.1:6379` |

O backend fala com a Evolution pela **rede interna do Docker**
(`http://evolution-api:8080`), sem passar pelo host — por isso a 8090 pode ficar
fechada.

Para conferir:

```bash
ss -tlnp | grep -E '8080|8090|5432|5433|6379'   # deve mostrar 127.0.0.1
```

> ⚠️ Melhoria pendente: o Redis da Evolution roda **sem senha**
> (`redis-server --appendonly yes`). Hoje isso não é explorável porque a porta
> está em localhost, mas o certo é adicionar `--requirepass` e ajustar o
> `CACHE_REDIS_URI` para `redis://:SENHA@redis:6379/6`.

### O que ainda falta para TLS

O certificado **não** é resolvido pela aplicação — use um proxy reverso.
Configuração de referência (nginx + Certbot):

```nginx
server {
    listen 443 ssl http2;
    server_name painel.binhodogas.com.br;

    ssl_certificate     /etc/letsencrypt/live/painel.binhodogas.com.br/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/painel.binhodogas.com.br/privkey.pem;

    # Painel (arquivos estáticos gerados por "npm run build")
    root /var/www/binho-painel;
    index index.html;
    location / { try_files $uri $uri/ /index.html; }

    # API
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}

server {
    listen 80;
    server_name painel.binhodogas.com.br;
    return 301 https://$host$request_uri;
}
```

Depois de publicar, ajuste no `.env`:

```
APP_CORS_ORIGINS=https://painel.binhodogas.com.br
```

> **Inbound policy:** a única porta que precisa ficar exposta é a **443**.
> A 8080, 5433 e 8090 devem escutar apenas em `127.0.0.1`.
> Se a Evolution API não precisar receber webhooks externos, não exponha a 8090.

---

## 4. Migrações do banco (Flyway)

O schema é **versionado** em `src/main/resources/db/migration/`:

| Versão | Arquivo | O que faz |
|---|---|---|
| V1 | `V1__schema_inicial.sql` | Schema completo (baseline do banco que já existia) |
| V2 | `V2__idempotencia_pedido.sql` | Coluna `chave_idempotencia` + índice único |

**Como funciona:**
- No perfil `docker` o Flyway roda no startup; no perfil de testes (H2) fica desligado.
- `baseline-on-migrate=true` + `baseline-version=1`: um banco **já existente** é
  marcado como versão 1 (a V1 não é reexecutada), e só as migrações novas rodam.
  Em um banco **vazio** (VPS nova), a V1 cria tudo do zero.
- `spring.jpa.hibernate.ddl-auto=validate`: o Hibernate **não altera** mais o
  schema — ele apenas confere se as entidades batem com o banco. Se alguém
  mudar uma entidade sem criar a migração, a aplicação **não sobe** (fail fast).

**Para alterar o schema daqui pra frente:**

```bash
# 1. crie o próximo arquivo, sempre com o próximo número
src/main/resources/db/migration/V3__descricao_curta.sql

# 2. use SQL idempotente quando fizer sentido
ALTER TABLE public.pedidos ADD COLUMN IF NOT EXISTS observacao character varying(255);

# 3. reinicie e confira o histórico
sg docker -c "docker compose up -d --build"
sg docker -c "docker exec gas-delivery-db-1 psql -U gas -d gasdelivery \
  -c 'SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank'"
```

> ⚠️ **Nunca edite uma migração já aplicada.** O Flyway guarda um checksum e
> recusa subir se o arquivo mudou — crie uma V(n+1) em vez disso.

---

## 5. Backup do banco

O volume `pgdata` guarda tudo (pedidos, clientes, caixas). Faça dump diário:

```bash
#!/usr/bin/env bash
# /opt/binho/backup.sh
set -euo pipefail
DIR=/opt/binho/backups
mkdir -p "$DIR"
ARQ="$DIR/gasdelivery-$(date +%Y-%m-%d-%H%M).sql.gz"

sg docker -c "docker compose -f /opt/binho/gas-delivery/docker-compose.yml \
  exec -T db pg_dump -U gas gasdelivery" | gzip > "$ARQ"

# mantém 14 dias
find "$DIR" -name '*.sql.gz' -mtime +14 -delete
echo "backup ok: $ARQ"
```

Agendar no cron (`crontab -e`):

```
0 2 * * * /opt/binho/backup.sh >> /var/log/binho-backup.log 2>&1
```

**Restaurar:**

```bash
gunzip -c backups/gasdelivery-2026-10-16-0200.sql.gz | \
  sg docker -c "docker compose exec -T db psql -U gas -d gasdelivery"
```

> Teste a restauração **antes** de precisar dela de verdade.

---

## 6. Monitoramento

| O que observar | Como |
|---|---|
| API viva | `curl -f https://painel.binhodogas.com.br/api/... ` ou `GET /actuator/health` |
| Containers | `docker ps` (todos com `restart: unless-stopped`) |
| **Sessão do WhatsApp** | `GET http://127.0.0.1:8090/instance/connectionState/botgas` → precisa ser `"state":"open"` |
| Falhas de notificação | `docker logs gas-delivery-app-1 \| grep "Falha ao enviar"` |
| Disco / banco | `docker exec gas-delivery-db-1 pg_isready` |

### ⚠️ Risco nº 1: a sessão do WhatsApp cai

A sessão da Evolution API **desconecta** se o celular ficar sem internet por
muito tempo ou se o container for recriado. Quando isso acontece o robô para de
responder **silenciosamente** (do ponto de vista do cliente).

Mitigação recomendada:

```bash
# alerta simples a cada 5 minutos
*/5 * * * * curl -s http://127.0.0.1:8090/instance/connectionState/botgas \
  -H "apikey: $EVOLUTION_API_KEY" | grep -q '"state":"open"' \
  || echo "WHATSAPP CAIU $(date)" >> /var/log/binho-whatsapp.log
```

Se cair: escaneie o QR em `http://127.0.0.1:8090/manager`.

---

## 7. Passo a passo do deploy

1. **VPS** com Docker + Docker Compose instalados (Ubuntu 22.04+).
2. Clonar/copiar `gas-delivery` e `evolution-api` para a VPS.
3. Criar o `.env` a partir do `.env.example` **com senhas novas**.
4. Subir os containers:
   ```bash
   sg docker -c "docker compose up -d --build"
   ```
5. Conferir: `curl -f http://127.0.0.1:8080/actuator/health`
6. Subir a Evolution API e **escanear o QR** (`/manager`).
7. Configurar a instância (Typebot + ignorar grupos):
   ```bash
   curl -X POST http://127.0.0.1:8090/typebot/create/botgas \
     -H "apikey: $EVOLUTION_API_KEY" -H "Content-Type: application/json" \
     -d '{"url":"https://typebot.co","typebot":"my-typebot-fr0g096","triggerType":"all","delayMessage":500}'
   ```
8. No painel: **Configurações** → conferir os preços.
9. Gerar o build do painel e servir via nginx:
   ```bash
   cd gas-panel-github && npm ci && npm run build   # gera dist/
   sudo cp -r dist/* /var/www/binho-painel/
   ```
10. Certificado TLS:
    ```bash
    sudo certbot --nginx -d painel.binhodogas.com.br
    ```
11. No Typebot, adicionar o header `X-Api-Key: <BOT_API_KEY>` no bloco HTTP.
12. **Teste de fumaça:** pedido real pelo WhatsApp → aparece no painel →
    despachar → entregue → notificação chega no celular do cliente.

---

## 8. Checklist antes de considerar "no ar"

- [ ] `APP_ADMIN_SENHA` trocada (não é mais o padrão)
- [ ] `BOT_API_KEY` definida e configurada no Typebot
- [ ] `APP_CORS_ORIGINS` com o domínio real
- [ ] Portas 8080/5433/8090 fechadas para a internet (só 443 exposta)
- [ ] HTTPS funcionando (certificado válido)
- [ ] Backup diário agendado **e restauração testada**
- [ ] Alerta de sessão do WhatsApp agendado
- [ ] Preços conferidos na tela de Configurações
- [ ] Banco sem dados de teste
- [ ] Textos do Typebot publicados (menu, promoção de quarta, boas-vindas)
