# The Prost Toolchain

Every tool this project depends on: what it does, why it is here, and what you
would use instead. Written for someone who has never met any of them.

Reflects the stack as merged to `main`, 30 August 2026.

## How a request flows

Prost is a web application: a program running on a server that builds a page and
sends it to your browser. Nearly every tool below does one step of the same short
journey.

1. **Request arrives** — Tomcat, Spring MVC
2. **Who are you?** — Spring Security
3. **Fetch the data** — JPA, Hibernate, PostgreSQL
4. **Build the HTML** — Thymeleaf
5. **Style it** — Tailwind, webpack

The rest exist so developers can build, test and ship that loop.

---

## The foundation

### Java 17

The programming language the server is written in.

Version 17 is an LTS (Long-Term Support) release, meaning it gets security
patches for years rather than months. Companies pin to LTS versions so they are
not forced into an upgrade every six months. The version is fixed in the build
file rather than left to whatever is installed on a given machine, so the project
compiles identically everywhere.

**Instead of it:** Kotlin runs on the same platform and interoperates freely with
Java, with far less ceremony — it is the mainstream modern choice for new work
here. Java 21 is a newer LTS; this project stays on 17 as a deliberate
constraint. Outside the ecosystem: C#/.NET is the closest cousin, Go trades
features for simplicity, TypeScript on Node lets one language cover server and
browser.

### Gradle 8.5

The program that turns source code into something runnable.

A project is hundreds of source files plus dozens of libraries downloaded from
the internet. Something has to fetch those libraries, compile everything in the
right order, run the tests, and package the result. That is Gradle. The project
commits a *wrapper* (`gradlew`), so a newcomer runs `./gradlew build` and the
correct Gradle version downloads itself — nothing to install first.

**Instead of it:** Maven is the other major Java build tool and is probably more
common. It is configured in XML and is rigidly conventional, which makes it more
predictable and much more verbose. Gradle is configured in a real programming
language, which makes custom steps easy (this project uses that to run the
frontend build) at the cost of being easier to overcomplicate. Bazel exists for
enormous multi-language codebases and would be overkill here.

### Spring Boot 3.5

The framework that supplies everything a web application needs except your actual
features.

Without a framework you would hand-write thousands of lines of plumbing: opening
network connections, parsing HTTP, managing database connections, wiring objects
together. Spring Boot provides all of it, plus a web server (Tomcat) bundled
*inside* the application. The output is a single file, `prost.jar`, which you run
with `java -jar prost.jar` and you have a website.

Its signature trick is auto-configuration: it inspects which libraries you have
added and configures them with sensible defaults. Adding the database library is
most of the work of connecting to a database. The web half is Spring MVC, which
maps an incoming URL to a method in your code.

**Instead of it:** In Java, Quarkus and Micronaut are newer frameworks built for
fast startup and low memory — they matter for serverless and containers, less so
for a long-running web app. Jakarta EE is the older standards-based approach.
Elsewhere, Django (Python), Ruby on Rails, Laravel (PHP) and ASP.NET Core (C#)
solve the same problem with the same batteries-included philosophy; Express or
NestJS are the Node equivalents.

### Spring Boot DevTools

Restarts the application automatically when you change the code. Without it,
every edit means stopping, rebuilding and starting again. It is excluded from the
production jar, so it costs nothing once shipped.

**Instead of it:** JRebel swaps changed code into a running process without
restarting at all — faster, but commercial. Or restart by hand, which is what
everyone did for years.

---

## Storing the data

### PostgreSQL 15

The database used in production — the program that stores data on disk and
survives restarts.

Postgres is the mainstream open-source choice: free, reliable, and correct under
many simultaneous users. That last property matters here, because two people can
try to buy the last crate at the same moment and the database is what stops both
succeeding.

**Instead of it:** MySQL/MariaDB is the other big open-source option,
historically easier to start with and less strict. SQL Server and Oracle are the
commercial heavyweights. SQLite is a single file with no server — wonderful for
small or embedded apps, not for many concurrent writers. MongoDB and other
document databases store free-form documents instead of tables; a shop with
orders, users and stock has highly relational data, so a relational database is
the right shape.

### H2

A second, different database used only while developing and running tests.

H2 lives entirely in memory and disappears when the application stops. Why
bother? Tests that need a real database server are slow and require someone to
have started one. H2 starts instantly and every run begins from a clean slate.

The trade-off is real: you develop against one database and deploy to another, so
a difference between the two can stay hidden until production.

**Instead of it:** Testcontainers starts a genuine PostgreSQL in a container for
the tests. It removes the mismatch entirely at the cost of slower tests and a
Docker requirement — the more faithful choice, and the usual recommendation for a
team. HSQLDB and Apache Derby occupy the same in-memory niche as H2.

### Spring Data JPA and Hibernate

The translator between Java objects and database tables.

Your code thinks in objects — a `Bottle`, an `Order`, a `User`. The database
thinks in tables and rows. Writing that conversion by hand for every operation is
tedious and easy to get wrong, so an ORM (Object-Relational Mapper) does it:
Hibernate. You ask for a bottle, it writes the `SELECT`, runs it, and hands back a
Java object. Spring Data JPA goes further: declare a method called
`findByNameContaining` and it generates the query from the name alone.

**Instead of it:** jOOQ inverts the idea — you write SQL, but in type-safe Java so
the compiler catches mistakes. Excellent when queries are the interesting part.
MyBatis keeps hand-written SQL in mapping files. Spring Data JDBC is a
deliberately simpler ORM without Hibernate's more surprising behaviours.

An ORM is a genuine trade: it removes enormous amounts of boilerplate, and in
exchange it sometimes generates queries you did not expect and would not have
written.

### Flyway

Version control for the shape of the database.

Your code lives in Git, so every change is recorded and repeatable. The
database's structure — its tables and columns — does not. So when you add a
"phone number" column, how does the production database find out?

Flyway makes you write each change as a numbered SQL file, such as
`V1__initial_schema.sql`. It records which have already run and applies any that
have not, on startup. It is Git for your table structure.

**Instead of it:** Liquibase is the main rival: more features, including
rollback, and changes described in XML or YAML rather than raw SQL. More capable,
more machinery. Flyway's appeal is that a migration is just a SQL file anyone can
read.

Letting Hibernate alter the schema automatically (`ddl-auto`) works in
development and is dangerous in production, because "automatically drop that
column" is not a decision you want made for you. This project does exactly that
split on purpose: `ddl-auto: create` in dev, Flyway plus `validate` in prod.

### HikariCP

Keeps a small set of database connections open and lends them out.

Opening a connection to a database takes a surprising amount of time; doing it
fresh for every page view would be wasteful. Hikari is the fastest of its kind
and Spring Boot includes it by default, so most developers never think about it.

**Instead of it:** Tomcat JDBC Pool, Apache DBCP and C3P0 all do the same job and
are all older and slower. There is no live debate here.

### Docker Compose and Adminer

Runs PostgreSQL on your machine without installing it, plus a web page for
looking inside it.

A container is a pre-packaged, isolated environment. Rather than installing
Postgres and configuring it correctly, `docker compose up` downloads and starts
it in seconds, set up identically for everyone. Adminer comes along in the same
file: a small browser interface for browsing tables and running queries by hand.

**Instead of it:** Installing PostgreSQL directly works and is one less moving
part, until two projects want different versions. Podman is a drop-in Docker
alternative. Kubernetes solves a much larger problem and is not a
local-development tool. For browsing data: pgAdmin is the official heavyweight,
DBeaver and TablePlus are desktop clients, `psql` is the built-in terminal
client.

---

## Knowing who you are

### Spring Security

Handles logins, permissions, and a category of attacks you have not thought of
yet.

Two separate jobs. *Authentication* asks "is this really who they claim to be?" —
checking the password. *Authorization* asks "is this person allowed here?" —
keeping ordinary users out of the admin pages.

It also defends against attacks automatically. The main one is CSRF: a malicious
website silently causing your logged-in browser to submit a request to Prost.
Spring Security requires a secret token on every form, which the attacker cannot
obtain. Passwords are stored using BCrypt, a hashing algorithm deliberately
designed to be slow, so stolen password data is expensive to crack.

**Instead of it:** Apache Shiro is simpler and easier to learn, with far less
integration into the surrounding framework. Keycloak, Auth0 and Okta take a
different route: hand login off to a dedicated identity service and let it handle
passwords, resets and multi-factor — increasingly the default for real products.

For password hashing specifically, Argon2 is the modern recommendation over
BCrypt, with scrypt and PBKDF2 also acceptable. Writing your own is the one
option that is simply wrong.

### Spring Session JDBC

Stores your session — including your shopping cart — in the database.

HTTP is stateless: each request arrives with no memory of the last one. So the
server needs somewhere to record "this visitor has three beers in their basket".
By default that lives in the server's memory, which means it is lost on restart,
and breaks entirely if you run two copies of the application — your second
request might reach the copy that has never heard of you.

This library moves sessions into the database, so carts survive a restart and
would survive running multiple servers.

**Instead of it:** Spring Session with Redis is the common production choice —
Redis is an in-memory store, so much faster for this, at the cost of another
service to run. Sticky sessions configure the load balancer to always send you to
the same server: simpler, and fails when that server does. JWT tokens avoid
server-side sessions altogether by giving the browser a signed token — popular,
and genuinely harder to get right, with immediate logout the classic difficulty.

---

## Building the pages

### Thymeleaf 3.1

Fills real data into HTML templates, on the server.

A template is an HTML file with placeholders. Rather than hardcoding a beer's
name you write `th:text="${beverage.name}"`, and Thymeleaf substitutes the real
value, or loops to produce one card per beer. There are around two dozen of these
files.

The finished HTML is assembled *on the server* and sent complete — as opposed to
the modern alternative, where the server sends raw data and JavaScript assembles
the page in the browser. Thymeleaf's particular charm is that its templates remain
valid HTML files, so you can open one directly and see roughly the right layout.

**Instead of it:** Staying server-rendered, JTE compiles templates into Java, so a
typo or a renamed field becomes a build error instead of a broken page at runtime
— the strongest modern option. FreeMarker and Mustache are established; JSP is the
legacy answer nobody chooses today.

Changing the architecture: React, Vue, Next.js or Nuxt would render in the
browser with this application reduced to a data API. That buys rich interactivity
and costs a second application to build and deploy. For a catalogue and a
checkout, server-rendering is the simpler and defensible choice.

### Thymeleaf Security integration

Lets templates ask security questions directly. Writing
`sec:authorize="hasRole('ADMIN')"` around a link shows it only to administrators,
without the surrounding code having to work out what to pass in.

**Instead of it:** Pass the user's roles into the template as ordinary data and
use a normal condition. More explicit, slightly more code, and what you would do
on a template engine without this integration.

### Tailwind CSS 3

Styles pages with many tiny single-purpose classes written directly in the HTML.

Traditional CSS asks you to invent a name for every component and describe it in a
separate file. Tailwind gives you thousands of small classes to combine in place:
`flex items-center p-4 rounded shadow` means flex layout, vertically centred, some
padding, rounded corners, a shadow. You stop naming things and stop hunting
through stylesheets. It also scans the templates and discards every class you did
not use, so what ships stays small.

**Instead of it:** Plain CSS with a naming convention such as BEM is the classical
answer and still perfectly good. Sass adds variables and nesting on top. Bootstrap
takes the opposite approach — ready-made components rather than raw utilities — so
you get a working design faster and every Bootstrap site looks related.

Tailwind's genuine downside: the HTML becomes visually noisy. People feel strongly
about this in both directions.

### webpack 5

Packs the JavaScript and CSS into a few optimised files.

Developers want code split across many small files; browsers prefer to download
few. webpack resolves the tension, and drops the result inside the application so
it ships as part of the jar. It also watches files during development and rebuilds
as you save. In this project the Gradle build invokes it automatically, so a fresh
checkout cannot accidentally produce a jar with no styling in it.

**Instead of it:** Vite is the modern default and is dramatically faster in
development; a new project would almost certainly start there. esbuild and Rollup
are the fast, lower-level options, and Parcel aims at zero configuration. For a
project this size you could skip bundling entirely and serve a couple of
hand-written files — you would lose Tailwind's build step, which is what makes it
worth having.

### PostCSS and friends

The assembly line that turns source stylesheets into the finished one.

PostCSS is the engine that transforms CSS — Tailwind itself is a PostCSS plugin.
`postcss-preset-env` rewrites modern CSS into forms older browsers understand.
Two further plugins pull the CSS into its own file and compress it.

**Instead of it:** Sass is the long-standing alternative with its own syntax and
feature set. Lightning CSS is a much faster modern replacement. Or skip the step:
plain CSS has absorbed most of what these tools were invented to provide.

### pnpm and Font Awesome

pnpm is to JavaScript what Gradle is to Java: it downloads libraries and records
exact versions in a lock file so everyone installs precisely the same thing. The
"p" is for performant — it saves disk space by keeping one copy of each package
shared between projects. Font Awesome provides ready-drawn icons: cart, user,
trash.

**Instead of it:** npm ships with Node and needs no decision; yarn is the older
challenger; bun is the fast newcomer. Any of them works — the important thing is a
committed lock file. For icons, Lucide, Heroicons and Phosphor are lighter and
more current, and ship as SVG rather than an icon font.

---

## Keeping it correct

Nothing here is visible to a user. All of it exists so that mistakes are caught by
a machine rather than by a customer.

### Hibernate Validator

Checks that submitted data is sane, declared as labels on the fields themselves.

Instead of scattering `if` checks through the code, you mark a field `@NotEmpty`
or `@Email` and the framework enforces it, gathering up every failure so the form
can show them all at once. It is the reference implementation of a Java standard
called Bean Validation.

**Instead of it:** Hand-written checks are always available and are clearer for
genuinely complex rules. The declarative style wins on the many boring cases —
required, length, format — which is most of them.

### JUnit 5, MockMvc and AssertJ

Automated checks that the application still behaves. There are 99 of them.

JUnit is the standard Java testing framework. Spring Boot Test can start the whole
application in memory so a test makes a genuine request to `/cart` and inspects
the response — via MockMvc, which drives the application without opening a real
network port. AssertJ supplies readable checks such as
`assertThat(cart).isEmpty()`. A companion library lets a test pretend to be a
logged-in administrator without performing a real login.

**Instead of it:** TestNG is the long-standing alternative; Spock offers a very
expressive Groovy syntax. For testing through a real browser rather than in
memory, Playwright or Selenium — far slower, and the only way to catch a
JavaScript failure.

### Spotless and palantir-java-format

Reformats all code to one identical style, enforced by the build. This ends every
argument about where the brackets go, and keeps change histories clean — a
reformatted file no longer shows up as a hundred modified lines hiding the one
real change.

**Instead of it:** google-java-format is the most widely used style; Checkstyle
reports violations rather than fixing them, which is more annoying and more
configurable. EditorConfig handles the basics across every editor and language.

### ESLint and Prettier

The same idea for the browser-side code. Prettier formats; ESLint catches likely
mistakes such as a variable that is declared and never used.

**Instead of it:** Biome replaces both with one much faster tool and is steadily
winning converts.

### Lombok

Generates the repetitive parts of Java classes at compile time.

Java traditionally expects a hand-written getter and setter for every field — for
a ten-field class, twenty near-identical methods that say nothing. Lombok
generates them from a one-line annotation. It is configured to vanish from the
final jar; it exists only while compiling.

**Instead of it:** Java records cover much of this natively for simple immutable
data and need no library. Letting the IDE generate the methods works too, and
leaves you maintaining them. Kotlin makes the problem disappear.

Lombok is mildly controversial: it works by modifying the compiler's view of your
code, which occasionally confuses tools.

### GitHub Actions

Runs the build, the formatter check and all 99 tests automatically on every push.

Called CI, for continuous integration. The value is that it is not optional and
not forgettable: a machine checks out the code on a clean computer and verifies
it, catching both real breakage and the classic "it works on my machine".

**Instead of it:** GitLab CI if the code lives on GitLab; Jenkins is the
self-hosted veteran, endlessly flexible and a job to maintain; CircleCI and
Buildkite are established commercial options. Actions wins here for one reason:
the code is already on GitHub, so it is one file and no account.

---

## What is deliberately absent

A stack is defined as much by what it leaves out. Prost has no message queue, no
caching layer, no search engine, no microservices, and no separate frontend
application. Each would be defensible on a larger system and each would be weight
without benefit here.

The clearest example is the frontend. Adding React or Vue would mean an API layer,
a second thing to deploy, and authentication solved twice over — in exchange for
interactivity that a catalogue and a checkout do not need. Choosing not to adopt a
tool is a design decision, and worth being able to explain.
