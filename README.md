# Nova Online Banking Portal

A seeded Philippine retail-banking **demonstration**, built with Angular 18.2, Java 21 / Spring Boot 3.3.13 and PostgreSQL 16. No real money, applications, credit checks, communications or live agents are involved. NOVA BANK has no affiliation with BPI.

## Features
- BCrypt login, three-attempt persistent lockout, signed JWTs, durable token revocation and 120-second inactivity warning with a 30-second countdown.
- Own-account portfolios: Miguel PHP 142,860.25 across deposits, Rosa PHP 54,300.00. Cards and loans are excluded from relationship totals; all API account identifiers are masked.
- URL-preserved transaction filters, pagination, accessible disclosure and complete structured text statements.
- Two product recommendations, segment/date-filtered promotion terms and idempotent persisted simulated interest.
- Persisted support directory/FAQs and simulated enquiries with unique reference numbers.
- Scripted persona-aware assistant without conversation retention.
- Administrator-only audit inspection, transactional fixture reset, customer persona switching and session-scoped service-error simulation.

## Demo identities
| User ID | Password | Role |
|---|---|---|
| miguel | demo-only | Customer |
| rosa | demo-only | Customer |
| admin | demo-only | Facilitator |
These credentials are deliberately public demonstration fixtures. Do not expose the demo to real customer data. A locked user needs an administrator reset. Switching persona revokes the issuing admin token; log out and sign in as admin to regain controls.

## Run with Compose
Docker Engine and Compose v2 required:
```sh
docker compose up --build -d
```
Open `http://localhost:4200`. Frontend nginx serves the SPA and proxies `/api` to `backend:8080`; backend connects to `db:5432`. The PostgreSQL named volume preserves data. Ordinary shutdown preserves it:
```sh
docker compose down
```
Use the application's Facilitator reset for repeatable demos; do not erase Flyway history. Compose values use local-only dummy defaults. For nonlocal deployment set a unique `DB_NAME`, actual database credentials, `DEPLOYMENT=production`, a random `JWT_SECRET` of at least 32 bytes, and terminate HTTPS with your deployment ingress. Local Compose does not implement TLS.

## Development
Prerequisites: Java 21, Maven 3.8+, Node 22.12+ (validated on Node 24.22), PostgreSQL 16.
1. Review `backend/.env.example` and `frontend/.env.example`. Java reads `application.yml` placeholders from **process environment**, not `.env`. The maintenance `DATABASE_URL` and separate `DB_NAME` let startup create the database before Flyway; provisioning credentials need CREATE DATABASE permission. Use a dedicated account/instance, not an unrelated shared production database.
2. Backend:
```sh
export DATABASE_URL=jdbc:postgresql://localhost:5432/postgres
export DB_USERNAME=postgres DB_PASSWORD=postgres DB_NAME=nova
mvn -f backend/pom.xml spring-boot:run
```
3. Frontend in another terminal:
```sh
cd frontend
npm ci
node node_modules/@angular/cli/bin/ng.js serve --host 0.0.0.0 --proxy-config proxy.conf.cjs
```
The dev proxy reads `API_PROXY_TARGET` (default local backend). API base defaults to same-origin `/api/v1`; an optional `window.NOVA_CONFIG` may supply `apiBase` and `demoDate` before bootstrap. If changing backend `DEMO_DATE`, keep frontend `NOVA_CONFIG.demoDate` in sync. Frontend environment template documents build/dev/test settings; browser code does not read a server `.env` directly.

## Builds and tests
```sh
mvn -f backend/pom.xml package -DskipTests
cd frontend
node node_modules/@angular/cli/bin/ng.js build
```
Backend tests require a dedicated real PostgreSQL database whose name ends `_test`; they reset fixtures and must never target application/customer data:
```sh
DB_NAME=nova_test mvn -f backend/pom.xml test
DB_NAME=nova_test mvn -f backend/pom.xml -Dtest=BankIntegrationTests test
```
Frontend component tests use Jasmine/Karma. Chromium must be installed once (`node node_modules/@playwright/test/cli.js install chromium`); Karma uses Playwright's executable or `CHROME_BIN`:
```sh
cd frontend
node node_modules/@angular/cli/bin/ng.js test --watch=false
node node_modules/@playwright/test/cli.js test
```
Playwright starts/reuses both real services, runs one worker on desktop and mobile, asserts backend data and captures browser errors. Set `DB_NAME=nova_e2e` for the live backend and persistence checks; `E2E_BASE_URL`, `E2E_BACKEND_URL` and proxy configuration must match server ports. `e2e/preflight.cjs` captures desktop/mobile accessibility trees, styles, HTML and screenshots with those live services running. It is diagnostic, not a replacement for journeys.

Sandbox note: bin symlinks can be disabled; invoke the package entry through Node as above. If `npm ci` lifecycle tools fail solely due disabled bin links, `npm ci --ignore-scripts --no-bin-links` was validated for the sandbox dependency install. Start PostgreSQL, run tests and stop it within one supervised command. Live servers must remain owned by the enclosing test command and be cleaned up afterward.

## API contract
Business routes have no trailing slash and use `/api/v1`. Public `GET /api/health` returns `{status:"UP"}`. Bearer auth is required except login/health. Errors use `{status,message,errors,timestamp}`. Money is serialized as decimal strings; PostgreSQL NUMERIC and Java BigDecimal are authoritative.

| Method | Route | Behavior |
|---|---|---|
| POST | /auth/login | `{username,password}` → token/tokenType/expiresIn/safe user; 400 invalid input, 401 credentials, 423 locked |
| POST | /auth/logout | Persist bearer revocation; 200 message |
| GET | /portfolio/summary | Own greeting, last login, deposit-only total and safe accounts |
| GET | /accounts | Own masked account summaries |
| GET | /accounts/{id}/transactions | Search/category/direction/startDate/endDate/page/size; snapshot, count and items |
| GET | /accounts/{id}/statement/download | UTF-8 full activity attachment, no filter/page truncation |
| GET | /products | Two approved recommendation products |
| GET | /promotions | Active unexpired allowed-segment offers |
| GET | /promotions/{id} | Allowed full terms/eligibility and interested state |
| POST | /promotions/{id}/express-interest | Idempotent persisted simulated interest and explicit notice |
| GET | /support/content | Persisted contacts, branches and FAQ content |
| POST | /support/inquiries | `{topic,subject,message}` → 201 reference/status/24h |
| POST | /chat/query | `{message}` → deterministic text/actionLabel/actionRoute, no retention |
| GET | /admin/audit | Admin-only bounded audit metadata |
| POST | /admin/reset | Restore fixtures/unlock/clear activity/revoke customers, retain audit/admin |
| POST | /admin/persona | `{username: miguel|rosa}` → customer session, revoke issuing admin |
| POST | /admin/simulation | `{serviceError:boolean}` → current admin-session setting |

Account operations return 403 for another customer's account, 404 unknown and 401 invalid/revoked auth, without metadata. Transaction dates are inclusive UTC calendar dates; default page0/size20, max100; 30-day default anchored to configurable demo date 2026-10-14. The first enquiry after reset is NB-99214; later references are unique. Unknown chat input returns topic guidance. Recovery links show guidance only; Filipino localization covers login controls.

## Architecture and known limitations
Feature-based Java entity/repository/DTO/service/controller packages and standalone Angular page/client modules; schema-only Flyway migrations plus idempotent startup seeds. Reset is atomic and does not replay versioned migrations. Extra identities remain to preserve audit references; their customer activity/session state is cleared. Admin simulation affects that session's business GET reads, not logout/admin recovery.

The discovery PRD was the only upstream design artifact; implementation architecture and bounded open behaviors were explicitly approved before coding. Verification: 167 backend feature tests plus four cross-feature tests; 178 Angular tests; 58 desktop/mobile browser journeys; production builds and Compose startup. See stage-published test report for exact execution history.

**Warnings:** Mobile header is in-flow at <=767px to avoid obstructing controls; desktop remains sticky. Pinned Angular 18/tooling dependencies report 64 npm audit findings (6 low, 36 moderate, 20 high, 2 critical), including deprecated transitive tools. A dependency/security upgrade assessment and deployment hardening are mandatory before production use. Passing functional tests is not a regulatory, WCAG certification, vulnerability clearance, disaster-recovery or live-banking readiness claim. Trust badges are presentation copy explicitly qualified as simulation; local development has no TLS. Seed dates, financial balances, contact numbers and offers are not live data.
