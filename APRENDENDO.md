# Aprendendo — trilha de estudo do projeto

Você não precisa virar especialista em tudo. Este guia diz **o que estudar, em
que ordem, e o quanto é essencial para ESTE projeto**.

Legenda: 🔴 essencial (você vai usar toda semana) · 🟡 importante (bom saber) ·
⚪ só quando precisar

---

## Como usar este guia

1. Leia o **nível 0** se você ainda não está confortável com terminal, JSON e HTTP.
2. Vá direto ao **nível 3 (Spring Boot)** — é onde está 90% do código deste projeto.
3. O resto você aprende **conforme precisar**, usando a seção *Receitas* do [README](README.md).
4. No fim tem uma **trilha de leitura do código real**, na ordem certa.

> 💡 Dica de método: não assista 40h de curso antes de mexer. Leia o conceito,
> **abra o arquivo do projeto que usa aquilo**, quebre de propósito e conserte.

---

## Nível 0 — O mínimo para não se perder 🔴

Sem isso, nada mais faz sentido. São conceitos, não tecnologias.

| Assunto | O que precisa saber | Onde aprender |
|---|---|---|
| **Terminal (bash)** | `cd`, `ls`, `grep`, `cat`, pipes `\|`, variáveis | [Linux Journey](https://linuxjourney.com/) |
| **HTTP** | métodos (GET/POST/PUT/DELETE), status (200/400/401/403/404/500), headers | [MDN — HTTP](https://developer.mozilla.org/pt-BR/docs/Web/HTTP) |
| **JSON** | estrutura chave-valor, array, aninhamento | [MDN — JSON](https://developer.mozilla.org/pt-BR/docs/Learn/JavaScript/Objects/JSON) |
| **Git** | `add`, `commit`, `push`, `pull`, branch, merge | [Pro Git (PT)](https://git-scm.com/book/pt-br/v2) |

**Teste prático:** rode `curl -u SEU_USUARIO:SUA_SENHA http://localhost:8080/api/admin/pedidos`
e entenda o que voltou. Se não entendeu, volte ao HTTP.

⏱️ ~1 semana em paralelo com o resto.

---

## Nível 1 — Ferramentas do dia a dia 🔴

### Docker e Docker Compose

**O que é:** empacota a aplicação + tudo que ela precisa (Java, banco) numa
"caixa" que roda igual em qualquer máquina.

**O que você usa aqui:**

```bash
sg docker -c "docker ps"                        # o que está rodando
sg docker -c "docker logs -f gas-delivery-app-1" # ver logs ao vivo
sg docker -c "docker compose up -d"             # subir
sg docker -c "docker compose down"              # parar
sg docker -c "docker exec -it gas-delivery-db-1 psql -U gas -d gasdelivery"
```

**Conceitos que importam:** imagem × container, volume (dados que sobrevivem),
porta `host:container`, rede do Docker, `environment`.

📚 [Docker — Getting Started](https://docs.docker.com/get-started/) · [Compose](https://docs.docker.com/compose/)

> `sg docker -c "..."` é preciso nesta máquina porque seu usuário não está no
> grupo `docker`. Resolve com `sudo usermod -aG docker $USER` + relogar.

### SQL e PostgreSQL

**O que é:** a linguagem para conversar com o banco. Mesmo usando JPA, você vai
precisar disso para investigar problemas.

**O mínimo essencial:**

```sql
SELECT * FROM pedidos ORDER BY data_criacao DESC LIMIT 10;
SELECT status, count(*) FROM pedidos GROUP BY status;
UPDATE pedidos SET status = 'CANCELADO' WHERE id = 5;
```

📚 [SQLBolt (interativo)](https://sqlbolt.com/) · [PostgreSQL Tutorial](https://www.postgresqltutorial.com/)

⏱️ Docker: 1–2 dias · SQL: 1 semana.

---

## Nível 2 — Java 🔴

**O que é:** a linguagem do backend.

**Conceitos que você VAI encontrar neste projeto:**

| Conceito | Onde aparece no projeto |
|---|---|
| Classe, objeto, método | tudo |
| Tipos (`String`, `Long`, `BigDecimal`) | `Pedido.valorTotal` (dinheiro é `BigDecimal`, **nunca** `double`) |
| `List<T>`, `Map<K,V>`, `Optional<T>` | `PedidoRepository.findByStatus(...)` devolve `List` |
| Interface + implementação | `PedidoRepository` é interface; o Spring cria a implementação |
| Enum | `Status`, `TipoPedido`, `FormaPagamento` |
| Exceções | `IllegalStateException("Já existe um caixa aberto")` |
| Anotações (`@Algo`) | onipresentes — veja abaixo |
| Lambda / stream | `entregues.forEach(p -> ...)` |

**Anotações que você precisa reconhecer:**

| Anotação | Significa |
|---|---|
| `@Service` | "esta classe tem regra de negócio" |
| `@RestController` | "esta classe responde HTTP" |
| `@Entity` | "esta classe é uma tabela" |
| `@Autowired` / construtor | "o Spring me entrega essa dependência" |
| `@Transactional` | "tudo aqui dentro é uma operação só — se falhar, desfaz" |
| `@Value("${...}")` | "esse valor vem do application.properties/env" |

📚 [dev.java — Java Tutorials](https://dev.java/learn/) · [Baeldung Java](https://www.baeldung.com/java-tutorial)

⏱️ 2–3 semanas para o básico sólido.

---

## Nível 3 — Spring Boot 🔴 (o coração do projeto)

**O que é:** o framework que junta tudo. Você escreve a classe; ele cria o
servidor HTTP, injeta dependências e conecta no banco.

### A ideia central: injeção de dependência

Você **não** escreve `new PedidoRepository()`. Você declara que precisa e o
Spring entrega:

```java
@Service
public class CaixaService {
    private final PedidoRepository pedidoRepository;

    public CaixaService(PedidoRepository pedidoRepository) {   // o Spring injeta
        this.pedidoRepository = pedidoRepository;
    }
}
```

### As camadas (e por que existem)

```
HTTP  →  @RestController  →  @Service  →  @Repository  →  Banco
         (recebe)            (decide)      (salva)
```

- **Controller** não decide nada — só traduz HTTP ↔ Java.
- **Service** tem as regras. É aqui que você mexe quando a regra muda.
- **Repository** só sabe ler/gravar.

### Os 5 arquivos deste projeto para ler primeiro

1. `entities/Status.java` — os estados possíveis (5 min)
2. `controller/PedidoBotController.java` — como um endpoint nasce
3. `service/PedidoBotService.java` — regra de negócio de verdade
4. `repository/PedidoRepository.java` — como se lê o banco sem escrever SQL
5. `service/CaixaService.java` — lógica mais complexa do sistema

📚 [Spring Guides (oficiais)](https://spring.io/guides) — faça o *Building a RESTful Web Service*
📚 [Baeldung Spring Boot](https://www.baeldung.com/spring-boot) · [Spring Data JPA](https://www.baeldung.com/the-persistence-layer-with-spring-data-jpa)

### Flyway 🟡

Versiona o schema. Você cria `V3__algo.sql` e o Flyway aplica uma única vez.
Sem ele, cada ambiente teria um banco diferente.

📚 [Flyway — Quickstart](https://documentation.red-gate.com/flyway)

⏱️ 3–4 semanas para produtividade real em Spring.

---

## Nível 4 — Frontend 🟡

Você mexe no painel com menos frequência, mas precisa entender.

| Tecnologia | O que é | Onde aprender |
|---|---|---|
| **JavaScript** | linguagem do navegador | [javascript.info](https://javascript.info/) |
| **TypeScript** | JS com tipos | [TS Handbook](https://www.typescriptlang.org/docs/handbook/intro.html) |
| **React** | monta a tela por componentes | [react.dev](https://react.dev/learn) |
| **Vite** | servidor de dev + build | [vitejs.dev](https://vitejs.dev/guide/) |
| **Tailwind** | CSS por classes utilitárias | [tailwindcss.com](https://tailwindcss.com/docs) |
| **React Query** | busca/cache de dados do servidor | [TanStack Query](https://tanstack.com/query/latest/docs/framework/react/overview) |
| **Axios** | cliente HTTP do navegador | [axios-http.com](https://axios-http.com/docs/intro) |

**Conceitos React que aparecem no painel:** componente, `props`, `useState`
(estado), `useEffect` (efeito colateral), lista com `.map()` + `key`.

**Onde está o quê no painel:**

```
src/
├── App.tsx                    ← junta tudo, busca os dados
├── services/api.ts            ← TODAS as chamadas ao backend
├── services/auth.ts           ← login
├── types/index.ts             ← formato dos dados (espelha o backend)
├── components/kanban/         ← o quadro de pedidos
├── components/caixa/          ← turno de caixa
├── components/config/         ← tela de preços
├── components/auth/           ← tela de login
└── index.css                  ← o "design system" (cores, sombras, botões)
```

📚 Comece por [react.dev/learn](https://react.dev/learn) — é o melhor recurso que existe hoje.

⏱️ 2–3 semanas.

---

## Nível 5 — Integrações ⚪

### REST e webhooks 🟡
REST é o estilo das nossas APIs (verbo + recurso). Webhook é quando **outro
sistema chama a gente** — é o caso do `POST /api/pedidos/via-bot`.

📚 [MDN — REST](https://developer.mozilla.org/pt-BR/docs/Glossary/REST)

### Evolution API ⚪
Gateway não-oficial do WhatsApp. Roda em container na porta 8090.
Conceitos: **instância** (um número conectado), **estado** (`open`/`close`),
**QR code** (como se conecta), **API key** (autenticação).

```bash
curl -s http://127.0.0.1:8090/instance/connectionState/botgas -H "apikey: $CHAVE"
```

📚 [Documentação da Evolution](https://doc.evolution-api.com/)

### Typebot ⚪
Construtor visual do fluxo de conversa. Conceitos que já nos morderam:
- **Blocos** (mensagem, pergunta, condição, requisição HTTP).
- **Variáveis** — só funcionam se **declaradas** no fluxo.
- **Eventos** (`Reply`, `Invalid Reply`) — têm subfluxo próprio que precisa
  terminar num bloco **Return**.
- **Publish** — editar não basta; sem publicar, o robô continua no fluxo antigo.

📚 [Docs do Typebot](https://docs.typebot.io/)

### ngrok ⚪
Cria uma URL pública temporária apontando para a sua máquina — é como o Typebot
(nuvem) alcança seu `localhost:8080` em desenvolvimento.

📚 [ngrok — Getting Started](https://ngrok.com/docs/getting-started/)

---

## Nível 6 — Produção ⚪ (quando for publicar)

| Assunto | Onde aprender |
|---|---|
| nginx (proxy reverso) | [nginx.org — Beginner's Guide](https://nginx.org/en/docs/beginners_guide.html) |
| HTTPS/Let's Encrypt | [letsencrypt.org — Getting Started](https://letsencrypt.org/getting-started/) |
| Segurança web (OWASP Top 10) | [owasp.org](https://owasp.org/www-project-top-ten/) |
| Backups com pg_dump | [PostgreSQL — Backup](https://www.postgresql.org/docs/current/backup-dump.html) |

Tudo aplicado ao SEU caso já está escrito no **[PRODUCAO.md](PRODUCAO.md)**.

---

## Trilha de leitura do código (ordem sugerida)

Leia nesta ordem — cada passo usa só o que o anterior ensinou:

| # | Arquivo | O que você aprende |
|---|---|---|
| 1 | `entities/Status.java` | Como se declara um enum |
| 2 | `entities/Pedido.java` | Como uma classe vira tabela |
| 3 | `repository/PedidoRepository.java` | Consultas sem SQL |
| 4 | `dto/PedidoBotRequest.java` | O formato do JSON que chega |
| 5 | `controller/PedidoBotController.java` | Como nasce um endpoint |
| 6 | `service/PedidoBotService.java` | 🎯 **regra de negócio de verdade** |
| 7 | `service/CaixaService.java` | Lógica com cálculos e transações |
| 8 | `config/SecurityConfig.java` | Como o login protege a API |
| 9 | `service/WhatsappNotificationService.java` | Integração externa + retry |
| 10 | `AdminController.java` | Vários endpoints juntos |

Depois, no painel: `types/index.ts` → `services/api.ts` → `App.tsx` → `components/kanban/PedidoCard.tsx`.

---

## Exercícios práticos (do fácil ao difícil)

1. **Aquecer:** mude o texto da mensagem de `DESPACHADO` em
   `WhatsappNotificationService` e rode `./mvnw test` para ver o que quebra.
2. **Entender teste:** rode `./mvnw test -Dtest=CaixaServiceTest` e leia o teste
   que verifica se o saldo do entregador é zerado.
3. **Banco:** entre no psql e rode `SELECT status, count(*) FROM pedidos GROUP BY status;`
4. **API:** com o sistema no ar, faça `curl -u SEU_USUARIO:SUA_SENHA http://localhost:8080/api/admin/configuracao`
5. **Frontend:** no painel, troque a cor de um botão em `index.css` e veja ao vivo.
6. **Integração:** siga um pedido do início ao fim — Typebot → `via-bot` →
   `PedidoBotService` → banco → painel → `AdminService` → notificação.
7. **Desafio:** crie um campo `observacao` em `Pedido` (migração V3 + entidade +
   DTO + painel).

---

## Regra de ouro

Você **não** precisa saber tudo de cor. Precisa saber:

1. **onde** cada coisa mora (seção *Mapa do código* do README);
2. **como** testar uma hipótese (logs, psql, curl);
3. **o que** perguntar quando travar.

Isso vale mais do que decorar sintaxe.
