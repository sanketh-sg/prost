# The Spring Boot upgrade, explained simply

`upgrading-spring-boot.md` is the full technical reference — exact commands,
exact grep patterns, exact decision log. This is a companion to it: the four
hardest gotchas from that document, explained with an everyday comparison and
a concrete before/after, for anyone who wants to understand *why* each one
happened before diving into the reference itself.

Each section names which part of the full playbook it maps to, in case you
want the precise version afterwards.

---

## 1. The ticket-machine problem (database IDs)

**Maps to:** §2.2, "Hibernate 6 changes ID generation."

### The everyday version

Imagine a bakery with one ticket machine at the door: everyone entering pulls
the next number, 1, 2, 3, 4, regardless of which counter — bread, cakes,
coffee — they're actually queuing for. Now imagine the bakery expands and puts
a *separate* ticket machine at each counter. If you don't reset them properly,
the new bread-counter machine might hand out ticket #3 to someone, at the
exact moment the old shared log already has a ticket #3 sold five minutes ago
for cake. Two different people are now holding "ticket 3," and whoever the
system asks to trust that number gets confused.

That's what changed under the hood: the old version of Hibernate (the library
that turns Java objects into database rows) handed out new row IDs from **one
shared counter** for every kind of record. The new version gives **each kind
of record its own counter**. If a database already has real rows in it, the
new counters can start from 1 and immediately collide with an ID that's
already taken — a genuine "two people holding ticket 3" bug, and it corrupts
data.

### The example

```
Old behaviour (Hibernate 5):
Hibernate: create sequence hibernate_sequence start with 1 increment by 1
→ one shared machine: address #1, order #2, beverage #3, order #4, ...

New behaviour (Hibernate 6):
→ separate machines: address_seq, orders_seq, beverage_seq, each starting at 1
```

Picture a database that already has orders #1 through #40 saved from before
the upgrade. The new `orders_seq` machine, freshly installed and set to start
at 1, would hand out order #1 to the very next real order — except order #1
already exists. That's the collision.

### What was actually done here

Checked whether the bakery actually had any customers yet — i.e., whether
there was any real data in the database. There wasn't (no Docker container had
ever been started, no real deployment had ever run). So the fix wasn't "add
complexity to preserve the old single-machine behaviour forever" — it was
"accept the new per-counter machines, and make sure Flyway (the tool that sets
up the database schema) creates them correctly from day one." The generalisable
lesson: check whether the scary scenario in the changelog actually applies to
you before you pay the cost of working around it.

---

## 2. The robot that refuses to guess (Spring Security startup crash)

**Maps to:** §2.3, "The `requestMatchers(String)` trap."

### The everyday version

Imagine a mail-sorting robot at a building with one mailbox. You hand it an
envelope addressed to "Reception" and it drops it straight in — there's only
one place it could possibly go, so there's nothing to guess. Now imagine the
building adds a second, differently-shaped mailbox for parcels. Hand the robot
the same envelope, and a careful robot says: *"I now have two mailboxes and
your label doesn't specify which kind. I'd rather stop and ask than guess
wrong and deliver your letter into the parcel box."*

That's exactly the crash this project hit. The security rules were written as
plain text patterns like `"/admin/**"`. As long as there was only one thing in
the app capable of receiving a web request, Spring Security could confidently
figure out what that pattern meant. The moment a *second* thing showed up — the
development profile's built-in database console, running alongside the main
app — Security refused to guess and crashed the whole application on startup.

### The example

```
IllegalArgumentException: This method cannot decide whether these patterns
are Spring MVC patterns or not... there is more than one mappable servlet
in your servlet context:
{DispatcherServlet=[/], JakartaWebServlet=[/h2-console/*]}
```

The fix is to stop making the robot guess — hand it an already-labelled
envelope instead of a plain one:

```java
// Before: a plain string, ambiguous once there are two mailboxes
req.requestMatchers("/admin/**").hasRole("ADMIN");

// After: explicitly says which kind of pattern this is
req.requestMatchers(antMatcher("/admin/**")).hasRole("ADMIN");
```

### Why the test suite didn't catch it

The tool used to simulate web requests in tests never actually sets up that
second mailbox (the database console), so from a test's point of view there
was only ever one mailbox and nothing to be ambiguous about. **All 51 tests
passed, and the real application still couldn't start.** That's the single
most important finding in the whole upgrade: a green test suite proves the
code behaves correctly *inside the test's simplified world*, not that the real
application boots.

---

## 3. The light switch wired to nothing (the silent session bug)

**Maps to:** §2.4, "Spring Session 3 — a schema change underneath the cart."

### The everyday version

Imagine a light switch on the wall that turns off a machine in the next room —
except one day, someone renovates and reroutes the wiring, and forgets to
reconnect that particular switch to anything. The switch still clicks. It
still looks exactly like it always did. But it is now connected to nothing,
and nobody told you. You'd only find out when the machine you thought was off
turns out to be running.

That's what happened to a test setting. A configuration value used to
switch off part of the session-handling machinery during tests (so the
test tool's session helpers could work correctly). The new Spring Boot
version **removed that setting from its list of recognised properties** — and
critically, an unrecognised setting doesn't cause an error, it's just silently
ignored. The switch kept existing in the config file. It just stopped being
connected to anything.

### The example

```
Before, in a config file:
spring.session.store-type=none      # "flip the switch off"

After the upgrade:
spring.session.store-type=none      # still there, now connected to nothing —
                                     # no error, no warning, the machine turns
                                     # back on and nobody is told
```

The result: six shopping-cart test failures that had nothing to do with the
cart itself — the session machinery had silently turned back on underneath
them.

### The fix, and why it's better than the original

```java
@TestPropertySource(properties =
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.session.SessionAutoConfiguration")
```

Instead of a setting that fails silently when wrong, this names the exact
piece of machinery to switch off. If that class name is ever wrong or
disappears in a future version, this throws an error at startup instead of
quietly doing nothing. The lesson: given the choice between a switch that can
be silently disconnected and one that refuses to work at all when disconnected,
pick the one that refuses.

---

## 4. The waiter who's no longer allowed in the kitchen (Thymeleaf templates)

**Maps to:** §2.9, "Thymeleaf 3.1 removes the request and session expression objects."

### The everyday version

Imagine a restaurant where, up to now, any waiter could walk straight into the
kitchen and grab whatever ingredient they wanted off the counter to finish a
dish at the table. It's convenient, but it means the kitchen has no control
over what leaves it, and waiters end up depending on exactly how the kitchen
happens to be arranged today. The restaurant changes its policy: waiters may
no longer enter the kitchen. If they need something, the chef must hand it to
them at the pass, already prepared.

That's what Thymeleaf 3.1 (the templating engine that builds the HTML pages)
did. Templates used to be able to reach directly into the raw web request —
"give me the current page's web address" — right there in the HTML file. The
new version removes that ability entirely, on the reasoning that a page
template reaching into the raw network request is a layering violation: that's
the controller's job, not the template's.

### The example

```
Before, inside a template:
${#httpServletRequest.getRequestURI()}     ← waiter walks into the kitchen

IllegalArgumentException: The 'request','session','servletContext' and
'response' expression utility objects are no longer available by default
for template expressions...

After — the controller hands it over instead:
@ModelAttribute("currentUri")
public String currentUri(HttpServletRequest request) {
    return request.getRequestURI();        ← chef prepares it at the pass
}

// and in the template:
${currentUri}                              ← waiter just takes the plate
```

### Why this one looked much scarier than it was

The piece of template that used this trick lives inside the navigation bar —
which appears on *every single page*. So when it broke, every page-rendering
test failed at once: 18 failures out of 24, all from one cause. Looked at
quickly, that reads as "the entire rendering system is broken." It wasn't —
it was one specific technique, used in nine places, that Thymeleaf now
forbids. Fixing it once, properly, fixed all 18.

**A bonus catch.** Two of those templates built a page's URL by joining the
path and the query string with a `?`, unconditionally — so a page with no
query string rendered a literal, visible `?null` in a link. Writing the
replacement once, correctly, made that old bug disappear along with the
Thymeleaf breakage. Nobody set out to fix it; it just couldn't survive being
rewritten properly.

---

## The one idea all four of these share

In every case, nothing about *this project's own code* was wrong. A piece of
code that hadn't changed at all had the ground shift underneath it — a
counter, a matcher, a config property, a template API — and the failure showed
up somewhere that made it look like a much bigger problem than it was. That's
the whole reason the full playbook insists on one change per commit with a
green test suite in between: when something breaks, you want exactly one
thing to have moved.
