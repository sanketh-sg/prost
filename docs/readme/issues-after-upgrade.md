# Bugs and issues found while upgrading

`docs/issues-before-upgrade.md` covers defects that were already in the code
before anyone touched it. This document is different: it covers things that
**broke because of the upgrade itself** — new framework versions changing
behaviour underneath code that had not changed at all.

None of these were bugs in the original project. They were new problems, caused
by moving Spring Boot from 2.7 to 3.5, that had to be found and fixed before the
upgrade could be called finished.

---

## The one that mattered most: the test suite lied

**What happened:** all 51 tests passed. The application would not start.

Spring Security 6 refuses to guess what a plain text URL pattern like `/admin/**`
means once more than one thing is capable of receiving a request — and the
development setup registers a second one, the database console. Rather than
guess, the new version throws an error and refuses to boot.

**Why the tests missed it:** the tool the tests use to simulate requests never
registers that second thing. So from the tests' point of view there was only one
possible interpretation, no ambiguity, no error — and a completely healthy-looking
test run against an application that could not actually start.

**Why it matters more than any individual bug:** it is proof that "the tests pass"
and "the application works" are different claims. From this point on, the upgrade
process included an explicit step of actually starting the application and
visiting it in a browser, specifically because the tests had just demonstrated
they could not be trusted alone for this.

**What fixed it:** being more explicit about which kind of pattern-matcher to use,
rather than letting the framework guess. This was applied everywhere the old
pattern appeared, not only where it broke, because the two ways of guessing do not
always agree — leaving the ones that happened to still work would have meant two
different, undocumented behaviours living side by side.

---

## The one that was invisible on purpose: a safety net silently switched off

**What happened:** the shopping cart began failing in tests for reasons that had
nothing to do with the cart.

The cart lives in the user's session. A previous fix had disabled part of the
session-handling machinery during tests, specifically so the testing tool's
built-in session helpers would work correctly — done by setting one configuration
value.

The new framework version **removed that configuration value.** And here is the
part that made it dangerous: an unrecognised setting does not cause an error. It
is silently ignored. So the switch that used to turn the session machinery off
during tests quietly stopped doing anything, the machinery turned back on, and six
unrelated test failures appeared with no obvious connection to the actual change.

**Why it matters:** a workaround that fails loudly is an inconvenience. A
workaround that fails silently is a trap — everything looks configured correctly,
and nothing tells you it stopped working.

**What fixed it:** the replacement no longer relies on a setting that can be
misspelled or removed without complaint. It names the specific piece of machinery
to switch off directly, so that if it is ever wrong, the application refuses to
start rather than quietly doing the wrong thing.

---

## The one nobody predicted: 18 failures from one removed feature

**What happened:** on the first attempt at the biggest version jump, 24 tests
failed. 18 of them turned out to share a single cause that had not been mentioned
anywhere in the planning.

Templates had been reaching directly into the incoming web request to read things
like the current page's address. The new Thymeleaf version removed that ability
outright, on the reasoning that a page template asking the raw network request
directly is the template doing a job that belongs to the code behind it.

Because the piece of template affected sits inside the navigation bar — present on
literally every page — every single page-rendering test failed at once. Looked at
quickly, that looks like the entire rendering system is broken. It was one
specific technique, used in nine places, that had lost its way of working.

**What fixed it:** rather than fight to keep the old technique alive, the values
the templates actually needed were handed to them directly by the code, the way
the framework now expects. In three of those places the value being fetched by
hand had *already* been captured by the code a few lines earlier for an unrelated
reason — the template was reaching around a value it was already being given.

**A genuine bug caught by accident.** Two of the affected templates built a page
address by joining the path and the query string with a question mark,
unconditionally — so a page with no query string rendered a stray, literal `?null`
in a link. Fixing the removed-feature problem the straightforward way happened to
remove this too, because the replacement was written once, correctly, rather than
patched in nine slightly different ways.

**Worth remembering:** the one piece of the frontend flagged in advance as the
highest risk of the whole upgrade — a small third-party add-on, unmaintained for a
newer version — turned out to be completely unaffected. The actual risk was in the
main library everyone assumed was safe, and the tool that was supposedly least
safe caused no problem at all. Predictions about what *will* break are worth much
less than checking.

---

## Smaller changes in behaviour, not bugs, but real

These did not cause failures during the upgrade — the code that would have been
affected either did not exist here or was already written the safer way — but each
is a real change in what the framework does, worth knowing before it surprises
someone later.

**A web address with a trailing slash stopped matching.** `/bottles/` used to reach
the same page as `/bottles`. After the upgrade it returns "not found". Nothing
inside the application links to a trailing-slash address, so nothing broke — but
an old bookmark or a link from a search engine could.

**A setting Hibernate used to accept quietly started being rejected.** One
production configuration line referred to a setting the database layer no longer
recognises. Harmless to remove, but it had to be found and removed rather than
simply forgotten about.

**Database identifiers are now assigned differently.** The previous version handed
out new identifiers from one shared counter across every kind of record. The new
version gives every kind of record its own counter. On a database that already has
real data, this can hand out an identifier that is already in use, corrupting
data. It did not matter here — there is no real data in this project's
database yet — but the difference had to be recognised and consciously accepted
rather than missed.

---

## What this adds up to

Every serious problem here shares the same shape: a piece of code did not change
at all, but the meaning of something it depended on changed underneath it, and the
result ranged from a refusal to start, to a false test failure, to a silently
disabled safety feature.

The two lessons worth keeping:

- **A passing test suite is not proof the application runs.** It proves the
  application behaves as tested *inside the simulated environment the tests use* —
  which is not always the same environment the real application runs in.
- **A workaround that can fail silently will, eventually, fail silently.**
  Whenever there was a choice between a setting that is quietly ignored if wrong
  and a name that is checked and rejected if wrong, the second was chosen from
  this point on.
