# Prost Modernization — Design

Date: 2026-08-09
Status: Approved for planning

## Context

Prost is a beverage web shop built as a University of Bamberg DSAM group project:
Spring Boot 2.7.5 on Java 17, Thymeleaf server-rendered pages, Spring Security,
JPA, and a TailwindCSS frontend bundled by Webpack. Two Google Cloud Functions
live in sibling Gradle subprojects.

The repository has one commit, is pushed to a **public** GitHub remote, and
carries committed credentials. Spring Boot 2.7 reached end of life in November
2023 and no longer receives free security patches.

### Verified baseline

The application was built and run before any design work, to establish what
"working" means. On the `dev` profile it runs with no GCP, no Docker, and no
Postgres — H2 in-memory only.

| Step | Result |
| --- | --- |
| `pnpm install` + `pnpm build` (Node 26) | Compiles clean |
| `./gradlew compileJava` | Succeeds |
| `./gradlew bootRun` | Started in 6.4s on port 8080 |
| Login as `admin`/`admin` | 302 to `/` |
| Seed via `databaseLoader::importAll` | 29 inserts |
| Catalogue `/bottles`, `/crates`, pagination | Populated |
| Add to cart | 3 items in `/cart` |
| Checkout `/cart/submit` | 302 to `/order_success`, order persisted |
| Order detail for own order | 200 |
| Order detail for another user's order | 302, correctly denied |
| Stock decrement on checkout | `update beverage set in_stock=?` fired |

This baseline is what the characterization tests in Phase 3 must reproduce.

### Verified findings

**Credentials (public repo):**

- `invoicing-function/.../SendEmail.java:24-25` contains a Gmail address and
  **app password as plaintext string literals**. Highest risk of the three: an
  app password grants SMTP send on that account and does not expire on its own.
- `sendgrid.env` contains a `SENDGRID_API_KEY`. It is committed and pushed. The
  file is UTF-16 encoded. SendGrid is a separate account from the GCP project,
  so this key may still be live.
- `src/main/resources/static/maximal-radius-375114-c8c681e93685.json` is a GCP
  service-account private key. It is committed, pushed, and sits in `static/`,
  so Spring serves it at `/maximal-radius-375114-c8c681e93685.json`. Harmless
  only because the project is dead.
- `.gitlab-ci.yml` references a second service-account key,
  `…f09823b3e627.json`, held in GitLab secure files rather than the repo.

**Authorization:**

- `SecurityConfig.java:27` — the `dev` filter chain has no `/admin/**` rule. The
  default active profile is `dev,local` (`application.yml:3`). Reproduced:
  `GET /admin` returns 200 unauthenticated.

**Correctness:**

- `CartController.addToCart` checks `count <= beverage.getInStock()`, but
  `submitCart` never re-checks. Concurrent checkouts can oversell;
  `Math.max(reduced, 0)` clamps stock to zero instead of failing.
- `AdminController.runAction` (`:54`) has four `return "redirect:"` early-exits
  with no error and no logging. Observed: an unrecognized action name returned
  302 and silently did nothing.

**Dead code:**

- `StatisticsService.sendStats()` is never called anywhere.
- `AdminController.callSendEmail` (`:132`) is a `//TODO` stub returning a
  template. Neither Cloud Function is reachable at runtime.
- `ThymeleafConfig` registers a `Java8TimeDialect` bean, but `temporals.` appears
  in no template. The dialect and its dependency are unused.

**Build and repo hygiene:**

- `.gitignore` contains `./build`, which git does not honor; 113 build and
  `node_modules` artifacts are tracked. The file also has mangled UTF-16
  `sendgrid.env` entries.
- Gradle 7.5.1 warns it is incompatible with Gradle 8.0.
- `application-prod.yml:16` uses `ddl-auto: update` — schema mutates on every
  startup with no recorded history.
- `Dockerfile:22` bakes the DB password into an image layer.
- Three test files exist, covering only the cart.

## Goals

1. Remove the exposed credentials from the repository and its history.
2. Delete all Google Cloud and email code, none of which ever worked, after
   capturing its design intent as reference documentation.
3. Bring the application to a supported stack: Gradle 8, Spring Boot 3.5, Java 21.
4. Replace runtime schema mutation with recorded migrations.
5. Fix the authorization hole and the correctness bugs found above.
6. Keep the whole thing runnable locally with no GCP and no containers.

## Non-goals

- Deploying anywhere. No GCP project, no App Engine, no Docker requirement.
- Reimplementing statistics or invoicing. Both are deleted, not rebuilt. The
  reference doc describes how to build them properly if they are ever wanted.
- Redesigning the UI or the Thymeleaf templates.
- Any refactoring not named in Phase 5.

## Architecture decisions

### GCP and email are removed entirely

Superseding an earlier decision to keep the Cloud Functions as stubs: all Google
Cloud and email code is deleted, not retained.

The justification is that none of it worked. `sendStats()` had no caller,
`callSendEmail` was a `//TODO` returning a template, no template referenced
`/printPDF`, and `SendEmail` read absolute Windows paths that cannot exist in a
Linux Cloud Function. Keeping non-functional code as a "stub" means carrying it
through the Boot 3 migration — `javax.mail` alone would need addressing — for
code that never ran.

Their design intent is preserved in
`docs/reference/removed-gcp-and-email-architecture.md`, written before deletion.
It records the payload shapes, the deployment topology, and each defect found, so
a future implementation starts from knowledge rather than archaeology.

### Postgres stays, de-GCP'd

`application-prod.yml` is repointed from the Cloud SQL proxy address
(`172.17.0.1:5678`) to a plain `localhost` Postgres. `docker-compose.yml` remains
as the optional way to start one.

This keeps a realistic non-H2 target to validate Flyway migrations against.
Migrations exercised only against H2 would not prove much, since H2 is not what a
deployment would run.

### Local-first runtime

The `dev` profile (H2 in-memory) is the supported development path.
`docker-compose.yml` and the Postgres `prod` profile remain in the repository but
are optional. No phase of this work requires a container.

### Phase ordering

Phases are ordered so that any failure has exactly one candidate cause.

Phase 1 is independent of everything else and cannot break the application.

Phase 2 (deletion) precedes Phase 3 (tests) so that no test is written against
code about to be removed, and precedes Phase 4 (upgrade) so that dead code is
never migrated — `javax.mail` and the Cloud Functions SDK would otherwise all
need Boot 3 treatment for code that never ran.

Phase 3 must precede Phase 4, because tests written against a verified-working
baseline are what make an upgrade auditable. Phase 5 is deliberately separated
from Phase 4: refactoring and migrating in the same diff means a failing test has
two possible explanations.

## Phases

### Phase 1 — Secrets and access

Prerequisite, performed by the repository owner outside this work. Revocation is
the actual fix; purging an unrevoked credential accomplishes nothing.

1. **Revoke the Gmail app password** for `pytestertrack@gmail.com` at
   myaccount.google.com/apppasswords, and review that account's recent security
   activity. Highest priority — it is live until revoked.
2. Delete the SendGrid API key in the SendGrid dashboard and check its activity
   feed for sends that were not yours.
3. Delete both GCP service accounts if the project still exists.

Then, in the repository:

1. Delete `src/main/resources/static/maximal-radius-375114-c8c681e93685.json` and
   `sendgrid.env` from the working tree.
2. Rewrite the single commit without those files and force-push. The repository
   has one commit, one branch, and no collaborators, so `git filter-repo` is
   unnecessary. Owner has approved the force-push.
3. Fix `.gitignore`: `./build` becomes `build/`; add `*/build/`; repair the
   mangled UTF-16 `sendgrid.env` entries; add `.vscode/`, `*.iml`, `.idea/`.
4. `git rm -r --cached` the tracked `build/`, `node_modules/`,
   `bi-function/build/`, and the `.idea/` and `.iml` files under `src/test/`.
5. Add the `/admin/**` → `hasRole("ADMIN")` rule to the `dev` filter chain in
   `SecurityConfig`, matching `prod`.

Exit criteria: `git log -p` contains neither credential; `GET /admin`
unauthenticated returns 403; `git status` is clean of build artifacts; the app
still starts and the baseline flows still pass by hand.

### Phase 2 — Remove GCP and email

Runs before the characterization tests so that no test is ever written against
code that is about to be deleted, and before the upgrade so that deleted code is
never migrated.

Prerequisite: `docs/reference/removed-gcp-and-email-architecture.md` is written
and committed. It already is.

Delete:

- `bi-function/` and `invoicing-function/` subprojects, and both `include` lines
  in `settings.gradle`
- `app.yaml`, `.gcloudignore` (root and both subprojects)
- `.gitlab-ci.yml` — every job in it is a `gcloud` deploy
- `Dockerfile` — it existed only to give App Engine Flex a custom runtime, and it
  bakes the DB password into an image layer. Deleting it resolves that finding
  without a fix.
- `Send/` and `Send.zip` — a complete stale duplicate of the application, 50 Java
  files plus its own Gradle cache. Not referenced by `settings.gradle`, so never
  built.
- `StatisticsService.java`
- `Order.getStats()` (`Order.java:67`) — its only caller was `StatisticsService`
- `AdminController.callSendEmail` and its `/printPDF` mapping — verified
  unreferenced by any template
- `com.google.code.gson:gson` from `build.gradle` — verified used only by
  `StatisticsService` and `Order.getStats()`
- `prost.cfg.bi-function-url` and `prost.cfg.invoicing-function-url` from
  `application.yml` and `application-dev.yml`

Retain: `CallFailedException`, which is used by `AdminActionsProvider` and
`AdminController` and is unrelated to GCP. `VersionReader` also stays — it
degrades to `"unknown"` without the CI-written `version` file and returns
`"dev"` on the dev profile.

Also repoint `application-prod.yml` from `172.17.0.1:5678` to a plain
`localhost:5432` Postgres, matching `docker-compose.yml`.

Exit criteria: `./gradlew build` succeeds; `grep -ri "gcloud\|google.cloud\|
sendgrid\|javax.mail"` over the source tree returns nothing outside
`docs/reference/`; the app starts and the full baseline table still passes by
hand.

### Phase 3 — Characterization tests

Automate the baseline table above using `@SpringBootTest` with `MockMvc` and
Spring Security's test support, against the `dev` profile and H2.

These tests pin current behavior rather than asserting correctness. Where current
behavior is wrong — for example, checkout not re-validating stock — the test
records what it does today, with a comment marking it for Phase 5.

Coverage required:

- Unauthenticated access: public pages return 200; `/cart`, `/orders`, `/user`
  redirect to login; `/admin` requires ADMIN.
- Login with `admin`/`admin` succeeds; bad credentials fail.
- `databaseLoader::importAll` populates bottles and crates.
- Catalogue pages render and paginate.
- Add to cart, then remove one, then remove all.
- Checkout persists an `Order` with its `OrderItem`s and decrements stock.
- Order detail returns 200 for the owner and redirects for a different user.

Exit criteria: `./gradlew test` green on Boot 2.7. This is the reference result
every later phase is compared against.

### Phase 4 — Upgrade

Performed in ordered steps, running the Phase 3 suite after each. Each step is a
separate commit so a regression can be bisected.

1. Gradle wrapper 7.5.1 → 8.x. Fix deprecated build syntax, including
   `bootJar { archiveName }` → `archiveFileName`.
2. Spring Boot 2.7.5 → 3.0.x. This is the large step: `javax.*` → `jakarta.*`
   across every entity, `Cart`, `CartController`, and the validator classes;
   Spring Security 6 rewrites both filter chains (`antMatchers` →
   `requestMatchers`, and the `authorizeHttpRequests` lambda style).
3. Introduce Flyway before any Boot 3 startup touches a persistent database.
   Baseline `V1__initial.sql` is generated from the schema Hibernate currently
   emits, captured from the verified `bootRun` log. `ddl-auto` becomes
   `validate` in `prod` and stays `create` in `dev`.
4. Spring Boot 3.0.x → 3.5.x.
5. Java 17 → 21: `sourceCompatibility` in `build.gradle`. The Dockerfile and CI
   were deleted in Phase 2, so nothing else pins a JDK version.
6. Refresh the frontend toolchain only as far as needed: run
   `npx update-browserslist-db@latest` to clear the stale `caniuse-lite` warning.
   Webpack 5 builds clean on Node 26 and is not otherwise touched.

Exit criteria: `./gradlew test` green with the same assertions as Phase 3, on
Boot 3.5 and Java 21, plus a manual pass through the baseline table.

### Phase 5 — Cleanup

Only after Phase 4 is green.

1. Extract an `OrderService` from `CartController.submitCart`. The controller
   keeps HTTP concerns; order creation, item persistence, and stock decrement
   move to the service. Phase 3 tests must stay green throughout.
2. Fix overselling: re-validate stock inside the transaction at checkout, and
   fail the order with a toast rather than clamping to zero with
   `Math.max(reduced, 0)`. Update the Phase 3 test that recorded the old
   behavior, and add a test for the rejection path.
3. Fix the silent admin dispatcher: each early-exit in
   `AdminController.runAction` adds an error toast and a log line.
4. Delete the unused `Java8TimeDialect` bean from `ThymeleafConfig` and drop the
   `thymeleaf-extras-java8time` dependency. No template uses `temporals.`.

`StatisticsService` and the Docker image's baked-in DB password are handled in
Phase 2, by deletion.

Exit criteria: `./gradlew test` green, including new tests for the overselling
rejection path.

## Testing strategy

One suite, built in Phase 3 and carried through unchanged into Phase 4. It is the
control: identical assertions before and after the upgrade, so any difference is
attributable to the upgrade alone.

Phase 5 is the only phase permitted to change existing assertions, and only for
the overselling behavior, which is a deliberate behavior change.

Integration tests use `@SpringBootTest` with `MockMvc` against H2. No mocking of
repositories — the point is to exercise the real persistence path, since JPA and
Hibernate are exactly what the Boot 3 upgrade changes underneath.

## Risks

**Hibernate 5 → 6 schema differences.** Boot 3 ships Hibernate 6, which generates
schema differently. Mitigated by introducing Flyway in Phase 4 step 3, before any
Boot 3 startup can touch a persistent database. The `dev` profile is in-memory
and recreated per run, so it is not at risk.

**`javax` → `jakarta` breadth.** The change touches nearly every file. The
compiler catches import errors; the Phase 3 suite catches behavioral ones.

**Spring Session JDBC 2.x → 3.x.** Found while writing Phase 3; not in the
original analysis. The app uses `spring-session-jdbc`, which stores sessions in a
`SPRING_SESSION` table keyed by its own `SESSION` cookie rather than in the
servlet session. Boot 3 moves this to Spring Session 3.x, which changes that
schema, and `spring.session.jdbc.initialize-schema: always` applies the change on
startup.

This matters because the cart lives entirely in session state — a silent failure
here empties every user's cart while the rest of the application looks healthy.

`SessionPersistenceTest` covers it. It is the only test that runs with Spring
Session enabled, driving requests through the real `SESSION` cookie and asserting
both that the schema exists and that cart contents round-trip through the
database. Every other integration test disables Spring Session via
`@IntegrationTest`, because `MockMvc`'s `.session(...)` helper sets a servlet
session the application never reads once Spring Session is active — which made
every cart and login assertion fail for reasons unrelated to the behavior under
test. If `SessionPersistenceTest` alone fails during Phase 4, the cause is the
session store, not the controllers.

**Thymeleaf extras compatibility.** Three separate cases, verified:

- `thymeleaf-extras-springsecurity5` → `-springsecurity6`. Straightforward
  version swap. The `sec:` namespace is used in `footer.html`, `navbar.html`,
  and `index.html`.
- `thymeleaf-extras-java8time` — the `Java8TimeDialect` bean is registered in
  `ThymeleafConfig`, but `temporals.` appears in **no** template. The dependency
  and the bean are both unused and get deleted rather than upgraded.
- `thymeleaf-extras-nl2br:1.0.2` — a small third-party library, last released
  for Thymeleaf 3.0, unlikely to have a Boot 3 build. It is genuinely used in
  three places: `toasts.html:14`, `admin.html:62`, and `cart.html:27`. If no
  compatible release exists, the `Nl2brDialect` bean is deleted and the three
  usages become `th:text` with `white-space: pre-line` in CSS, which renders
  newlines without the dialect and without introducing `th:utext` (which would
  be an HTML-injection risk on user-supplied address text).

The nl2br case is the most likely place to need an unplanned decision.

**Force-push.** Destructive to the remote, approved by the owner. Single commit,
single branch, no collaborators.

## Open items

None. Statistics and invoicing are deleted by decision, not by omission; the
reference doc records how to rebuild them properly if they are ever wanted.
