# Bugs and issues found before the upgrade

Prost was a working university group project. It ran, it sold beer, and it was
submitted. Everything below is what turned up when the code was picked up again
and modernised — some of it serious, some of it only untidy.

Each entry says what was wrong, why it mattered, and what happened to it.

---

## Security

### Passwords and keys were committed to the repository

Two credential files were checked into Git: a Google Cloud service-account key
and an email-service credential file. Alongside them sat 113 compiled build
artifacts that should never have been tracked, because the ignore rule meant to
exclude them (`./build`) was written in a form Git does not honour.

**Why it matters:** anyone with access to the repository — including anyone it is
ever shared with, and anyone who clones it later — has those keys. Deleting the
file is not enough, because Git keeps history.

**What happened:** both credentials were revoked, the files removed, the ignore
rule corrected, and the history purged.

### The admin pages were unprotected in development

The application defines two sets of security rules: one for developers' machines,
one for production. Production correctly required an administrator role for
`/admin`. Development did not — the rule was simply missing from that copy, and
the two methods had been named in a way that made the mistake invisible.

**Why it matters:** any logged-in user running the app locally could reach every
admin page, including the ones that create and delete products.

**What happened:** fixed early in the upgrade (`e9969c6`). The deeper problem —
that the two rule sets are duplicated by hand and can drift apart again — is still
open, see below.

### An attacker could redirect users off the site

Several pages accepted a `next` parameter saying where to go after the action, and
pasted it straight into the redirect. A link like
`/cart/add?beverageId=1&next=https://evil.example` came from the real Prost
domain, but landed the visitor on someone else's site.

**Why it matters:** it is a phishing tool. The victim trusts the link because it
genuinely starts at your site, then arrives at a convincing fake login page.

**What happened:** fixed. Redirect targets are now checked, and anything that
could leave the site falls back to the home page. Eight places were affected.

### Admin actions ran on a plain link, chosen by name

The admin panel worked by taking a method name out of the URL and calling
whatever matched. The buttons were ordinary links, so the actions ran on a
simple page visit.

**Why it matters:** two reasons. First, anything that follows links — a browser
prefetching, a chat app generating a preview, a crawler — could trigger them.
Second, actions triggered this way skip the protection that stops another website
submitting requests on your behalf. One of the available actions deletes every
user, bottle and crate in the database.

**What happened:** fixed. The five actions are now explicit, require a form
submission rather than a link, and carry the anti-forgery token.

### Registration accepted an empty password

The form checked that the password was not empty — but it ran that check *after*
scrambling the password for storage. The scrambled version is never empty, so the
check always passed.

**Why it matters:** an account could be created with no password at all. The
browser's own "required" marking hid this in normal use; anything submitting the
form directly bypassed it.

**What happened:** fixed. Validation now runs on what the user typed, before
anything is hashed or saved. Confirmed by a test that failed before the fix.

### Test-only code shipped to production

A library used only for writing tests was declared as a normal dependency, so it
was packaged into the deployable application.

**Why it matters:** it puts test scaffolding — including helpers for faking a
logged-in user — inside the running production app. Harmless in the best case,
and not something you want present.

**What happened:** fixed (`dffc81f`).

---

## Getting the numbers wrong

### The shop could sell stock it did not have

Stock was checked when you added something to the basket, but never again at
checkout. Two separate additions could each be individually valid and add up to
more than existed.

Worse, the code that reduced the stock afterwards clamped the result at zero. So
selling twelve of the ten remaining crates left the stock reading zero rather than
minus two — the evidence of the problem was destroyed by the same line that caused
it.

**Why it matters:** you take money for goods you cannot ship, and nothing in the
data shows that it happened.

**What happened:** fixed (`c0adf7d`). Every line of the order is now locked and
checked before anything is written. A simple re-check would not have been enough:
two customers checking out at the same instant can both read "two in stock" before
either writes, and both pass. The lock forces them into a queue.

### Prices are stored as approximate numbers

Money is held in a type designed for scientific measurement, not currency. It
cannot represent most decimal amounts exactly: in this type, `0.1 + 0.2` does not
equal `0.3`. Every basket total is a sum of these.

**Why it matters:** totals drift by fractions of a cent, and the more items in the
basket the more they drift. This is the classic money bug, and the reason every
finance system uses an exact decimal type.

There is a second bug in the same place: a rule requiring a minimum price of 1
silently forbids selling anything under €1.

**What happened:** fixed. Every price field (bottles, crates, orders, order
lines) is now an exact decimal type instead of a floating-point one, backed by a
`numeric(10, 2)` column. The minimum-price rule was corrected at the same time —
it now blocks a price of zero rather than anything under a whole euro.

### The basket's lookup table did not work as a lookup table

Products were used as keys in a fast-lookup structure, which works by grouping
items into buckets by a numeric fingerprint. Every product returned the *same*
fingerprint, so they all landed in one bucket and every lookup had to check them
one by one.

**Why it matters:** it was correct only by luck — each page happens to load each
product exactly once. Load the same product twice and the basket would have
misbehaved.

**What happened:** fixed. Products are now fingerprinted by their database
identity.

### An order of one crate wrote 24 rows

Buying a crate of 24 bottles records 24 identical rows in the orders table, one
per bottle, instead of one row saying "24".

**Why it matters:** the orders table grows far faster than it should, and any
future work on order history has to cope with the duplication.

**What happened:** fixed. Order lines now carry a quantity column, so one line
in the basket becomes one row, however many units it represents.

### An off-by-one in the page navigation

The project contained a hand-written 106-line replacement for a paging feature the
framework already provides in one line — and the replacement had an error in its
"is there a previous page?" logic.

**What happened:** fixed by deleting all 106 lines and using the built-in version.

---

## Things that made the project hard to work on

### The tests could not run at all

Every test failed immediately with a missing database driver. The test database
had been declared in a way that made it available to the running application but
not to the tests. A second test then failed on its own terms, because it assumed
products with particular identifiers already existed.

**Why it matters:** a test suite that does not run is worse than none, because it
looks like safety and provides none.

**What happened:** fixed first (`7a6f0d9`), before anything else was touched.
Everything after that had a working safety net.

### Nothing ran the tests automatically

Even once the tests worked, they only ran when a human remembered.

**What happened:** fixed. Every push now builds the project, checks formatting and
runs all 99 tests on a clean machine.

### The admin panel failed silently

When an admin action went wrong, the page simply reloaded with no message and
nothing in the logs. A mistyped action looked exactly like success.

**Why it matters:** this genuinely wasted time — the first attempt to load sample
data in this project appeared to work and inserted nothing, and the only way to
find out why was to read the source code.

**What happened:** fixed (`5d0dee2`). Every failure now shows a message and writes
a log entry.

### The packaged application contained no styling

Building the deployable file never ran the step that produces the CSS and
JavaScript. It picked up whatever a developer happened to have generated by hand
earlier.

**Why it matters:** a fresh copy of the project, built on a clean machine, would
produce an application with no styling at all — and nothing would report an error.

**What happened:** fixed. The build now produces the frontend as part of packaging.

### The frontend shipped in development mode

The bundling tool had its development setting hardcoded, so the files sent to
users were the large, uncompressed versions meant for debugging.

**What happened:** fixed. The main bundle went from 10.1 KB to 1.5 KB.

### Production let the framework rearrange the database by itself

The live configuration allowed the data layer to alter the database structure
automatically to match the code.

**Why it matters:** "automatically drop that column" is not a decision you want
made on your behalf against real customer data.

**What happened:** fixed (`168dde9`). Production now applies numbered, reviewable
migration files, and refuses to start if the database does not match what the code
expects.

---

## Unnecessary complexity

None of these were broken. They were simply more code than the job required, and
every extra line is one more thing to read, test and get wrong.

- **A 106-line custom paging class** replaced by one line of built-in
  functionality. *Fixed.*
- **58 lines of JavaScript** doing what an ordinary link does — and making the
  page numbers impossible to bookmark or open in a new tab in the process.
  *Fixed.*
- **A permissions system that granted nothing.** There is an elaborate
  role-and-privilege structure, but the code that populates it always fills it
  with an empty list, so it grants no permissions to anyone. Several of its
  methods are never called at all. *Fixed by deletion*: nothing anywhere in the
  application ever checked a privilege, only a role, so populating it would have
  changed no behaviour — the honest fix was to remove the unused structure
  (`Privilege` entity, the join table, the dead-code methods), not build out a
  feature nobody consumed.
- **Reflection-based admin actions** — 78 lines to look up methods by name, when
  five ordinary methods would do. *Fixed, along with the security problem it
  caused.*
- **Four dependencies that nothing used**, including a whole third-party library
  doing the work of a single line of CSS. *Fixed.*
- **Dead subsystems** — cloud functions, an email sender, and a statistics module
  — left behind but no longer part of the application. *Removed.*

---

## What is still open

The four things that were known, documented and deliberately left for later have
all since been fixed:

| Issue | What was done |
|---|---|
| Money stored as an approximate number | Prices are now an exact decimal type (`numeric(10, 2)`), and the minimum-price rule now blocks €0 instead of anything under €1 |
| One order row per bottle instead of a quantity | Order lines now carry a quantity column |
| The permissions system that grants nothing | Deleted — nothing in the app ever checked a privilege, only a role |
| The two sets of security rules are duplicated | Extracted into one shared method both profiles call, so they cannot drift apart silently again |

The first two required altering the live database, which is the one kind of
change the test suite cannot verify on its own: development and tests rebuild
their database from scratch each time, so a faulty migration only reveals itself
when production starts up. There is no production data yet, so the schema file
was edited in place rather than layered with a new migration.

There is also one cosmetic defect, introduced during this work and knowingly left:
the "passwords didn't match" message displays without its apostrophe, because the
validation system treats that character as punctuation of its own.

---

## The short version

The serious problems were **credentials in the repository**, **an admin area
unprotected in development**, **a shop that could sell stock it did not have**,
**destructive admin actions reachable by an ordinary link**, and **a registration
form that accepted empty passwords**.

The most instructive one is the overselling bug, because it was not a missing
check — it was a check in the wrong place, combined with a line of code that
quietly erased the evidence.

The most easily missed one is the test suite, which could not run at all. Fixing
that first is what made everything after it safe to attempt.
