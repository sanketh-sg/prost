# Speeding up CI

Every push runs the pipeline in `.github/workflows/ci.yml`: check formatting,
run the test suite. It worked, but it was slow in a specific, fixable way.
Written so the reasoning makes sense even if you've never touched Gradle.

## The kitchen analogy

Imagine a recipe with two parts: baking a loaf of bread, and tasting a spoonful
of soup to check the seasoning. If the recipe accidentally says "before you can
taste the soup, bake a fresh loaf of bread first" — every single time, even
though the soup has nothing to do with the bread — you'd notice. You'd end up
baking bread ten times a day just to taste soup ten times.

That is almost exactly what was happening. "Tasting the soup" is running the
test suite. "Baking the bread" is bundling the frontend (turning the CSS and
JavaScript source files into the compressed files the browser downloads) with
a tool called webpack. Nothing in the tests reads the bundled files — but the
build was wired so that running tests always baked a fresh loaf first.

## Measuring it

Before any of this, running the test suite from a clean checkout looked like
this on a stopwatch:

```
$ ./gradlew test
> Task :frontendBuild      ← webpack runs, ~80 seconds
> Task :test                ← the actual tests, ~15 seconds
BUILD SUCCESSFUL in 2m 19s
```

Four fifths of that time was spent on something the tests never look at.

## Fix 1: let Gradle remember yesterday's loaf of bread

Gradle (the tool that runs the build) has a feature called the **build cache**:
if a step's inputs haven't changed since last time, skip running it and reuse
the result you already have. Think of it as a fridge — if yesterday's bread is
still in the fridge and nothing about the recipe changed, don't bake a new one,
just take it out.

Two things were stopping this from happening:

**The fridge itself was switched off.** Turning on the build cache is one file:

```properties
# gradle.properties (new file)
org.gradle.caching=true
org.gradle.parallel=true
```

**And the specific task that bakes the bread wasn't allowed to use the fridge
even once it existed.** The frontend-bundling step runs a plain shell command
(`pnpm run build:prod`), and Gradle treats "run a shell command" tasks as
unsafe to cache unless you explicitly say otherwise — because it has no way to
know whether that command is safe to skip. So it had to be told:

```groovy
tasks.register('frontendBuild', Exec) {
    ...
    outputs.dir('src/main/resources/static/build')
    outputs.cacheIf { true }   // "yes, it's safe to reuse this result"
    commandLine pnpm(['run', 'build:prod'])
}
```

With both pieces in place, here's the same command a second time, after
deleting the bundled output to prove it isn't just sitting there untouched:

```
$ rm -rf src/main/resources/static/build
$ ./gradlew test
> Task :frontendBuild FROM-CACHE   ← pulled from the fridge, instant
> Task :test UP-TO-DATE
BUILD SUCCESSFUL in 10s
```

2 minutes 19 seconds down to 10 seconds, because nothing that affects the
bundle (frontend source files, Tailwind/webpack config) had actually changed.
The moment one of those *does* change, the cache misses and it bakes fresh
bread again — that's the correct behaviour, not a bug.

## Fix 2: don't run the recipe at all for a grocery list

Some commits only touch a `.md` documentation file — no code, nothing that
could possibly need a test run. That's like re-running the whole recipe
because someone updated the grocery list pinned to the fridge. One line tells
GitHub Actions to skip the pipeline entirely for those:

```yaml
on:
  push:
    paths-ignore: ['**.md']
  pull_request:
    paths-ignore: ['**.md']
```

This commit, being docs-only, won't trigger CI at all — that's this fix
working as intended, not a mistake.

## Fix 3: stop setting the table twice

The pipeline had two separate steps — one running `./gradlew spotlessCheck`,
another running `./gradlew test`. Each is its own invocation of Gradle, and
each pays a fixed startup cost (like a chef re-reading the whole recipe book
before doing anything, even for a five-minute task). Combining them into one
call:

```yaml
- run: ./gradlew spotlessCheck test
```

pays that startup cost once instead of twice.

## A wrong turn, and why it was wrong

The first idea was more direct: instead of caching the bread, just stop the
soup recipe from requiring bread at all — make `test` not depend on the
frontend bundle in the first place, since it's true that no test reads it.
Tried it, and it worked *for tests*: a clean `test` run dropped to 55 seconds
with webpack not running at all.

But Gradle refused to build the actual shippable jar (`bootJar`) afterwards,
and it was right to refuse. Packaging the jar still needs the bundled files —
and by only gluing the frontend build to `bootJar` and not to the general
"collect all the files" step (`processResources`), it became possible for the
jar-packaging step to run *before* the bread finished baking, and ship a jar
with old or missing CSS/JS depending on lucky timing. That's not a hypothetical
— it's almost word-for-word a bug this project already shipped once and fixed
(a packaged build with no styling at all, documented in
`issues-before-upgrade.md`). So that change was reverted. The cache-based fix
above gets nearly the same speed win without reopening that door.

## Net effect

| Change | Plain-English effect |
|---|---|
| Build cache turned on + opted the frontend build into it | The ~80s frontend rebuild is skipped and pulled from a cache when nothing relevant changed |
| `paths-ignore: ['**.md']` | Doc-only commits never start a pipeline run |
| One `./gradlew spotlessCheck test` call | One startup cost instead of two |
| (Reverted) decoupling tests from the frontend build | Looked faster, but could let a broken jar ship — not worth the risk for the same win the cache already gives |
