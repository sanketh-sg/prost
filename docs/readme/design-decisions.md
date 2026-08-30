# Design decisions made during the upgrade

Every entry is a choice that could reasonably have gone the other way. Each says
what was decided, what the alternative was, and why — because the reasoning is the
part that transfers, and the conclusion alone usually does not.

---

## How the upgrade was approached

### Write tests before touching anything

**Decided:** repair the broken test suite first, then add integration tests
describing how the application currently behaves, and only then start upgrading.

**Instead of:** upgrading first and fixing whatever broke.

An upgrade changes thousands of lines of framework code underneath you. Without
tests, there is no way to tell the difference between "the framework changed" and
"I broke something", and no way to know whether the application still works
except by clicking through it and hoping.

The tests written at this stage are *characterization* tests: they describe what
the code does today, not what it ought to do. That distinction matters. A few of
them recorded behaviour that was actually wrong — those were labelled as such and
left failing-by-description, so that fixing the bug later would visibly change
the test rather than silently pass.

### Upgrade in hops, not one jump

**Decided:** Gradle 7 to 8, then Spring Boot 2.7 to 3.0, then 3.0 to 3.5 — each a
separate step, verified before the next began.

**Instead of:** going straight from Boot 2.7 to 3.5.

When several versions change at once and something breaks, you have no way to
know which change caused it. Hop by hop, every failure has one obvious suspect.

This paid off immediately. The Gradle hop turned out to be far less risky than the
documentation implied — Boot 2.7's compatibility table says it stops at Gradle 7,
but the plugin runs fine on 8.5. Discovering that on its own, with nothing else in
flight, meant it could be verified rather than guessed at.

### Write down what actually broke, including where the plan was wrong

**Decided:** keep an upgrade playbook, and correct it against reality afterwards.

The predictions were wrong in instructive ways. The library flagged as highest
risk turned out to be fine. Meanwhile Thymeleaf 3.1 removed the ability for
templates to read the incoming request directly — a change the analysis had not
mentioned at all, and which caused 18 of the 24 failures.

The most valuable entry in the whole document ended up being a symptom nobody had
anticipated: *the tests are green but the application will not start*. Spring
Security 6 refuses an ambiguous URL rule when a second servlet is registered,
which the tests never exercised.

---

## Version and framework choices

### Stop at Java 17 rather than going to 21

**Decided:** stay on Java 17.

**Instead of:** upgrading to Java 21, the newer long-term-support release.

Partly practical — the machine had only Java 17 installed, and the build tool
could not fetch another version without an extra plugin. But it stands on its own
merits: Java 17 is supported until 2029, it is the minimum Spring Boot 3.5
requires, the goal was to reach a supported framework and that goal is met, and
the features Java 21 adds are ones this codebase does not use.

Recorded explicitly, because "we ran out of road" and "we chose to stop here" look
identical in the finished code.

### Accept Hibernate 6's new ID scheme instead of pinning the old one

**Decided:** leave the entities alone and let the new version generate database
identifiers its own way.

**Instead of:** adding an annotation to six entities to preserve the old
behaviour.

Hibernate 6 changed how it allocates identifiers: one shared counter became one
counter per table. On a database with existing data that difference can collide
with identifiers already in use — which is exactly why it looked alarming.

The decision rests on checking whether that actually applied here. It did not:
there was no stored data, no database volume, and the application had never been
deployed with real records. The alternative would have meant adding a deprecated,
vendor-specific annotation to six files to solve a problem this project does not
have.

The consequence was recorded alongside it: the initial database script must be
generated from the *new* Hibernate, never carried over from the old one.

### Let Flyway own the production database, not the framework

**Decided:** production applies numbered SQL migration files and refuses to start
if the database does not match the code. Development and tests keep rebuilding
their database from the code automatically.

**Instead of:** one approach everywhere.

The two environments genuinely want different things. In development you change a
field constantly and want the database to follow without ceremony. In production
you want no automatic changes at all, because "drop that column" is not a decision
to delegate.

**The cost is real and was accepted knowingly:** a change to the data model will
pass every test and then fail on production startup, because tests rebuild from
the code and never run the migrations. That gap is documented, and the tasks that
touch the database are deliberately batched so they can be checked against a real
database once, carefully.

---

## Architecture choices

### Keep rendering pages on the server

**Decided:** keep Thymeleaf. The server builds finished HTML and sends it.

**Instead of:** moving to React, Vue or similar, with this application reduced to
a data feed.

Prost is a catalogue, a basket and a checkout. Nearly every page is a fresh read
from the database, and there is no long-lived state in the browser worth managing.
Adding a browser framework would introduce a second application to build and
deploy, an API layer, and login handled twice — in exchange for interactivity this
application does not need.

Related, and in the same spirit: the page navigation was *moved back* to ordinary
links during the cleanup, replacing 58 lines of JavaScript. Page numbers can now
be bookmarked and opened in a new tab, which they could not before.

### Keep the basket in the session, not in the database

**Decided:** the basket lives in the visitor's session, stored in the database by
a library that handles it automatically.

**Instead of:** a basket table tied to a user account.

A basket is disposable. It has no history worth keeping and belongs to a browsing
session rather than to a person — and treating it that way means a visitor does
not need an account to start shopping. Storing sessions in the database rather
than in server memory means baskets survive a restart, and would survive running
more than one server.

**The trade:** the basket does not follow you to another device, and disappears
when the session ends.

### Lock the stock at checkout rather than re-reading it

**Decided:** when an order is placed, each product is locked until the transaction
finishes.

**Instead of:** simply checking stock again at checkout.

This is the subtle one. Checking again looks like it fixes the overselling bug,
and does not. Two customers checking out at the same instant can *both* read "two
in stock" before either has written anything, and both then pass their own check.
The check has to be combined with a lock that forces the two into a queue, so the
second sees the first one's result.

### Move order creation out of the controller — without fixing its bug at the same time

**Decided:** extract the ordering logic into its own service, carrying the
overselling bug across unchanged, and document it in place.

**Instead of:** fixing the bug during the move, which was tempting and would have
saved a step.

Mixing a behaviour change into a code move makes both impossible to review. If the
move breaks something, you cannot tell whether it was the move or the fix. So the
bug was carried over deliberately, with a comment saying so — so nobody would
mistake it for intended behaviour — and fixed in its own separate commit
afterwards.

One structural point was recorded with it: the transaction boundary moved from the
controller to the service, so the service now owns it. That is the whole reason
the extraction was worth doing.

### Keep two separate sets of security rules for development and production

**Decided:** development and production keep their own security configuration.

**Instead of:** one shared configuration for both.

They genuinely need to differ. Development runs a database console that requires
relaxing certain protections; production enforces HTTPS and stricter headers. That
part is deliberate.

What is *not* deliberate is that the access rules — who may reach which page — are
copied out by hand in both. They drifted apart once already, and the development
copy shipped without the rule protecting the admin area. Merging just that shared
part is planned and not yet done, which is honest rather than ideal.

---

## Things deliberately not done

Recorded so they are not silently reopened later.

### Replacing the test database with a real one

Running tests against genuine PostgreSQL in a container would remove the
development-versus-production mismatch entirely. **Rejected for now:** the whole
test suite is built around the in-memory database, so this is a test
infrastructure rewrite rather than a swap, and the specific problem it was
proposed to solve has a much cheaper fix.

### Wrapping the user account in an adapter for the security system

A common recommendation is to keep the stored user separate from the object the
security system sees. **Rejected:** here it is strictly more code for no present
benefit. Worth revisiting the day accounts need suspending.

### Replacing the message system with the framework's built-in one

The framework has a standard way of carrying a message across a redirect.
**Rejected:** 27 places would need changing, and one of them is inside the login
failure handler where the framework's mechanism is not available. The cost exceeds
the benefit.

### Deciding whether a crate is stock or a container

If ten crates of a beer are in stock, does buying a crate reduce the bottle count
too? The code currently treats bottles and crates as entirely separate stock.
**Not decided, and deliberately not decided by a developer** — this is a question
about how the business works, not about how the code should be written.

---

## Choices made while fixing the issues

### Keep the "clear database" action rather than delete it

The most dangerous thing in the codebase was an admin action that wipes every
user, product and crate — reachable by clicking an ordinary link.

**Decided:** keep the feature, but require a proper form submission with the
anti-forgery token, like the others.

**Instead of:** deleting it outright, which would certainly have removed the risk.

The task at hand was about *how actions are triggered*, not about which features
the admin panel should have. Removing a working tool is a product decision, and
belongs to whoever owns the product.

### Prefer no new dependency, when a flag will do

The plan called for adding a small helper library so the frontend build could be
told to produce a production bundle. **Decided instead** to use a flag the
bundling tool already provides — no new dependency, and it works on Windows, where
the originally suggested approach does not.

### Let the automated build do the frontend build too

Wiring the frontend build into the packaging step meant every build — including
the automated one on every push — now needs the JavaScript toolchain installed.

**Decided:** install it there, accepting slower builds.

**Instead of:** a flag to skip the frontend step automatically.

Skipping would have kept builds fast, at the price of never checking that the
frontend build still works. Since the packaging step had, until then, been
producing an application with no styling at all and reporting no error, the whole
point was to have something notice.

### Turn manual checks into automated ones

Several fixes came with an instruction to "open the page and confirm it looks
right". Wherever possible those became tests instead — that the disabled page
links carry the class that makes them inert, that the rendered forms actually
contain their security token.

The reason is specific rather than general: every other test supplies that token
directly, bypassing the form. A page that rendered without one would have left the
entire suite green while every button failed in a real browser.

### Write the bugs down instead of quietly fixing them

**Decided:** keep a written record of the known defects, including the ones still
unfixed, and including one introduced during this work.

**Instead of:** fixing what was convenient and letting the rest go unmentioned.

A list of what is wrong is more useful than a claim that nothing is. It also
resists a specific failure: a defect that nobody wrote down is rediscovered from
scratch, usually by a user.
