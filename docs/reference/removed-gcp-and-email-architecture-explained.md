# The deleted cloud features, explained simply

`removed-gcp-and-email-architecture.md` is the forensic record: exact class
names, exact payloads, exact credentials that were compromised. This is a
companion to it — the handful of mechanisms in that record that are genuinely
confusing unless you've hit each pattern before, explained with an everyday
comparison. Each section names which part of the original it maps to.

---

## 1. Always signing the same name (the statistics bug that overwrote itself)

**Maps to:** "bi-function — order statistics," known defect #2.

### The everyday version

Imagine a hotel guestbook where every visitor is asked to sign in — except the
front desk, by mistake, hands every single guest a name tag that already says
"Guest." Nobody writes their own name. At the end of the year, the guestbook
doesn't show a thousand visitors. It shows one name, "Guest," written a
thousand times in the same spot, each new signature erasing the last.

That's exactly what happened to the order-statistics feature. Every time an
order's stats were meant to be saved, the code built the storage key like
this:

```java
newKeyFactory().setKind("stat").newKey("stat-value")
```

`"stat-value"` is not a placeholder — it's the literal, hardcoded key for
*every single record, forever*. Cloud Datastore's save operation ("put")
replaces whatever already lives at a given key. So instead of accumulating one
row per order, the table could only ever hold exactly one row: whatever the
most recent order happened to be, with every previous one silently erased.

(It never actually got the chance to cause damage in production — a separate,
unrelated bug meant nothing ever called this code at all. But the pattern
itself — "give every record the same name tag" — is worth recognizing on
sight, because it's an easy mistake to make in a hurry and a very quiet one to
have made.)

**The fix, if rebuilt:** let the storage system generate a unique ID per
record, or build the key from something that's actually unique to the order
(its own database ID), instead of a fixed string.

---

## 2. Two bridge crews building from different blueprints

**Maps to:** "invoicing-function — invoice email," "The intended payload."

### The everyday version

Imagine two construction crews building a bridge from opposite riverbanks,
each given a blueprint — except the blueprints don't actually match. One crew
is building their half assuming the bridge will be a suspension design; the
other is building theirs assuming a truss design. Neither crew is working
carelessly. Both are executing their own blueprint correctly. The bridge will
simply never meet in the middle, because the two ends were never designed
against the same plan.

That is precisely what happened between the two halves of the invoicing
feature. The admin-panel side (the caller) was built — half-built, really,
recovered from an abandoned working copy — to send a flat payload describing
the order: `user`, `order_id`, `amount`, `date`, `address`, `products`, all as
plain text fields, implying the receiving function should **generate** the
invoice PDF from that data.

The receiving side (`SendEmail`, the function that actually got deployed)
assumed the *opposite*: that a file literally named `Invoice.pdf` already
existed, sitting in a cloud storage bucket, ready to be fetched and emailed as
an attachment. It never generated anything — it only ever expected to find a
finished document waiting for it.

```
Caller's blueprint:  "Here are six fields — please build me a PDF and send it."
Function's blueprint: "I will fetch a file called Invoice.pdf that
                        someone already made, and email that."
```

Neither half was ever going to work with the other, no matter how correctly
each was individually finished — which is exactly why neither ever got
finished. **The lesson for a rebuild:** write down the contract between two
pieces before building both sides of it, and if it's tempting to build them
separately anyway, at minimum get one end talking to a stub of the other
early, so a mismatch like this surfaces in days, not never.

---

## 3. Writing your house key on the visible side of the label

**Maps to:** "GitLab CI," the known defect about `GOOGLE_APPLICATION_CREDENTIALS`.

### The everyday version

A locker system works by giving you a small paper tag with a locker *number*
written on it — the tag points to where the valuable thing is, it isn't the
valuable thing itself. Imagine someone, in a rush, instead writes the actual
combination to their house safe directly on that paper tag, then pins the tag
up on a public corkboard where dozens of people can glance at it.

That's what the deployment pipeline did. `GOOGLE_APPLICATION_CREDENTIALS` is
meant to hold a *file path* — "the credentials live over there, in this file
on disk." The deploy job instead set it to the raw *contents* of the
credentials file itself, passed as a plain environment variable to the cloud
function:

```
--set-env-vars GOOGLE_APPLICATION_CREDENTIALS=<the actual JSON key, inline>
```

Two things follow from that. First, it almost certainly wouldn't even have
worked technically — the library reading that variable expects a path it can
open as a file, not a blob of JSON to parse in place. Second, and worse: an
environment variable is visible to anyone with viewer access to that
function's configuration, which is a far larger audience than "everyone who
can read a specific credentials file on disk." The tag meant to point at the
lockbox *became* the lockbox's contents, in public view.

**The fix, if rebuilt:** a deployed cloud function should run with its own
ambient identity (the cloud platform grants it permissions directly) and need
no key file passed to it at all — the strongest version of "don't write the
combination on the tag" is not needing a tag in the first place.

---

## 4. A whiteboard that remembers every word ever written on it

**Maps to:** "Dockerfile," the defect about the password file.

### The everyday version

An ordinary whiteboard forgets what you erase — wipe off yesterday's note and
it's gone. Now imagine a whiteboard that, instead of truly erasing, just paints
a fresh layer of whiteboard surface directly over what was there, keeping the
old writing intact and readable underneath if you ever peeled that layer back.
Writing something secret on it and "erasing" it later gives you false
confidence: the secret didn't disappear, it just went one layer down.

That's how Docker images work, and it's exactly what happened here. The build
process wrote the database password into a file (`db_passwd`) *inside* the
image during one build step. Even if a later step deleted that file, Docker
images are built as a stack of layers, and each layer preserves exactly what
existed at that point in the build — deleting a file in a later layer doesn't
remove it from the earlier layer's history. Anyone who could pull that image
and inspect its layer history could recover the password, regardless of
whether the final, running container still had the file present.

**The fix, if rebuilt:** never write a secret into any layer of an image.
Pass it in at container-start time as a runtime environment variable, or have
the running container fetch it from a secrets manager — something that never
becomes part of the image's permanent, inspectable history.

---

## The one idea all four of these share

Every one of these bugs is invisible from the outside while things are
working normally — a "record was saved," a "credential was configured," a
"payload was sent." The failure only shows up when someone looks a layer
deeper: opens the actual Datastore table and finds one row instead of a
thousand, compares the two blueprints side by side, checks what an environment
variable is actually visible to, or inspects an image's layer history instead
of trusting its final state. None of these needed cleverness to avoid — they
needed one extra "wait, let me actually check what this does" before shipping.
