# Removed: GCP and Email Architecture (reference)

Captured 2026-08-09, before deletion.

This documents what the Google Cloud and email pieces of Prost did, so a future
implementation can start from knowledge rather than archaeology. **None of this
code was working.** The notes below record intent and mechanism, plus what was
actually broken, so the same mistakes are not rebuilt.

> Every credential mentioned here was committed to a public repository and must
> be treated as compromised. Do not reuse any of them.

## Overall shape

Three deployables, all deployed from GitLab CI to GCP project
`maximal-radius-375114`, region `europe-west3`:

1. **Prost** — the Spring Boot app, on App Engine Flex with a custom Docker runtime.
2. **bi-function** — an HTTP Cloud Function writing order statistics to Datastore.
3. **invoicing-function** — an HTTP Cloud Function intended to email a PDF invoice.

The main app knew about the two functions only through two config properties,
`prost.cfg.bi-function-url` and `prost.cfg.invoicing-function-url`.

## bi-function — order statistics

**Entry point:** `de.unibamberg.dsam.group6.bifunction.Statistics` (`HttpFunction`)
**Runtime:** Java 17, 512 MB, max 1 instance, HTTP trigger
**Storage:** Cloud Datastore, kind `stat`

Intended flow: on checkout, the app POSTs an anonymized summary of the order;
the function persists it for later business-intelligence queries.

Request body, produced by `Order.getStats()`:

```json
{
  "timestamp": 1699999999999,
  "postalCode": "96047",
  "orderItems": { "<beverageId>": <count> }
}
```

`postalCode` came from the user's delivery address, defaulting to `"00000"` when
absent. The payload deliberately carried no username — postal code was the only
location signal, which is a reasonable privacy stance worth preserving.

The function rejected any request whose `Content-Type` was not
`application/json`, and required all three fields present with a non-empty
`orderItems`.

### Known defects

- **Never called.** `StatisticsService.sendStats()` had no caller anywhere in the
  codebase. No statistic was ever sent.
- **Every record overwrote the previous one.** The Datastore key was built as
  `newKeyFactory().setKind("stat").newKey("stat-value")` — a hardcoded constant
  key. Datastore `put` on an existing key replaces it, so the table would have
  held exactly one row forever. A rebuild needs an allocated ID or a natural key.
- `orderItems` was stored as a JSON *string* rather than structured properties,
  making it unqueryable — which defeats the point of a BI store.
- `StatisticsService` constructed a new `RestTemplate` per call.
- The POST was synchronous and unguarded, so a slow or down function would have
  blocked checkout. Statistics should never be able to fail an order.

## invoicing-function — invoice email

**Entry points:** `Invoice` (the `HttpFunction`) and `SendEmail` (the logic)
**Runtime:** Java 17, 512 MB, max 1 instance, HTTP trigger
**Dependencies:** `javax.mail:mail:1.4.7`, `google-cloud-storage:2.16.0`

Intended flow: the admin panel POSTs an order to the function; the function
fetches a pre-generated `Invoice.pdf` from a Cloud Storage bucket and emails it
to the customer as an attachment.

Mechanism, as written:

- SMTP via `smtp.gmail.com:587`, STARTTLS, TLSv1.2.
- Bucket `prost--invoice-pdf-bucket`, object `Invoice.pdf`.
- Downloaded to a local path, attached via `MimeMultipart` alongside a
  `"PFA Order details"` text part, then deleted after sending.

### Known defects

- **`Invoice.service()` ignored its request entirely.** It wrote
  `"Hello, World from Invoicing function!"` and called `SendEmail.main()`. No
  order data was ever read from the request body.
- **The caller was a stub.** `AdminController.callSendEmail` (`/admin/printPDF`)
  contained only a `//TODO` and returned a template. No template in the app
  referenced this endpoint, so it was unreachable from the UI.
- **Hardcoded absolute Windows paths** — `D:\Sanketh\Spring Workspace\group6\...`
  for both the credentials file and the PDF scratch location. These cannot exist
  in a Linux Cloud Function; the function would have thrown on first use.
- **Hardcoded credentials in source** — a Gmail address and app password as
  string literals, plus a service-account JSON read from disk. A deployed
  function should use the ambient service account, not a key file.
- **Hardcoded recipient** — every invoice went to one fixed personal address
  rather than the ordering user.
- **Nothing generated the PDF.** The function assumed `Invoice.pdf` already
  existed in the bucket. No code anywhere created it. The `//TODO: get the file
  from bucket and send it as a attachment` comment marks where this stalled.
- Sending was synchronous inside the HTTP handler with no retry.

## App Engine deployment

`app.yaml`:

```yaml
runtime: custom
env: flex
beta_settings:
  cloud_sql_instances: maximal-radius-375114:europe-west3:prost-db=tcp:5678
handlers:
  - url: .*
    secure: always
    redirect_http_response_code: 301
    script: auto
```

`runtime: custom` + `env: flex` means App Engine built the repo `Dockerfile`
rather than using a managed runtime. The `beta_settings` line opened a Cloud SQL
proxy on `tcp:5678`, which is why `application-prod.yml` pointed at
`jdbc:postgresql://172.17.0.1:5678/prost` — `172.17.0.1` being the Docker bridge
gateway inside the Flex VM.

`handlers` forced HTTPS with a 301, matching
`http.requiresChannel().anyRequest().requiresSecure()` in the prod filter chain.

### Dockerfile

Two-stage, `amazoncorretto:17-alpine-jdk` → `amazoncorretto:17-alpine`:

1. Build the frontend first (`npm install && npm run build`), because Gradle
   copies `src/main/resources/static` into the jar afterward. **Order matters** —
   reversing it ships a jar with no CSS.
2. `./gradlew clean bootJar` → `prost.jar`.
3. Generate a `start.sh` that read the DB password from a file and exec'd the jar.

**Defect:** the password was read from `/app/resources/main/db_passwd`, a file
written into the image by CI. This baked the secret into an image layer, where it
persists in the layer history regardless of later deletion. A rebuild should pass
it as a runtime environment variable or use Secret Manager.

## GitLab CI

`image: google/cloud-sdk:alpine`, single `deploy` stage, `only: main`, three
parallel jobs — one per deployable.

Authentication used `download-secure-files` to fetch
`maximal-radius-375114-f09823b3e627.json` (a *second* service-account key,
distinct from the one committed to `static/`) into `.secure_files/`, then
`gcloud auth activate-service-account`.

The app job injected two files into `src/main/resources` before deploying:

- `db_passwd` — from the `$DB_PASSWD` CI variable
- `version` — branch, short SHA, commit message, and job start time

`VersionReader` read that `version` file to display a build identity in the admin
panel. It degrades to `"unknown"` when the file is absent and returns `"dev"` on
the dev profile, so removing CI does not break it.

**Defect:** the function jobs passed the *contents* of the JSON key via
`--set-env-vars GOOGLE_APPLICATION_CREDENTIALS=...`. That variable is meant to
hold a *path*, not a payload — so this could not have worked, and it wrote the
key into the function's environment where it is visible to anyone with viewer
access. Deployed Cloud Functions should use their ambient service account
identity and need no key at all.

## Compromised credentials

All were committed to a public repository. All must be revoked, not merely
deleted.

| Credential | Location | Notes |
| --- | --- | --- |
| Gmail app password | `SendEmail.java:24-25`, plaintext | Highest risk — grants SMTP send on that account, no expiry |
| SendGrid API key | `sendgrid.env` (UTF-16) | Separate account from the GCP project, so possibly still live |
| GCP service account key | `src/main/resources/static/maximal-radius-…c8c681e93685.json` | Also publicly served by Spring at its URL |
| GCP service account key (2nd) | `…f09823b3e627.json`, GitLab secure files | Not in the repo, but tied to the same project |

## If rebuilding

Nothing here needs to be a Cloud Function. Both features are small enough to be
services inside the Spring app, and that removes the deployment, credential, and
network-failure surface entirely.

- **Statistics** — a `stats` table and a Spring `@EventListener` on order
  creation. Asynchronous, and failure must never propagate into checkout.
- **Invoices** — generate the PDF (the missing piece in the original) and send
  via `spring-boot-starter-mail`. Trigger asynchronously after checkout. Address
  it to the ordering user. Keep SMTP credentials in environment variables.
- If either genuinely needs to scale independently later, extract it *then*,
  with a real reason.
