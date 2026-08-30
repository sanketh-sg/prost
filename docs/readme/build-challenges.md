# Challenges faced while building Prost

Three stories each for the three features most worth talking about: security,
the cart, and pagination. Written after the fact, from what the code and its
scars say about how it was built — not a diary kept at the time.

---

## Security

### 1. Two security configs, one bug waiting to happen

The app needs different rules for a laptop and a live server: the dev profile
runs an H2 database console alongside the app, so its security chain has to
know about a second servlet, and can relax things like HTTPS redirection that
would be pointless on `localhost`. Production needs neither, but does need
headers and redirects dev doesn't.

The naive approach — one method per profile — meant writing the same
authorization rules twice. That's exactly the kind of duplication that
byte-for-byte agrees today and quietly stops agreeing in six months, and it's
also exactly what happened: the two copies drifted, and the drift landed on the
one rule that mattered — `/admin/**` required a role in production and simply
didn't in dev. The fix wasn't "add the missing rule," it was "make it
impossible to have two rules" — extract the shared matrix into one method, call
it from both chains, and let only the genuinely different parts (headers, CSRF,
the login page) stay separate.

### 2. Deciding what "logged in" should mean by default

Most security guides tell you to default-deny: block everything, then open up
what's safe. A storefront works backwards from that. If a routing mistake
locks out `/checkout`, someone notices in a day. If the same mistake
accidentally exposes the product catalogue, nobody notices at all, because it
was supposed to be public anyway.

So the app defaults open — `permitAll()` on `anyRequest()` — and pulls three
things back under `authenticated()`: the cart, order history, and account
pages. `/admin/**` needs a role on top of that. It's the less orthodox choice,
and it only works because the three exceptions are enumerated explicitly
rather than inferred, so there's nothing left to guess about which pages are
which.

### 3. A reflection trick that looked clever and wasn't

The admin panel originally ran actions by taking a method name straight out of
the URL and invoking whatever matched by reflection. It felt efficient — one
generic handler instead of five — right up until two things became obvious.
First, it meant admin actions ran on a plain `GET`, so anything that follows
links (a prefetching browser, a crawler, a chat client generating a preview)
could trigger one. Second, one of those actions deletes every row in three
tables, and there was no anti-forgery protection standing in front of it
because GET requests don't carry one by default.

The honest fix cost more lines, not fewer: five named methods, five POST-only
forms, and a CSRF token on each. Slower to write, much harder to trigger by
accident.

---

## Cart

### 1. The lookup table that only worked by luck

The cart is a map from product to quantity, and Java maps need their keys to
know how to hash themselves. Both product types initially inherited a default
`hashCode()` that returned the same constant for every instance of a class —
fine for identity comparisons, useless as a hash. Every product landed in the
same bucket, and the map degraded into a list you had to scan linearly.

It never showed up in testing, because every test happened to add each product
to the cart exactly once. It would have shown up the moment someone bought the
same beer twice in one session — two entries for what should have been one,
each silently splitting the true quantity. The fix was to hash and compare by
the database id once, in the shared base class, rather than trust the default.

### 2. Selling more than the shelf holds

Stock got checked at "add to cart" and nowhere else. Two people — or one
person in two tabs — could each add the last two crates, each check pass
individually, and checkout would happily total up an order for four crates
against a shelf that holds two.

Re-checking stock right before saving the order looks like a fix and isn't: two
checkouts can both read "two remaining" a moment apart, both see enough stock,
and both proceed. The actual fix takes a row lock on every line in the order
before anything is written, so a second checkout touching the same product has
to wait its turn instead of racing the first one to the finish.

### 3. A quiet subtraction bug that hid its own evidence

The code that decremented stock after a sale clamped the result at zero. It
felt defensive — never let stock go negative — but it meant that overselling
by two units left the stock reading exactly zero, identical to a shelf that
was simply empty. There was no number anywhere showing the shop had promised
more than it had. Removing the clamp was uncomfortable at first — the
underlying number could go negative — but that discomfort was the whole point:
a negative number is a fact you can act on, and a floor at zero is a fact
being erased.

---

## Pagination

### 1. A hundred lines to reinvent one

The project had a hand-rolled paging class: page number, page size, an offset
calculation, a check for whether a previous page exists. All of it duplicates
what the framework already provides as `Pageable`/`PageRequest` in a single
call. Worse, the hand-written "is there a previous page?" check had an
off-by-one in it, so the first page sometimes rendered a "previous" link that
led nowhere useful.

The lesson wasn't really about pagination — it was about writing 106 lines
before checking whether the framework already had one. Deleting all of it and
calling the built-in version fixed the off-by-one for free, because it was
never a bug in the concept, just in a bespoke implementation of something that
didn't need to be bespoke.

### 2. Page links that only worked with a mouse

The original page-number controls were `onclick` handlers that mutated the
page in place with JavaScript. It worked, technically, but it broke every
other way of using the web: middle-click to open page 3 in a new tab did
nothing, the browser's back button didn't return to the previous page of
results, and a bookmarked or shared URL always reloaded page one. None of that
shows up in a manual click-through test, because a person testing by hand
doesn't usually try to bookmark page 4 of a beer list.

The fix was to make each page number a real `<a href>` carrying the page
number as a query parameter, and let the server render the requested page
directly — the same content, delivered the way HTML has always been able to
deliver it, at a fraction of the JavaScript.

### 3. A test suite that agreed with a broken page

Pagination tests checked that the endpoint returned `200 OK` and that the page
metadata (page number, total pages) looked right. None of them checked that
the *rows themselves* were the correct rows for that page. That's a real gap
someone doing this again should watch for: a status-code test and a
correct-content test are different claims, and a refactor of the underlying
query could pass every existing test while quietly returning page 2's data on
page 1. Once noticed, the fix was cheap — assert on the actual beverage names
or ids that come back for a known, seeded page — but it's the kind of gap that
only becomes obvious once you go looking for it on purpose.
