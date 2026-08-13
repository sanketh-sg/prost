# Prost

A beverage web shop, originally a University of Bamberg DSAM group project.
Server-rendered Spring Boot MVC + Thymeleaf, Tailwind/Webpack frontend, JPA over
H2 (dev) or Postgres (prod).

Current stack: **Spring Boot 3.5.0**, Gradle 8, Java 17 toolchain, Flyway for the
prod schema.

## Running it

Java 17. The `dev` profile is the supported local path — H2 in-memory, no Docker,
no Postgres required.

```bash
./gradlew bootRun          # http://localhost:8080
./gradlew test
```

Seeded admin account: `admin` / `admin`, created on every context refresh by
`StartListener`. Catalogue data is loaded from the admin panel via the
`databaseLoader::importAll` action.

Dev database (`application-dev.yml`), also reachable at `/h2-console`:

```yaml
username: prostadmin
password: strongpassword
url: jdbc:h2:mem:prost
```

Frontend (Node + [pnpm](https://pnpm.io/)):

```bash
pnpm install
pnpm build      # or `pnpm dev` to watch
```

Built with [PostCSS](https://postcss.org/), [TailwindCSS](https://tailwindcss.com/),
and [Font Awesome](https://fontawesome.com/).

## Domain model (`entity/`)

```
User (PK = username, implements UserDetails)
 ├─ roles ─── Role ─── privileges ─── Privilege     (both → GrantedAuthority)
 ├─ billing/deliveryAddress → Address
 └─ orders → Order ─ orderItems → OrderItem → Beverage

Beverage (abstract, SINGLE_TABLE, dtype)
 ├─ Bottle  (volume, volumePercent, inStock)     isAlcoholic = volumePercent > 0
 └─ Crate   (noOfBottles, cratesInStock, → Bottle)
```

Two things worth knowing:

- `Beverage` abstracts over `getInStock`/`setInStock`, so `Crate.cratesInStock`
  and `Bottle.inStock` are the same concept to the cart and to checkout. That is
  why one stock check covers both.
- **`OrderItem` is one row per physical unit**, not a line with a quantity.
  Ordering 3 bottles produces 3 rows with `position` 0, 1, 2. `CartDTO.fromOrder`
  re-collapses them into counts for display.

Every entity uses the Hibernate-idiom `equals` (`Hibernate.getClass` + id) with
`hashCode = getClass().hashCode()`. The constant hashCode is deliberate — it is
proxy-safe.

## The cart flow

The spine of the application.

```
POST /cart/add    → CartController → Cart (session bean)  ← Map<Long,Integer> under "__cart"
GET  /cart        → Cart.getCartState() → CartDTO (Map<Beverage,Integer> + totalPrice)
POST /cart/submit → OrderService.placeOrder(user, cart)   @Transactional
```

`Cart` is a `@Service` holding an injected `HttpSession` — session-scoped state in
a singleton via Spring's session proxy. The cart stores **only ids**; beverages
are re-fetched on every read, so stale prices cannot linger.

`OrderService.placeOrder` (`service/OrderService.java:62`):

1. Lock *every* line first via `BeveragesRepository.findByIdForUpdate`
   (`PESSIMISTIC_WRITE`) and check stock. A rejection therefore leaves nothing
   behind, and the locks are held until commit so concurrent checkouts serialize.
2. Validate the `Order`, save it, then attach and save the `OrderItem`s.
3. Decrement stock — **no `Math.max` clamp**, deliberately, since step 1
   guarantees sufficiency.

The gap this closes: `/cart/add` validates stock *per request*, so two
individually valid requests can build a cart that exceeds stock.
`CheckoutTest.checkoutIsRejectedWhenTheCartExceedsStock` pins exactly that.

## Controllers

| Route | Class | Notes |
| --- | --- | --- |
| `/`, `/bottles`, `/crates`, `/order_success` | `IndexController` | 9 per page via `OffsetBasedPageRequest` |
| `/cart/**` | `CartController` | translates `OrderService` exceptions into toasts |
| `/orders`, `/orders/{id}` | `OrdersController` | ownership enforced by querying `username AND id` — non-owner and nonexistent take the same branch |
| `/user/**` | `UserController` | address selection, birthday |
| `/login`, `/register`, `/whoami` | `AuthController` | manual `req.login()` after register; 16+ age gate |
| `/admin/**` | `AdminController` | reflection dispatcher, see below |
| `/error` | `ProstErrorController` | |

### The admin action dispatcher

The unusual part of the codebase. `AdminActionsProvider` scans the context for
`@AdminAction` beans and exposes any public method named `action__*`. The admin
page renders Call/Await buttons, and
`/admin/action?a=databaseLoader::importBottles&await=1` invokes the method by
reflection.

Only `DatabaseLoader` carries the annotation. Its actions seed from
`data.json`, are `@Async`, and return `Future`.

Every early exit in the dispatcher logs *and* toasts. That was a deliberate fix:
because actions are resolved by name from a query parameter, a typo is routine,
and a bare redirect previously looked identical to success.

## Cross-cutting mechanics

- **Toasts** (`UserErrorManager` + `util/Toast`) — session-stored flash messages,
  drained on read by `ControllerAdviceSetup.getToasts()`, rendered by
  `include/toasts.html`, auto-dismissed by `transitions.js`. This is the
  application's entire user-feedback channel.
- **`currentUri` / `currentUrl` model attributes** exist because Thymeleaf 3.1
  removed `#request`. Templates use them for nav highlighting and `next=`
  round-trip parameters. For the same reason `AdminController` passes
  `selectedPanel` / `selectedUsername` explicitly.
- **Security** — two profile-scoped filter chains. Both use explicit
  `PathPatternRequestMatcher` rather than bare Strings, because the dev profile
  registers the H2 console as a second servlet and Security 6 refuses to guess
  what a String means. Prod adds HSTS, `redirectToHttps`, and secure cookies.
  BCrypt throughout.

## Persistence

- **dev** — H2 in-memory, `ddl-auto: create`, Flyway **off** on purpose: the
  schema is rebuilt from the entities on every start, so the migrations have
  nothing to migrate.
- **prod** — Postgres, Flyway owns the schema, `ddl-auto: validate`.
  `V1__initial_schema.sql` carries an honest header: verified against H2 in
  PostgreSQL compatibility mode, **not yet against a real PostgreSQL server**.
  It also encodes a Hibernate 6 change — per-entity sequences (`orders_seq` and
  friends) allocating 50 at a time, replacing Hibernate 5's shared
  `hibernate_sequence`.
- Sessions live in a `SPRING_SESSION` table via `spring-session-jdbc`, not in the
  servlet session. This is the single most load-bearing detail in the test suite.

## Tests

`@IntegrationTest` is a composed annotation: `@SpringBootTest` + MockMvc + dev
profile + **excludes `SessionAutoConfiguration`**. Without that exclusion,
MockMvc's `.session(...)` sets a servlet session the application never reads once
Spring Session is active, and every cart assertion fails for reasons unrelated to
the behavior under test.

`SessionPersistenceTest` is the deliberate exception. It runs *with* Spring
Session, on its own database (`jdbc:h2:mem:prost-session`, because two contexts
sharing one in-memory database collide over pre-allocated id blocks), driving
requests through real `SESSION` cookies.

Eleven classes cover: authentication, the authorization matrix, catalogue
rendering, cart flow (HTTP and unit), checkout including the over-stock rejection
and the exact-stock boundary, order ownership, admin dispatcher error paths, and
the session round-trip.

## Frontend

Webpack produces three bundles — `bundle`, `summary_page`, `admin` — into
`src/main/resources/static/build/`. Tailwind with PostCSS nesting, plus Font
Awesome.

Only two JavaScript behaviors exist: `transitions.slideOffScreen` (toast
dismissal) and `pagination.changePage` (a hidden-input page form). Both are
exported as a `prostLib` global so inline `th:onclick` can reach them.

## Modernization status

Per `docs/superpowers/specs/2026-08-09-prost-modernization-design.md`, Phases 1–5
are essentially complete: GCP and email code deleted, a characterization suite
written before the upgrade and carried through unchanged, Boot 3.5, Flyway,
`OrderService` extracted from `CartController`, overselling fixed, admin
dispatcher made loud, unused `Java8TimeDialect` dropped.

**Java 21 was deliberately not taken** (commit `ce94f27`). Java 17 is an LTS
supported into 2029 and is Boot 3.5's floor; the goal was a supported framework,
and that is met. The toolchain stays on 17 by decision, not by omission.

Still open:

- `V1__initial_schema.sql` has not been verified against a real Postgres server —
  Docker was unavailable when it was written. Run `docker compose up -d
  prost_postgres` then `APP_ENV=prod ./gradlew bootRun` before trusting it.
- The work lives on `upgrade/boot-3`, 15 commits ahead of `origin/main`, and has
  not been merged.

`docs/whys-and-whats-of-upgrade.txt` records what actually broke during the Boot 3
hop, including the three failures nobody predicted.
