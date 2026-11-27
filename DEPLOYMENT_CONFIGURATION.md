# Deployment Configuration

## Profiles

| Profile | Purpose |
| --- | --- |
| *(none)* | Base config. Safe defaults; nothing secret. |
| `dev` | Local convenience — loose rate limits, `DEBUG` logging, stack traces on `?trace=true`. |
| `prod` | Every credential from the environment with no fallback. Stack traces off, `baseline-on-migrate` off, Prometheus exposed. |

```sh
SPRING_PROFILES_ACTIVE=prod java -jar app.jar
```

`prod` deliberately sets `spring.flyway.baseline-on-migrate: false`. Baselining
exists to adopt a pre-Flyway database once; leaving it on in production would let
an empty or wrong database be silently stamped as migrated instead of failing.

## Bootstrap administrator

On the first startup outside `dev` (or when `prod` is also active), set
`BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD`. The password must be
at least 12 characters and at most 72 UTF-8 bytes. Choose a unique, randomly
generated password and provide it through your deployment's secret management.
Startup fails if no administrator exists and either value is missing.

The application only creates an administrator when no user has the `ADMIN`
role, including inactive users. It never resets an existing password or
promotes an existing non-administrator account. Once an administrator exists,
remove the bootstrap credentials from the environment. With `dev` active and
`prod` inactive, the fictional demo seed provides its own administrator.
Bootstrap a fresh database with one application instance before starting
additional instances.

**Existing deployments:** earlier versions created an administrator with a
publicly known password. This change does not modify existing accounts.
Change that password or disable the account before exposing the application,
and review other previously created accounts.

## Required environment variables

| Variable | Profile | Notes |
| --- | --- | --- |
| `JWT_SECRET` | all | Token signing key. No default — the application refuses to start without it. |
| `BOOTSTRAP_ADMIN_EMAIL` | first non-`dev` startup | Email for a new administrator when none exists. |
| `BOOTSTRAP_ADMIN_PASSWORD` | first non-`dev` startup | Unique password for that account; remove after bootstrap. |
| `DB_URL` | `prod` | JDBC URL. |
| `DB_USERNAME` | `prod` | |
| `DB_PASSWORD` | `prod` | |
| `APP_CORS_ALLOWED_ORIGINS` | `prod` | Comma-separated. Defaults to localhost, which blocks any deployed frontend. |

### `JWT_SECRET`

Generate one per environment:

```sh
openssl rand -base64 64
```

Must be at least **64 bytes**. `Keys.hmacShaKeyFor` picks the HMAC variant from
the key length, so a shorter key silently downgrades the signature below HS512.
`JwtTokenProvider` validates this at startup and fails fast with a message
naming the problem.

Three values are rejected outright:

- empty or unset
- shorter than 64 bytes
- the old placeholder that used to be committed as the default
  (`your-secret-key-change-this-in-production-...`). Anyone with access to the
  repository history knows it and could mint a token for any account, so it is
  rejected by name rather than merely discouraged.

Rotating the secret invalidates every issued token, so all users are signed out.

## Optional environment variables

| Variable | Default | Notes |
| --- | --- | --- |
| `MAIL_HOST` | `localhost` | Only used when `app.mail.enabled` is true. |
| `MAIL_PORT` | `25` | |
| `MAIL_USERNAME` | *(empty)* | |
| `MAIL_PASSWORD` | *(empty)* | |
| `MAIL_SMTP_AUTH` | `false` | |
| `MAIL_SMTP_STARTTLS` | `false` | |
| `MAIL_ENABLED` | `true` in `prod` | Controls email notifications in the `prod` profile; disabled in the base configuration. |
| `EVIDENCE_UPLOAD_DIR` | `/var/lib/kpi/evidence` in `prod` | Directory for evidence files in the `prod` profile. |
| `GITHUB_METRICS_ENABLED` | `false` | Turns on automated KPI actuals from GitHub. |
| `GITHUB_TOKEN` | *(empty)* | PAT with read access. Without it the connector reports itself disabled even when the flag is on. |
| `GITHUB_API_URL` | `https://api.github.com` | Point at GitHub Enterprise, or at a stub for testing. |

## Response security headers

Set globally in `SecurityConfig` and asserted by `HttpSecurityHeadersTest`:

| Header | Value |
| --- | --- |
| `Content-Security-Policy` | `default-src 'self'`, `script-src 'self'`, `object-src 'none'`, `frame-ancestors 'none'` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `X-Content-Type-Options` | `nosniff` |
| `Strict-Transport-Security` | 180 days, `includeSubDomains` (applied over HTTPS only) |

`script-src` deliberately omits `'unsafe-inline'`. Adding it back — usually to
make an embedded widget or analytics snippet work — removes most of the
protection the policy provides.

Uploaded evidence is served as an **attachment**, never inline. These are
user-supplied bytes returned from the application's own origin: a PDF can carry
JavaScript, and an image that is really markup only needs the browser to sniff
its way to the same conclusion. Downloading hands the file to the operating
system rather than to the page holding the session.

If a deployment fronts the application with a CDN or a proxy that injects
scripts, the policy has to be widened deliberately — not by deleting it.

## Tunable settings

| Property | Default | Notes |
| --- | --- | --- |
| `app.security.login.max-attempts` | `10` | Failed sign-ins allowed per window. |
| `app.security.login.window-seconds` | `300` | Length of the window. |
| `spring.servlet.multipart.max-file-size` | `10MB` | Evidence upload cap. |
| `spring.servlet.multipart.max-request-size` | `12MB` | |
| `app.reminders.cron` | `0 0 8 * * *` | Daily 08:00 deadline and stale-review sweeps. |
| `app.evidence.upload-dir` | `uploads/kpi-evidence` | |

### Login rate limiting

`/api/auth/login` is limited per **client address + account**, not by either
alone. Address alone would let one bad actor lock out everyone behind a shared
office NAT; account alone would let an attacker lock a colleague out of their
own login just by failing it repeatedly. Exceeding the limit returns **429**
with a `Retry-After` header. A successful sign-in clears the counter.

State is in memory, so **each instance counts separately** — behind a load
balancer the effective limit multiplies by the instance count. Move it to Redis
before scaling out.

## Sessions and logout

A JWT validates itself, so dropping it from the browser never actually ended a
session: a token copied beforehand stayed usable until it expired. `POST
/api/auth/logout` now revokes the presented token server-side via
`TokenDenylist`, and `JwtAuthenticationFilter` rejects it thereafter.
The browser sends both its access and refresh tokens separately on logout.
Revocation only succeeds for requests received by the server; a network failure
can leave a token usable until it expires. Refresh tokens cannot authenticate
API requests, and access tokens cannot be used to refresh.

Tokens are held as a SHA-256 hash, so a dump of that map hands out nothing
usable. Entries are swept once past the token's own expiry
(`app.security.denylist.sweep-ms`), because beyond that point the signature check
rejects the token anyway.

Same caveat as rate limiting: **in memory, per instance**. Other instances or
an instance after restart may still accept a logged-out token until it expires.

## Endpoint exposure

`/api/auth/**` used to be blanket `permitAll`, which also exposed
`/api/auth/change-password` and `/api/auth/me`. Public access is now enumerated:

| Public | Authenticated |
| --- | --- |
| `POST /api/auth/login` | `GET /api/auth/me` |
| `POST /api/auth/refresh-token` | `POST /api/auth/change-password` |
| `POST /api/auth/logout` | everything else |
| `GET /api/auth/validate` | |
| `GET /actuator/health`, `/actuator/info` | `/actuator/**` requires `ADMIN` |

`logout` stays public on purpose: revoking an expired or unknown token is a
no-op, and a client whose token has already lapsed should still be able to
complete a sign-out.

Metrics and the Prometheus scrape require `ADMIN` — they disclose endpoint
names, traffic volumes and memory profile. Only `health` and `info` are open, so
the container runtime can probe them.

## Request tracing

Every response carries `X-Correlation-Id`, and the same value is on each log
line via `%X{correlationId}`. A user reporting a failure only needs to supply
that header value to locate the exact request.

An inbound `X-Correlation-Id` is reused so a call chain stays linked, but only
if it is short and alphanumeric — the value reaches the log, and a newline would
let a caller forge log entries.

## HTTP status contract

The API previously returned `400` for almost every failure. It now
distinguishes:

| Status | Meaning |
| --- | --- |
| `400` | Malformed request, missing header/parameter, or a business rule violation. |
| `401` | Not authenticated — bad credentials, expired or invalid token. |
| `403` | Authenticated but not permitted. |
| `404` | No such record, or no such endpoint. |
| `405` | The endpoint exists but does not accept that verb. |
| `409` | Conflict — concurrent edit, duplicate, or wrong workflow state. |
| `413` | Upload exceeds the multipart limit. |
| `429` | Login rate limit exceeded. |
| `500` | Defect. Body is deliberately opaque; details are in the log. |

`5xx` bodies never contain the exception message. Database constraint names and
`NullPointerException` text were both previously returned to clients.

Everything Spring raises before a controller runs is mapped too. Left to the
catch-all, an unmapped URL, a missing `Authorization` header and a malformed
JSON body all returned **500** — reporting a caller's mistake as a server fault,
sending them to the wrong team, and filling the error log with stack traces for
every scanner that guessed a path. Messages name the missing header or
parameter but never echo a submitted value, since the response is rendered by
the client.

The frontend reads these via `extractHttpError()` in
`frontend/src/app/services/http-error.util.ts`, which falls back to a
status-appropriate message when the server sends no body.

## Token lifetime

`expires_in` on the login and refresh responses is a count of **seconds**, per
RFC 6749. It previously carried milliseconds, so a one-hour token advertised
itself as roughly 41 days. Nothing consumed the value, which is the only reason
it had not surfaced — the first client to schedule a refresh from it would have
waited well past expiry and dropped the user mid-edit.

## Health and metrics

Actuator exposes `health`, `info` and `metrics` in the base configuration;
the `prod` profile also exposes `prometheus`:

```
GET /actuator/health
```

## Container

```sh
docker build -t kpi:latest .

docker run -p 8080:8080 \
  -e JWT_SECRET="$(openssl rand -base64 64)" \
  -e BOOTSTRAP_ADMIN_EMAIL \
  -e BOOTSTRAP_ADMIN_PASSWORD \
  -e DB_URL=jdbc:postgresql://db:5432/kpi \
  -e DB_USERNAME=kpi_user \
  -e DB_PASSWORD=... \
  -e APP_CORS_ALLOWED_ORIGINS=https://kpi.example.com \
  -v kpi-evidence:/var/lib/kpi/evidence \
  kpi:latest
```

Set `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` in the shell or your
secret manager before first startup. The database host in the example must be
reachable from the container.

Three-stage build: Angular, then Maven, then a JRE-only runtime. Runs as an
unprivileged user and sizes the heap with `MaxRAMPercentage` so it honours the
container's memory limit rather than the host's total.

**Mount a volume at `/var/lib/kpi/evidence`.** Uploaded evidence files live
there and are referenced by database rows that do survive a restart; without a
volume every attachment 404s after a redeploy.

Tests are skipped during the image build — they need a live PostgreSQL, which is
not available at build time. Run them in CI instead.

## Automated KPI actuals

A KPI can be measured from a connected system instead of by hand. Three pieces
have to line up:

| Piece | Where | Meaning |
| --- | --- | --- |
| A connector | `GITHUB_METRICS_ENABLED` + `GITHUB_TOKEN` | The system can be called at all. |
| A binding | `POST /api/config/metrics/bindings` | *This* KPI is measured by *that* metric. |
| An identity | `POST /api/config/metrics/identities` | Who the employee is over there. |

Missing any one of them is reported as a distinct, actionable outcome rather
than as a failure — `NOT_AUTOMATED`, `PROVIDER_UNAVAILABLE`, `NO_IDENTITY`.
`GET /api/metrics/providers` says which connectors are usable.

`kpi_masters.data_source` is unrelated: it is free text describing intent to a
human. The binding is the executable form.

### A collected value is a suggestion, not the record

`POST /api/kpi/{id}/collect-metric` writes to `kpis.suggested_actual_*` and
never to `actual_value`. The employee copies it across explicitly.

This is deliberate. An external API's definition of "merged pull requests"
drifts, a handle can be mismapped, and the person being measured is the one
held accountable for the resulting rating — so the number they submit has to be
one they chose. It also keeps an outage from presenting itself as a score of
zero: a provider that cannot be reached returns `FAILED`, never `0`.

Supported GitHub metrics: `pull_requests_merged`, `reviews_given`,
`issues_closed`. A binding naming anything else is rejected when it is created,
not months later when someone needs the number.

## Verification scripts

```sh
export E2E_ADMIN_EMAIL=admin@example.invalid
export E2E_ADMIN_PASSWORD='<unique test-only password>'
# A fresh non-dev database also needs these values for initial bootstrap.
export BOOTSTRAP_ADMIN_EMAIL="$E2E_ADMIN_EMAIL"
export BOOTSTRAP_ADMIN_PASSWORD="$E2E_ADMIN_PASSWORD"
./mvnw clean -DskipTests package     # required first; `clean test` removes the jar

# Migrations apply and Hibernate validates the mapping
./tools/verify_migrations.sh <db> /tmp/boot.log

# JWT secret enforcement and login rate limiting
./tools/smoke_auth.sh <db>

# Logout really revokes a token; correlation ids are returned
./tools/smoke_logout.sh <db>

# /api/config/lookups still serves data
./tools/smoke_lookups.sh <db>

# Automated actuals: bindings, identities, and the full collection path
# against a local stub GitHub
./tools/smoke_metrics.sh <db>

# Catch blocks that swallow exceptions silently
python3 tools/find_silent_catches.py
```
