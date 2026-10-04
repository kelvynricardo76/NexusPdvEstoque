# Nexus PDV & Estoque

Plataforma SaaS **multi-tenant** de PDV e controle de estoque, um produto **Nexus Development**.
Várias empresas usam a mesma aplicação com isolamento absoluto de dados.

```
backend/    Java 21 · Spring Boot 3.5 · Spring Security · JPA/Hibernate · Flyway · H2 (PostgreSQL-ready)
frontend/   Angular 22 · standalone · signals · zoneless · SCSS · Vitest
```

> **Banco atual: H2.** As migrations usam SQL portável (H2 em `MODE=PostgreSQL`).
> Docker, PostgreSQL e Testcontainers ficaram fora do escopo por enquanto (ver "Próximos passos").

---

## Execução local

Requisitos: **Java 21** e **Node.js 22+**. O Maven não precisa estar instalado (`mvnw`).

```bash
# Backend (perfil dev: H2 em arquivo + dados de demonstração)
cd backend
./mvnw spring-boot:run            # Windows: mvnw.cmd spring-boot:run   → http://localhost:8080

# Frontend
cd frontend
npm install
npm start                         # → http://localhost:4200 (proxy de /api para :8080)
```

Se a porta 8080 estiver ocupada: `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=18080`
e ajuste o `target` em `frontend/proxy.conf.json`.

### Usuários de demonstração (somente perfil `dev`)

Senha padrão: `Nexus@2026` (altere com a variável `NEXUS_DEV_PASSWORD`).

| Acesso | E-mail | Perfil |
|---|---|---|
| Super Admin (`/super-admin/login`) | `admin@nexus.dev` | Nexus Development |
| Loja Exemplo (plano **Plus**) | usuário **`admin`**, senha **`admin`** (atalho local) | Administrador |
| | `erick@lojaexemplo.com.br` | Administrador |
| | `maria@lojaexemplo.com.br` | Gerente |
| | `joao@lojaexemplo.com.br` | Caixa (entra direto no PDV) |
| | `pedro@lojaexemplo.com.br` | Estoquista |
| Mercado Central (plano **Básico**, trial) | `ana@mercadocentral.com.br` | Administrador |

O seed cria produtos, clientes, fornecedores, contas financeiras e cerca de 45 dias de vendas.
Ele roda **apenas** no perfil `dev` e somente quando o banco não tem tenants. Para recriar os dados,
pare a aplicação e apague `backend/data/`.

Ferramentas de desenvolvimento (só no perfil dev): Swagger em `/swagger-ui.html` e console H2 em `/h2-console`.

---

## Arquitetura

**Monólito modular** organizado por domínio (`com.nexus.pdv.*`):

| Pacote | Responsabilidade |
|---|---|
| `auth` | Login/logout por sessão, `/me`, esqueci/redefinir/trocar senha |
| `platform` | Super Admin: empresas, planos, catálogo, admins, dashboard, auditoria global |
| `tenant` · `subscription` · `billing` | Tenant e personalização · assinatura · cobrança da Nexus (`BillingProvider`) |
| `plan` · `entitlement` | Planos/features/limites (dados) · motor de direitos efetivos |
| `user` · `permission` | Funcionários · RBAC (cargos, permissões, overrides) |
| `product` · `category` · `stock` · `customer` · `supplier` | Cadastros e estoque com movimentações |
| `sale` · `financial` · `report` · `importer` · `dashboard` · `audit` | PDV, financeiro do cliente, relatórios/exportação, importação, indicadores, auditoria |
| `shared` | Persistência multi-tenant, segurança, acesso, erros, web |

O frontend segue a mesma divisão (`core/`, `shared/ui/` = design system, `layout/`, `features/*`),
com lazy loading por tela.

### Conceitos (separados em banco, backend, frontend e testes)

| Conceito | Significado |
|---|---|
| **FEATURE** | O que a **empresa** contratou (ex.: `FINANCIAL`) |
| **PERMISSION** | O que o **funcionário** pode fazer (ex.: `FINANCIAL_READ`) |
| **PLAN** | Pacote comercial de features + limites, **editável pelo Super Admin** (nada hardcoded) |
| **ROLE** | Conjunto reutilizável de permissões (padrão ou personalizado) |
| **SUBSCRIPTION** | Relação comercial tenant ↔ plano |
| **ENTITLEMENT** | Resultado efetivo: override do tenant → plano → padrão do catálogo |

---

## Multi-tenancy

- Banco compartilhado com `tenant_id` em todas as tabelas de domínio.
- **Hibernate `@TenantId`**: toda consulta de entidade é filtrada pelo tenant do usuário autenticado,
  e o `tenant_id` é preenchido nos inserts. O tenant **nunca** vem do cliente: um `tenantId` enviado
  no JSON é ignorado.
- **FKs compostas** `(tenant_id, x_id)` garantem no próprio banco que relacionamentos não cruzam tenants.
- Um ID de outro tenant responde **404** (não revela a existência do registro).
- O SUPER_ADMIN usa o contexto *root* (sem filtro) e só acessa `/api/super-admin/**`.
- Um teste de arquitetura falha se alguma entidade com `tenant_id` não tiver `@TenantId`.

## Segurança e autorização

**Autenticação.** Sessão no servidor com cookie `NEXUS_SESSION` (HttpOnly, SameSite=Lax, Secure em
produção). CSRF double-submit (`XSRF-TOKEN` → `X-XSRF-TOKEN`). Nenhum token fica no `localStorage`.
No login, a sessão anterior é descartada e o token CSRF é rotacionado.

**Regra de acesso efetivo**, aplicada no backend (`TenantAccessInterceptor`) em toda a API do tenant:

```
AUTENTICADO → TENANT ATIVO → ASSINATURA VÁLIDA → TENANT TEM FEATURE → USUÁRIO TEM PERMISSÃO → RECURSO DO TENANT
```

- Os endpoints declaram `@RequiresPermission` (a feature vem da própria permissão), `@RequiresFeature`,
  `@RequiresTenantAdmin` ou `@TenantAuthenticated`. **Endpoint sem anotação é negado** (fail-closed),
  e um teste de arquitetura garante isso.
- **Permissão efetiva:** `(cargo ∪ ALLOW) − DENY`, depois filtrada pelas features do tenant.
  **DENY sempre vence.** O TENANT_ADMIN tem tudo o que foi contratado.
- **Anti-escalonamento:** só o TENANT_ADMIN concede o cargo Administrador. Os demais só concedem
  permissões que eles próprios possuem. Ninguém altera o próprio acesso. Não é possível conceder
  permissões de módulos não contratados. A empresa sempre mantém pelo menos um administrador ativo.
- **Limites** (`MAX_USERS`, `MAX_PRODUCTS`, `MAX_MONTHLY_SALES`, `REPORT_HISTORY_DAYS`...) são
  verificados com a linha do tenant travada, o que evita corridas.
- **Demais proteções:**
  - Senhas com o encoder recomendado pelo Spring Security (bcrypt) e política mínima de senha.
  - Bloqueio temporário por força bruta (por e-mail e por IP) e rate limit por IP na API.
  - Headers de segurança (CSP, X-Frame-Options, Referrer-Policy, Permissions-Policy) e CORS restritivo.
  - Erros padronizados `{code, message, timestamp, traceId}`, sem stack trace.
  - Auditoria com metadados sanitizados: nunca registra senha, token ou cartão.

## Regras de negócio relevantes

- **Venda** em uma única transação: itens, pagamentos, validação e baixa de estoque, movimentos e
  auditoria.
  - Os produtos são travados em ordem de id, então vendas concorrentes nunca deixam o estoque negativo.
  - O preço vem sempre do cadastro, nunca do cliente.
  - O header `Idempotency-Key` impede venda duplicada.
  - Aceita pagamento dividido, e o troco só é permitido em dinheiro.
  - O desconto exige a permissão `SALE_DISCOUNT`.
- **Cancelamento** é idempotente e exige motivo. O estoque é restaurado com movimentos inversos, e a
  venda original é mantida (vendas nunca são apagadas).
- **Devolução** total ou parcial (permissão `SALE_REFUND`), por linha da venda e com o `Idempotency-Key` opcional.
  - O valor estornado é proporcional ao que foi pago, com o desconto geral da venda rateado entre os itens.
    A última devolução de cada item leva o saldo exato, então a soma dos estornos nunca passa do total.
  - Os itens voltam ao estoque (movimento `RETURN`), exceto os marcados como avariados.
  - A venda continua `COMPLETED`, com `refundStatus` `PARTIAL` ou `FULL`. Faturamento, ticket, ranking de
    produtos e clientes e o custo das mercadorias são **líquidos de devoluções**, abatidos na data da venda original.
  - Venda com devolução não pode ser cancelada, e venda cancelada não aceita devolução.
- **Estoque**: nenhuma alteração sem `StockMovement` (inicial, entrada, venda, ajuste, cancelamento).
  O estoque negativo é bloqueado por padrão e pode ser liberado nas configurações da empresa.
- **Planos**: alterações que reduzem direitos de planos em uso exigem confirmação explícita
  (`confirmImpact`), e nada muda silenciosamente em contratos existentes.
- **Billing ≠ Financeiro**: `subscription_invoices` é a cobrança da Nexus. `financial_entries` são as
  contas do cliente.
  - Rotina diária: fatura vencida → inadimplente → suspensão após a carência.
  - Webhooks de cobrança são idempotentes.
- **Importação** CSV/XLSX: upload → mapeamento → validação/preview → confirmação → relatório. Linhas
  inválidas só são ignoradas com confirmação explícita e aparecem no relatório.
- **Relatórios** com exportação CSV (compatível com Excel pt-BR e protegida contra injeção de fórmula)
  e PDF (gerador próprio). Ambas respeitam as features `CSV_EXPORT`/`PDF_EXPORT` e o limite de histórico.

## Banco de dados e migrations

- O schema é gerenciado **somente pelo Flyway** (`backend/src/main/resources/db/migration`, V1 a V10).
  O Hibernate roda com `ddl-auto=validate`.
- Os dados de referência (features, limites, planos BASIC e PLUS, permissões) são seed de migration e
  editáveis pelo Super Admin.
- Um teste garante que os enums `Permission`, `FeatureCode` e `LimitCode` estão sincronizados com o banco.

## Testes

```bash
cd backend && ./mvnw verify                  # 98 testes (integração com H2)
cd frontend && npx ng test --watch=false     # 19 testes (Vitest)
```

Cobertura principal: isolamento de tenant (GET/UPDATE/DELETE/venda/estoque/IDs indiretos),
permissões (incluindo ALLOW/DENY), planos e features, limites e overrides, escalonamento de privilégio,
tenant suspenso e trial vencido, vendas concorrentes, idempotência, cancelamento, relatórios e
exportações, importação CSV/XLSX, CSRF, força bruta, redefinição de senha e regras de arquitetura.

## Variáveis de ambiente

Veja `.env.example`. Perfis disponíveis:

| Perfil | Descrição |
|---|---|
| `dev` | Padrão. H2 em arquivo, Swagger, console H2 e dados de demonstração |
| `test` | H2 em memória (testes) |
| `prod` | Cookies `Secure`, logs estruturados (ECS/JSON), `forward-headers-strategy=native`, ferramentas de dev desligadas; banco via `NEXUS_DB_*` |

O primeiro SUPER_ADMIN de produção é criado a partir de `NEXUS_BOOTSTRAP_ADMIN_EMAIL` e
`NEXUS_BOOTSTRAP_ADMIN_PASSWORD`. Não há credenciais padrão no código.

## Build e deploy

```bash
cd backend && ./mvnw package      # backend/target/pdv-0.1.0-SNAPSHOT.jar
cd frontend && npx ng build       # frontend/dist/frontend (arquivos estáticos)

SPRING_PROFILES_ACTIVE=prod java -jar backend/target/pdv-0.1.0-SNAPSHOT.jar
```

Recomendação de produção: servir o frontend e a API **na mesma origem**, atrás de um proxy reverso
HTTPS (`/` → estáticos do Angular, `/api` → backend). Health checks em `/actuator/health/liveness` e
`/actuator/health/readiness`.

## Próximos passos

1. **PostgreSQL + Docker + Testcontainers:** adicionar o driver `postgresql` e o módulo
   `flyway-database-postgresql`, criar o `docker-compose` (frontend, backend, postgres) e rodar a suíte
   de integração em PostgreSQL real.
2. **E-mail transacional:** implementar `PasswordResetNotifier` (hoje o link só aparece no log do dev).
3. **Gateway de cobrança** (ASAAS / Mercado Pago): implementar `BillingProvider` com validação de
   assinatura de webhook.
4. **Múltiplas instâncias:** Spring Session JDBC/Redis, e rate limit e bloqueio de login em
   armazenamento compartilhado.
5. **Recursos já preparados no catálogo**, ainda sem implementação: filiais, lotes, validade, balança e 2FA.
