# Interview Q&A — security, cart, pagination

You designed and built these three features. This document rehearses the questions an
interviewer is likely to ask about each, with answers grounded in what the code and the
design docs actually say — including the parts where the honest answer is "I got that
wrong, and here's why."

Source material: `docs/prost-day1-security-design.md`, `docs/prost-day1-cart-design.md`,
`docs/prost-interview-story.md`, and the current code on `main`.

---

## How to use this

Three rules that make the difference between reciting an answer and actually holding your
own:

1. **Lead with the decision, then the reasoning, then the trade-off you accepted.** Not
   "I used Spring Security" — "I chose default-open authorization for this route table,
   because it's a public shop and the alternative means maintaining an allowlist where one
   omission breaks the storefront."
2. **Own the bugs.** Two real bugs shipped in this project's cart and security code, and
   both are described below with what caused them and how they were found. Naming them
   unprompted is a stronger position than hoping they don't come up — it demonstrates you
   understand the code well enough to find its own flaws.
3. **If you don't know, say what you'd check.** Several answers below end with "the way to
   verify that would be..." — that is a legitimate answer in an interview, and better than
   guessing confidently.

---

## Security

### "Walk me through how a request gets authorized."

Two filter chains, one for each environment, because the two environments have genuinely
different needs — the development one runs a database console that needs certain
protections relaxed, and the production one enforces HTTPS. Both chains hold the same
route table, an actor-by-resource matrix, evaluated top to bottom with first match
winning:

| Area | Anonymous | Customer | Admin |
|---|---|---|---|
| public routes | allowed | allowed | allowed |
| `/cart/**` | denied | allowed | allowed |
| `/orders/**` | denied | own orders only | own orders only |
| `/user/**` | denied | allowed | allowed |
| `/admin/**` | denied | denied | allowed |

Specific rules are listed first; the fallback rule for everything not explicitly matched
comes last. Order matters mechanically — put the fallback first and every rule below it is
dead code that still looks correct on review.

### "What happens to a route nobody wrote a rule for?"

This is the question to be ready to defend, because it's the single most consequential
line in the whole design: the *default polarity*. Two choices, and they fail in opposite
directions.

- **Default-deny** (require login unless told otherwise): a forgotten route 403s. Fails
  closed — annoying, but safe and visible immediately.
- **Default-open** (allow unless told otherwise): a forgotten route is silently public.
  Fails open.

Security orthodoxy says always default-deny. **I chose default-open, and I'd defend it
here**: this is a public shop, the catalogue *is* the product, and default-deny would mean
maintaining a long allowlist where a single missed entry breaks the storefront for real
customers. For internal tooling, or anything holding money at rest, I would invert that
without hesitation.

What makes it a decision rather than an accident: the risk is written down next to it. A
new sensitive route that doesn't follow the `/admin/**` or `/cart/**` prefix convention is
silently public under this policy. That's exactly why a naming convention for sensitive
routes matters as much as the polarity choice — **the two decisions are load-bearing for
each other**, and that's the part worth an interviewer hearing, because it shows a system
rather than a pile of individual settings.

### "How does your `User` entity plug into Spring Security?"

Spring Security wants a `UserDetails` implementation. Two ways to provide one:

- **A — `User implements UserDetails` directly.** Zero mapping code; the security
  principal *is* the domain object.
- **B — a separate adapter class wrapping `User`.** One more class and one more mapping,
  but the entity stays free of framework-specific interface methods.

I chose A. For a small project the adapter earns nothing on day one, and the alternative
collapses the login-lookup service to four lines. The cost is real and I'd name it rather
than hide it: four methods — `isEnabled()`, `isAccountNonLocked()`, and two more —
hardcode `true`, because no such columns exist on the entity. The class advertises account
suspension as a capability it doesn't actually have. The day someone asks for that
feature, option B becomes the right call, and I'd make that trade again knowing that.

### "What's `ROLE_ADMIN` versus `hasRole("ADMIN")`? Why does that matter here?"

This is a classic gotcha, and it bit this exact project. `hasRole("ADMIN")` checks for the
authority string `ROLE_ADMIN` — Spring silently prepends the prefix. Roles here are stored
as free-text strings in a seed data file, so the seed has to spell out
`"role": "ROLE_ADMIN"` literally. There's no compile-time check and no startup error if
you get it wrong; the rule simply never matches, silently, and an admin account behaves
like it has no role at all.

### "Tell me about a bug you shipped in this code."

**The development admin panel had no authorization check for four years.** The production
security chain correctly required the admin role for `/admin/**`. The development chain
was missing that rule entirely — not disabled, just never written — so any logged-in user
running the app locally could reach every admin page, including the ones that create and
delete products. It went unnoticed because production, the environment anyone would
actually attack, was correct the whole time; it only surfaced when the two configurations
were compared side by side during the upgrade.

The fix was one line. The lesson generalises: **two copies of a security rule set will
drift, and only one of them needs to drift wrong.** That's still true today — both chains
currently match, by hand, and there's an open task to make one shared method the only
place the rule can be written, so there's structurally one copy to get wrong instead of
two.

### "Are there any known, unfixed security issues in this code right now?"

Yes, and naming it unprompted is deliberate — say so if asked why you're volunteering it.
**There's an open redirect.** Several endpoints accept a `next` parameter saying where to
send the user afterward, and it used to be concatenated straight into the redirect target:

```java
return "redirect:" + next.orElse("/");
```

`POST /cart/add?beverageId=1&next=https://evil.example` sends a logged-in user off-site —
a phishing primitive, because the link genuinely originates from the real domain right up
until the redirect fires. It appeared in eight places across two controllers.

**Status:** this one is actually fixed now — a shared guard rejects anything that isn't a
same-site relative path, including the sneaky bypasses (`//evil.example`,
`/\evil.example`, embedded control characters that a browser would otherwise normalise
into a scheme-relative URL). But be ready to talk about *why it was left open for a
while first*: recording a finding and fixing it later, deliberately, beats a half-patched
fix that's harder to reason about than a fully documented open one. It's also a stronger
interview position than a clean repository that prompts no questions — "here's a
vulnerability I found in my own code, here's the bug class, here's the fix" demonstrates
more than a file with nothing to discuss.

### "What's the deeper pattern connecting that bug to anything else?"

User input concatenated directly into a decision the code then trusts. Same shape as SQL
injection; same shape as a price passed in from the client instead of looked up
server-side. I applied that principle carefully to cart contents and missed it on a
redirect target three lines away in the same file. Worth having that self-aware framing
ready — it shows the bug wasn't a knowledge gap, it was an inconsistency in applying
something I already knew.

### "How do you stop one user from viewing another user's order by guessing the ID?"

Three options were on the table: fetch by ID and compare the owner in an `if`; a
declarative `@PostAuthorize` check; or put the ownership check *inside the query itself* —
`findByUser_usernameAndId(principal.getName(), id)`. I chose the third. Someone else's
order ID returns nothing, and the page says "this order does not exist" — not "you don't
own this," which would leak that the ID is valid and invite enumeration.

The reasoning matters more than the choice: an `if` after a broad `findById` is one
careless refactor away from reintroducing the vulnerability, because nothing forces you to
remember it's there. Baking the check into the method signature means the insecure version
simply isn't expressible — you cannot call that method without supplying the owner.

---

## Cart

### "Where does the cart live, and why there?"

In the HTTP session, persisted to the database by a library rather than kept only in
server memory. The reasoning: a cart is disposable, it has no history worth keeping, and
treating it as session state rather than account state means a visitor can start shopping
without registering. Storing sessions in the database rather than memory means a cart
survives an application restart, and would survive running more than one server instance.

The honest trade-off: it doesn't follow you to another device, and it's gone when the
session ends. That's an acceptable trade for a shop; it would not be for, say, a
long-running project a user might return to after weeks.

### "What's actually stored — full product objects, or references?"

References: a beverage ID and a quantity. The cart is rebuilt from the database on every
read. The alternative — storing a snapshot of the product, including its price, at the
moment it's added — was considered and rejected: it would mean the displayed price could
silently diverge from the real price, and reconciling that at checkout adds a whole class
of problem for no real benefit at this scale. Price is looked up fresh at read time and
fixed only at the moment of purchase.

### "Tell me about the concurrency bug. Walk me through it like I've never heard of it."

This is the one to know cold — it's the best story in the whole project.

The stock check happened when you added an item to the cart. The stock *decrement*
happened later, at checkout, roughly fifty lines away in the same method. Nothing
re-verified stock in between those two moments. Two ways that breaks:

1. **Time.** You add 5 units while 5 are in stock. Someone else buys 3 in the meantime.
   You check out. You just bought 5 units of something that had 2 left.
2. **Concurrency.** Two customers check out at the exact same instant. Both read "2 in
   stock" before either has written anything. Both pass their own check. Both decrement.
   At the database's default isolation level, neither transaction can see the other's
   write until it commits — so both succeed, and the shop just oversold.

And then the part that turns a bug into a bug nobody notices: the line that wrote the new
stock value was `bev.setInStock(Math.max(reduced, 0))`. Stock should have gone to -3. The
clamp wrote 0 instead. **The symptom was erased while the cause kept happening.** The
database never showed a negative number, so nothing ever looked wrong, so nobody
investigated. That clamp was almost certainly added by someone who saw an alarming
negative number during testing and "fixed" the number instead of the check.

### "What's the general lesson from that clamp?"

**A defensive clamp on a value that should be impossible to go negative is not a safety
measure — it's a silencer.** If a number genuinely cannot legitimately go negative, let it
go negative and let it be loud about it, because that's a signal something upstream is
broken. Only clamp where the clamp itself is the correct business rule, never as a way to
make an alarming number quietly disappear.

### "How did you actually fix it?"

Move the check and the write into the same transaction, lock every line item *before*
validating any of them, and delete the clamp entirely. Concretely: every beverage line in
the checkout is loaded with a write lock (`SELECT ... FOR UPDATE`) before any quantity is
checked against stock. If any line fails, the whole order is rejected — nothing partial
gets written. Holding the locks until commit is what actually closes the concurrency hole;
a bare re-check without the lock would still have the exact same race, just with smaller
odds.

Be ready for the natural follow-up — **why pessimistic locking and not optimistic
(version-column, retry-on-conflict)?** Optimistic locking is usually the better default
under low contention, because it doesn't block anyone. But checkout here is a short
transaction handling something of real value, and optimistic locking would mean writing
and testing a retry loop for a path where getting the retry logic wrong is expensive. The
general rule I'd offer an interviewer: reach for optimistic locking when conflicts are
rare and a retry is cheap; reach for pessimistic when the cost of getting a conflict wrong
is high.

### "Why did that bug survive at all? What let it hide?"

Structural reason, not bad luck: checkout was around fifty lines written inline inside the
controller method, reachable only via an HTTP POST. There was no way to write a
concurrency test against it without spinning up a full mock web server and threading
simultaneous requests through it — so nobody did.

The actual fix extracted that logic into its own service class first, as a separate step
*before* touching the bug. Not because layering is architecturally tidy — because the bug
wasn't *expressible as a test* until there was a clean seam to call directly. Once
`placeOrder(user, cart)` was a plain method you could call from a test with no HTTP
involved, the failing test was about three lines long.

**The line to remember:** the refactor didn't fix the bug. It made the bug sayable. That's
a stronger argument for extracting services out of fat controllers than any appeal to
clean architecture on its own — it's about what becomes testable, not what looks tidier.

### "What kind of bug can your test strategy never catch, structurally?"

Concurrency bugs are the honest answer, and this project is the example. You can click
"add to cart" by hand a hundred times and never once get two transactions to interleave —
it isn't a diligence failure, it's a category of defect invisible to manual testing and to
any test that doesn't deliberately force two things to happen at once. The actual skill is
recognising *which* bug classes your chosen verification method structurally cannot see —
concurrency is one, and anything hidden behind a clamp or a silently-swallowed exception is
another.

---

## Pagination

### "Walk me through how pagination works end to end."

`PageRequest.of(currentPage, 9)` — nine items per page, using the framework's built-in
paging type. The controller reads a `page` query parameter, defaulting to zero and
rejecting negative values, and passes that request object straight to the repository,
which returns exactly the slice of results for that page plus the total count. The
template then renders First / Previous / Next / Last as plain links, each pointing at the
current path with an updated `page` parameter.

### "Was there ever a custom pagination implementation? What happened to it?"

Yes — a 106-line hand-written class implementing Spring's paging interface, doing
precisely what `PageRequest.of(page, 9)` does in one line, and it had never been noticed as
redundant. It also had a genuine bug: an off-by-one error in its "is there a previous
page?" logic. It was deleted entirely and replaced with the framework's own type.

The useful part of this story for an interview isn't the deletion — it's **how the
replacement was verified**. The existing test for pagination only asserted that
`?page=0` returns a 200 status code. That passes just as happily whether the paging
arithmetic is right or wrong; it would not have caught a bad swap like
`PageRequest.of(currentPage * 9, 9)`, which looks plausible and is completely wrong. So
before making the swap, I wrote a real regression test — one that asserts page 1 actually
holds the *second* nine items, not just that the endpoint returns something — confirmed it
passed against the *old* code first, made the swap, and then deliberately broke the new
code on purpose to confirm the test would actually catch it. It did.

That's the answer if asked "how do you know your refactor didn't break anything" —
returning a status code is not the same claim as returning the right data, and a test
suite that only checks the former will wave through a real regression.

### "Why real links instead of JavaScript?"

There used to be 58 lines of JavaScript that set a hidden form field and submitted a GET
form to change pages — reimplementing what a plain `<a href>` already does for free. The
practical cost of that approach: the page number couldn't be bookmarked, couldn't be
opened in a new tab with a middle-click, and stopped working entirely if JavaScript failed
to load. None of that is a hypothetical edge case for a shop — those are exactly the
behaviours a real shopper expects from pagination without thinking about it.

The fix generates ordinary anchor tags with the page number baked into the URL as a query
parameter. No JavaScript is involved in navigating between pages at all.

### "How do the disabled 'first'/'previous' controls actually work, and how would you verify they're really disabled?"

They're still real `<a>` tags — a `disabled` CSS class is applied conditionally, and that
class sets `pointer-events: none`, which is what actually makes the link inert; without
that specific rule, a "disabled" link with an `href` is still a fully clickable link to
page zero. That's a subtle enough failure mode that it's worth testing directly rather
than trusting a visual check: there's a test that asserts the class is present on the
backward controls specifically on page one, and a separate test that asserts the forward
controls are *not* disabled at the same time — checking both directions catches a rule
that's backwards as easily as one that's missing.

### "Any tricky details in how the page links preserve filters?"

Yes — the bottles page can be filtered by alcohol content via a query parameter, and that
filter has to survive clicking through pages, or a filtered view resets itself the moment
you paginate. The link-building logic reads any active filter from the current request and
threads it back into every generated page link. One detail worth being precise about if
asked: the parameter is appended conditionally rather than always, because appending it
unconditionally when no filter is active would render a dangling, empty parameter in every
link — small, but the kind of detail that separates "it looks right" from "it is right."

---

## The 30-second version, if asked to summarize the whole project

Six-person Spring Boot project, submitted, then picked up four years later and modernized
across a two-major-version framework upgrade. Zero tests before; the test suite written to
make that upgrade safe found two real bugs already shipped — an authorization rule missing
in development, and a stock check that could be defeated by two customers checking out at
the same moment, with a clamp quietly hiding the evidence.

If there's time for one more line: **the tests written to make the upgrade safe are what
found the bugs — the upgrade wasn't the risk, the absence of tests up to that point was.**
