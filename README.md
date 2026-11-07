# 3Line Wallet System

A secure wallet service built with Java 17 and Spring Boot 3.5. Users register with their Nigerian mobile number, which gives them **two account numbers for the same NGN wallet**:

- a generated 10-digit account number, and
- their **phone account number**: the mobile number without its leading 0 (`07068565534` → `7068565534`).

They can then log in, save beneficiaries, and transfer funds to any account number, phone account number or saved beneficiary.

- **Security:** stateless JWT authentication, `USER` and `ADMIN` roles, current-user ownership checks on every endpoint, and a 4-digit **transaction PIN** on every transfer, with lockout after repeated wrong attempts.
- **Money movement:** every balance change is atomic, safe under concurrent requests, idempotent on retry, and recorded in a double-entry ledger.
- **Search:** statements, transactions and accounts can be filtered. The filters are built with JPA Specifications.

The supplied starter project's `ServiceCall` contract is kept and implemented; see [Starting point](#starting-point) for how the submission maps to it.

---

## Starting point

3Line's starter project (`com.example.test`, Spring Boot 3.5, H2, port 9090) defines the two requirements as an interface, with an empty `DoService` that implements it:

```java
public interface ServiceCall {
    void createUserAndAccount(User user);     // 1. Create a user & generate account
    void doIntraTransfer(DoTransDto request); // 2. Fund transfer between accounts
}
```

The submission builds on that project. Here is what changed and why:

| Starter | Submission | Why |
|---|---|---|
| Package `com.example.test`, Spring Boot 3.5.0, H2, port 9090 | Same | Kept as supplied |
| `ServiceCall` | Unchanged | It is the contract being assessed |
| `DoService` (empty) | Renamed to **`WalletService implements ServiceCall`**; both methods are implemented, and the REST API is built on the same logic | The name describes what the class does. Look for `WalletService` where you would expect `DoService` |
| `DoTransDto(fromAccount, toAccount, amount)` | Same fields and 3-argument constructor, plus an optional `narration` and Bean Validation | Backwards compatible with the starter |
| `User(id, email)` | Email kept; adds first and last name, Nigerian phone number, password hash, PIN hash and role. The `(id, email)` constructor and the setters are gone | Registration needs these details. The phone number becomes a second account number. Without setters, a user can't be modified behind the service's back |
| `Account(id, accountNumber)` | Kept; adds phone account number, owner, wallet, currency, status and creation time | Links each account to its owner and wallet, and supports freezing an account |
| `WalletBalance(id, amount = 0)` | Kept; adds a `@Version` column and `credit()` / `debit()` methods | Concurrent updates are detected, and a balance can't be changed except through the rules |
| `UserRepo`, `AccountRepo` | Kept, with extra queries (row-locked lookup, phone resolution, Specifications) | Needed for safe transfers and search |
| `WalletBalanceRepo` | Removed | Unused: the balance is saved together with its `Account` |
| `TestApplication` | Renamed `WalletApplication` | Descriptive name |
| `spring.jpa.hibernate.ddl-auto=update` | Flyway migrations (`db/migration`) with `ddl-auto=none` | The schema is versioned and reviewable, not generated at runtime |
| — | New: ledger, beneficiaries, transaction PIN, JWT security, audit trail | See *Design* |

How the two `ServiceCall` methods behave:

- **`createUserAndAccount(User)`** creates the user, generates a unique 10-digit account number, and opens a zero-balance NGN wallet. The supplied `User` must carry the fields registration now requires: name, a valid Nigerian phone number, and BCrypt hashes of the password and PIN. An email-only `User`, as in the starter, is rejected with `VALIDATION_ERROR`. Customers register through `POST /api/v1/auth/register`, which takes the plain password and PIN, hashes them, and then runs the same creation logic.
- **`doIntraTransfer(DoTransDto)`** is the internal, system-level transfer between any two accounts (either number type). It is atomic, row-locked and writes the ledger, but it is not tied to a logged-in user, so it asks for no PIN. Customers transfer through `POST /api/v1/transfers`. That endpoint uses the same transfer core and adds the ownership check (the source is always the caller's own wallet), PIN verification and audit.

---

## Quick start

**Requirements:** Java 17+. The Maven wrapper is included, so you don't need Maven installed.

```bash
./mvnw spring-boot:run          # starts on http://localhost:9090 with an in-memory H2 database
./mvnw verify                   # runs the full test suite
```

| URL | Purpose |
|---|---|
| http://localhost:9090/swagger-ui.html | Swagger UI. Log in, click **Authorize** and paste the `accessToken` |
| http://localhost:9090/api-docs | OpenAPI 3 spec |
| http://localhost:9090/actuator/health | Health check |

A bootstrap admin is created on startup: `admin@wallet.local` / `Admin@12345`. You can change it with `ADMIN_EMAIL` / `ADMIN_PASSWORD`.

### Run with PostgreSQL (Docker)

```bash
docker compose up --build
```

This starts PostgreSQL 16 and the API on port 9090. Flyway creates the schema on first start.

---

## API

Base path: `/api/v1`. Bodies are JSON and amounts are NGN with at most 2 decimal places. Every endpoint except register and login needs `Authorization: Bearer <accessToken>`.

### Public

| Method | Path | Description |
|---|---|---|
| `POST` | `/auth/register` | Register with a Nigerian mobile number, a password and a 4-digit transaction PIN. Creates the user and opens a zero-balance wallet with a generated account number and a phone account number |
| `POST` | `/auth/login` | Log in and receive an access token (60 minutes by default) |

### Customer (`USER`): always scoped to the logged-in user

| Method | Path | Description |
|---|---|---|
| `GET` | `/me` | My profile and account |
| `GET` | `/me/balance` | My balance |
| `GET` | `/me/transactions` | Search my statement (filters below) |
| `PUT` | `/me/pin` | Change my transaction PIN. Body: `currentPin`, `newPin` |
| `GET` | `/me/beneficiaries?q=` | My saved beneficiaries, paginated. `q` matches nickname, name, account number or phone account number |
| `POST` | `/me/beneficiaries` | Save a beneficiary. Body: `accountNumber` (either kind), `nickname` |
| `DELETE` | `/me/beneficiaries/{id}` | Remove a beneficiary |
| `GET` | `/accounts/{number}/name-enquiry` | Holder's name before transferring, by account number or phone account number (no email, phone or balance) |
| `POST` | `/transfers` | Transfer **from my wallet** to an account number or phone account number. Body: `toAccount`, `amount`, `narration`, `pin`, plus optional `saveAsBeneficiary`. Accepts an `Idempotency-Key` header |
| `POST` | `/transfers/beneficiaries/{beneficiaryId}` | Transfer **from my wallet** to a saved beneficiary, picked from `GET /me/beneficiaries`. Body: `amount`, `narration`, `pin`. Accepts an `Idempotency-Key` header |
| `GET` | `/transactions/{reference}` | A transaction I sent or received |

### Admin (`ADMIN`)

| Method | Path | Description |
|---|---|---|
| `GET` | `/admin/accounts` | Search customers and accounts |
| `GET` | `/admin/accounts/{accountNumber}` | Any account with owner details |
| `PATCH` | `/admin/accounts/{accountNumber}/status` | Set `ACTIVE`, `FROZEN` or `CLOSED` |
| `GET` | `/admin/accounts/{accountNumber}/transactions` | Search any account's statement |
| `GET` | `/admin/transactions` | Search all transactions |
| `GET` | `/admin/transactions/{reference}` | Any transaction |
| `GET` | `/admin/audit-events` | Search the audit trail (filters below) |
| `POST` | `/admin/accounts/{accountNumber}/fund` | **Demo only:** credit a wallet so transfers can be tested. Can be turned off with config |

### Search filters

All filters are optional, and every filter you supply must match. Results are paginated with `page` (from 0) and `size` (1–100, default 20), newest first. Dates are `yyyy-MM-dd` in Lagos time and both ends are inclusive.

| Endpoint | Filters |
|---|---|
| `/me/transactions`, `/admin/accounts/{n}/transactions` | `entryType` (DEBIT/CREDIT), `transactionType` (TRANSFER/FUNDING), `reference`, `minAmount`, `maxAmount`, `fromDate`, `toDate` |
| `/admin/transactions` | `reference`, `type`, `status`, `accountNumber` (either side), `minAmount`, `maxAmount`, `fromDate`, `toDate` |
| `/admin/audit-events` | `actorUserId`, `action`, `outcome`, `resourceType`, `resourceId`, `requestId`, `fromDate`, `toDate` |
| `/admin/accounts` | `q` (partial, case-insensitive match on name, email, phone number, account number or phone account number), `status`, `fromDate`, `toDate` |

Example: `GET /api/v1/me/transactions?entryType=DEBIT&minAmount=1000&fromDate=2026-11-01&page=0&size=10`

### Walkthrough

```bash
BASE=http://localhost:9090/api/v1

# 1. Register two customers
curl -s -X POST $BASE/auth/register -H 'Content-Type: application/json' \
  -d '{"firstName":"Ada","lastName":"Obi","email":"ada@example.com","phoneNumber":"08031234567","password":"Passw0rd!","pin":"2580"}'
curl -s -X POST $BASE/auth/register -H 'Content-Type: application/json' \
  -d '{"firstName":"Bayo","lastName":"Ade","email":"bayo@example.com","phoneNumber":"+2347068565534","password":"Passw0rd!","pin":"7391"}'

# 2. Log in (jq extracts the token)
ADA=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"ada@example.com","password":"Passw0rd!"}' | jq -r .accessToken)
ADMIN=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@wallet.local","password":"Admin@12345"}' | jq -r .accessToken)

# 3. Admin funds Ada's wallet (demo endpoint)
curl -s -X POST $BASE/admin/accounts/<ADA_ACCOUNT>/fund -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d '{"amount":10000.00}'

# 4. Ada pays Bayo using his phone account number (his mobile number without the 0), with her PIN,
#    and saves him as a beneficiary
curl -s $BASE/accounts/7068565534/name-enquiry -H "Authorization: Bearer $ADA"
curl -s -X POST $BASE/transfers -H "Authorization: Bearer $ADA" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"toAccount":"7068565534","amount":2500.00,"narration":"Rent","pin":"2580","saveAsBeneficiary":true}'

# 5. Next time, Ada pays him from her beneficiaries
curl -s $BASE/me/beneficiaries -H "Authorization: Bearer $ADA"
curl -s -X POST $BASE/transfers/beneficiaries/<ID> -H "Authorization: Bearer $ADA" -H 'Content-Type: application/json' \
  -d '{"amount":1000.00,"narration":"Allowance","pin":"2580"}'

# 6. Balance and statement
curl -s $BASE/me/balance -H "Authorization: Bearer $ADA"
curl -s "$BASE/me/transactions?entryType=DEBIT" -H "Authorization: Bearer $ADA"
```

Sample login response:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "expiresAt": "2026-11-06T23:15:30Z",
  "role": "USER"
}
```

Sample transfer response (`201 Created`):

```json
{
  "reference": "TRF20261106221530A1B2C3D4E5F6",
  "type": "TRANSFER",
  "status": "SUCCESSFUL",
  "fromAccount": "4821937065",
  "toAccount": "7390215548",
  "amount": 2500.00,
  "currency": "NGN",
  "narration": "Rent",
  "createdAt": "2026-11-06T22:15:30.456Z"
}
```

### Errors

Every error, including 401 and 403 responses from the security layer, uses the same shape. `code` is stable and safe for clients to branch on:

```json
{
  "timestamp": "2026-11-06T22:15:30.789Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "code": "INSUFFICIENT_FUNDS",
  "message": "Insufficient wallet balance",
  "path": "/api/v1/transfers"
}
```

| HTTP | Codes |
|---|---|
| 400 | `VALIDATION_ERROR` (includes `fieldErrors`), `MALFORMED_REQUEST`, `INVALID_AMOUNT` |
| 401 | `UNAUTHORIZED` (missing, invalid or expired token), `INVALID_CREDENTIALS` (wrong password) |
| 403 | `FORBIDDEN` (authenticated but not allowed, e.g. a customer calling `/admin/**`), `INVALID_PIN` (the message says how many attempts are left) |
| 404 | `EMAIL_NOT_REGISTERED` (login with an unknown email), `ACCOUNT_NOT_FOUND`, `TRANSACTION_NOT_FOUND`, `BENEFICIARY_NOT_FOUND`, `RESOURCE_NOT_FOUND` |
| 409 | `DUPLICATE_EMAIL`, `DUPLICATE_PHONE`, `BENEFICIARY_EXISTS`, `IDEMPOTENCY_KEY_REUSED`, `CONCURRENT_UPDATE`, `DATA_CONFLICT` |
| 422 | `INSUFFICIENT_FUNDS`, `SAME_ACCOUNT_TRANSFER`, `ACCOUNT_NOT_ACTIVE`, `CURRENCY_MISMATCH`, `PIN_NOT_SET`, `INVALID_BENEFICIARY` |
| 423 | `PIN_LOCKED` (too many wrong PINs; the message says when to try again) |
| 500 | `INTERNAL_ERROR` (details are logged, never returned) |

---

## Design

```
controller/   HTTP layer: validation, status codes, OpenAPI docs. Calls services only, never touches entities.
service/      WalletService (onboarding, money movement, admin actions; implements ServiceCall)
              AuditService (writes and searches the audit trail)
              AccountQueryService (read-only, enforces ownership). Services return DTOs, so no entity
              or lazy proxy leaves a transaction.
              AuthService (login)
security/     SecurityConfig, CurrentUser (the logged-in user, built from the token), token issuing,
              401/403 handlers, bootstrap admin
config/       OpenAPI setup, RequestLoggingFilter (X-Request-Id + access log), CurrentUserMdcInterceptor
repo/         Spring Data repositories (with JpaSpecificationExecutor)
repo/spec/    JPA Specifications behind the search endpoints
dto/          Request and response records; dto/search holds the search criteria
model/        JPA entities: User, Account, WalletBalance, WalletTransaction, LedgerEntry
common/       ErrorCode, BusinessException, ApiError, GlobalExceptionHandler
db/migration  Flyway SQL migrations (owns the schema)
```

### Security

- **Authentication.** `POST /auth/login` checks a BCrypt password hash and returns a JWT signed with HS256. The token's `sub` is the user id, plus `email` and `roles` claims. Each request is checked by Spring Security's OAuth2 resource-server filter: signature, expiry and issuer. No server-side session is kept.
- **Authorisation.** `/admin/**` needs `ROLE_ADMIN`, enforced by a URL rule and again by `@PreAuthorize` on the admin controllers. Everything else except register, login, docs and health needs a valid token.
- **Current user.** `CurrentUser` (`id`, `email`, `role`) is the logged-in user. Its static factory `CurrentUser.from(Jwt)` is registered as the resource server's converter, so each verified token becomes a `CurrentUser`, which is itself the Spring `Authentication` and carries `ROLE_USER` or `ROLE_ADMIN`. Code that needs the logged-in user calls `CurrentUser.get()`, or `CurrentUser.find()` where nobody may be logged in. This reads Spring Security's per-request context, so it needs no method parameters, works in any layer, and does not depend on an HTTP request. Ownership is decided from this object, never from identifiers the client sends:
  - A transfer has **no source-account field**. The source is the caller's own account, looked up from the token, so nobody can debit another customer's wallet.
  - `/me/**` endpoints resolve the account from the token.
  - `GET /transactions/{reference}` returns 404 unless the caller sent or received the transaction, so a guessed reference can't be confirmed to exist.
  - Name enquiry returns only the account name.
- **Passwords.** Hashed with Spring's delegating encoder (BCrypt) and never returned or logged.
- **Login errors.** An unknown email returns 404 `EMAIL_NOT_REGISTERED` ("Please register first"). A known email with the wrong password returns 401 `INVALID_CREDENTIALS`. This deliberately reveals whether an email is registered, for a clearer user experience; registration already reveals it through `DUPLICATE_EMAIL`. The defence against password guessing would be login lockout and rate limiting (see *What I would add*).
- **Transaction PIN.** Every transfer needs the customer's PIN, and customers set it at registration.
  - The PIN is exactly 4 digits. Repeated digits (`0000`) and straight runs (`1234`, `9876`) are rejected.
  - It is stored as a BCrypt hash and masked in every `toString`.
  - After 3 wrong PINs in a row it locks for 30 minutes (configurable). A correct PIN resets the count.
  - The check runs in its own transaction (`REQUIRES_NEW`, no rollback on a wrong PIN). This means a failed attempt is still counted even though the transfer itself rolls back.
  - The user row is locked during the check, so parallel guesses can't lose increments.
  - `PUT /me/pin` changes the PIN. A wrong current PIN counts towards the same lockout, so it can't be used to guess the PIN.
- **Errors.** 401 and 403 responses use the same `ApiError` body as everything else, and 401 includes `WWW-Authenticate: Bearer`.
- **Traceability.** See *Logging and audit trail* below.

### Two account numbers per wallet

The mobile number is accepted as `07068565534` or `+2347068565534` (`PhoneNumbers.parse`). It is stored in E.164 form and is unique, so the same number can't be registered twice in different formats. The 10 digits after the `0` become the **phone account number**.

| | Generated account number | Phone account number |
|---|---|---|
| Example | `4821937065` | `7068565534` |
| First digit | 1–6 | 7, 8 or 9 (Nigerian mobile prefixes 070/080/081/090/091) |

Because the first digits never overlap, the two kinds of number can never collide. The generator also checks both columns before it issues a number. Wherever a number is accepted (transfer, name enquiry, beneficiaries, funding, admin lookups and search), either kind works.

Internally there is one canonical number. Every input is resolved to the generated account number, and that number is what locks, transactions, ledger entries and beneficiaries store. So:

- statements look the same whichever number the sender used;
- paying your own phone number counts as a same-account transfer;
- one account can't be saved twice as a beneficiary under its two numbers.

### Beneficiaries

A beneficiary belongs to the customer who saved it. The holder's name is read live from the account, so it never goes stale. Paying a beneficiary is optional and has its own endpoint, `POST /transfers/beneficiaries/{beneficiaryId}`, so a normal transfer body never contains beneficiary fields. Another customer's beneficiary id returns 404 for listing, transferring and deleting, so ids can't be probed. `saveAsBeneficiary: true` on a transfer saves the recipient after the transfer succeeds, unless it is already saved. Listing uses Specifications: `ownedBy(me).and(matching(q))`.

### Logging and audit trail

**Logs.** Every request gets a correlation id (`RequestLoggingFilter`). An incoming well-formed `X-Request-Id` is reused; otherwise one is generated. The id is returned in the `X-Request-Id` response header and stored on audit events. Every log line carries it, plus the logged-in user id once the request is authenticated (`CurrentUserMdcInterceptor`):

```
INFO [req=7f3c... user=12] c.e.t.config.RequestLoggingFilter : POST /api/v1/transfers -> 201 (38 ms)
INFO [req=7f3c... user=12] AUDIT : action=TRANSFER_COMPLETED outcome=SUCCESS actor=12 role=USER resource=TRANSACTION:TRF2026... details={"fromAccount":...}
```

- **One access line per request:** method, path, status and duration. It is INFO for 2xx/3xx, WARN for 4xx and ERROR for 5xx. Health checks are logged at DEBUG.
- **Errors:** rejected business rules are logged at INFO with their error code, validation failures at INFO with field names only, 401 and 403 at WARN, and unexpected errors at ERROR with the stack trace (the client never sees it).
- **No secrets in logs:** passwords, PINs and tokens are never logged. Request DTOs mask them in `toString`, and query strings are left out of access lines.
- **Configuration:** `LOG_LEVEL` sets the application log level. `SQL_LOG_LEVEL=DEBUG` shows SQL. `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` switches to JSON logs (Spring Boot structured logging), which include the MDC fields. `LOGGING_FILE_NAME` writes to a rotating file as well.

**Audit trail.** The `audit_events` table is append-only (`@Immutable`). Each event records:

- what happened (`action`, `outcome`, `errorCode`, plus a small JSON `details` object with no secrets);
- who did it (`actorUserId`, `actorRole`);
- what it affected (`resourceType` and `resourceId`, e.g. `TRANSACTION`/`TRF…` or `ACCOUNT`/`4821937065`);
- where it came from (`requestId`, `ipAddress`, `userAgent`).

Every event is also written to the `AUDIT` logger.

| Action | When |
|---|---|
| `USER_REGISTERED` | Registration |
| `LOGIN_SUCCEEDED` / `LOGIN_FAILED` | Login. A failure records the attempted email and the error code (`EMAIL_NOT_REGISTERED` or `INVALID_CREDENTIALS`) |
| `PIN_VERIFICATION_FAILED` / `PIN_LOCKED` / `PIN_CHANGED` | Transaction PIN |
| `TRANSFER_COMPLETED` / `TRANSFER_FAILED` | Every customer transfer, with from, to, amount, beneficiary and replay flag, or the error code |
| `WALLET_FUNDED`, `ACCOUNT_STATUS_CHANGED` | Admin actions, with the admin as actor |
| `BENEFICIARY_ADDED` / `BENEFICIARY_REMOVED` | Beneficiary changes |

When the audit record is saved matters:

- **Successes** are written in the same transaction as the change they describe, so a success record exists only if the change committed.
- **Failures in a rolled-back request** (a failed transfer or login) are written in a separate transaction (`REQUIRES_NEW`), so the attempt stays on record even though the change is undone. If writing that record fails, the error is logged and the original error still reaches the client.
- **PIN failures** are written in the PIN check's own transaction, which commits along with the failed-attempt count.

To debug something a client reports, take the `X-Request-Id` from their response, then search `GET /admin/audit-events?requestId=…` and grep the logs for `req=…`.

### Search with Specifications

Each search endpoint binds its query parameters to a criteria record, such as `TransactionSearchCriteria`. A Specification class (`TransactionSpecifications`, `LedgerEntrySpecifications`, `AccountSpecifications`) turns the supplied filters into one AND-ed predicate and skips the ones left empty. A join is added only when a filter needs it.

A customer's statement is always `forAccount(myAccount).and(matching(criteria))`, so the ownership restriction can't be dropped by the filters. Before any query runs, the code rejects ranges where the minimum is above the maximum or the start date is after the end date. LIKE wildcards in `q` are escaped. Statement queries fetch the parent transaction with an entity graph to avoid N+1 selects.

### How a transfer works

All of this runs in **one database transaction**:

1. Resolve the source account from the authenticated user and verify their transaction PIN.
2. Validate the input: different accounts, an amount greater than zero, at most 2 decimal places.
3. If the `Idempotency-Key` has been seen before, return the original transaction (`200`, `Idempotent-Replayed: true`). If the same key comes with a different body, reject it (`409`).
4. Lock both account rows with `SELECT … FOR UPDATE`, **always in ascending account-number order**. Opposite transfers (A→B and B→A) therefore can't deadlock, and concurrent debits can't both spend the same balance.
5. Check the idempotency key again, in case a concurrent retry committed while this request waited for the lock.
6. Check that both accounts are `ACTIVE` and use the same currency, and that the source has enough funds.
7. Debit and credit the wallets. Write one `WalletTransaction` and two `LedgerEntry` rows (DEBIT and CREDIT), each with the balance before and after.

If any step fails, nothing is persisted. There are backstops in the database too:

- A `CHECK` constraint rejects negative balances.
- `WalletBalance` has an `@Version` column for optimistic locking.
- Unique constraints cover email, account number, reference and idempotency key.

Money is always `BigDecimal` and stored as `NUMERIC(19,2)`. Amounts with more than 2 decimal places are rejected, not rounded.

### Persistence

- Flyway owns the schema, and Hibernate never alters it.
- Entity associations are `LAZY`. Each query fetches what its use case needs with an entity graph or fetch join, so there are no hidden per-row selects.
- `WalletTransaction` and `LedgerEntry` are `@Immutable`, so once written they can never be updated.
- Emails are normalised to lowercase once (`Emails.normalise`) and looked up with exact matches, so the unique index is used.
- Timestamps are stored as UTC.

---

## Tests

`./mvnw verify` runs:

| Test | What it proves |
|---|---|
| `SecurityIntegrationTest` | 401 without a token or with a tampered one. An unknown email gets 404 `EMAIL_NOT_REGISTERED`; a wrong password gets 401 `INVALID_CREDENTIALS`. Customers get 403 on admin endpoints and 404 on other customers' transactions. A transfer always debits the caller, even if the body names another account. Docs and health are public. |
| `BeneficiaryAndPhoneAccountIntegrationTest` | Phone normalisation, and duplicates across formats. Name enquiry and transfers by phone account number land in the right wallet, with the canonical number recorded. Paying your own phone number is rejected. Beneficiaries: add, list and search, transfer to a beneficiary through its own endpoint, the duplicate, self and unknown-account rules, other customers' beneficiaries being invisible, `saveAsBeneficiary`, and plain transfers that need only `toAccount`. |
| `PhoneNumbersTest` | Local and international formats give the same result, valid prefixes are accepted, and malformed numbers are rejected |
| `PinIntegrationTest` | A transfer needs a PIN. A wrong PIN moves no money and reports the attempts left. Three wrong PINs lock it, even against the correct PIN. A correct PIN resets the count. Changing the PIN means only the new one works afterwards. Weak PINs are rejected. |
| `PinServiceTest` | The PIN strength rules: repeated digits, sequences and bad formats |
| `AuditTrailIntegrationTest` | A successful transfer is audited with the response's `X-Request-Id`. A failed transfer is audited even though it rolled back. A wrong PIN produces both a PIN failure and a failed transfer. Login success and failure are audited, the password is never stored and the real reason is. Admin actions record the admin as actor. Customers can't read the audit trail. Invalid filters are rejected. |
| `SearchIntegrationTest` | Statement filters (entry type, transaction type, amount range, date range, paging). Admin transaction search with combined filters. Account search by name or email. Rejection of invalid filters and page sizes. |
| `WalletApiIntegrationTest` | End-to-end register → login → fund → name enquiry → transfer → balance and statement. Idempotent replay. Validation and business error codes. Frozen accounts. |
| `WalletServiceIntegrationTest` | Onboarding, email normalisation, password hashing, transfers that always debit the current user's own wallet, ledger correctness, insufficient funds, amount rules, idempotency, funding |
| `CurrentUserTest` | A token becomes a `CurrentUser` with the matching `ROLE_` authority. Tokens with malformed claims are rejected as invalid. |
| `ConcurrentTransferIntegrationTest` | 150 concurrent 10.00 debits against a 1,000.00 balance: exactly 100 succeed. Opposite-direction transfers finish with no deadlock and no lost money. |
| `AccountNumberGeneratorTest` | Number format, retry on collision, and failure after the maximum number of attempts |

---

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `9090` | HTTP port |
| `LOG_LEVEL` / `SQL_LOG_LEVEL` | `INFO` / `WARN` | Application log level, and SQL logging (`DEBUG` to show statements) |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | unset (plain text) | `ecs` or `logstash` for JSON logs |
| `DB_URL` | in-memory H2 (PostgreSQL mode) | JDBC URL. The driver and dialect are detected automatically |
| `DB_USERNAME` / `DB_PASSWORD` | `sa` / empty | Database credentials |
| `JWT_SECRET` | local development value | HS256 signing secret, at least 32 characters. **Always set this outside local development.** |
| `JWT_TTL` | `60m` | Access-token lifetime |
| `PIN_MAX_ATTEMPTS` / `PIN_LOCK_DURATION` | `3` / `30m` | Wrong PINs in a row before the PIN locks, and for how long |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `admin@wallet.local` / `Admin@12345` | Bootstrap admin. Leave `ADMIN_EMAIL` empty to skip creating one |
| `WALLET_FUNDING_ENABLED` | `true` | Registers the demo funding endpoint |

---

## Assumptions

1. A customer registers with first name, last name, email, a Nigerian mobile number (required, unique), password and a 4-digit transaction PIN. The phone account number is fixed at registration. Changing the phone number would need a verified (OTP) flow, which is out of scope. Phone number is optional. Email is the login identity, stored in lowercase.
2. Each customer has exactly one account and one wallet, in NGN, starting at 0.00. Admins are back-office users without a wallet.
3. A transfer goes from the caller's own wallet to a different, existing, `ACTIVE` account in the same currency. The amount must be positive with at most 2 decimal places and covered by the balance. The brief defines no fees, limits or overdrafts, so none are applied.
4. Transfers are synchronous, so a stored transaction is always `SUCCESSFUL`. Failed attempts are rolled back.
5. The brief has no funding flow, but transfers need money to move. A demo, admin-only funding endpoint is provided and can be switched off with config. Funding writes one CREDIT entry. In production, wallets would be credited from verified payment or bank events against a settlement account.
6. Access tokens are short-lived and there is no refresh token or logout. Logging in again issues a new token.
7. Idempotency keys are optional and unique across all clients.

## What I would add for production

- Refresh tokens with rotation and revocation, an asymmetric signing key (RS256 via JWKS), lockout after repeated failed logins, a PIN reset flow (for example OTP to the phone or email on file), and step-up authentication (OTP) for high-value transfers
- Idempotency keys scoped per client, with expiry
- Transfer limits, fees and velocity checks
- Funding through verified payment webhooks posted against a settlement or GL account, plus reversals
- Daily reconciliation of each balance against the sum of its ledger entries
- An outbox pattern to publish transaction events (for example to Kafka), shipping logs and audit events to a central store (ELK or Loki) with retention policies, rate limiting, metrics and distributed tracing (Micrometer/OpenTelemetry), and secrets management
