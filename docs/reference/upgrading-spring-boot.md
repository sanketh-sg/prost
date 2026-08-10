# Upgrading Prost: Spring Boot 2.7 → 3.5 (staying on Java 17)

A working reference for this repository, written to be read start to finish once and
then dipped into later. It covers what has to be true before an upgrade begins, how
the upgrade itself is sequenced, and how each step is proven.

Written 2026-08-09 and updated as each hop landed. The Boot upgrade is **complete**:
Gradle 8.5, Spring Boot 3.5.0, Hibernate 6.6, Spring Security 6.5, Tomcat 10.1,
Thymeleaf 3.1, with Flyway owning the prod schema.

**Java 21 was deliberately not taken** — see §3.5.

---

## The one idea everything else follows from

An upgrade is not risky because the code changes are hard. `javax` → `jakarta` is
mechanical, and the compiler finds every occurrence.

An upgrade is risky because **when something breaks afterwards, you cannot tell why.**
Did the upgrade break it? Was it already broken? Did the refactor you bundled in break
it? Every hour after that point is spent untangling causes instead of fixing code.

So the organising principle for all of it is:

> **Arrange the work so that every failure has exactly one candidate cause.**

Every rule below — establish a baseline first, delete before migrating, one commit per
change, never refactor during a migration — is that single idea applied to a different
moment. If you remember nothing else, remember that, and most of the specifics can be
re-derived.

---

## Part 1 — Before the upgrade

### 1.1 Get it running, unmodified

The first thing to do with inherited code is watch it work. Not read it — *run* it.

This sounds like a formality and is the step most often skipped. Its value is entirely
diagnostic: once you have seen login, checkout, and the admin panel behave, every later
failure is a deviation from something you observed rather than a mystery about code you
never saw functioning.

For Prost this is cheap. The `dev` profile uses H2 in memory, so there is no database to
provision, no container, and no cloud account:

```bash
npx pnpm install     # once
npx pnpm build       # frontend bundle → src/main/resources/static/build
./gradlew bootRun
```

Then `admin` / `admin` at <http://localhost:8080>, seed the catalogue from `/admin` →
**importAll**, and walk the flow.

**Write down what you saw.** That written list becomes the acceptance criteria for
everything afterwards:

| Behaviour | Expected |
| --- | --- |
| `/`, `/bottles`, `/crates`, `/login`, `/register` anonymous | 200 |
| `/cart`, `/orders`, `/user` anonymous | 302 → `/login` |
| `/admin` anonymous | 302 → `/login` |
| `/admin` as authenticated non-admin | 403 |
| Login `admin` / `admin` | 302 → `/` |
| `databaseLoader::importAll` | catalogue populated |
| Add to cart → `/cart` | items present |
| `POST /cart/submit` | 302 → `/order_success`, order persisted, stock decremented |
| `/orders/{id}` own order | 200 |
| `/orders/{id}` another user's order | 302 → `/orders` |

### 1.2 Clear the ground that has nothing to do with the upgrade

Some work is independent of the framework version and should be finished first, purely
so it never gets entangled in the migration diff.

For this repository that meant three things:

**Secrets.** The repository was public and contained a GCP service-account key, a
SendGrid API key, and a Gmail app password hardcoded in a Java file. Three lessons,
recorded here because they generalise:

- *Revocation is the fix; deletion is cleanup.* A key removed from a repository but
  still valid is still compromised. Rotate first.
- *`git rm` does not touch history.* The blob remains in every earlier commit. Removing
  it requires rewriting history and force-pushing.
- *Search by content, not by filename.* The first pass targeted credential *files* and
  missed a password embedded in `SendEmail.java`. A path-based check reported "clean"
  while the secret was still present. `git log --all -S "<literal>"` is what actually
  finds it — and note that `-S` flags any change in occurrence count, so the commit that
  *deletes* a secret matches exactly like the one that added it. Check the direction
  before drawing conclusions.

**Dead code.** Anything you are going to delete should be deleted *before* the upgrade,
never migrated first. Prost carried two Google Cloud Functions, a duplicate copy of the
entire application in `Send/`, and a `StatisticsService` with no caller. Migrating
`javax.mail` and the Cloud Functions SDK to `jakarta` — for code that had never run —
would have been pure waste.

Their design intent was captured in `docs/reference/removed-gcp-and-email-architecture.md`
before deletion, so nothing was lost that mattered.

**Known-broken configuration.** Two defects predated the work and would have masqueraded
as upgrade damage:

- The test suite had **never run.** H2 was declared `developmentOnly`, a configuration
  that feeds `runtimeClasspath` but not `testRuntimeClasspath`, so every
  `@SpringBootTest` failed on `Cannot load driver class: org.h2.Driver`. Nobody had run
  `./gradlew build` — only `bootRun`.
- The `dev` filter chain had no `/admin/**` rule, and `dev` is the default active
  profile, so `GET /admin` returned 200 anonymously. A contributing factor: the two
  filter-chain beans had *swapped names* — the `@Profile("dev")` bean was called
  `securityFilterChainProd`.

### 1.3 Build the safety net

This is the step that converts an upgrade from hopeful to auditable.

**Characterization tests** capture what the code *currently does*, not what it should do.
That distinction matters. Where behaviour is wrong, the test records the wrong behaviour
and marks it:

```java
/**
 * CHARACTERIZATION — records a known bug, not desired behavior.
 * Task 22 fixes this and replaces this test.
 */
@Test
void overselling_isCurrentlyPossible() throws Exception { ... }
```

That looks perverse until you consider the alternative. During a migration touching every
file, a silent behaviour change is the thing you most need to catch. Pinning current
behaviour turns it into a failing test. The bug is then fixed in a later, separate commit
that deletes this test and replaces it — so the behaviour change appears in a small,
readable diff instead of buried inside a 500-file upgrade.

Prost's suite covers the baseline table above across seven classes and 51 tests. Two are
worth calling out as *diagnostic instruments* rather than ordinary coverage:

- **`SessionPersistenceTest`** — the only test that runs with `spring-session-jdbc`
  *enabled*, driving requests through the real `SESSION` cookie. If this alone fails
  after the upgrade, the cause is the session store, not the controllers.
- **`overselling_isCurrentlyPossible`** — if this *stops* failing, Hibernate 6 changed
  transaction or flush semantics, and that is a finding worth chasing.

> **A trap worth knowing.** The first attempt to configure these tests used a
> `src/test/resources/application-dev.yml`. That file **shadows** the real dev profile on
> the classpath instead of merging with it. The tests passed only because Spring Boot
> silently auto-configured a default embedded H2 when it found no datasource config — so
> they were validating against settings that do not exist in the application. The fix was
> `@TestPropertySource` on the shared `@IntegrationTest` annotation.

### 1.4 Immediate pre-flight

Short checklist, run right before the first upgrade commit.

```bash
# 1. Back up. Nine commits of purged history living only on a laptop is not a backup.
git push origin main

# 2. Work on a branch.
git checkout -b upgrade/boot-3

# 3. Capture "before" evidence — artifacts you can diff, not impressions.
./gradlew clean build > ../before-tests.txt
./gradlew dependencies > ../before-deps.txt
```

Also save the Hibernate-generated DDL from a `bootRun` log. Hibernate 6 generates schema
differently from Hibernate 5, and having the old version written down is what makes the
Flyway baseline trustworthy later.

### 1.5 The dependency blast radius

Read your own `build.gradle` line by line and classify every entry before starting. For
this repository, as of `aeaebe6`:

| Dependency | Verdict |
| --- | --- |
| `org.springframework.boot` 2.7.5 | → 3.0.x, then 3.5.x. Needs Java 17+ and Gradle 7.5+ |
| `io.spring.dependency-management` 1.0.15 | → 1.1.x |
| `thymeleaf-extras-springsecurity5` | → `-springsecurity6`, straight swap. Used by `sec:` in `footer/navbar/index` |
| `thymeleaf-extras-java8time` | **Delete.** The `Java8TimeDialect` bean is registered but no template uses `temporals.` |
| `thymeleaf-extras-nl2br:1.0.2` | **Highest risk.** Third-party, last released for Thymeleaf 3.0, unlikely to have a Boot 3 build. Genuinely used in `toasts.html:14`, `admin.html:62`, `cart.html:27` |
| `spring-session-jdbc` | → 3.x, **which changes the `SPRING_SESSION` schema**, and the cart lives entirely in session state |
| `spring-security-test` | **Bug:** declared `implementation`, so test-only code ships in the production jar. Should be `testImplementation`. Fix before upgrading |
| `hibernate-validator` (direct) | Prefer `spring-boot-starter-validation` so Boot manages the version |
| `postgresql`, `commons-lang3`, `jackson-core` | Version-managed, no action |
| `lombok` | Boot 3.5 manages a suitable version. 1.18.30+ would be needed only if Java 21 is taken later |
| `spotless` 6.11.0 | Verified fine on Gradle 8.5 and Boot 3.5. Untested on Java 21, which was not taken |
| `bootJar { archiveName }` | Removed in Gradle 8 → `archiveFileName` |
| `sourceCompatibility = '17'` | → Gradle `java { toolchain { ... } }` block |

Read the **Spring Boot 3.0 Migration Guide** and the **3.0 Release Notes** on the Spring
wiki — not blog posts. They enumerate removed properties and relocated classes.

### 1.6 The `nl2br` decision, decided in advance

The one dependency likely to have no Boot 3 release. Decide the fallback now rather than
under pressure: delete the `Nl2brDialect` bean and replace the three usages with
`th:text` plus Tailwind's `whitespace-pre-line`, which renders newlines without a dialect.

**Do not reach for `th:utext`.** `cart.html:27` renders a user-supplied delivery address,
and `th:utext` would turn that into an HTML-injection point. A rendering convenience is
never worth an XSS vector.

---

## Part 2 — What actually changes in Boot 3.0

Ordered by how likely each is to bite *this* codebase, not by how much press it gets.

The through-line: **the loud change is safe, the quiet ones are dangerous.** The package
rename touches hundreds of lines and the compiler catches every one. The changes that
hurt are the ones that compile cleanly and behave differently.

### 2.1 The Jakarta namespace — biggest diff, smallest risk

Every `javax.*` Enterprise Edition package becomes `jakarta.*`. The surface in this
repository, measured:

| Package | Occurrences |
| --- | --- |
| `javax.validation` | 26 |
| `javax.persistence` | 9 |
| `javax.servlet` | 8 |

Across 23 files.

**Why it happened.** Oracle donated Java EE to the Eclipse Foundation but retained the
*"javax"* trademark. Eclipse could keep the code, not the name. Java EE became Jakarta EE
and every package was renamed. A legal event, not a technical one.

**Why it is low risk.** The compiler finds all of it. Nothing compiles-but-misbehaves.

**The one trap.** `javax.sql.DataSource` and `javax.crypto` are *not* renamed — they are
Java SE, not EE. A blind replacement of `javax.` breaks them. Target the specific EE
packages:

```bash
grep -rl "javax\.\(persistence\|validation\|servlet\|annotation\)" src/main/java src/test/java \
  | xargs sed -i 's/javax\.persistence\./jakarta.persistence./g; s/javax\.validation\./jakarta.validation./g; s/javax\.servlet\./jakarta.servlet./g; s/javax\.annotation\./jakarta.annotation./g'
```

### 2.2 Hibernate 6 changes ID generation — the one that can corrupt data

**The most serious item for this project, and it appears in no standard checklist.**

Six entities use `@GeneratedValue(strategy = GenerationType.AUTO)`: `Address`,
`Beverage`, `Order`, `OrderItem`, `Privilege`, `Role`.

Under Hibernate 5, `AUTO` produced **one shared sequence** for the entire schema. The
verified startup log shows exactly that:

```
Hibernate: create sequence hibernate_sequence start with 1 increment by 1
Hibernate: call next value for hibernate_sequence
```

Under **Hibernate 6, `AUTO` maps to a sequence per entity** — `address_seq`,
`beverage_seq`, `orders_seq`, and so on.

Against an existing database holding data, Hibernate then looks for sequences that do not
exist. With `ddl-auto: update` it creates them **starting at 1**, while the tables already
contain rows using those ids. The first insert collides on the primary key.

Two ways out — decide which before Hop 3, because the Flyway baseline depends on it:

```java
// Option A — keep Hibernate 5 behaviour explicitly
@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hibernate_sequence")
@GenericGenerator(name = "hibernate_sequence", strategy = "native")

// Option B — accept per-entity sequences, and create them in Flyway
// initialised to the current max(id), never to 1
```

The `dev` profile hides this completely: H2 is in-memory with `ddl-auto: create`, rebuilt
every run and therefore always self-consistent. It surfaces only against a persistent
database — which is precisely why Flyway lands before Boot 3 ever touches Postgres.

#### Decision: Option B

**Chosen 2026-08-09, before Hop 2.** The entities are left untouched; Hibernate 6's
per-entity sequences are accepted, and `V1__initial_schema.sql` creates them.

The reasoning is worth recording, because it is the reasoning and not the conclusion that
transfers to the next project.

*The entire case for Option A is protecting existing data. Verified: there is no existing
data anywhere.*

- No `db_data/` volume — the `docker-compose` Postgres has never been started
- Docker is not running on this machine
- The GCP project is dead and the application was never deployed with real data

The collision scenario requires a **populated** database. There isn't one. Choosing Option
A would have meant adding `@GenericGenerator` — a Hibernate-specific annotation, deprecated
in Hibernate 6.2+ — to six entity classes permanently, to solve a problem that does not
exist here, and then re-fighting it at Hibernate 7.

Option B also removes a real if minor flaw: a single shared sequence is a contention point
that every insert into every table serialises on.

**The generalisable lesson.** The honest answer to "how do I handle this breaking change?"
is sometimes *confirm it does not apply to you*. A generic migration checklist would have
produced `@GenericGenerator` across six files for nothing. Check whether the precondition
holds before paying the cost of the mitigation.

**What this makes non-optional.** With Option B the per-entity sequences must exist before
the first insert. Flyway therefore stops being a precaution and becomes a hard requirement
of Hop 3, and `V1__initial_schema.sql` must be generated from **Hibernate 6**, never
carried over from the Hibernate 5 output captured during pre-flight.

**If this project is ever pointed at a database that does hold data** — a colleague's
dump, a restored backup — Option B no longer applies to it, and the sequences must be
created with `start with (max(id) + 1)` rather than 1.

### 2.3 Spring Security 6 — the config rewrite

Two mechanical changes that together touch every line of `SecurityConfig`.

**`antMatchers` → `requestMatchers`.** The old `antMatchers` / `mvcMatchers` /
`regexMatchers` trio collapsed into one `requestMatchers` that selects the strategy.

**Lambda-only DSL.** The `.and()` chaining style is gone:

```java
// Boot 2.7 — current
http.headers(h -> {
    h.httpStrictTransportSecurity().disable();
    h.frameOptions().disable();
});
http.csrf().ignoringAntMatchers("/h2-console/**");

// Boot 3 / Security 6
http.headers(h -> {
    h.httpStrictTransportSecurity(hsts -> hsts.disable());
    h.frameOptions(fo -> fo.disable());
});
http.csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"));
```

**A blocker this project dodges.** `WebSecurityConfigurerAdapter` was removed entirely in
Security 6 and is the single most common upgrade blocker. This code already uses the
modern `SecurityFilterChain` bean style, so there is nothing to migrate.

**Also worth knowing.** Authorization now denies by default when no rule matches. The
`anyRequest().permitAll()` catch-all means behaviour is unchanged here — but delete that
line and the application locks itself shut.

#### The `requestMatchers(String)` trap — found only at runtime

**This stopped the application from starting, and no test caught it.**

```
IllegalArgumentException: This method cannot decide whether these patterns are Spring
MVC patterns or not... there is more than one mappable servlet in your servlet context:
{DispatcherServlet=[/], JakartaWebServlet=[/h2-console/*]}
```

Security 6's `requestMatchers(String)` picks between `MvcRequestMatcher` and
`AntPathRequestMatcher` by inspecting the servlet context. With more than one mapped
servlet it refuses to guess and throws. The `dev` profile registers the H2 console servlet,
so the `dev` chain failed at startup.

**Why the suite could not catch it.** `@AutoConfigureMockMvc` never registers the H2 console
servlet, so tests see a single servlet and no ambiguity. All 51 tests passed against an
application that could not actually boot. This is the concrete argument for Layer 4 in
Part 4: a green suite is not a running application.

**The fix, and a second reason for it:**

```java
import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

req.requestMatchers(antMatcher("/cart/**"), antMatcher("/orders/**")).authenticated();
req.requestMatchers(antMatcher("/admin/**")).hasRole("ADMIN");
http.csrf(csrf -> csrf.ignoringRequestMatchers(antMatcher("/h2-console/**")));
```

Being explicit is not merely a workaround. The pre-upgrade code used `antMatchers()`, which
is **always** an `AntPathRequestMatcher`. Where the `String` overload does resolve without
error, it resolves to an `MvcRequestMatcher`, which matches differently — so accepting it
would silently change authorization semantics. `antMatcher(...)` preserves exactly what was
there before, which is why it is applied to **both** chains and not only the one that
failed.

### 2.4 Spring Session 3 — a schema change underneath the cart

`spring-session-jdbc` moves to 3.x, which alters the `SPRING_SESSION` and
`SPRING_SESSION_ATTRIBUTES` tables. Because `initialize-schema: always` is set, the change
applies on startup.

This matters more here than in most applications, because **the cart lives entirely in
session state**. A silent failure empties every user's cart while the rest of the
application looks healthy.

`SessionPersistenceTest` is the instrument for it — see §1.3.

**Outcome, verified in Hop 2.** The schema change was a non-event:
`springSessionCreatesItsSchema` passed on the first run against Boot 3.

**What actually broke was the mitigation, not the risk.** Boot 3 **removes the
`spring.session.store-type` property**, confirmed by comparing the configuration metadata
in both jars:

| | `spring.session.store-type` |
| --- | --- |
| `spring-boot-autoconfigure` 2.7.5 | present |
| `spring-boot-autoconfigure` 3.0.13 | **removed** |

That property is what `@IntegrationTest` used to disable Spring Session so MockMvc's
servlet-session helpers would work. Spring ignores unknown properties **silently** rather
than failing, so the upgrade quietly re-enabled Spring Session and six cart assertions
broke for a reason unrelated to the code under test.

The replacement excludes the auto-configuration instead:

```java
@TestPropertySource(properties =
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.session.SessionAutoConfiguration")
```

Better than the property in one specific way: a wrong class name throws at startup, where a
wrong property name does nothing at all.

**The lesson worth carrying.** A test-scoped workaround is itself something the upgrade can
break, and it will break *silently* when it is expressed as a configuration property. When
a mitigation can be written to fail loudly, write it that way.

### 2.5 Spring MVC 6 — trailing slashes stop matching

A quiet behavioural change. In Spring 6, `PathPatternParser` is the default matcher and
trailing-slash matching is off:

- Boot 2.7 — `/bottles` and `/bottles/` both reach the handler
- Boot 3 — `/bottles/` returns **404**

The templates here use exact paths, so internal navigation is unaffected. The exposure is
external: a bookmark, a search-engine result, an old link. If it matters,
`setUseTrailingSlashMatch(true)` restores the old behaviour (deprecated), or add an
explicit redirect.

### 2.6 Hibernate 6 — the smaller items

- **Obsolete properties must be removed.** `application-prod.yml` sets
  `hibernate.jdbc.lob.non_contextual_creation`, which Hibernate 6 no longer recognises.
- **Date and time handling is stricter.** `Order.createdOn` is a `java.util.Date` with
  `@Temporal(TemporalType.TIMESTAMP)`. Still supported, but this is a natural moment to
  consider `Instant` or `LocalDateTime`.
- **The HQL parser was rewritten.** No impact here — there are no `@Query` strings, only
  derived method names.
- **`@EntityGraph` semantics are unchanged**, so the N+1 defences carry over intact.

### 2.7 Platform floors

| | Boot 2.7 | Boot 3.0 |
| --- | --- | --- |
| Java | 8+ | **17+** |
| Servlet API | Servlet 4 (Tomcat 9) | **Servlet 6 (Tomcat 10)** |
| Gradle | 6.8+ | **7.5+** |

Java 17 is already in place. The Tomcat 9 → 10 jump *is* the Jakarta rename — Tomcat 10
was the first release shipping the renamed servlet API.

### 2.8 What does not change

Listed so time is not wasted looking for problems that do not exist:

- Constructor injection, `@Service` / `@Controller` / `@Repository`, bean scoping
- Spring Data JPA derived query methods such as `findAllByUser_username`
- `@ControllerAdvice`, `@ModelAttribute`, view resolution, the `redirect:` prefix
- BCrypt, `UserDetailsService`, `DaoAuthenticationProvider`
- Bean Validation annotations — same annotations, new package

> **Correction.** An earlier version of this list claimed "Thymeleaf template syntax —
> the extras libraries change, the templates do not." **That was wrong**, and it was the
> largest gap in this analysis. Thymeleaf 3.1 removes expression objects the templates
> here use in twenty places. See §2.9.

### 2.9 Thymeleaf 3.1 removes the request and session expression objects

**Added after the fact. This caused 18 of the 24 failures in Hop 2 and was not predicted
anywhere in the original analysis.**

Boot 3 brings Thymeleaf 3.1, which removes four expression utility objects outright:

```
IllegalArgumentException: The 'request','session','servletContext' and 'response'
expression utility objects are no longer available by default for template
expressions and their use is not recommended.
```

Removed: `#request`, `#session`, `#servletContext`, `#response`. Spring's
`#httpServletRequest` and `#httpSession` go with them.

**Still available**, so do not rewrite these unnecessarily: `param`, `session` as a map,
`application`, and every `#strings` / `#lists` / `#temporals` utility.

The rationale is deliberate: templates reaching directly into the servlet API is a layering
violation. The framework is pushing you to pass what the view needs through the model.

**Scale in this repository:** 20 usages across 9 templates.

| Usage | Count |
| --- | --- |
| `#httpServletRequest.getRequestURI()` | 12 |
| `#httpServletRequest.getQueryString()` | 5 |
| `#request.getParameter(...)` | 3 |

Because `include/navbar.html` is on every page, this broke **every** rendering test at
once — which is misleading. The blast radius looks catastrophic; the actual fix is small.

**The fix used here.** Expose what the templates need as model attributes rather than
handing them the request:

```java
// ControllerAdviceSetup — applies to every @Controller
@ModelAttribute("currentUri")
public String currentUri(HttpServletRequest request) {
    return request.getRequestURI();
}

@ModelAttribute("currentUrl")
public String currentUrl(HttpServletRequest request) {
    var query = request.getQueryString();
    return query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
}
```

For the three `getParameter` calls, the controller had **already bound those very
parameters** as method arguments — `@RequestParam(name = "p") Optional<String> page`. They
only needed adding to the model. The template was reaching around a value it was already
being handed.

**A bug fixed incidentally.** Two templates built their return URL as
`uri + '?' + queryString` unconditionally, rendering a literal `?null` whenever there was
no query string. Consolidating on `currentUrl` removed it. Worth noting rather than hiding:
the removed API forced a rewrite, and there was no way to reproduce that behaviour without
deliberately reimplementing the bug.

**`nl2br` was fine.** `thymeleaf-extras-nl2br:1.0.2` was flagged in §1.5 as the highest
risk — a third-party dialect last released for Thymeleaf 3.0. It works unchanged on 3.1.
The fallback plan in §1.6 was not needed. The genuine risk was in Thymeleaf itself, not the
third-party add-on, which is the opposite of what the dependency audit suggested.

---

## Part 3 — Doing the upgrade

**One change per commit. Suite green between every one.** Never bundle two hops. The
entire value of the sequence is that a regression can be bisected to a single change.

### Hop 1 — Gradle 7.5.1 → 8.5

Build tooling only, no framework change, so build-DSL breakage never overlaps with
framework breakage.

```bash
./gradlew wrapper --gradle-version 8.5
./gradlew wrapper --gradle-version 8.5   # twice, deliberately
```

The first invocation updates `gradle-wrapper.properties`; the second regenerates the
wrapper scripts and jar *using* the new version.

Then fix what Gradle 8 removed: `archiveName` → `archiveFileName`, and
`sourceCompatibility` → a toolchain block, pinned to 17. The toolchain form is what makes a later Java 21 move a one-line change (§3.5).

**Outcome, verified at commit `b2072e3`.** Both predicted fixes were the only ones needed.
The Boot **2.7.5** plugin runs on Gradle 8.5 without complaint — worth stating explicitly,
because Boot 2.7's documented support matrix stops at Gradle 7.x, which makes this look
riskier than it is. The build failed on the removed `archiveName` property alone, having
already evaluated the plugin successfully. Tests came out byte-identical to the baseline.

Two leftovers, neither a problem:

- **Three `Convention`-type deprecations remain**, scheduled for removal in Gradle 9. They
  carry no file or line attribution, which is how you can tell they originate inside a
  plugin rather than the build script — the Boot 2.7.5 plugin predates Gradle 8. They
  clear when Boot upgrades. Do not mistake them for damage caused by a later hop.
- **Spotless 6.11.0 needed no bump** for Gradle 8, contrary to the caution in §1.5. It
  went on to need none for Boot 3.5 either. It remains untested on Java 21, which was not
  taken (§3.5).

When documentation does not answer a compatibility question cleanly, prefer a two-minute
experiment to a confident guess. A wrapper change is one file and trivially reverted.

### Hop 2 — Boot 2.7.5 → 3.0.13

The large one. Everything else is small by comparison.

**Package rename.** `javax.persistence`, `javax.validation`, `javax.servlet`,
`javax.annotation` all become `jakarta.*`. Mechanical, and the compiler finds every one:

```bash
grep -rl "javax\.\(persistence\|validation\|servlet\|annotation\)" src/main/java src/test/java \
  | xargs sed -i 's/javax\.persistence\./jakarta.persistence./g; s/javax\.validation\./jakarta.validation./g; s/javax\.servlet\./jakarta.servlet./g; s/javax\.annotation\./jakarta.annotation./g'
```

Read the resulting diff rather than trusting the script.

**Spring Security 6.** `antMatchers` → `requestMatchers`, and the `.and()` chaining style
is gone in favour of lambdas throughout. Both filter chains in `SecurityConfig` are
rewritten. Preserve the rules exactly — this is not the moment to improve them.

**Hibernate 5 → 6.** The subtle part. Remove obsolete properties such as
`hibernate.jdbc.lob.non_contextual_creation`, and expect schema generation to differ.

**ID generation: nothing to do here.** `GenerationType.AUTO` changes from one shared
`hibernate_sequence` to a sequence per entity, but Option B was chosen (see §2.2), so the
six affected entities are left untouched. The consequence lands in Hop 3: the Flyway
baseline must create those per-entity sequences.

**Thymeleaf.** Swap `springsecurity5` → `springsecurity6`, delete `java8time`, and resolve
`nl2br` per the decision made in §1.6.

### Hop 3 — Flyway

Placed deliberately *after* Boot 3.0 and *before* any Boot 3 startup touches a persistent
database.

The reason is specific: Hibernate 6 generates different DDL, and `ddl-auto: update` would
apply that difference **silently and irreversibly** to production data on first boot.
Flyway converts the schema into versioned SQL you can review in a pull request.

1. Generate the baseline DDL for Postgres via
   `spring.jpa.properties.javax.persistence.schema-generation.scripts`.
2. Copy it to `src/main/resources/db/migration/V1__initial_schema.sql`.
3. **Strip every `drop` statement** — migrations must be additive.
   **Confirm the per-entity sequences are present.** Per the Option B decision in §2.2, the
   baseline must contain `address_seq`, `beverage_seq`, `orders_seq`, `order_items_seq`,
   `privileges_seq` and `roles_seq` — *not* the single `hibernate_sequence` that Hibernate
   5 emitted. If the file contains `hibernate_sequence`, it was generated before the Boot 3
   hop and must be regenerated. Starting each at 1 is correct here only because no database
   holds data; against existing rows they must start above `max(id)`.
4. Prod moves to `ddl-auto: validate` with `flyway.enabled: true`.
5. Dev keeps `ddl-auto: create` on in-memory H2 with `flyway.enabled: false`, so tests
   stay fast and the schema is rebuilt per run.

### Hop 4 — Boot 3.0.13 → 3.5.0

A minor-version walk, and the only hop that required **no source changes at all**. Tests
came out byte-identical to baseline on the first run, and the full flow verified on a
running instance.

It did surface one thing: Spring Security 6.5 marks `AntPathRequestMatcher` **deprecated
for removal** — the very matcher adopted in Hop 2 to fix the multi-servlet startup failure
and to preserve pre-upgrade path-matching semantics.

It was deliberately left in place. Replacing it changes how authorization paths are
matched, and folding a behaviour-sensitive change into a version bump reintroduces the
ambiguity this whole sequence exists to prevent. Deprecated is not broken. The replacement
is `PathPatternRequestMatcher`, and it deserves its own commit with its own verification.

### Hop 5 — Java 17 → 21: **not taken**

**Decision, 2026-08-10: stay on Java 17.**

The machine had only JDK 17 installed, and Gradle 8.5 will not provision a toolchain
without a resolver plugin. That left three options: add the Foojay resolver so Gradle
downloads a JDK 21 automatically, install one by hand, or stop at 17.

Stopping was chosen, and it is a defensible engineering position rather than a shortcut:

- **Java 17 is an LTS supported into 2029.** Nothing is end-of-life, unlike Boot 2.7 was.
- **Boot 3.5 runs on 17.** The floor is 17; 21 is optional.
- **The upgrade's actual goal was reaching a supported framework**, and that is done.
- Java 21's benefits here — virtual threads, pattern matching — are features this codebase
  does not currently use. Taking a runtime bump to enable nothing is cost without return.

**To take it later**, the whole change is one line, because the Dockerfile and CI are gone:

```gradle
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

plus a JDK 21 that Gradle can find. Add `id 'org.gradle.toolchains.foojay-resolver-convention'`
to `settings.gradle` if you would rather Gradle fetch it than install one. Then re-run
Part 4's six verification layers — nothing about them is version-specific.

### Hop 6 — Frontend browser data

The frontend needs only `npx update-browserslist-db@latest` to clear a stale
`caniuse-lite` warning — Webpack 5 builds clean on Node 26, verified.

---

## Part 4 — Verifying it

Layered, cheapest first. Each layer catches a class of failure the one above it cannot.

### Layer 1 — It compiles

```bash
./gradlew compileJava
```

Catches every import and signature change. **Necessary, nowhere near sufficient** — it
proves nothing about behaviour.

### Layer 2 — The suite is unchanged

```bash
./gradlew clean build
```

The bar is exact: **51 tests, the same 51, same assertions.** Not "51 green" — a dropped
test class still reports success. Compare per-class counts against `before-tests.txt`.

> If a test fails, **investigate before touching it.** The suite exists precisely to catch
> this. Editing an assertion to make it pass discards the only signal you have. If a
> change is genuinely intended by the new framework version, change the assertion *and say
> so in the commit message*.

### Layer 3 — The diagnostic tests

Two failures carry specific meaning:

- **`SessionPersistenceTest` alone fails** → the Spring Session 2→3 schema change. Not
  your controllers.
- **`overselling_isCurrentlyPossible` stops failing** → Hibernate 6 altered transaction or
  flush semantics. Investigate rather than celebrate.

### Layer 4 — Runtime smoke

Templates are not compiled, so Thymeleaf breakage — the `sec:` namespace swap, `nl2br` —
appears only at runtime.

```bash
npx pnpm build && ./gradlew bootRun
```

Confirm the banner reads Boot 3.5 on Java 17, then walk the full baseline table from
§1.1. Pay particular attention to `/`, `/login`, `/admin`, and `/cart`, which are the
pages using `sec:` or `nl2br`.

### Layer 5 — Diff the mechanics

```bash
./gradlew dependencies > ../after-deps.txt
diff ../before-deps.txt ../after-deps.txt
```

Surfaces transitive surprises: a library that silently vanished, downgraded, or arrived.

Diff the Hibernate-generated DDL against the version saved in §1.4 as well. Any column
type or constraint difference is precisely what `ddl-auto: update` would have applied to
production without asking.

### Layer 6 — The prod profile

The only layer exercising Postgres, Flyway, and `ddl-auto: validate` together. Run it
against an **empty** database:

```bash
docker compose down -v
docker compose up -d prost_postgres
APP_ENV=prod ./gradlew bootRun
```

Expect `Migrating schema "public" to version 1`, then a clean startup. A
`SchemaManagementException` means the migration does not match the entities — **fix the
SQL, not the entities.**

---

## Part 5 — When something breaks

| Symptom | Most likely cause |
| --- | --- |
| `cannot find symbol: javax.persistence` | A missed file in the package rename. Re-run the grep |
| `antMatchers()` undefined | Security 6 — use `requestMatchers()` |
| Every `@SpringBootTest` fails to load context | A bean definition problem; read the *last* `Caused by`, not the first |
| Only session/cart/login tests fail | Spring Session, not your code. See Layer 3 |
| `SchemaManagementException` on prod start | `V1__initial_schema.sql` disagrees with the entities |
| Primary-key collision on the first insert after upgrade | Hibernate 6 per-entity sequences starting at 1 against existing rows. See §2.2 |
| `sequence "orders_seq" does not exist` | Same cause — Hibernate 6 wants per-entity sequences, the schema has `hibernate_sequence` |
| A URL with a trailing slash now 404s | Spring MVC 6 dropped trailing-slash matching. See §2.5 |
| Every rendering test fails with `TemplateInputException` | Thymeleaf 3.1 removed `#request` / `#httpServletRequest`. See §2.9 |
| Cart or login assertions fail but the app works by hand | `spring.session.store-type` was removed and ignored silently. See §2.4 |
| App will not start: "cannot decide whether these patterns are Spring MVC patterns" | Security 6 with a second servlet mapped. Use `antMatcher(...)`. See §2.3 |
| Tests all green but the application does not boot | Almost always a servlet-context difference MockMvc does not reproduce. Run it |
| Page renders unstyled | The frontend bundle is missing. Run `npx pnpm build` |
| Template throws only at runtime | Thymeleaf dialect — `sec:` or `nl2br` |
| `spotlessJavaCheck` fails on every file | CRLF working tree. `.gitattributes` plus `./gradlew spotlessApply` |
| Port 8080 in use after Ctrl+C | Gradle runs the app as a child JVM and orphans it. Kill the port owner |

**Rolling back.** Because every hop is one commit with a green suite behind it, recovery
is `git revert <sha>` and a bounded investigation — never archaeology through a
five-hundred-file diff. That property is the entire return on the sequencing discipline.

---

## Part 6 — What is deliberately *not* in the upgrade

Refactoring. It is scheduled after the upgrade, on purpose.

Prost has real design problems: forty lines of order logic inside
`CartController.submitCart`, an overselling race at checkout, an admin dispatcher with
four silent early-exits. All of them are worth fixing, and none of them are touched until
the suite is green on Boot 3.5.

The reason is the same one that governs everything else here. Refactoring and migrating in
the same diff means a failing test has two possible explanations, and separating them
afterwards costs far more than the sequencing ever did.

---

## Appendix — Command reference

```bash
# Run locally (H2 in-memory; no Docker, no cloud)
npx pnpm install && npx pnpm build
./gradlew bootRun                       # admin / admin, seed at /admin → importAll

# Full build with tests
./gradlew clean build

# One test class
./gradlew test --tests "*CheckoutTest*"

# Formatting (Spotless, palantir-java-format)
./gradlew spotlessApply

# Alternate port when 8080 is busy
./gradlew bootRun --args='--server.port=8099'

# Kill an orphaned app (PowerShell)
Get-NetTCPConnection -LocalPort 8080 -State Listen | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }

# Search git history for a secret by content
git log --all --oneline -S "<literal>"

# Local Postgres for prod-profile checks
docker compose up -d prost_postgres     # adminer on :8082
```

### Related documents

- `docs/superpowers/specs/2026-08-09-prost-modernization-design.md` — the design and its rationale
- `docs/superpowers/plans/2026-08-09-prost-modernization.md` — the task-by-task plan
- `docs/reference/removed-gcp-and-email-architecture.md` — what the deleted cloud code did
