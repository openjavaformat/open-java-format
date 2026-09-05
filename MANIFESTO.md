# The open-java-format Manifesto

## Preamble

Every language that solved code formatting solved it the same way: one obvious tool, owned by
nobody in particular, that everyone runs and nobody argues about. Go has `gofmt`. Rust has
`rustfmt`. Python has `black` and now `ruff format`. JavaScript has `prettier`. Zig ships
`zig fmt` in the compiler.

Java does not have this. Java has two good formatters, and both of them belong to a corporation:
[google-java-format](https://github.com/google/google-java-format) and
[palantir-java-format](https://github.com/palantir/palantir-java-format).

This is not an accusation. Google and Palantir both did excellent engineering and both released it
under Apache 2.0, which is why this project can exist at all. We are standing on their work and we
say so on every page.

It is a structural observation. A tool that we ask every Java project in the world to run in its
build should not depend on one company's release engineer, one company's private CI credentials,
one company's product roadmap, or one company's public reputation. Those are four separate single
points of failure, and today the Java ecosystem has no formatter that is free of all four.

**open-java-format exists to fix the structure, not the code.** The formatting algorithm is good.
The way it reaches you is not.

## Principles

### 1. Formatting is shared infrastructure

A formatter is not a product. It is a fixed point that a community agrees on so it can stop
discussing whitespace. Infrastructure that everyone depends on should be governed like
infrastructure: in public, by more than one party, with the rules written down.

### 2. The name must be neutral

Nobody should have to justify a dependency's brand to a security reviewer, a procurement team, or
a colleague who has never heard of the company. A whitespace tool should not start a political
conversation, and an unfamiliar corporate name in a build file is friction even for people with no
opinion at all.

There is also a legal reason, and it applies to any fork: Apache 2.0 §6 grants patent and copyright
rights but explicitly **no trademark rights**. A fork may use the code; it may not use the name.
The rename is not optional, so we treat it as an opportunity rather than a cost.

### 3. Every artifact is built in public

No binary ships that you cannot trace back to a public commit, a public workflow run, and a
published provenance attestation. Every jar, every plugin, every native image, on every platform,
built by a workflow you can read and re-run.

This matters most for the native images. A compiled binary produced on infrastructure that nobody
outside the company can inspect is a trust assertion, not a supply chain. "Trust us" is exactly the
thing we are trying to remove.

### 4. Output stability is a contract

This formatter's output lives in millions of lines of other people's repositories. A change in
output is an API break, and it lands as a diff on someone's Monday morning. Style changes ship only
in versioned, documented, deliberately-chosen increments — never as a side effect of a bug fix,
never silently, never in a patch release.

### 5. Drop-in first, opinions later

Migration must be one line in one file. Byte-identical output to the version you were already
using, verified by a differential test over a large public corpus, not by assertion. We earn the
right to have opinions about style only after we have proven we can be boring.

### 6. Every contribution gets an answer

Merge, change request, or a stated reason for "no" — within a published window. "No" is a perfectly
good answer and maintainers are allowed to give it. Silence is not an answer, and a PR that sits
for a year because it is not on some company's quarterly roadmap is the failure mode we are
forking away from.

### 7. Upstream is family, not competition

We are a fork, not a rival. Fixes we make flow back to palantir-java-format, and to
google-java-format where they apply. We track upstream for as long as tracking upstream serves
users. We keep the copyright headers, the attribution, and the credit — permanently, and not only
because the licence requires it.

### 8. No single point of failure, including us

The whole argument of this document is that one owner is one owner too few. That applies to the
current maintainers of this fork. Ownership here is temporary by design: multiple maintainers with
release credentials, a written succession plan, and the explicit intent to hand the project to a
neutral foundation once it has earned the right to ask.

## What this is not

- **Not a licence dispute.** Apache 2.0 in, Apache 2.0 out. Nothing was taken away from anyone.
  Unlike OpenTofu, Valkey, OpenSearch or OpenBao, we have no relicensing to react to and nothing to
  demand. This manifesto is a set of standing promises, not an ultimatum.
- **Not a hostile fork.** We would be delighted to become redundant. If the upstream projects adopt
  open builds, open governance and a neutral home, that is a better outcome than this repository
  succeeding.
- **Not a rewrite.** We are not going to redesign the formatting algorithm to prove we can.
- **Not a rebrand-and-abandon.** A fork that ships one release and dies has made the ecosystem
  worse, not better. If we cannot sustain releases, we will say so in public and hand the keys over.

## Endorsing

If you want this to exist, say so — star the repository, open an issue introducing yourself and
your project, or add your name or organisation to `SUPPORTERS.md`. Adoption is the only argument
that matters, and a fork with users is a fork with a future.
