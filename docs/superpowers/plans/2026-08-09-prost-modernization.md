# Prost Modernization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Purge exposed credentials, delete all non-functional Google Cloud and email code, then upgrade Prost from Spring Boot 2.7.5 / Java 17 / Gradle 7.5.1 to Spring Boot 3.5 / Java 21 / Gradle 8 behind a characterization test suite.

**Architecture:** Five sequential phases, ordered so any failure has exactly one candidate cause. Secrets first (independent, cannot break the app). Deletion second (so no test is written against doomed code and no dead code is migrated). Characterization tests third (pinning the verified baseline). Upgrade fourth (incremental hops, suite green between each). Refactoring last (never mixed with migration).

**Tech Stack:** Spring Boot, Thymeleaf, Spring Security, Spring Data JPA, Hibernate, H2 (dev) / PostgreSQL (prod), Flyway, Gradle, JUnit 5, MockMvc, AssertJ, Webpack + TailwindCSS.

## Global Constraints

- Target stack: Gradle 8.x, Spring Boot 3.5.x, Java 21. Do not skip intermediate versions in Phase 4.
- The `dev` profile must remain runnable with no GCP, no Docker, and no Postgres — H2 in-memory only.
- `./gradlew test` must be green at the end of every task from Task 8 onward.
- Never change an existing test assertion except where a task explicitly says to. Phase 5 Task 22 is the only permitted behavior change.
- Package root is `de.unibamberg.dsam.group6.prost`. Tests live under `src/test/java/de/unibamberg/dsam/group6/prost/`.
- Code style is enforced by Spotless with `palantirJavaFormat`, 4-space indent. Run `./gradlew spotlessApply` before committing Java changes.
- Commit after every task. Never bundle two tasks into one commit.
- The spec for this work is `docs/superpowers/specs/2026-08-09-prost-modernization-design.md`. The reference doc for deleted subsystems is `docs/reference/removed-gcp-and-email-architecture.md`.

---

## Baseline (verified 2026-08-09, must remain true)

| Behavior | Expected |
| --- | --- |
| `GET /`, `/bottles`, `/crates`, `/login`, `/register` anonymous | 200 |
| `GET /cart`, `/orders`, `/user` anonymous | 302 → `/login` |
| Login `admin` / `admin` | 302 → `/` |
| `GET /admin/action?a=databaseLoader::importAll&await=true` as admin | 302, seeds bottles + crates |
| `POST /cart/add` with `beverageId`, `count` | 302, item in cart |
| `POST /cart/submit` with non-empty cart | 302 → `/order_success`, Order persisted, stock decremented |
| `GET /orders/{id}` for own order | 200 |
| `GET /orders/{id}` for another user's order | 302 → `/orders` |

The `admin`/`admin` account is created on every startup by `StartListener`.

---

## File Structure

**Phase 1–2 delete:**
- `sendgrid.env`, `src/main/resources/static/maximal-radius-375114-c8c681e93685.json`
- `bi-function/`, `invoicing-function/`, `Send/`, `Send.zip`
- `app.yaml`, `.gcloudignore`, `.gitlab-ci.yml`, `Dockerfile`
- `src/main/java/.../service/StatisticsService.java`

**Phase 1–2 modify:**
- `.gitignore`, `settings.gradle`, `build.gradle`
- `src/main/java/.../configuration/SecurityConfig.java`
- `src/main/java/.../controller/AdminController.java`
- `src/main/java/.../entity/Order.java`
- `src/main/resources/application.yml`, `application-dev.yml`, `application-prod.yml`

**Phase 3 create (one file per concern):**
- `src/test/java/.../support/IntegrationTest.java` — shared test annotation
- `src/test/java/.../support/TestData.java` — seeding helper
- `src/test/java/.../AuthorizationTest.java`
- `src/test/java/.../AuthenticationTest.java`
- `src/test/java/.../CatalogueTest.java`
- `src/test/java/.../CartFlowTest.java`
- `src/test/java/.../CheckoutTest.java`
- `src/test/java/.../OrderAccessTest.java`

**Phase 4 create:**
- `src/main/resources/db/migration/V1__initial_schema.sql`

**Phase 5 create:**
- `src/main/java/.../service/OrderService.java`
- `src/test/java/.../OrderServiceTest.java`

---

# PHASE 1 — Secrets and access

## Prerequisite (repository owner, outside this plan)

These are not code steps. Revocation is the fix; deleting a file does not invalidate a key.

- [ ] Revoke the Gmail app password for `pytestertrack@gmail.com` at https://myaccount.google.com/apppasswords, and review that account's recent security activity.
- [ ] Delete the SendGrid API key at https://app.sendgrid.com/settings/api_keys and check the Activity Feed for sends that were not yours.
- [ ] Delete both GCP service accounts in project `maximal-radius-375114` if it still exists.

**Do not start Task 2 until these are done.** Rewriting history around a live credential accomplishes nothing.

---

### Task 1: Remove credential files and fix repository hygiene

**Files:**
- Delete: `sendgrid.env`, `src/main/resources/static/maximal-radius-375114-c8c681e93685.json`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: nothing
- Produces: a `.gitignore` that actually excludes build output, which every later task depends on for clean `git status`

The current `.gitignore` contains `./build`. Git does not honor a leading `./` in ignore patterns, which is why 113 build artifacts are tracked. The file is also partly UTF-16 encoded, producing the mangled `s e n d g r i d . e n v` entries.

- [ ] **Step 1: Confirm what is currently tracked**

```bash
git ls-files | grep -cE '^(build|node_modules)/'
```

Expected: `113`. Record this number; Step 6 verifies it becomes `0`.

- [ ] **Step 2: Delete the two credential files**

```bash
git rm --cached sendgrid.env "src/main/resources/static/maximal-radius-375114-c8c681e93685.json"
rm -f sendgrid.env "src/main/resources/static/maximal-radius-375114-c8c681e93685.json"
```

- [ ] **Step 3: Replace `.gitignore` entirely**

Write `.gitignore` as UTF-8 with exactly this content:

```gitignore
# Build output
build/
*/build/
.gradle
*/.gradle

# Node
node_modules/

# Generated frontend bundle
src/main/resources/static/build/

# IDE
.idea/
.vscode/
*.iml
.project
.settings

# Local database
*.db
db_data/

# Secrets — never commit
sendgrid.env
*.env
**/db_passwd
**/*service-account*.json
maximal-radius-*.json
```

- [ ] **Step 4: Untrack all build artifacts**

```bash
git rm -r --cached build node_modules bi-function/build invoicing-function/build --ignore-unmatch
git rm -r --cached "src/test/java/de/unibamberg/dsam/group6/.idea" --ignore-unmatch
git rm --cached "src/test/java/de/unibamberg/dsam/group6/group6.iml" --ignore-unmatch
git rm -r --cached "src/main/resources/static/build" --ignore-unmatch
```

- [ ] **Step 5: Verify the app still builds and runs**

```bash
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`. Deleting a file from `static/` cannot affect compilation, but confirm it anyway.

- [ ] **Step 6: Verify nothing unwanted remains tracked**

```bash
git ls-files | grep -cE '^(build|node_modules)/'
git ls-files | grep -ciE 'sendgrid|maximal-radius'
```

Expected: `0` and `0`.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "chore: remove committed credentials and fix gitignore

Deletes the GCP service-account key and sendgrid.env from the working
tree, replaces the broken './build' ignore pattern with 'build/', and
untracks 113 build artifacts. Both credentials are revoked separately;
history purge follows."
```

---

### Task 2: Purge credentials from git history

**Files:** none — history operation only

**Interfaces:**
- Consumes: Task 1's deletions
- Produces: a remote with no credential in any commit

The repository has exactly two commits at this point (the original `initial comment` plus Task 1), one branch, and no collaborators. `git filter-repo` is unnecessary — a root rebase is enough. The owner has approved the force-push.

**Three credentials must leave history, not two.** The Gmail address and app password are string literals inside
`invoicing-function/src/main/java/de/unibamberg/dsam/group6/invoicingfunction/SendEmail.java`, not in a standalone
credential file. Task 4 deletes that directory, but an ordinary `git rm` never removes anything from history — so
`SendEmail.java` must be purged here, in the same pass as the other two files.

Purging `invoicing-function/` removes it from the working tree as well, which breaks the build until
`settings.gradle` drops its `include` line. **Task 4 must therefore run immediately after this task, before the
force-push**, so the pushed state is coherent.

- [ ] **Step 1: Create a safety backup branch**

```bash
git branch backup-before-purge
git log --oneline
```

Keep this branch locally until Step 6 passes. Do not push it.

- [ ] **Step 2: Confirm the credentials exist in history**

```bash
git log --all --oneline -- sendgrid.env "src/main/resources/static/maximal-radius-375114-c8c681e93685.json"
```

Expected: at least one commit listed. If empty, skip to Step 6.

- [ ] **Step 3: Rewrite every commit without the credential-bearing paths**

```bash
FILTER_BRANCH_SQUELCH_WARNING=1 git filter-branch --force --index-filter \
  'git rm -r --cached --ignore-unmatch sendgrid.env "src/main/resources/static/maximal-radius-375114-c8c681e93685.json" invoicing-function' \
  --prune-empty --tag-name-filter cat -- --all
```

`invoicing-function` is included because `SendEmail.java` carries the Gmail app password as a source literal. The
whole directory goes rather than the single file — it is deleted in Task 4 regardless, so there is nothing to
preserve.

`git filter-branch` prints a warning recommending `filter-repo`. For two commits and three paths it is fine and
needs no extra tooling; the env var suppresses the nag.

- [ ] **Step 4: Expire the original refs**

```bash
rm -rf .git/refs/original
git reflog expire --expire=now --all
git gc --prune=now --aggressive
```

- [ ] **Step 5: Verify the credentials are gone from all history**

Check by path **and** by content — a path check alone would miss a credential embedded in source, which is exactly how the Gmail app password inside `SendEmail.java` was nearly shipped.

```bash
git log --all --oneline -- sendgrid.env "src/main/resources/static/maximal-radius-375114-c8c681e93685.json"
git rev-list --all | xargs -I{} git ls-tree -r {} --name-only | sort -u | grep -ciE 'sendgrid|maximal-radius|SendEmail'
```

Expected: no commits from the first, `0` from the second.

For the content check, read the literal out of the old commit rather than writing it into this file — pasting a credential into documentation puts it right back into the repository you are cleaning:

```bash
SECRET=$(git show <pre-purge-sha>:invoicing-function/src/main/java/de/unibamberg/dsam/group6/invoicingfunction/SendEmail.java \
         | sed -n 's/.*password = "\([^"]*\)".*/\1/p')
git log --all --oneline -S "$SECRET"
git log --all --oneline -S "BEGIN PRIVATE KEY"
```

Expected: no commits from either.

Two traps, both of which produced false positives during the real run:

1. Any doc in the repo that quotes the search string will itself match. If a hit resolves to a file under `docs/`, it is your own verification text, not a leak — but remove it anyway.
2. `-S` reports any change in occurrence count, so the commit that *deletes* a secret matches just as the commit that added it does. Confirm the direction with `git show <sha> -- <path>` before concluding anything.

- [ ] **Step 6: Run Task 4 before pushing**

Purging `invoicing-function/` leaves `settings.gradle` including a directory that no longer exists, so the build is
broken until Task 4 lands. Complete Task 4 now, then return here.

- [ ] **Step 7: Force-push**

```bash
git push --force origin main
```

- [ ] **Step 8: Delete the backup branch**

```bash
git branch -D backup-before-purge
```

Only after Step 5 passed and the push succeeded.

---

### Task 3: Close the anonymous `/admin` hole

**Files:**
- Modify: `src/main/java/de/unibamberg/dsam/group6/prost/configuration/SecurityConfig.java:27-46`

**Interfaces:**
- Consumes: nothing
- Produces: `/admin/**` requires `ROLE_ADMIN` on every profile — Task 9 asserts this

The `dev` filter chain has no `/admin/**` rule, and `dev,local` is the default active profile (`application.yml:3`). Reproduced: `GET /admin` returns 200 while logged out.

- [ ] **Step 1: Reproduce the hole**

Start the app in one terminal:

```bash
./gradlew bootRun
```

Wait for `Started ApplicationMain`. In another terminal:

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/admin
```

Expected: `200`. This is the bug.

- [ ] **Step 2: Add the missing rule**

In `SecurityConfig.java`, inside `securityFilterChainProd` (the `@Profile("dev")` bean — the name is misleading and is corrected in Step 3), change:

```java
        http.authorizeHttpRequests(req -> {
            req.antMatchers("/cart/**", "/orders/**", "/user/**").authenticated();
            req.anyRequest().permitAll();
        });
```

to:

```java
        http.authorizeHttpRequests(req -> {
            req.antMatchers("/cart/**", "/orders/**", "/user/**").authenticated();
            req.antMatchers("/admin/**").hasRole("ADMIN");
            req.anyRequest().permitAll();
        });
```

- [ ] **Step 3: Fix the swapped bean names**

The `@Profile("dev")` bean is named `securityFilterChainProd` and the `@Profile("prod")` bean is named `securityFilterChain`. Rename the dev bean to `securityFilterChainDev` and the prod bean to `securityFilterChainProd`. This is a rename only — do not change any rule while renaming.

- [ ] **Step 4: Verify the hole is closed**

Stop the app (`Ctrl+C`, or kill the port owner — Gradle runs the app as a child JVM, so `Ctrl+C` sometimes orphans it):

```bash
./gradlew bootRun
```

Then:

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/admin
```

Expected: `403`.

Confirm an admin can still reach it:

```bash
rm -f /tmp/c.txt
T=$(curl -s -c /tmp/c.txt http://localhost:8080/login | grep -oP 'name="_csrf"\s+value="\K[^"]+' | head -1)
curl -s -b /tmp/c.txt -c /tmp/c.txt -o /dev/null -d "username=admin&password=admin&_csrf=$T" http://localhost:8080/login
curl -s -b /tmp/c.txt -o /dev/null -w "%{http_code}\n" http://localhost:8080/admin
```

Expected: `200`.

- [ ] **Step 5: Stop the app**

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply
git add src/main/java/de/unibamberg/dsam/group6/prost/configuration/SecurityConfig.java
git commit -m "fix: require ROLE_ADMIN for /admin on the dev profile

The dev filter chain had no /admin/** rule and dev is the default
active profile, so GET /admin returned 200 anonymously. Also fixes the
two filter-chain bean names, which were swapped."
```

---

# PHASE 2 — Remove GCP and email

Prerequisite: `docs/reference/removed-gcp-and-email-architecture.md` exists and is committed. Verify with `ls docs/reference/`. It captures the payload shapes, deployment topology, and known defects of everything deleted below.

---

### Task 4: Delete the Cloud Function subprojects and deployment config

**Files:**
- Delete: `bi-function/`, `invoicing-function/`, `app.yaml`, `.gcloudignore`, `.gitlab-ci.yml`, `Dockerfile`
- Modify: `settings.gradle`

**Interfaces:**
- Consumes: nothing
- Produces: a single-module Gradle build — Task 14's Gradle 8 upgrade assumes this

Deleting the `Dockerfile` also resolves the finding that it baked the DB password into an image layer (`Dockerfile:22`).

- [ ] **Step 1: Confirm nothing in the main app imports these**

```bash
grep -rn "bifunction\|invoicingfunction" src/main/java || echo "NO REFERENCES"
```

Expected: `NO REFERENCES`. The main app never imported the function classes; it only knew about them via two config URLs, removed in Task 6.

- [ ] **Step 2: Delete the directories and files**

```bash
git rm -r --cached bi-function invoicing-function --ignore-unmatch
rm -rf bi-function invoicing-function
git rm -f app.yaml .gcloudignore .gitlab-ci.yml Dockerfile --ignore-unmatch
rm -f app.yaml .gcloudignore .gitlab-ci.yml Dockerfile
```

- [ ] **Step 3: Reduce `settings.gradle` to a single module**

Replace the entire contents of `settings.gradle` with:

```gradle
rootProject.name = 'prost'
```

- [ ] **Step 4: Verify the build still works**

```bash
./gradlew clean compileJava
```

Expected: `BUILD SUCCESSFUL`, and the output no longer mentions `:bi-function:compileJava` or `:invoicing-function:compileJava`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "chore: delete GCP cloud functions and deployment config

Removes bi-function, invoicing-function, app.yaml, .gcloudignore,
.gitlab-ci.yml, and the Dockerfile. None of it worked: sendStats had no
caller, the invoicing function ignored its request body and read
absolute Windows paths, and the CI passed a service-account key's
contents where a path was expected. Design intent is preserved in
docs/reference/removed-gcp-and-email-architecture.md.

Deleting the Dockerfile also removes the DB password baked into an
image layer."
```

---

### Task 5: Delete the stale duplicate application

**Files:**
- Delete: `Send/`, `Send.zip`

**Interfaces:**
- Consumes: nothing
- Produces: nothing

`Send/` is a complete stale copy of the application — 50 Java files, its own `build.gradle`, its own tests, its own `.gradle` cache. It is not referenced by `settings.gradle`, so it has never been built.

- [ ] **Step 1: Confirm it is unreferenced**

```bash
grep -c "Send" settings.gradle
```

Expected: `0`.

- [ ] **Step 2: Confirm it is a duplicate, not unique work**

```bash
diff -rq Send/src/main/java src/main/java | head -20
```

Expected: differences are limited to the files this plan is already changing, or none. If this reveals substantial unique code, **stop and report it** rather than deleting.

- [ ] **Step 3: Delete**

```bash
git rm -r --cached Send --ignore-unmatch
rm -rf Send
git rm -f Send.zip --ignore-unmatch
rm -f Send.zip
```

- [ ] **Step 4: Verify the build**

```bash
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "chore: delete Send/ and Send.zip

A complete stale duplicate of the application (50 Java files plus its
own Gradle cache), never referenced by settings.gradle and never built."
```

---

### Task 6: Remove statistics and email code from the main app

**Files:**
- Delete: `src/main/java/de/unibamberg/dsam/group6/prost/service/StatisticsService.java`
- Modify: `src/main/java/de/unibamberg/dsam/group6/prost/entity/Order.java` (remove `getStats()`, lines 67-79, and the `com.google.gson.JsonObject` import)
- Modify: `src/main/java/de/unibamberg/dsam/group6/prost/controller/AdminController.java` (remove `callSendEmail` and its `/printPDF` mapping)
- Modify: `build.gradle` (remove the gson dependency)
- Modify: `src/main/resources/application.yml`, `src/main/resources/application-dev.yml`

**Interfaces:**
- Consumes: nothing
- Produces: an `Order` entity with no gson dependency — Task 15's jakarta migration touches this file

`CallFailedException` is **retained** — `AdminActionsProvider` and `AdminController` both use it for the live admin-action dispatcher, which is unrelated to GCP.

- [ ] **Step 1: Confirm gson is used only by the two files being changed**

```bash
grep -rn "gson\|JsonObject" src/main/java
```

Expected: matches only in `StatisticsService.java` and `Order.java`. If anything else appears, **stop and report**.

- [ ] **Step 2: Confirm `/printPDF` is unreferenced by the UI**

```bash
grep -rn "printPDF" src/main/resources/templates frontend || echo "NO REFERENCES"
```

Expected: `NO REFERENCES`.

- [ ] **Step 3: Delete `StatisticsService`**

```bash
git rm -f src/main/java/de/unibamberg/dsam/group6/prost/service/StatisticsService.java
```

- [ ] **Step 4: Remove `getStats()` from `Order.java`**

Delete the import line:

```java
import com.google.gson.JsonObject;
```

and delete this entire method:

```java
    public JsonObject getStats() {
        final var deliveryAddress = this.getUser().getDeliveryAddress();

        final var items = new JsonObject();
        CartDTO.fromOrder(this)
                .beverages
                .forEach((key, value) -> items.addProperty(key.getId().toString(), value));

        final var obj = new JsonObject();
        obj.addProperty("timestamp", this.createdOn.getTime());
        obj.addProperty("postalCode", deliveryAddress == null ? "00000" : deliveryAddress.getPostalCode());
        obj.add("orderItems", items);

        return obj;
    }
```

If `CartDTO` is now an unused import in `Order.java`, remove that import too. Check with `grep -n "CartDTO" src/main/java/de/unibamberg/dsam/group6/prost/entity/Order.java` after the deletion.

- [ ] **Step 5: Remove `callSendEmail` from `AdminController.java`**

Delete this entire method:

```java
    @PostMapping("/printPDF")
    public String callSendEmail(@RequestParam Optional<String> op, @ModelAttribute Order order) {
        //TODO Use RestController to make POST API Call to Cloud function

        return "pages/admin";
    }
```

Then check whether the `Order` import is still used in that file: `grep -n "Order" src/main/java/de/unibamberg/dsam/group6/prost/controller/AdminController.java`. Remove the import if it is now unused.

- [ ] **Step 6: Remove the gson dependency**

In `build.gradle`, delete this line:

```gradle
    implementation 'com.google.code.gson:gson'
```

- [ ] **Step 7: Remove the function URL config**

From `src/main/resources/application.yml`, delete these four lines:

```yaml
prost:
    cfg:
        bi-function-url: ''
        invoicing-function-url: ''
```

From `src/main/resources/application-dev.yml`, delete these four lines:

```yaml
prost:
    cfg:
        bi-function-url: http://localhost:8081/
        invoicing-function-url: http://localhost:8081/
```

- [ ] **Step 8: Verify no GCP or email code remains outside the reference doc**

```bash
grep -rniE "gcloud|google\.cloud|com\.google|sendgrid|javax\.mail|bi-function|invoicing" \
  --include="*.java" --include="*.gradle" --include="*.yml" --include="*.html" . \
  | grep -v "^./docs/" || echo "CLEAN"
```

Expected: `CLEAN`.

- [ ] **Step 9: Verify build and the full baseline**

```bash
./gradlew clean build
```

Expected: `BUILD SUCCESSFUL` and the 3 existing tests pass.

Then start the app and walk the baseline table at the top of this plan by hand — login, seed via `/admin`, catalogue, add to cart, checkout, order detail. All must behave as recorded.

- [ ] **Step 10: Commit**

```bash
./gradlew spotlessApply
git add -A
git commit -m "chore: remove statistics and email code from the main app

Deletes StatisticsService (no caller), Order.getStats (only used by
StatisticsService), AdminController.callSendEmail and its /printPDF
mapping (unreferenced by any template), the gson dependency (used only
by those two files), and the two cloud-function URL properties.

CallFailedException is retained; it is used by the live admin-action
dispatcher and is unrelated to GCP."
```

---

### Task 7: Repoint the prod profile off Cloud SQL

**Files:**
- Modify: `src/main/resources/application-prod.yml:5`

**Interfaces:**
- Consumes: nothing
- Produces: a prod datasource pointing at a plain Postgres, which Task 16 validates Flyway migrations against

`172.17.0.1:5678` was the Docker bridge gateway inside an App Engine Flex VM, where the Cloud SQL proxy listened. Neither exists now. `docker-compose.yml` already publishes Postgres on `5432`.

- [ ] **Step 1: Change the JDBC URL**

In `application-prod.yml`, change:

```yaml
        url: jdbc:postgresql://172.17.0.1:5678/prost
```

to:

```yaml
        url: ${DB_URL:jdbc:postgresql://localhost:5432/prost}
```

- [ ] **Step 2: Align the credentials with docker-compose**

`docker-compose.yml` sets `POSTGRES_USER: postgres` and `POSTGRES_PASSWORD: strongpassword`. The prod profile already uses `username: postgres`. Change the password line to keep the env-var override but add a local default:

```yaml
        password: ${DB_PASSWD:strongpassword}
```

- [ ] **Step 3: Verify the dev profile is unaffected**

```bash
./gradlew bootRun
```

Expected: starts on H2 as before. The prod profile is not activated, so this only proves nothing was broken by the edit.

Stop the app:

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/application-prod.yml
git commit -m "chore: repoint prod datasource from Cloud SQL to local Postgres

172.17.0.1:5678 was the Cloud SQL proxy inside an App Engine Flex VM.
Now defaults to localhost:5432, matching docker-compose.yml, with
DB_URL and DB_PASSWD overrides."
```

---

# PHASE 3 — Characterization tests

These tests pin **current** behavior, not correct behavior. Where behavior is wrong — checkout not re-validating stock — the test records what happens today with a `// CHARACTERIZATION` comment marking it for Task 22.

Cart state lives in the HTTP session (`Cart.java` injects `HttpSession`), so every multi-request flow must reuse one `MockHttpSession`.

---

### Task 8: Test infrastructure

**Files:**
- Create: `src/test/java/de/unibamberg/dsam/group6/prost/support/IntegrationTest.java`
- Create: `src/test/java/de/unibamberg/dsam/group6/prost/support/TestData.java`

**Interfaces:**
- Consumes: nothing
- Produces:
  - `@IntegrationTest` — a composed annotation applying `@SpringBootTest`, `@AutoConfigureMockMvc`, and `@ActiveProfiles("dev")`
  - `TestData.seedCatalogue(DatabaseLoader)` → `void` — seeds bottles and crates synchronously
  - `TestData.firstBottleId(BottlesRepository)` → `Long` — id of any seeded bottle

- [ ] **Step 1: Write the shared annotation**

Create `src/test/java/de/unibamberg/dsam/group6/prost/support/IntegrationTest.java`:

```java
package de.unibamberg.dsam.group6.prost.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public @interface IntegrationTest {}
```

- [ ] **Step 2: Write the seeding helper**

`DatabaseLoader`'s action methods are `@Async` and return `Future`. In tests they must be awaited, or assertions race the seeding.

Create `src/test/java/de/unibamberg/dsam/group6/prost/support/TestData.java`:

```java
package de.unibamberg.dsam.group6.prost.support;

import de.unibamberg.dsam.group6.prost.repository.BottlesRepository;
import de.unibamberg.dsam.group6.prost.service.admin.DatabaseLoader;

public final class TestData {
    private TestData() {}

    /** Seeds bottles and crates from data.json, blocking until complete. */
    public static void seedCatalogue(DatabaseLoader loader) {
        try {
            loader.action__importBottles().get();
            loader.action__importCrates().get();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to seed test catalogue", e);
        }
    }

    /** Returns the id of an arbitrary seeded bottle. Fails if none exist. */
    public static Long firstBottleId(BottlesRepository bottles) {
        return bottles.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No bottles seeded"))
                .getId();
    }
}
```

- [ ] **Step 3: Write a test proving the infrastructure works**

Create `src/test/java/de/unibamberg/dsam/group6/prost/support/TestDataTest.java`:

```java
package de.unibamberg.dsam.group6.prost.support;

import static org.assertj.core.api.Assertions.assertThat;

import de.unibamberg.dsam.group6.prost.repository.BottlesRepository;
import de.unibamberg.dsam.group6.prost.service.admin.DatabaseLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class TestDataTest {
    @Autowired
    DatabaseLoader loader;

    @Autowired
    BottlesRepository bottles;

    @Test
    void seedsCatalogue() {
        TestData.seedCatalogue(this.loader);
        assertThat(this.bottles.findAll()).isNotEmpty();
        assertThat(TestData.firstBottleId(this.bottles)).isNotNull();
    }
}
```

- [ ] **Step 4: Run it**

```bash
./gradlew test --tests "*TestDataTest*"
```

Expected: PASS. If it fails with "No bottles seeded", `@Async` is returning before completion — verify `.get()` is being called on both futures.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApply
git add src/test/java/de/unibamberg/dsam/group6/prost/support/
git commit -m "test: add integration test infrastructure

Shared @IntegrationTest annotation and a TestData helper that awaits
DatabaseLoader's @Async seeding methods."
```

---

### Task 9: Authorization characterization tests

**Files:**
- Create: `src/test/java/de/unibamberg/dsam/group6/prost/AuthorizationTest.java`

**Interfaces:**
- Consumes: `@IntegrationTest` from Task 8
- Produces: nothing

- [ ] **Step 1: Write the failing test**

Create `src/test/java/de/unibamberg/dsam/group6/prost/AuthorizationTest.java`:

```java
package de.unibamberg.dsam.group6.prost;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.unibamberg.dsam.group6.prost.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class AuthorizationTest {
    @Autowired
    MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"/", "/bottles", "/crates", "/login", "/register"})
    void publicPagesAreAnonymouslyAccessible(String path) throws Exception {
        this.mvc.perform(get(path)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/cart", "/orders", "/user"})
    void protectedPagesRedirectAnonymousUsersToLogin(String path) throws Exception {
        this.mvc.perform(get(path))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void adminIsForbiddenForAnonymousUsers() throws Exception {
        this.mvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "someone", roles = "USER")
    void adminIsForbiddenForNonAdminUsers() throws Exception {
        this.mvc.perform(get("/admin")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminIsAccessibleToAdmins() throws Exception {
        this.mvc.perform(get("/admin")).andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: Run it**

```bash
./gradlew test --tests "*AuthorizationTest*"
```

Expected: PASS. Task 3 already closed the `/admin` hole, so this suite documents the fixed state.

If `adminIsForbiddenForAnonymousUsers` fails with 200, Task 3 was not applied — go back and apply it.

If it fails expecting 403 but receiving a 302, that is Spring Security redirecting unauthenticated users to the login page rather than returning 403. That is correct behavior for a form-login app; the test above already expects a 3xx for the anonymous case and 403 only for the authenticated-but-unauthorized case.

- [ ] **Step 3: Commit**

```bash
./gradlew spotlessApply
git add src/test/java/de/unibamberg/dsam/group6/prost/AuthorizationTest.java
git commit -m "test: characterize route authorization rules"
```

---

### Task 10: Authentication characterization tests

**Files:**
- Create: `src/test/java/de/unibamberg/dsam/group6/prost/AuthenticationTest.java`

**Interfaces:**
- Consumes: `@IntegrationTest` from Task 8
- Produces: nothing

- [ ] **Step 1: Write the failing test**

Create `src/test/java/de/unibamberg/dsam/group6/prost/AuthenticationTest.java`:

```java
package de.unibamberg.dsam.group6.prost;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;

import de.unibamberg.dsam.group6.prost.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class AuthenticationTest {
    @Autowired
    MockMvc mvc;

    @Test
    void seededAdminCanLogIn() throws Exception {
        this.mvc.perform(formLogin("/login").user("admin").password("admin"))
                .andExpect(authenticated().withUsername("admin").withRoles("ADMIN"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        this.mvc.perform(formLogin("/login").user("admin").password("wrong"))
                .andExpect(unauthenticated());
    }

    @Test
    void unknownUserIsRejected() throws Exception {
        this.mvc.perform(formLogin("/login").user("nobody").password("nothing"))
                .andExpect(unauthenticated());
    }
}
```

The `admin` account with `ROLE_ADMIN` is created on every context startup by `StartListener`, so no seeding is needed here.

- [ ] **Step 2: Run it**

```bash
./gradlew test --tests "*AuthenticationTest*"
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
./gradlew spotlessApply
git add src/test/java/de/unibamberg/dsam/group6/prost/AuthenticationTest.java
git commit -m "test: characterize form login behavior"
```

---

### Task 11: Catalogue and seeding characterization tests

**Files:**
- Create: `src/test/java/de/unibamberg/dsam/group6/prost/CatalogueTest.java`

**Interfaces:**
- Consumes: `@IntegrationTest`, `TestData.seedCatalogue` from Task 8
- Produces: nothing

- [ ] **Step 1: Write the failing test**

Create `src/test/java/de/unibamberg/dsam/group6/prost/CatalogueTest.java`:

```java
package de.unibamberg.dsam.group6.prost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import de.unibamberg.dsam.group6.prost.repository.BottlesRepository;
import de.unibamberg.dsam.group6.prost.repository.CratesRepository;
import de.unibamberg.dsam.group6.prost.service.admin.DatabaseLoader;
import de.unibamberg.dsam.group6.prost.support.IntegrationTest;
import de.unibamberg.dsam.group6.prost.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class CatalogueTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    DatabaseLoader loader;

    @Autowired
    BottlesRepository bottles;

    @Autowired
    CratesRepository crates;

    @BeforeEach
    void seed() {
        TestData.seedCatalogue(this.loader);
    }

    @Test
    void seedingPopulatesBottlesAndCrates() {
        assertThat(this.bottles.findAll()).isNotEmpty();
        assertThat(this.crates.findAll()).isNotEmpty();
    }

    @Test
    void bottlesPageRendersWithBeverages() throws Exception {
        this.mvc.perform(get("/bottles"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/bottles"))
                .andExpect(model().attributeExists("beverages"));
    }

    @Test
    void cratesPageRendersWithBeverages() throws Exception {
        this.mvc.perform(get("/crates"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/crates"))
                .andExpect(model().attributeExists("beverages"));
    }

    @Test
    void bottlesPageAcceptsAPageParameter() throws Exception {
        this.mvc.perform(get("/bottles").param("page", "0")).andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: Run it**

```bash
./gradlew test --tests "*CatalogueTest*"
```

Expected: PASS.

If `model().attributeExists("beverages")` fails, open `IndexController.bottleCatalogue` and read the actual model attribute name, then correct the assertion to match. Do not change the controller — this is a characterization test.

- [ ] **Step 3: Commit**

```bash
./gradlew spotlessApply
git add src/test/java/de/unibamberg/dsam/group6/prost/CatalogueTest.java
git commit -m "test: characterize catalogue rendering and seeding"
```

---

### Task 12: Cart flow characterization tests

**Files:**
- Create: `src/test/java/de/unibamberg/dsam/group6/prost/CartFlowTest.java`

**Interfaces:**
- Consumes: `@IntegrationTest`, `TestData.seedCatalogue`, `TestData.firstBottleId` from Task 8
- Produces: nothing

Cart state lives in the session, so all requests in a flow share one `MockHttpSession`. CSRF is enabled, so POSTs need `with(csrf())`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/de/unibamberg/dsam/group6/prost/CartFlowTest.java`:

```java
package de.unibamberg.dsam.group6.prost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.unibamberg.dsam.group6.prost.repository.BottlesRepository;
import de.unibamberg.dsam.group6.prost.service.Cart;
import de.unibamberg.dsam.group6.prost.service.admin.DatabaseLoader;
import de.unibamberg.dsam.group6.prost.support.IntegrationTest;
import de.unibamberg.dsam.group6.prost.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@WithMockUser(username = "admin", roles = "ADMIN")
class CartFlowTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    DatabaseLoader loader;

    @Autowired
    BottlesRepository bottles;

    @Autowired
    Cart cart;

    MockHttpSession session;
    Long bottleId;

    @BeforeEach
    void setUp() {
        TestData.seedCatalogue(this.loader);
        this.bottleId = TestData.firstBottleId(this.bottles);
        this.session = new MockHttpSession();
        this.cart.clear();
    }

    @Test
    void addingABeveragePutsItInTheCart() throws Exception {
        this.mvc.perform(post("/cart/add")
                        .session(this.session)
                        .with(csrf())
                        .param("beverageId", this.bottleId.toString())
                        .param("count", "3"))
                .andExpect(status().is3xxRedirection());

        assertThat(this.cart.getCartItemIds()).containsEntry(this.bottleId, 3);
    }

    @Test
    void removingOneDecrementsTheCount() throws Exception {
        this.cart.addToCart(this.bottleId, 3);

        this.mvc.perform(post("/cart/remove")
                        .session(this.session)
                        .with(csrf())
                        .param("beverageId", this.bottleId.toString()))
                .andExpect(status().is3xxRedirection());

        assertThat(this.cart.getCartItemIds()).containsEntry(this.bottleId, 2);
    }

    @Test
    void removingAllClearsThatBeverage() throws Exception {
        this.cart.addToCart(this.bottleId, 3);

        this.mvc.perform(post("/cart/remove")
                        .session(this.session)
                        .with(csrf())
                        .param("beverageId", this.bottleId.toString())
                        .param("all", "true"))
                .andExpect(status().is3xxRedirection());

        assertThat(this.cart.getCartItemIds()).doesNotContainKey(this.bottleId);
    }

    @Test
    void addingWithoutABeverageIdIsABadRequest() throws Exception {
        this.mvc.perform(post("/cart/add").session(this.session).with(csrf()).param("count", "1"))
                .andExpect(status().is4xxClientError());
    }
}
```

- [ ] **Step 2: Run it**

```bash
./gradlew test --tests "*CartFlowTest*"
```

Expected: PASS.

If `addingWithoutABeverageIdIsABadRequest` produces a 500 rather than a 4xx, read how `BadRequestException` is mapped in `ControllerAdviceSetup` and `ProstErrorController`, then assert whatever actually happens. This is a characterization test — record reality.

- [ ] **Step 3: Commit**

```bash
./gradlew spotlessApply
git add src/test/java/de/unibamberg/dsam/group6/prost/CartFlowTest.java
git commit -m "test: characterize cart add and remove flows"
```

---

### Task 13: Checkout characterization tests

**Files:**
- Create: `src/test/java/de/unibamberg/dsam/group6/prost/CheckoutTest.java`

**Interfaces:**
- Consumes: `@IntegrationTest`, `TestData` from Task 8
- Produces: the test that Task 22 modifies when the overselling bug is fixed

**This task records a known bug as passing behavior.** `overselling_isCurrentlyPossible` documents that checkout does not re-validate stock. Task 22 replaces it.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/de/unibamberg/dsam/group6/prost/CheckoutTest.java`:

```java
package de.unibamberg.dsam.group6.prost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.unibamberg.dsam.group6.prost.repository.BeveragesRepository;
import de.unibamberg.dsam.group6.prost.repository.BottlesRepository;
import de.unibamberg.dsam.group6.prost.repository.OrdersRepository;
import de.unibamberg.dsam.group6.prost.service.Cart;
import de.unibamberg.dsam.group6.prost.service.admin.DatabaseLoader;
import de.unibamberg.dsam.group6.prost.support.IntegrationTest;
import de.unibamberg.dsam.group6.prost.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@WithMockUser(username = "admin", roles = "ADMIN")
class CheckoutTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    DatabaseLoader loader;

    @Autowired
    BottlesRepository bottles;

    @Autowired
    BeveragesRepository beverages;

    @Autowired
    OrdersRepository orders;

    @Autowired
    Cart cart;

    MockHttpSession session;
    Long bottleId;

    @BeforeEach
    void setUp() {
        TestData.seedCatalogue(this.loader);
        this.bottleId = TestData.firstBottleId(this.bottles);
        this.session = new MockHttpSession();
        this.cart.clear();
    }

    @Test
    void submittingAnEmptyCartRedirectsBackToCart() throws Exception {
        this.mvc.perform(post("/cart/submit").session(this.session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        assertThat(this.orders.findAllByUser_username("admin")).isEmpty();
    }

    @Test
    void submittingAPopulatedCartCreatesAnOrder() throws Exception {
        this.cart.addToCart(this.bottleId, 2);

        this.mvc.perform(post("/cart/submit").session(this.session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/order_success"));

        var placed = this.orders.findAllByUser_username("admin");
        assertThat(placed).hasSize(1);
        assertThat(placed.get(0).getOrderItems()).isNotEmpty();
    }

    @Test
    void checkoutEmptiesTheCart() throws Exception {
        this.cart.addToCart(this.bottleId, 2);

        this.mvc.perform(post("/cart/submit").session(this.session).with(csrf()));

        assertThat(this.cart.getCartItemIds()).isEmpty();
    }

    @Test
    void checkoutDecrementsStock() throws Exception {
        var before = this.beverages.findById(this.bottleId).orElseThrow().getInStock();
        this.cart.addToCart(this.bottleId, 2);

        this.mvc.perform(post("/cart/submit").session(this.session).with(csrf()));

        var after = this.beverages.findById(this.bottleId).orElseThrow().getInStock();
        assertThat(after).isEqualTo(before - 2);
    }

    /**
     * CHARACTERIZATION — records a known bug, not desired behavior.
     *
     * addToCart validates count against stock, but submitCart never re-checks.
     * Adding to the cart directly (bypassing the controller's check, exactly as a
     * concurrent second buyer would) lets an order exceed available stock, and
     * Math.max(reduced, 0) silently clamps stock to zero instead of failing.
     *
     * Task 22 fixes this and replaces this test.
     */
    @Test
    void overselling_isCurrentlyPossible() throws Exception {
        var stock = this.beverages.findById(this.bottleId).orElseThrow().getInStock();
        this.cart.addToCart(this.bottleId, stock + 5);

        this.mvc.perform(post("/cart/submit").session(this.session).with(csrf()))
                .andExpect(redirectedUrl("/order_success"));

        assertThat(this.orders.findAllByUser_username("admin")).hasSize(1);
        assertThat(this.beverages.findById(this.bottleId).orElseThrow().getInStock())
                .isZero();
    }
}
```

- [ ] **Step 2: Run it**

```bash
./gradlew test --tests "*CheckoutTest*"
```

Expected: PASS, including `overselling_isCurrentlyPossible`.

If that test **fails**, the bug is not what the spec describes — **stop and report** rather than adjusting the test to pass.

- [ ] **Step 3: Commit**

```bash
./gradlew spotlessApply
git add src/test/java/de/unibamberg/dsam/group6/prost/CheckoutTest.java
git commit -m "test: characterize checkout, including the overselling bug

overselling_isCurrentlyPossible records existing broken behavior so the
upgrade cannot change it silently. Task 22 fixes it."
```

---

### Task 14: Order access characterization tests

**Files:**
- Create: `src/test/java/de/unibamberg/dsam/group6/prost/OrderAccessTest.java`

**Interfaces:**
- Consumes: `@IntegrationTest`, `TestData` from Task 8
- Produces: nothing

- [ ] **Step 1: Write the failing test**

Create `src/test/java/de/unibamberg/dsam/group6/prost/OrderAccessTest.java`:

```java
package de.unibamberg.dsam.group6.prost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.unibamberg.dsam.group6.prost.repository.BottlesRepository;
import de.unibamberg.dsam.group6.prost.repository.OrdersRepository;
import de.unibamberg.dsam.group6.prost.service.Cart;
import de.unibamberg.dsam.group6.prost.service.admin.DatabaseLoader;
import de.unibamberg.dsam.group6.prost.support.IntegrationTest;
import de.unibamberg.dsam.group6.prost.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class OrderAccessTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    DatabaseLoader loader;

    @Autowired
    BottlesRepository bottles;

    @Autowired
    OrdersRepository orders;

    @Autowired
    Cart cart;

    MockHttpSession session;
    Long bottleId;

    @BeforeEach
    void setUp() {
        TestData.seedCatalogue(this.loader);
        this.bottleId = TestData.firstBottleId(this.bottles);
        this.session = new MockHttpSession();
        this.cart.clear();
    }

    /** Places an order as admin and returns its id. */
    private Long placeOrderAsAdmin() throws Exception {
        this.cart.addToCart(this.bottleId, 1);
        this.mvc.perform(post("/cart/submit").session(this.session).with(csrf()));
        return this.orders.findAllByUser_username("admin").get(0).getId();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void ownerCanViewTheirOrder() throws Exception {
        var id = this.placeOrderAsAdmin();

        this.mvc.perform(get("/orders/" + id).session(this.session))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("order"))
                .andExpect(model().attributeExists("cartDTO"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void ordersListShowsOwnOrders() throws Exception {
        this.placeOrderAsAdmin();

        this.mvc.perform(get("/orders").session(this.session))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("orders"));

        assertThat(this.orders.findAllByUser_username("admin")).hasSize(1);
    }

    @Test
    @WithMockUser(username = "marek", roles = "ADMIN")
    void nonOwnerIsRedirectedAwayFromSomeoneElsesOrder() throws Exception {
        this.mvc.perform(get("/orders/999999").session(this.session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders"));
    }
}
```

`OrdersController.showOrders` queries by username **and** id, so a non-owner and a nonexistent id take the same path — both redirect to `/orders`. The test uses a nonexistent id, which exercises exactly that branch without needing a second seeded user's order.

- [ ] **Step 2: Run it**

```bash
./gradlew test --tests "*OrderAccessTest*"
```

Expected: PASS.

- [ ] **Step 3: Run the whole suite**

```bash
./gradlew test
```

Expected: all green — the 3 pre-existing tests plus everything from Tasks 8-14. **Record the total test count.** Phase 4 compares against it.

- [ ] **Step 4: Commit**

```bash
./gradlew spotlessApply
git add src/test/java/de/unibamberg/dsam/group6/prost/OrderAccessTest.java
git commit -m "test: characterize order listing and ownership checks"
```

---

# PHASE 4 — Upgrade

Each task is a separate commit so a regression can be bisected. Run the full suite after every task. If a task's suite is red, fix it within that task — never carry a red suite forward.

---

### Task 15: Gradle 7.5.1 → 8.x

**Files:**
- Modify: `gradle/wrapper/gradle-wrapper.properties`
- Modify: `build.gradle`

**Interfaces:**
- Consumes: the green suite from Task 14
- Produces: a Gradle 8 build, required by the Boot 3 plugin

- [ ] **Step 1: See the deprecation warnings**

```bash
./gradlew build --warning-mode all 2>&1 | grep -i "deprecat" | head -20
```

Record what appears. `bootJar { archiveName }` is removed in Gradle 8.

- [ ] **Step 2: Upgrade the wrapper**

```bash
./gradlew wrapper --gradle-version 8.5
./gradlew wrapper --gradle-version 8.5
```

Run it twice — the first run updates the properties file, the second regenerates the wrapper scripts and jar using the new version.

- [ ] **Step 3: Fix the removed `archiveName` property**

In `build.gradle`, change:

```gradle
bootJar {
    archiveName "prost.jar"
}
```

to:

```gradle
bootJar {
    archiveFileName = "prost.jar"
}
```

- [ ] **Step 4: Fix `sourceCompatibility` if Gradle 8 warns about it**

If the build warns that `sourceCompatibility` is deprecated on the project, replace:

```gradle
sourceCompatibility = '17'
```

with:

```gradle
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
```

Keep 17 for now — Java 21 is Task 19.

- [ ] **Step 5: Verify**

```bash
./gradlew --version
./gradlew clean build
```

Expected: Gradle 8.5, `BUILD SUCCESSFUL`, same test count as Task 14 Step 3, all green.

- [ ] **Step 6: Commit**

```bash
git add gradle/wrapper/ gradlew gradlew.bat build.gradle
git commit -m "build: upgrade Gradle 7.5.1 to 8.5

Replaces the removed bootJar.archiveName with archiveFileName. Boot 3
requires Gradle 7.5+; moving to 8.x first isolates build-tool breakage
from framework breakage."
```

---

### Task 16: Spring Boot 2.7.5 → 3.0.x

**Files:**
- Modify: `build.gradle`
- Modify: every file importing `javax.persistence`, `javax.validation`, `javax.servlet`
- Modify: `src/main/java/de/unibamberg/dsam/group6/prost/configuration/SecurityConfig.java`

**Interfaces:**
- Consumes: Gradle 8 from Task 15
- Produces: a Boot 3 application on `jakarta.*` — every later task assumes this

This is the largest task in the plan. Work through the steps in order; do not skip ahead to running the app.

- [ ] **Step 1: Inventory what must change**

```bash
grep -rln "javax.persistence\|javax.validation\|javax.servlet\|javax.annotation" src/main/java src/test/java
```

Record this list. Every file on it changes in Step 3.

- [ ] **Step 2: Bump the plugin and dependency versions**

In `build.gradle`:

```gradle
    id 'org.springframework.boot' version '3.0.13'
    id 'io.spring.dependency-management' version '1.1.4'
```

Change the Thymeleaf security extra:

```gradle
    implementation 'org.thymeleaf.extras:thymeleaf-extras-springsecurity6'
```

Remove the java8time extra entirely — it is folded into Thymeleaf 3.1 and no template uses `temporals.`:

```gradle
    implementation 'org.thymeleaf.extras:thymeleaf-extras-java8time:3.0.4.RELEASE'
```

Also delete the corresponding bean from `ThymeleafConfig.java`:

```java
    @Bean
    public Java8TimeDialect timeDialect() {
        return new Java8TimeDialect();
    }
```

and its import `org.thymeleaf.extras.java8time.dialect.Java8TimeDialect`.

- [ ] **Step 3: Rewrite `javax` imports to `jakarta`**

For every file from Step 1, replace the import prefixes:

- `javax.persistence.` → `jakarta.persistence.`
- `javax.validation.` → `jakarta.validation.`
- `javax.servlet.` → `jakarta.servlet.`
- `javax.annotation.` → `jakarta.annotation.`

A scripted pass, followed by reading the diff:

```bash
grep -rl "javax\.\(persistence\|validation\|servlet\|annotation\)" src/main/java src/test/java \
  | xargs sed -i 's/javax\.persistence\./jakarta.persistence./g; s/javax\.validation\./jakarta.validation./g; s/javax\.servlet\./jakarta.servlet./g; s/javax\.annotation\./jakarta.annotation./g'
git diff --stat
```

Do **not** rewrite `javax.mail` — Task 6 already deleted every file that used it. Verify: `grep -rn "javax.mail" src/ || echo CLEAN`.

- [ ] **Step 4: Rewrite both security filter chains for Spring Security 6**

`antMatchers` is removed; `requestMatchers` replaces it. Replace the whole of `SecurityConfig.java` with:

```java
package de.unibamberg.dsam.group6.prost.configuration;

import de.unibamberg.dsam.group6.prost.service.UserDetailSecurityService;
import de.unibamberg.dsam.group6.prost.service.UserErrorManager;
import de.unibamberg.dsam.group6.prost.util.Toast;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final UserDetailSecurityService detailsService;
    private final UserErrorManager errors;

    @Bean
    @Profile("dev")
    public SecurityFilterChain securityFilterChainDev(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(req -> {
            req.requestMatchers("/cart/**", "/orders/**", "/user/**").authenticated();
            req.requestMatchers("/admin/**").hasRole("ADMIN");
            req.anyRequest().permitAll();
        });
        http.formLogin(form -> form.loginPage("/login").failureHandler((req, res, e) -> {
            this.errors.addToast(Toast.error(e.getMessage()));
            res.sendRedirect("/login");
        }));
        http.headers(h -> {
            h.httpStrictTransportSecurity(hsts -> hsts.disable());
            h.frameOptions(fo -> fo.disable());
        });
        http.logout(l -> l.logoutUrl("/logout"));
        http.csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"));

        return http.build();
    }

    @Bean
    @Profile("prod")
    public SecurityFilterChain securityFilterChainProd(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(req -> {
            req.requestMatchers("/cart/**", "/orders/**", "/user/**").authenticated();
            req.requestMatchers("/admin/**").hasRole("ADMIN");
            req.anyRequest().permitAll();
        });
        http.formLogin(form -> {
            form.loginPage("/login").permitAll();
            form.failureHandler((req, res, e) -> {
                this.errors.addToast(Toast.error(e.getMessage()));
                res.sendRedirect("/login");
            });
        });
        http.logout(l -> l.logoutUrl("/logout").permitAll());
        http.csrf(csrf -> {});
        http.headers(h -> {
            h.httpStrictTransportSecurity(hsts -> {});
            h.frameOptions(fo -> fo.sameOrigin());
        });
        http.requiresChannel(c -> c.anyRequest().requiresSecure());
        return http.build();
    }

    @Bean
    public AuthenticationProvider daoAuthenticationProvider() {
        var provider = new DaoAuthenticationProvider();
        provider.setPasswordEncoder(this.passwordEncoder());
        provider.setUserDetailsService(this.detailsService);
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

- [ ] **Step 5: Compile and fix what breaks**

```bash
./gradlew compileJava
```

Expected: failures. Common ones and their fixes:

- `Hibernate5Module` / dialect not found → remove any explicit `hibernate.dialect` property; Hibernate 6 auto-detects.
- `@GeneratedValue` with `AUTO` behaves differently in Hibernate 6 — do **not** change generation strategy here. Task 17 handles schema via Flyway.
- `spring.jpa.properties.hibernate.jdbc.lob.non_contextual_creation` in `application-prod.yml` is obsolete in Hibernate 6; remove it.
- `SessionLocaleResolver` in `ThymeleafConfig` may need `setDefaultLocale` unchanged — no action expected.

Iterate until `compileJava` succeeds.

- [ ] **Step 6: Run the suite**

```bash
./gradlew test
```

Expected: same test count as Task 14 Step 3, all green.

If `AuthorizationTest.adminIsForbiddenForNonAdminUsers` now returns 302 instead of 403, that is a Spring Security 6 behavior difference. **Investigate before changing the test** — the suite exists to catch exactly this. If the change is intended by Security 6, update the assertion and note it in the commit message.

- [ ] **Step 7: Run the app and walk the baseline by hand**

```bash
./gradlew bootRun
```

Walk every row of the baseline table. Templates are not compiled, so Thymeleaf breakage — especially the `sec:` namespace from the springsecurity6 swap — only surfaces at runtime. Check `/`, `/login`, `/admin`, and `/cart` specifically, since those use `sec:` or `nl2br`.

Stop the app afterward.

- [ ] **Step 8: Handle nl2br if it broke**

`thymeleaf-extras-nl2br:1.0.2` was last released for Thymeleaf 3.0 and may not resolve or may fail at runtime under Thymeleaf 3.1.

If it works, leave it. If it does not:

1. Remove `implementation 'com.github.bufferings:thymeleaf-extras-nl2br:1.0.2'` from `build.gradle`.
2. Remove the `Nl2brDialect` bean and its import from `ThymeleafConfig.java`.
3. Replace the three usages with `th:text` plus a CSS class:
   - `src/main/resources/templates/include/toasts.html:14`
   - `src/main/resources/templates/pages/admin.html:62`
   - `src/main/resources/templates/pages/cart.html:27`

   Change `nl2br:text="${x}"` to `th:text="${x}" class="whitespace-pre-line"`. Tailwind's `whitespace-pre-line` renders newlines without HTML.

   Also remove the `xmlns:nl2br` declaration from `cart.html:3`.

**Do not** use `th:utext` as the replacement — `cart.html:27` renders a user-supplied delivery address, and `th:utext` would make that an HTML injection point.

- [ ] **Step 9: Full verification**

```bash
./gradlew clean build
```

Expected: `BUILD SUCCESSFUL`, all tests green.

- [ ] **Step 10: Commit**

```bash
./gradlew spotlessApply
git add -A
git commit -m "build: upgrade Spring Boot 2.7.5 to 3.0.13

Migrates javax.* to jakarta.*, rewrites both security filter chains for
Spring Security 6 (antMatchers -> requestMatchers, lambda-only DSL),
swaps thymeleaf-extras-springsecurity5 for 6, and drops the unused
thymeleaf-extras-java8time dialect."
```

---

### Task 17: Introduce Flyway

**Files:**
- Modify: `build.gradle`
- Create: `src/main/resources/db/migration/V1__initial_schema.sql`
- Modify: `src/main/resources/application-prod.yml`, `src/main/resources/application-dev.yml`

**Interfaces:**
- Consumes: the Boot 3 app from Task 16
- Produces: recorded schema migrations replacing `ddl-auto: update` in prod

This must land before any Boot 3 startup touches a persistent database. Hibernate 6 generates schema differently from Hibernate 5, and `ddl-auto: update` would apply that difference silently and irreversibly.

The `dev` profile keeps `ddl-auto: create` on in-memory H2 — it is recreated every run and is not at risk. Flyway is disabled there so tests keep their fast fresh-schema path.

- [ ] **Step 1: Add the Flyway dependencies**

In `build.gradle`:

```gradle
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-postgresql'
```

- [ ] **Step 2: Generate the baseline DDL for Postgres**

Temporarily add to `application-prod.yml` under `spring.jpa.properties`:

```yaml
                javax:
                    persistence:
                        schema-generation:
                            scripts:
                                action: create
                                create-target: build/schema.sql
```

Start a local Postgres and run the app once on the prod profile:

```bash
docker compose up -d prost_postgres
APP_ENV=prod ./gradlew bootRun
```

If you would rather not run Docker, generate against H2 instead by applying the same properties to `application-dev.yml` and running `./gradlew bootRun` — then hand-correct H2 types to Postgres equivalents in Step 3 (`double` → `double precision`, `timestamp` → `timestamp without time zone`).

Stop the app once started. `build/schema.sql` now contains the DDL.

- [ ] **Step 3: Create the migration**

Copy `build/schema.sql` to `src/main/resources/db/migration/V1__initial_schema.sql`. Then edit it:

- Remove every `drop table` / `drop sequence` statement. Migrations must be additive.
- Confirm it creates all nine tables: `addresses`, `beverage`, `order_items`, `orders`, `privileges`, `roles`, `roles_privileges`, `users`, `users_roles`, plus the `hibernate_sequence`.
- Confirm the foreign keys are present.

Then remove the temporary `schema-generation` properties added in Step 2.

- [ ] **Step 4: Switch prod to validate-only**

In `application-prod.yml`, change:

```yaml
        hibernate:
            ddl-auto: update
```

to:

```yaml
        hibernate:
            ddl-auto: validate
```

and add at the `spring` level:

```yaml
    flyway:
        enabled: true
        baseline-on-migrate: true
```

- [ ] **Step 5: Disable Flyway on dev**

In `application-dev.yml`, add at the `spring` level:

```yaml
    flyway:
        enabled: false
```

`dev` keeps `ddl-auto: create` on in-memory H2.

- [ ] **Step 6: Verify prod applies the migration cleanly**

Against an **empty** database:

```bash
docker compose down -v
docker compose up -d prost_postgres
APP_ENV=prod ./gradlew bootRun
```

Expected: Flyway logs `Migrating schema "public" to version 1`, then the app starts with no `SchemaManagementException`. `ddl-auto: validate` failing here means `V1__initial_schema.sql` does not match the entities — fix the SQL, not the entities.

Stop the app and tear down:

```bash
docker compose down -v
```

- [ ] **Step 7: Verify dev is unaffected**

```bash
./gradlew test
```

Expected: same test count, all green — Flyway is disabled on dev.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "build: replace prod ddl-auto with Flyway migrations

V1__initial_schema.sql is the baseline generated from the current
entities. Prod moves to ddl-auto: validate; dev keeps ddl-auto: create
on in-memory H2 with Flyway disabled.

Lands before the Hibernate 6 schema differences can be silently applied
to a persistent database by ddl-auto: update."
```

---

### Task 18: Spring Boot 3.0.x → 3.5.x

**Files:**
- Modify: `build.gradle`

**Interfaces:**
- Consumes: the Boot 3.0 app from Task 16, Flyway from Task 17
- Produces: the target framework version

- [ ] **Step 1: Bump the version**

In `build.gradle`:

```gradle
    id 'org.springframework.boot' version '3.5.0'
```

- [ ] **Step 2: Compile**

```bash
./gradlew compileJava
```

Fix whatever breaks. This hop is far smaller than 2.7 → 3.0; expect few or no changes.

- [ ] **Step 3: Run the suite**

```bash
./gradlew clean build
```

Expected: same test count, all green.

- [ ] **Step 4: Run the app and walk the baseline**

```bash
./gradlew bootRun
```

Walk the baseline table. Stop the app afterward.

- [ ] **Step 5: Commit**

```bash
git add build.gradle
git commit -m "build: upgrade Spring Boot 3.0.13 to 3.5.0"
```

---

### Task 19: Java 17 → 21 — NOT TAKEN

> **Superseded. This task was deliberately abandoned; see commit `ce94f27` and
> `docs/reference/upgrading-spring-boot.md`.** The machine had only JDK 17, and
> Gradle 8.5 cannot provision a toolchain without a resolver plugin. Rather than
> add one or install a JDK, the upgrade stops at Java 17 — an LTS supported into
> 2029, and Boot 3.5's own floor. The goal was reaching a supported framework,
> and that is met. The steps below are retained for the record only; do not
> execute them without first revisiting that decision.

**Files:**
- Modify: `build.gradle`

**Interfaces:**
- Consumes: Boot 3.5 from Task 18
- Produces: the target runtime

The Dockerfile and CI were deleted in Task 4, so `build.gradle` is the only place pinning a JDK version.

- [ ] **Step 1: Confirm a JDK 21 is available**

```bash
java -version
```

If this reports 17, either install JDK 21 or rely on the Gradle toolchain to provision it. The toolchain block from Task 15 Step 4 makes Gradle resolve a matching JDK automatically.

- [ ] **Step 2: Bump the toolchain**

In `build.gradle`, change the toolchain to 21:

```gradle
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

If Task 15 Step 4 left `sourceCompatibility = '17'` in place instead, replace that line with the toolchain block above.

- [ ] **Step 3: Build**

```bash
./gradlew clean build
```

Expected: `BUILD SUCCESSFUL`, same test count, all green.

- [ ] **Step 4: Confirm the app runs on 21**

```bash
./gradlew bootRun
```

The startup log line reads `Starting ApplicationMain using Java 21...`. Walk the baseline table. Stop the app.

- [ ] **Step 5: Commit**

```bash
git add build.gradle
git commit -m "build: upgrade Java 17 to 21 via Gradle toolchain"
```

---

### Task 20: Refresh the frontend browser data

**Files:**
- Modify: `pnpm-lock.yaml` (regenerated)

**Interfaces:**
- Consumes: nothing
- Produces: nothing

Webpack 5 builds clean on Node 26 — verified. The only frontend issue is a stale `caniuse-lite`, which prints four warnings per build.

- [ ] **Step 1: See the warning**

```bash
npx pnpm build 2>&1 | grep -c "caniuse-lite is outdated"
```

Expected: a non-zero count.

- [ ] **Step 2: Update the browser database**

```bash
npx update-browserslist-db@latest
```

- [ ] **Step 3: Verify the build is clean**

```bash
npx pnpm build 2>&1 | grep -c "caniuse-lite is outdated"
```

Expected: `0`. The build must still end with `webpack ... compiled successfully`.

- [ ] **Step 4: Commit**

```bash
git add pnpm-lock.yaml package.json
git commit -m "build: refresh caniuse-lite browser data"
```

---

# PHASE 5 — Cleanup

Only after Task 19's suite is green. Phase 5 is the only phase permitted to change an existing assertion, and only in Task 22.

The spec lists a fourth cleanup item — removing the unused `Java8TimeDialect` bean and its dependency. That is done earlier, in Task 16 Step 2, because `thymeleaf-extras-java8time` has to be resolved one way or the other during the Boot 3 upgrade and deleting it there avoids upgrading a dependency only to remove it two tasks later.

---

### Task 21: Extract `OrderService` from `CartController`

**Files:**
- Create: `src/main/java/de/unibamberg/dsam/group6/prost/service/OrderService.java`
- Modify: `src/main/java/de/unibamberg/dsam/group6/prost/controller/CartController.java`

**Interfaces:**
- Consumes: the green suite from Task 19
- Produces: `OrderService.placeOrder(User user, CartDTO cart)` → `Order` — Task 22 modifies this method

This is a **pure refactor**. Behavior must not change, and every existing test — including `overselling_isCurrentlyPossible` — must stay green.

- [ ] **Step 1: Confirm the suite is green before touching anything**

```bash
./gradlew test
```

Expected: all green. If not, stop — do not refactor on a red suite.

- [ ] **Step 2: Create the service, preserving current behavior exactly**

Create `src/main/java/de/unibamberg/dsam/group6/prost/service/OrderService.java`:

```java
package de.unibamberg.dsam.group6.prost.service;

import de.unibamberg.dsam.group6.prost.entity.Order;
import de.unibamberg.dsam.group6.prost.entity.User;
import de.unibamberg.dsam.group6.prost.repository.BeveragesRepository;
import de.unibamberg.dsam.group6.prost.repository.OrderItemsRepository;
import de.unibamberg.dsam.group6.prost.repository.OrdersRepository;
import de.unibamberg.dsam.group6.prost.util.CartDTO;
import jakarta.validation.Validator;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrdersRepository ordersRepo;
    private final OrderItemsRepository orderItemsRepo;
    private final BeveragesRepository beveragesRepo;
    private final Validator validator;

    /** Thrown when an order fails bean validation. Carries the messages for display. */
    public static class OrderRejectedException extends RuntimeException {
        private final Set<String> messages;

        public OrderRejectedException(Set<String> messages) {
            super(String.join("; ", messages));
            this.messages = messages;
        }

        public Set<String> getMessages() {
            return this.messages;
        }
    }

    /**
     * Persists an order for the given cart contents and decrements stock.
     *
     * @throws OrderRejectedException if the order fails validation
     */
    @Transactional
    public Order placeOrder(User user, CartDTO cart) {
        var order = new Order();
        order.setUser(user);
        order.setPrice(cart.getTotalPrice());

        var violations = this.validator.validate(order);
        if (!violations.isEmpty()) {
            throw new OrderRejectedException(violations.stream()
                    .map(v -> v.getMessage())
                    .collect(java.util.stream.Collectors.toSet()));
        }

        order = this.ordersRepo.save(order);

        var orderItems = cart.getOrderItems();
        for (var oi : orderItems) {
            oi.setOrder(order);
        }
        this.orderItemsRepo.saveAll(orderItems);

        var reduced = cart.beverages.entrySet().stream()
                .map(entry -> {
                    var bev = entry.getKey();
                    bev.setInStock(Math.max(bev.getInStock() - entry.getValue(), 0));
                    return bev;
                })
                .toList();
        this.beveragesRepo.saveAll(reduced);

        return order;
    }
}
```

The `Math.max(..., 0)` clamp is carried over deliberately — this task changes structure only. Task 22 fixes it.

- [ ] **Step 3: Rewrite `submitCart` to delegate**

In `CartController.java`, replace the `submitCart` method with:

```java
    @PostMapping("/cart/submit")
    public String submitCart(Principal principal) {
        var user = this.userRepo.findUserByUsername(principal.getName()).orElseThrow();
        var cartState = this.cart.getCartState();

        if (cartState.beverages.isEmpty()) {
            this.errors.addToast(Toast.notice("No items in cart."));
            return "redirect:/cart";
        }

        try {
            this.orderService.placeOrder(user, cartState);
        } catch (OrderService.OrderRejectedException e) {
            e.getMessages().forEach(m -> this.errors.addToast(Toast.error(m)));
            return "redirect:/cart";
        }

        this.cart.clear();
        return "redirect:/order_success";
    }
```

Then update the controller's fields — remove `ordersRepo`, `orderItemsRepository`, `validator`, and the now-unused `beveragesRepo` if `addToCart` no longer needs it (it does need it, so keep that one), and add:

```java
    private final OrderService orderService;
```

Remove the `@Transactional` annotation from the controller method — the service owns the transaction now. Remove any imports that are no longer used.

- [ ] **Step 4: Run the suite**

```bash
./gradlew test
```

Expected: same test count as Task 19, all green — including `overselling_isCurrentlyPossible`. A behavior change here means the refactor was not faithful.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApply
git add -A
git commit -m "refactor: extract OrderService from CartController

Moves order creation, item persistence, and stock decrement out of the
controller. Pure refactor — the overselling clamp is carried over
unchanged and fixed separately."
```

---

### Task 22: Fix the overselling bug

**Files:**
- Modify: `src/main/java/de/unibamberg/dsam/group6/prost/service/OrderService.java`
- Modify: `src/main/java/de/unibamberg/dsam/group6/prost/repository/BeveragesRepository.java`
- Modify: `src/test/java/de/unibamberg/dsam/group6/prost/CheckoutTest.java`

**Interfaces:**
- Consumes: `OrderService.placeOrder` from Task 21
- Produces: `OrderService.InsufficientStockException`

**This is the one permitted behavior change.** A bare re-check inside `@Transactional` does not close the race at default isolation — two transactions can both read sufficient stock before either writes. A pessimistic row lock does.

- [ ] **Step 1: Add a locking finder to the repository**

Replace `src/main/java/de/unibamberg/dsam/group6/prost/repository/BeveragesRepository.java` with:

```java
package de.unibamberg.dsam.group6.prost.repository;

import de.unibamberg.dsam.group6.prost.entity.Beverage;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BeveragesRepository extends JpaRepository<Beverage, Long> {

    /** Loads a beverage with a write lock so concurrent checkouts serialize on it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Beverage b where b.id = :id")
    Optional<Beverage> findByIdForUpdate(@Param("id") Long id);
}
```

- [ ] **Step 2: Write the failing test**

In `CheckoutTest.java`, **delete** `overselling_isCurrentlyPossible` entirely — including its `CHARACTERIZATION` javadoc — and add:

```java
    @Test
    void checkoutIsRejectedWhenStockIsInsufficient() throws Exception {
        var stock = this.beverages.findById(this.bottleId).orElseThrow().getInStock();
        this.cart.addToCart(this.bottleId, stock + 5);

        this.mvc.perform(post("/cart/submit").session(this.session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        assertThat(this.orders.findAllByUser_username("admin")).isEmpty();
    }

    @Test
    void rejectedCheckoutLeavesStockUntouched() throws Exception {
        var before = this.beverages.findById(this.bottleId).orElseThrow().getInStock();
        this.cart.addToCart(this.bottleId, before + 5);

        this.mvc.perform(post("/cart/submit").session(this.session).with(csrf()));

        assertThat(this.beverages.findById(this.bottleId).orElseThrow().getInStock())
                .isEqualTo(before);
    }

    @Test
    void checkoutSucceedsWhenStockIsExactlySufficient() throws Exception {
        var stock = this.beverages.findById(this.bottleId).orElseThrow().getInStock();
        this.cart.addToCart(this.bottleId, stock);

        this.mvc.perform(post("/cart/submit").session(this.session).with(csrf()))
                .andExpect(redirectedUrl("/order_success"));

        assertThat(this.beverages.findById(this.bottleId).orElseThrow().getInStock())
                .isZero();
    }
```

- [ ] **Step 3: Run and watch them fail**

```bash
./gradlew test --tests "*CheckoutTest*"
```

Expected: `checkoutIsRejectedWhenStockIsInsufficient` and `rejectedCheckoutLeavesStockUntouched` FAIL. `checkoutSucceedsWhenStockIsExactlySufficient` should already pass.

- [ ] **Step 4: Implement the fix**

In `OrderService.java`, add the exception type:

```java
    /** Thrown when a cart asks for more of a beverage than remains in stock. */
    public static class InsufficientStockException extends RuntimeException {
        public InsufficientStockException(String beverageName, int requested, int available) {
            super(String.format("Only %d of %s left — you asked for %d.", available, beverageName, requested));
        }
    }
```

Then replace the stock-decrement block in `placeOrder` with a locked read, a check, and an exact decrement. The full method becomes:

```java
    @Transactional
    public Order placeOrder(User user, CartDTO cart) {
        // Lock and validate every line before persisting anything, so a rejected
        // order rolls back cleanly and concurrent checkouts serialize per beverage.
        for (var entry : cart.beverages.entrySet()) {
            var requested = entry.getValue();
            var locked = this.beveragesRepo
                    .findByIdForUpdate(entry.getKey().getId())
                    .orElseThrow(() -> new InsufficientStockException(
                            entry.getKey().getName(), requested, 0));

            if (locked.getInStock() < requested) {
                throw new InsufficientStockException(locked.getName(), requested, locked.getInStock());
            }
        }

        var order = new Order();
        order.setUser(user);
        order.setPrice(cart.getTotalPrice());

        var violations = this.validator.validate(order);
        if (!violations.isEmpty()) {
            throw new OrderRejectedException(violations.stream()
                    .map(v -> v.getMessage())
                    .collect(java.util.stream.Collectors.toSet()));
        }

        order = this.ordersRepo.save(order);

        var orderItems = cart.getOrderItems();
        for (var oi : orderItems) {
            oi.setOrder(order);
        }
        this.orderItemsRepo.saveAll(orderItems);

        var reduced = cart.beverages.entrySet().stream()
                .map(entry -> {
                    var bev = entry.getKey();
                    bev.setInStock(bev.getInStock() - entry.getValue());
                    return bev;
                })
                .toList();
        this.beveragesRepo.saveAll(reduced);

        return order;
    }
```

The `Math.max(..., 0)` clamp is gone — stock is now guaranteed sufficient by the check above, so clamping would only hide a bug.

`Beverage` declares `public abstract String getName()` (`Beverage.java:30`) along with `getPrice()`, `getInStock()`, and `setInStock(int)`, so `locked.getName()` and `locked.getInStock()` both compile against the base type. No cast to `Bottle` or `Crate` is needed.

- [ ] **Step 5: Handle the exception in the controller**

In `CartController.submitCart`, extend the catch:

```java
        try {
            this.orderService.placeOrder(user, cartState);
        } catch (OrderService.OrderRejectedException e) {
            e.getMessages().forEach(m -> this.errors.addToast(Toast.error(m)));
            return "redirect:/cart";
        } catch (OrderService.InsufficientStockException e) {
            this.errors.addToast(Toast.error(e.getMessage()));
            return "redirect:/cart";
        }
```

- [ ] **Step 6: Run the tests**

```bash
./gradlew test --tests "*CheckoutTest*"
```

Expected: all PASS.

- [ ] **Step 7: Run the whole suite**

```bash
./gradlew test
```

Expected: all green. The count is now Task 19's count **+2** (three tests replaced one).

- [ ] **Step 8: Commit**

```bash
./gradlew spotlessApply
git add -A
git commit -m "fix: reject checkout when stock is insufficient

Checkout validated stock only on add-to-cart, so concurrent buyers could
oversell and Math.max(stock, 0) silently clamped the result to zero.

Beverages are now loaded with PESSIMISTIC_WRITE before the check, so
concurrent checkouts serialize per beverage — a bare re-check inside the
transaction would not close the race at default isolation.

Replaces the CheckoutTest characterization test that recorded the bug."
```

---

### Task 23: Make admin action failures visible

**Files:**
- Modify: `src/main/java/de/unibamberg/dsam/group6/prost/controller/AdminController.java:54-87`
- Create: `src/test/java/de/unibamberg/dsam/group6/prost/AdminActionTest.java`

**Interfaces:**
- Consumes: `@IntegrationTest` from Task 8
- Produces: nothing

`runAction` has four silent early-exits. Observed during baseline verification: an unrecognized action name returned 302 and did nothing, looking exactly like success.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/de/unibamberg/dsam/group6/prost/AdminActionTest.java`:

```java
package de.unibamberg.dsam.group6.prost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.unibamberg.dsam.group6.prost.repository.BottlesRepository;
import de.unibamberg.dsam.group6.prost.service.UserErrorManager;
import de.unibamberg.dsam.group6.prost.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@WithMockUser(username = "admin", roles = "ADMIN")
class AdminActionTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    BottlesRepository bottles;

    @Autowired
    UserErrorManager errors;

    @Test
    void validActionSeedsTheCatalogue() throws Exception {
        this.mvc.perform(get("/admin/action")
                        .param("a", "databaseLoader::importBottles")
                        .param("await", "true"))
                .andExpect(status().is3xxRedirection());

        assertThat(this.bottles.findAll()).isNotEmpty();
    }

    @Test
    void unknownInstanceReportsAnError() throws Exception {
        this.mvc.perform(get("/admin/action").param("a", "noSuchBean::importBottles"))
                .andExpect(status().is3xxRedirection());

        assertThat(this.errors.getToasts()).isNotEmpty();
    }

    @Test
    void malformedActionReportsAnError() throws Exception {
        this.mvc.perform(get("/admin/action").param("a", "missingSeparator"))
                .andExpect(status().is3xxRedirection());

        assertThat(this.errors.getToasts()).isNotEmpty();
    }

    @Test
    void missingActionReportsAnError() throws Exception {
        this.mvc.perform(get("/admin/action")).andExpect(status().is3xxRedirection());

        assertThat(this.errors.getToasts()).isNotEmpty();
    }
}
```

`UserErrorManager.getToasts()` returns `List<Toast>` and is session-backed, so it reflects toasts added during the request. Note the sibling `getToastsAndRemove()` — do not use it in assertions, since it clears the list as a side effect.

- [ ] **Step 2: Run and watch it fail**

```bash
./gradlew test --tests "*AdminActionTest*"
```

Expected: the three error tests FAIL — no toast is produced today.

- [ ] **Step 3: Add feedback to each early-exit**

In `AdminController.runAction`, replace each bare early-exit with one that reports. The method becomes:

```java
    @GetMapping("/action")
    public String runAction(
            @RequestParam(name = "a") Optional<String> action,
            @RequestParam Optional<Boolean> await,
            @RequestParam Optional<String> next) {
        if (action.isEmpty()) {
            this.errors.addToast(Toast.error("No action specified."));
            return "redirect:" + next.orElse("/admin");
        }

        var a = action.get().split("::");
        if (a.length != 2) {
            this.errors.addToast(Toast.error("Malformed action '%s'. Expected 'instance::method'.", action.get()));
            return "redirect:" + next.orElse("/admin");
        }

        var instance = this.actions.getAnnotatedInstances().stream()
                .filter(i -> i.getInstanceName().equals(a[0]))
                .toList();
        if (instance.size() != 1) {
            this.errors.addToast(Toast.error("Unknown action instance '%s'.", a[0]));
            return "redirect:" + next.orElse("/admin");
        }

        try {
            if (await.orElse(false)) {
                this.errors.addToast(
                        Toast.success(instance.get(0).callAndReturn(a[1]).get().toString()));
            } else {
                instance.get(0).call(a[1]);
            }
        } catch (CallFailedException | InterruptedException | ExecutionException e) {
            this.errors.addToast(Toast.error("Action failed: %s", e));
        }
        return "redirect:" + next.orElse("/admin");
    }
```

`Toast.error` already takes a format string and varargs — the existing `Toast.error("Action failed: %s", e)` call confirms the signature.

- [ ] **Step 4: Run the tests**

```bash
./gradlew test --tests "*AdminActionTest*"
```

Expected: all PASS.

- [ ] **Step 5: Run the whole suite**

```bash
./gradlew clean build
```

Expected: `BUILD SUCCESSFUL`, all green.

- [ ] **Step 6: Final manual verification**

```bash
./gradlew bootRun
```

Walk every row of the baseline table one final time. Confirm the app reports `Java 21` and Spring Boot 3.5 in the startup banner. Stop the app.

- [ ] **Step 7: Commit**

```bash
./gradlew spotlessApply
git add -A
git commit -m "fix: report admin action dispatch failures

All four early-exits in runAction returned a redirect with no toast and
no log, so a mistyped action name looked identical to success."
```

---

## Completion checklist

- [ ] No credential appears in `git log --all -p` or in any tree
- [ ] `grep -riE "gcloud|google\.cloud|sendgrid|javax\.mail" --include="*.java" --include="*.gradle" --include="*.yml" .` returns nothing outside `docs/`
- [ ] `./gradlew --version` reports Gradle 8.5
- [ ] Startup banner reports Spring Boot 3.5.x on Java 17 (Java 21 not taken — see Task 19)
- [ ] `./gradlew clean build` is green
- [ ] `GET /admin` anonymous returns a redirect, not 200
- [ ] Checkout with a cart exceeding stock is rejected and leaves stock untouched
- [ ] The app starts and completes the full baseline flow with no GCP, no Docker, no Postgres
