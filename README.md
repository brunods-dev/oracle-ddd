# Copa Ticketing MVP — FIFA World Cup 2026

Duas aplicações Java 25 / GraalVM Native Image:

| Módulo | Stack | Porta |
|--------|-------|-------|
| `backend/` | Helidon SE 4 · HikariCP · MySQL | 8080 |
| `frontend/` | Vaadin Flow 24.10 · Spring Boot 3 | 8081 |

---

## Pré-requisitos

- [Oracle GraalVM for JDK 25](https://www.graalvm.org/downloads/) (ou OpenJDK 25 para modo JIT)
- Maven 3.9+
- MySQL 8+ com o schema `copa_ticketing` já existente e acessível

---

## Configuração

### Backend — `backend/.env.example`

```text
BACKEND_PORT=8080
DB_URL=jdbc:mysql://HOST:3306/copa_ticketing?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true
DB_USER=copa_user
DB_PASS=changeme
DB_POOL_SIZE=20
ADMIN_USER=admin
ADMIN_PASS=changeme
CUSTOMER_USER=customer
CUSTOMER_PASS=changeme
```

```bash
cd backend
cp .env.example .env
# edite .env com suas credenciais reais
```

Antes de rodar, exporte as variáveis:

```bash
export $(cat .env | grep -v '#' | xargs)
```

### Frontend — `frontend/.env.example`

```text
FRONTEND_PORT=8081
BACKEND_URL=http://localhost:8080
BACKEND_CUSTOMER_USER=customer
BACKEND_CUSTOMER_PASS=changeme
BACKEND_ADMIN_USER=admin
BACKEND_ADMIN_PASS=changeme
```

```bash
cd frontend
cp .env.example .env
export $(cat .env | grep -v '#' | xargs)
```

---

## Executar (modo JIT — desenvolvimento)

```bash
# Terminal 1: backend
cd backend
export $(cat .env | grep -v '#' | xargs)
mvn package -DskipTests
java --enable-preview -jar target/copa-backend-1.0.0.jar

# Terminal 2: frontend
cd frontend
export $(cat .env | grep -v '#' | xargs)
mvn spring-boot:run
```

Abra: http://localhost:8081

---

## Build de produção (frontend)

O profile `production` gera o bundle Vite e empacota o JAR sem dependências de dev. Requer **Vaadin 24.10+** com **Java 25** (versões anteriores do plugin falham ao ler bytecode 69).

```bash
cd frontend
export $(cat .env | grep -v '#' | xargs)
mvn clean -Pproduction -DskipTests package
java --enable-preview -jar target/copa-frontend-1.0.0.jar
```

---

## Build Native Image (GraalVM)

### Backend

```bash
cd backend
export $(cat .env | grep -v '#' | xargs)
mvn package -Pnative-image -DskipTests
./target/copa-backend
```

### Frontend (produção + nativo)

```bash
cd frontend
export $(cat .env | grep -v '#' | xargs)
mvn clean package -Pproduction,native -DskipTests
./target/copa-frontend
```

---

## Endpoints da API (Backend)

Todos exigem Basic Auth. Use `CUSTOMER_USER:CUSTOMER_PASS` para endpoints públicos e `ADMIN_USER:ADMIN_PASS` para admin.

### Público

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/public/matches?city=&date=&team=&page=0&size=20` | Catálogo de partidas com filtros e paginação |
| GET | `/api/public/matches/{id}` | Detalhe de uma partida |
| GET | `/api/public/matches/{id}/sectors` | Setores disponíveis por partida |
| GET | `/api/public/matches/{id}/seat-map?sector=A&page=0&size=50` | Mapa de assentos paginado |
| POST | `/api/public/reservations` | Criar reserva temporária (10 min) |
| POST | `/api/public/reservations/{code}/checkout` | Gerar order + Pix simulado |
| POST | `/api/public/payments/{ref}/confirm` | Confirmar Pix → emitir tickets |
| GET | `/api/public/customers/{doc}/tickets` | Tickets por documento |

**Body da reserva:**
```json
{
  "fullName": "João Silva",
  "email": "joao@exemplo.com",
  "documentType": "CPF",
  "documentNumber": "123.456.789-00",
  "phone": "+55 11 99999-9999",
  "matchId": 1,
  "matchSectorId": 5,
  "unitPrice": 250.00,
  "seatIds": [101, 102]
}
```

### Admin (role ADMIN)

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/admin/dashboard` | KPIs + top partidas + vendas diárias |
| GET | `/api/admin/orders?page=0&size=20&status=PAID` | Pedidos com paginação |
| GET | `/api/admin/inventory?matchId=1` | Estoque por partida (view `vw_match_business_summary`) |
| GET | `/api/admin/payment-summary` | Resumo de pagamentos |
| GET | `/health` | Health check |

---

## Fluxo do Usuário

```
Catálogo de Partidas → Detalhe da Partida → Setor → Mapa de Assentos
    → Checkout (dados + Pix simulado) → Confirmação → Meus Ingressos
```

## Painel Admin

Acesse `/admin/dashboard` e faça login com `ADMIN_USER:ADMIN_PASS`.

- **Dashboard**: KPIs em tempo real, top partidas e vendas diárias
- **Pedidos**: Grid paginada lazy com filtro por status
- **Estoque**: Ocupação e receita por partida

---

## Tema Visual

Paleta Copa do Mundo 2026 (USA/CAN/MEX):

| Cor | Hex | Uso |
|-----|-----|-----|
| Verde México | `#006847` | Primary / botões / OK |
| Azul USA | `#002868` | Títulos / gradiente |
| Vermelho Canadá | `#C8102E` | Destaque / erros |
| Dourado FIFA | `#FFD100` | Accent / KPI revenue |

---

## Performance

- Paginação server-side em todos os Grids (Vaadin `CallbackDataProvider`)
- `LIMIT/OFFSET` + `COUNT(*)` em todas as queries de lista
- HikariCP com pool de 20 conexões e prepared statements em cache
- Native image: startup esperado < 100ms (backend) e < 500ms (frontend)
- `Cache-Control: max-age=30` em endpoints de catálogo
