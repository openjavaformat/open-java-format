<p align="center">
<a href="https://github.com/openjavaformat/open-java-format/actions/workflows/ci.yml"><img src="https://github.com/openjavaformat/open-java-format/actions/workflows/ci.yml/badge.svg" alt="CI"/></a>
<a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-blue" alt="License"/></a>
<a href="MANIFESTO.md"><img src="https://img.shields.io/badge/read-the%20manifesto-informational" alt="Manifesto"/></a>
<a href="#roadmap"><img src="https://img.shields.io/badge/status-bootstrapping%20(phase%201%2F5)-orange" alt="Status"/></a>
<a href="https://central.sonatype.com/search?q=com.palantir.javaformat"><img src="https://img.shields.io/maven-central/v/com.palantir.javaformat/palantir-java-format?label=upstream" alt="Upstream release"/></a>
</p>

# open-java-format

_A modern, lambda-friendly, 120-character Java formatter — built in the open._

> ### Status: bootstrapping. Nothing is published yet.
>
> This is a community fork of [palantir-java-format](https://github.com/palantir/palantir-java-format),
> which is itself a fork of [google-java-format](https://github.com/google/google-java-format).
> The formatting engine is unchanged and we intend to keep it that way for the whole 2.x line.
> What changes is **who builds the artifacts, in the open, and who can get a fix merged.**
>
> **Today you should keep using upstream.** This README is the plan for making that unnecessary.
> See the [roadmap](#roadmap) for where we are, and the [manifesto](MANIFESTO.md) for why.

**Quick links:** [Why this fork](#why-this-fork-exists) · [Roadmap](#roadmap) ·
[Compatibility promise](#compatibility-promise) · [Coordinates](#coordinates-old--new) ·
[Prior art](#prior-art-how-other-projects-did-this) · [Open decisions](#open-decisions) ·
[Using the formatter](#using-the-formatter)

---

## Why this fork exists

### Java is the only major language without a neutral formatter

Go has `gofmt`. Rust has `rustfmt`. Python has `black` / `ruff format`. JavaScript has `prettier`.
Each of those is, in practice, community infrastructure: you run it, you don't argue about it, and
no single vendor decides its future.

Java's two credible formatters are both corporate projects — google-java-format and
palantir-java-format. Both are good. Both are Apache 2.0, which is why this fork is possible and
why we are grateful. But "the tool every Java build in the world runs" and "a side project inside
one company" is a structural mismatch, and it shows up in four concrete ways.

### 1. The name is an adoption barrier on its own

For a growing number of teams, "Palantir" in a build file is a conversation — about a defence and
intelligence contractor — that nobody wanted to have while reviewing a whitespace tool. For people
who have never heard of the company it is simply an unfamiliar corporate name where a neutral one
should be. Either way it costs adoption for reasons that have nothing to do with the code.

There is also a hard constraint here that applies to any fork: **Apache 2.0 §6 grants no trademark
rights.** A fork may use the code; it may not carry the name. So the rename is not a preference,
it is a requirement — we simply choose to treat it as an upgrade.

### 2. The build is a black box

Upstream releases are produced by Palantir's private CircleCI and internal autorelease
infrastructure. You cannot read the pipeline, re-run it, or reproduce what you download. There are
no provenance attestations and no SBOM.

That is uncomfortable for a jar. It is worse for the **native images**: `palantir-java-format-native`
ships precompiled GraalVM binaries per OS/architecture that the Gradle plugin downloads and executes
on developer machines and CI runners. A binary you cannot rebuild from a public commit is a trust
assertion, not a supply chain — and the current matrix is also incomplete (see
[build matrix](#the-build-matrix-we-owe-you)).

### 3. Contributions move at one company's pace

The roadmap is whatever the owning company needs this quarter. External pull requests compete with
internal priorities for review attention, and the ones that lose simply sit. That is a rational way
to run an internal tool and a poor way to run ecosystem infrastructure.

### 4. Nothing here is a licence dispute

Worth stating plainly, because most famous forks were: Apache 2.0 in, Apache 2.0 out, nothing was
taken away from anyone. We are not reacting to a relicensing and we have nothing to demand. This is
a **governance and supply-chain fork**, which is a harder story to tell and a better one to be
honest about.

**The full set of commitments is in [MANIFESTO.md](MANIFESTO.md).** In one screen:

1. Formatting is shared infrastructure.
2. The name must be neutral.
3. Every artifact is built in public, with attestations.
4. Output stability is a contract; style changes are versioned and never silent.
5. Drop-in first — migration is one line, output is byte-identical, proven by a differential test.
6. Every contribution gets an answer within a published window. "No" counts; silence does not.
7. Upstream is family — fixes flow back.
8. No single point of failure, including us — multiple maintainers, then a neutral foundation.

---

## Roadmap

| Phase | Goal | Done when |
|---|---|---|
| **0** | Fork and inventory | Repo exists, every piece of vendor-specific infrastructure is catalogued |
| **1** | Build everything in the open | Every artifact — jars, all plugins, all native images — builds from a public GitHub Actions workflow, with attestations |
| **2** | Republish under neutral coordinates | Same version numbers as upstream, byte-identical output, published to Maven Central / Gradle Portal / JetBrains Marketplace |
| **3** | Ecosystem integrations | Spotless, Maven, a GitHub Action, pre-commit, editors — migration is one line |
| **4** | Detach and govern | Stop tracking upstream's branch, start merging features, written governance |
| **5** | A neutral home | Independent org and domain, multiple maintainers with release keys, foundation conversation |

Compatibility with upstream is **not** something we trade away for speed: phases 1–3 change nothing
about the formatter's behaviour. Phase 4 is where this project starts having opinions of its own.

### Phase 0 — Fork and inventory

- [x] Fork the repository
- [x] Catalogue everything that only works inside Palantir (below)
- [x] Decide the project name, Maven group and GitHub org — `openjavaformat`, `openjavaformat.com`
- [x] Write down the compatibility promise and the manifesto (this document + `MANIFESTO.md`)

### Phase 1 — Build everything in the open

This is the bulk of the work, and it is mostly *removal*. The build currently depends on roughly
twenty Palantir-authored Gradle plugins and four Palantir-hosted services. Each one has to be
replaced with something a stranger can run.

| Concern | Today | Replacement |
|---|---|---|
| CI | ~~CircleCI (`.circleci/config.yml`, generated by Palantir's Excavator)~~ | **Done** — [`.github/workflows/ci.yml`](.github/workflows/ci.yml), one explicit job per platform |
| Release | Palantir Autorelease + `com.palantir.gradle.externalpublish` | Tag-triggered workflow → Maven Central Portal, signing keys in a protected GitHub environment |
| Dependency updates | Excavator (`.excavator.yml`) | Renovate |
| Auto-merge | Bulldozer (`.bulldozer.yml`) | GitHub merge queue + auto-merge |
| Changelog | Palantir changelog-app (`.changelog.yml`) | Release Drafter, changelog entry in the PR template |
| Version pinning | `gradle-consistent-versions` (`versions.props` / `versions.lock`) | Version catalog (`gradle/libs.versions.toml`) + Gradle dependency locking |
| Static analysis | `gradle-baseline`, `baseline-error-prone`, `suppressible-error-prone`, `baseline-null-away`, `.baseline/` | `net.ltgt.errorprone` + NullAway directly; vendor the checkstyle config (checkstyle is disabled in this build today anyway) |
| JDK provisioning | `gradle-jdks`, `gradle-jdks-latest`, `gradle-jdks-settings`, `gradle/jdks/**`, `palantir.jdk.setup.enabled` | Gradle toolchains + foojay resolver locally; `actions/setup-java` and `graalvm/setup-graalvm` in CI |
| Version string | `gradle-git-version` + `CIRCLE_TAG` | `git describe` / `GITHUB_REF_NAME` |
| API compatibility | `gradle-revapi` (`.palantir/revapi.yml`) | japicmp, or revapi's own plugin |
| IntelliJ plugin publish | `com.palantir.external-publish-intellij` | `org.jetbrains.intellij.platform` + `publishPlugin` with a marketplace token |
| Native image publish | `com.palantir.external-publish-custom` | plain `maven-publish` with classifiers, plus GitHub Release assets |
| OS/arch detection | `com.palantir.gradle.utils:platform` | ~40 lines in `buildSrc` |
| Version comparison in the IDE plugin | `com.palantir.sls.versions` | a plain semver comparator |
| Self-formatting bootstrap | `com.palantir.javaformat:gradle-palantir-java-format` formats this repo | bootstrap from upstream once, then self-host our own build |
| Miscellaneous | `jakarta-package-alignment`, `failure-reports`, `gradle-guide`, `idea-configuration`, `idea-language-injector` | drop |

On top of the replacements, Phase 1 adds what upstream does not have:

- [ ] **SLSA build provenance** (`actions/attest-build-provenance`) for every jar and every binary
- [ ] **Sigstore / cosign signatures** on the native images
- [ ] **SBOM** (CycloneDX) attached to each release
- [ ] **Reproducible-build settings** — deterministic archive timestamps and file ordering, plus a
      job that rebuilds a release and diffs it
- [x] **Every action pinned to a full commit SHA**, not a floating tag — a moving `@v4` is a
      third party's write access to our build
- [ ] **Configuration-cache compatibility** — the GraalVM plugin (0.10.4) cannot serialise its
      `NativeConfigurations` bean, so `nativeCompile` fails whenever the configuration cache is on
- [ ] **Snapshot publishing** from `main`, so the pipeline is exercised continuously rather than
      only at release time

> **Licence note for contributors:** do *not* strip the `(c) Copyright … Palantir Technologies Inc.`
> or Google copyright headers from source files. Apache 2.0 §4 requires them to stay. Removing the
> trademark from artifact names is required; removing attribution from source is a violation.

#### The build matrix we owe you

Upstream compiled native images explicitly on only two platforms, even though the code already
computes a Windows classifier and a `.exe` extension. Every cell below has to become a job you can
point at.

| Artifact | linux x64 | linux aarch64 | macOS aarch64 | macOS x64 | windows x64 | linux musl |
|---|---|---|---|---|---|---|
| Core jars (platform-independent) | ✅ | — | — | — | — | — |
| Gradle plugin | ✅ | — | — | — | — | — |
| IntelliJ plugin | ✅ | — | — | — | — | — |
| Eclipse plugin | ✅ | — | — | — | — | — |
| Native image | ⚠️ | ✅ | ✅ | ❌ | ❌ | ❌ |

✅ built and uploaded by [`ci.yml`](.github/workflows/ci.yml) · ⚠️ built, but as a side effect of
`assemble` rather than by a job of its own · ❌ missing · — not applicable

Remaining gaps, each a self-contained PR:

- [ ] macOS x86-64 (`macos-13` runner)
- [ ] Windows x86-64 — the coordinate already exists in `NativeImageFormatProviderPlugin`, nothing
      produces the binary
- [ ] linux musl / Alpine
- [ ] Give linux x86-64 its own `nativeCompile` job instead of relying on `build`

### Phase 2 — Republish under neutral coordinates

- [ ] Register the Maven group and the Gradle plugin namespace
- [ ] Rename artifacts and plugin IDs (see [coordinates](#coordinates-old--new)); **Java packages
      stay `com.palantir.javaformat.*` for the whole 2.x line** — renaming them would break the SPI,
      Spotless and every programmatic user, and would make merging upstream commits painful forever
- [ ] Publish `2.x` releases whose numbers **match upstream exactly**, so migration is a coordinate
      swap and not a version negotiation
- [ ] Ship the equivalence gate below in CI

#### The equivalence gate

This is the argument that the fork is safe to adopt, and it should be a test rather than a promise:

> For each release, format a large corpus of public Java source with the upstream artifact and with
> ours, and assert the output is **byte-identical**. Any difference fails the build.

Run it over several hundred thousand lines drawn from public repositories, plus the existing
`palantir-java-format` test corpus, on every supported JDK and against both the JVM and native
implementations. Publish the corpus list and the result with each release.

### Phase 3 — Ecosystem integrations

- [ ] **Spotless** — this is the one that needs outside help. Spotless's
      `palantirJavaFormat()` step hardcodes the `com.palantir.javaformat` coordinates, so it will not
      pick up our jars. We need either a Spotless PR adding an `openJavaFormat()` step, or documented
      use of a custom step in the meantime. Both Gradle and Maven.
- [ ] **Maven** — a first-class path for Maven users (today it is spotless-maven-plugin only)
- [ ] **GitHub Action** — `open-java-format --check` as a marketplace action using the native image
- [ ] **pre-commit hook**
- [ ] **Distribution channels** for the native binary — GitHub Releases, a Homebrew tap, maybe an
      apt/rpm feed
- [ ] **Migration guide** — one line changed, with a `sed` script for the mechanical parts

### Phase 4 — Detach and govern

Only after everything above works.

- [ ] Stop tracking upstream's default branch; cherry-pick from it deliberately instead
- [ ] Start accepting behaviour changes — beginning with the backlog upstream never got to
- [ ] `CONTRIBUTING.md` rewritten for this project, with the review-time commitment from the manifesto
- [ ] `GOVERNANCE.md` — who the maintainers are, how decisions are made, how someone becomes one,
      what happens when a maintainer disappears
- [ ] **An RFC process for style changes**, because output stability is a contract (manifesto §4).
      Any change to formatter output needs a written proposal, before/after diffs on real code, and
      a release it can be opted into.
- [ ] `SECURITY.md` and a disclosure process
- [ ] A deprecation policy

### Phase 5 — A neutral home

- [ ] A GitHub organisation, not a personal account, with more than one maintainer holding release
      credentials
- [ ] A domain and a small docs site
- [ ] Open the foundation conversation. [Commonhaus](https://www.commonhaus.org/) is the natural
      candidate for a project this size — it already hosts Jackson, Hibernate, JBang and SDKMAN —
      with Eclipse and Apache as heavier alternatives. This is a Phase 5 item precisely because a
      foundation should be asked *after* a project has users, releases and more than one maintainer.

---

## Compatibility promise

For the entire **2.x** line:

- **Identical output.** Byte-for-byte the same as the upstream release with the same version number,
  verified by the [equivalence gate](#the-equivalence-gate) on every release.
- **Identical Java packages.** `com.palantir.javaformat.*` stays. Your code and the SPI keep working.
- **Matching version numbers.** Our `2.71.0` is upstream's `2.71.0`.
- **One-line migration.** Change the coordinates. Nothing else.

Behaviour changes wait for **3.0**, and even then they arrive through the RFC process, not by
surprise. A package rename, if it happens at all, happens at a major version with a relocation
artifact and a long overlap.

---

## Coordinates (old → new)

The organisation (`github.com/openjavaformat`) and the domain (`openjavaformat.com`) are registered,
so the group id is settled; the rest lands with Phase 2.

| | Upstream (today) | This fork |
|---|---|---|
| Maven group | `com.palantir.javaformat` | `com.openjavaformat` |
| Core | `palantir-java-format` | `open-java-format` |
| SPI | `palantir-java-format-spi` | `open-java-format-spi` |
| Native image | `palantir-java-format-native` | `open-java-format-native` |
| JDK bootstrap | `palantir-java-format-jdk-bootstrap` | `open-java-format-jdk-bootstrap` |
| Gradle plugin artifact | `gradle-palantir-java-format` | `gradle-open-java-format` |
| Gradle plugin ID | `com.palantir.java-format` | `com.openjavaformat.java-format` |
| IntelliJ plugin ID | `palantir-java-format` | `open-java-format` |
| Java packages | `com.palantir.javaformat.*` | **unchanged in 2.x** |
| Version numbers | `2.x` | **the same `2.x`** |

---

## Prior art: how other projects did this

Forking a healthy project is not novel, and the ones that worked share a pattern. Two different
kinds are relevant here.

**Governance forks** — the fork happened because of who controlled the project, not what the licence
said. This is our category.

| Fork | From | Year | Trigger | Lesson we are taking |
|---|---|---|---|---|
| [LibreOffice](https://www.libreoffice.org/) | OpenOffice.org (Oracle) | 2010 | Corporate control after the Sun acquisition | The community follows the maintainers, not the brand; the original stagnated |
| [Jenkins](https://www.jenkins.io/) | Hudson (Oracle) | 2011 | Trademark and infrastructure control | The rename is forced by the trademark — plan it from day one, do not fight it |
| [MariaDB](https://mariadb.org/) | MySQL (Oracle) | 2009 | Acquisition, roadmap control | Long-lived drop-in compatibility is what wins default-installation status |
| [Nextcloud](https://nextcloud.com/) | ownCloud | 2016 | Governance split | Ship immediately and publish governance early |
| [Forgejo](https://forgejo.org/) | Gitea | 2022 | A company took over the project's domain and trademark | Put custody in a non-profit (Codeberg e.V.) from the start, not later |

**Licence forks** — the fork happened because a licence changed. Not our situation, but they are the
best-documented playbooks for the mechanics.

| Fork | From | Year | Trigger | Lesson we are taking |
|---|---|---|---|---|
| [OpenSearch](https://opensearch.org/) | Elasticsearch | 2021 | SSPL relicensing | Ecosystem compatibility — clients, plugins, integrations — is the entire battle |
| [OpenTofu](https://opentofu.org/) | Terraform | 2023 | BUSL relicensing | A public manifesto with signatories created the mandate; a foundation gave it neutrality |
| [OpenBao](https://openbao.org/) | HashiCorp Vault | 2023 | BUSL relicensing | Getting into a foundation early buys credibility you cannot self-declare |
| [Valkey](https://valkey.io/) | Redis | 2024 | RSALv2/SSPL relicensing | Same-version drop-in releases got distributions to switch within months |

**Which one are we?** Closest to Jenkins and Forgejo: nothing was taken away, the licence is fine,
and the objection is to *control* — of the build, the release keys, the name and the merge button.

That makes our job harder than OpenTofu's. A licence fork can point at a diff in a `LICENSE` file
and everyone immediately understands. We have to make a positive case instead: a build you can
audit, binaries you can verify, and a review queue that answers you. Which is exactly why the
[manifesto](MANIFESTO.md) is a set of promises rather than a list of grievances — the OpenTofu
document was, in its first form, an ultimatum to HashiCorp, and we have nobody to issue one to.

The other lesson from all nine: **compatibility is the migration strategy.** Valkey, MariaDB and
OpenTofu all shipped drop-in replacements with matching version numbers. That is why Phase 2 is
"republish the same thing under a different name", and not "fix everything we always disliked".

---

## Open decisions

These need a call before the corresponding phase can finish. Recommendations included; disagreement
welcome in the issue tracker.

1. ~~**Maven group and GitHub org.**~~ **Resolved:** the `openjavaformat` GitHub organisation and
   the `openjavaformat.com` domain are registered, so the group id is `com.openjavaformat`, verified
   on Maven Central by DNS TXT record. Remaining task: claim the namespace on Sonatype before
   Phase 2 can publish.
2. **Version numbering.** Recommendation: lockstep with upstream through 2.x, fork the numbering at
   3.0 when we start making our own decisions.
3. **Java package rename.** Recommendation: never in 2.x; at 3.0 at the earliest, and only with a
   relocation artifact and a long overlap. Merging upstream commits into renamed packages is a
   permanent tax.
4. **Spotless strategy.** Recommendation: open a PR against Spotless for an `openJavaFormat()` step,
   and document a custom step as the interim. This is the single biggest integration risk, because a
   large share of current users reach the formatter through Spotless.
5. **How long to track upstream.** Recommendation: track until Phase 3 is finished, then cherry-pick
   deliberately. Never delete the upstream remote.
6. **Foundation target.** Recommendation: Commonhaus, but only after Phase 5's prerequisites exist.

---

## How to help

The work is unglamorous and very parallelisable:

- **Phase 1 is mostly deletion.** Pick one row from the [replacement table](#phase-1--build-everything-in-the-open)
  and remove one Palantir-specific plugin. Each is an independent PR.
- **Add a platform.** Windows and macOS x64 native images are missing entirely.
- **Build the equivalence gate.** A harness that formats a corpus with two jars and diffs the output
  is the single most valuable thing anyone can contribute — it is what makes the fork trustworthy.
- **Tell us you want this.** Star the repo, or add your project to `SUPPORTERS.md`. A fork with users
  is a fork with a future.

---

## Building and testing locally

[mise](https://mise.jdx.dev/) pins the tooling and wraps the common commands; `mise.toml` is the
single place that says what versions we develop against.

```bash
mise trust && mise install      # temurin-21 (bootstrap JVM) + act
mise run build                  # compile, test, and the native image for this platform
mise run native                 # just the native image
mise run format                 # format this repo with the formatter it builds
mise tasks                      # everything else
```

The Gradle build provisions its own JDKs — Amazon Corretto 21 for the daemon and GraalVM CE 23 for
`nativeCompile`, pinned in `gradle/jdks/**` — and ignores whatever is on `PATH`, because
`gradle.properties` sets `org.gradle.java.installations.auto-detect=false`. The first build
therefore downloads about 500 MB before it compiles anything. All of it comes from public vendor
URLs (corretto.aws, github.com/graalvm), not from any private mirror.

### Running CI locally

The workflows run under [act](https://github.com/nektos/act) in Docker, so a change to
`.github/workflows` can be checked before it is pushed:

```bash
mise run ci:list     # what jobs exist
mise run ci:lint     # dry-run every job — resolves images, actions and steps, executes nothing
mise run ci:build    # actually run the linux jar+native build job
mise run ci:native   # actually run the linux-aarch64 native job
```

Runner images and the artifact server are configured in [`.actrc`](.actrc). Two caveats:

- **act cannot containerise macOS**, so the `macos-aarch64` leg has to be checked by running
  `mise run native` on a Mac. `.actrc` has a commented-out mapping that runs it directly on the
  host if you want act to drive it.
- **On an Apple Silicon host** the `ubuntu-24.04` container runs as arm64, so the job labelled
  `linux-x86-64` produces an aarch64 binary locally. Fine for exercising the workflow, not a
  substitute for real CI.

### Known local footgun

`org.gradle.configuration-cache=true` breaks `nativeCompile`: the GraalVM plugin (0.10.4) cannot
serialise its `NativeConfigurations` bean. CI is unaffected because the configuration cache is off
by default, but a user-level `~/.gradle/gradle.properties` outranks the project's, so the
`mise run` tasks pass `--no-configuration-cache` explicitly. Making the build
configuration-cache-clean is a Phase 1 item.


# Using the formatter

Everything below describes the formatter as it exists today, under upstream's coordinates. It will
be updated as Phase 2 lands.

## Upsides of automatic formatting

- reduce 'nit' comments in code reviews, allowing engineers to focus on the important logic rather than bikeshedding about whitespace
- bot-authored code changes can be auto-formatted to a highly readable style (upstream uses [refaster](https://errorprone.info/docs/refaster) and [error-prone](https://errorprone.info/docs/patching) heavily)
- increased consistency across all repos, so contributing to other projects feels familiar
- reduce the number of builds that trivially fail checkstyle
- easier to onboard new devs

## Downsides of automatic formatting

- if you don't like how the formatter laid out your code, you may need to introduce new functions/variables
- the formatter is not as clever as humans are, so it can sometimes produce less readable code (we want to fix this where feasible)

## Motivation & examples

(1) google-java-format output:

```java
private static void configureResolvedVersionsWithVersionMapping(Project project) {
    project.getPluginManager()
            .withPlugin(
                    "maven-publish",
                    plugin -> {
                        project.getExtensions()
                                .getByType(PublishingExtension.class)
                                .getPublications()
                                .withType(MavenPublication.class)
                                .configureEach(
                                        publication ->
                                                publication.versionMapping(
                                                        mapping -> {
                                                            mapping.allVariants(
                                                                    VariantVersionMappingStrategy
                                                                            ::fromResolutionResult);
                                                        }));
                    });
}
```

(1) this formatter's output:

```java
private static void configureResolvedVersionsWithVersionMapping(Project project) {
    project.getPluginManager().withPlugin("maven-publish", plugin -> {
        project.getExtensions()
                .getByType(PublishingExtension.class)
                .getPublications()
                .withType(MavenPublication.class)
                .configureEach(publication -> publication.versionMapping(mapping -> {
                    mapping.allVariants(VariantVersionMappingStrategy::fromResolutionResult);
                }));
    });
}
```

(2) google-java-format output:

```java
private static GradleException notFound(
        String group, String name, Configuration configuration) {
    String actual =
            configuration.getIncoming().getResolutionResult().getAllComponents().stream()
                    .map(ResolvedComponentResult::getModuleVersion)
                    .map(
                            mvi ->
                                    String.format(
                                            "\t- %s:%s:%s",
                                            mvi.getGroup(), mvi.getName(), mvi.getVersion()))
                    .collect(Collectors.joining("\n"));
    // ...
}
```

(2) this formatter's output:

```java
private static GradleException notFound(String group, String name, Configuration configuration) {
    String actual = configuration.getIncoming().getResolutionResult().getAllComponents().stream()
            .map(ResolvedComponentResult::getModuleVersion)
            .map(mvi -> String.format("\t- %s:%s:%s", mvi.getGroup(), mvi.getName(), mvi.getVersion()))
            .collect(Collectors.joining("\n"));
    // ...
}
```

## Optimised for code review

Even though this formatter sometimes inlines code more than others, reducing what we see as
unnecessary breaks that don't help code comprehension, there are also cases where it will split code
into more lines too, in order to improve clarity and code reviewability.

One such case is long method chains. Whereas other formatters are content to completely one-line a long method call chain if it fits, it doesn't usually produce a very readable result:

```java
var foo = SomeType.builder().thing1(thing1).thing2(thing2).thing3(thing3).build();
```

To avoid this edge case, we employ a limit of 80 chars for chained method calls, such that _the last method call dot_ must come before that column, or else the chain is not inlined.

```java
var foo = SomeType.builder()
        .thing1(thing1)
        .thing2(thing2)
        .thing3(thing3)
        .build();
```

## Gradle plugin

Apply this plugin to all projects where you want your java code formatted, e.g.

```groovy
buildscript {
    dependencies {
        classpath 'com.palantir.javaformat:gradle-palantir-java-format:<version>'
    }
}
allprojects {
    apply plugin: 'com.palantir.java-format'
}
```

Applying this automatically configures IntelliJ, whether you run `./gradlew idea`
or import the project directly from IntelliJ, to use the correct version of the formatter
when formatting java code.

`./gradlew format` can be enabled by using the [com.palantir.baseline-format](https://github.com/palantir/gradle-baseline#compalantirbaseline-format) Gradle plugin.

## Spotless

- See [integration in the Spotless Gradle plugin](https://github.com/diffplug/spotless/tree/main/plugin-gradle#palantir-java-format).
- See [integration in the Spotless Maven plugin](https://github.com/diffplug/spotless/tree/main/plugin-maven#palantir-java-format).

Note that Spotless's step resolves the `com.palantir.javaformat` coordinates directly — see
[Phase 3](#phase-3--ecosystem-integrations) for how this fork plans to plug in.

## IntelliJ plugin

The [palantir-java-format IntelliJ plugin](https://plugins.jetbrains.com/plugin/13180) is available
from the plugin repository. To install it, go to your IDE's settings and select the `Plugins`
category. Click the `Marketplace` tab, search for the `palantir-java-format` plugin, and click the
`Install` button.

The plugin will be disabled by default on new projects, but as mentioned [above](#gradle-plugin),
if using the `com.palantir.java-format` gradle plugin, it will be recommended
in IntelliJ, and automatically configured.

To manually enable it in the current project, go
to `File→Settings...→palantir-java-format Settings` (or `IntelliJ
IDEA→Preferences...→Other Settings→palantir-java-format Settings` on macOS) and
check the `Enable palantir-java-format` checkbox.

To enable it by default in new projects, use `File→Other Settings→Default
Settings...`.

When enabled, it will replace the normal `Reformat Code` action, which can be
triggered from the `Code` menu or with the Ctrl-Alt-L (by default) keyboard
shortcut.

### Running a pre-release version of the IntelliJ plugin

1. Clone this repo
2. run `./gradlew :idea-plugin:build`
3. In IntelliJ, install a plugin from disk. Build artifacts are located in `./idea-plugin/build/distributions/`

![Install plugin from disk](./docs/images/install_plugin_from_disk.png)

## Eclipse plugin

See [eclipse_plugin](./eclipse_plugin).

## Java 21 support

Upstream [PR 1211](https://github.com/palantir/palantir-java-format/pull/1211) shipped Java 21
support. To use the Java 21 formatting capabilities, ensure that either:

- the Gradle daemon and the IntelliJ Project SDK are set to Java 21
- or the gradle property `palantir.native.formatter=true` is set. This runs the formatter as a
  native image, independent of the Gradle daemon / IntelliJ project JDK version.

### Native image formatter

[This comment](https://github.com/palantir/palantir-java-format/issues/952#issuecomment-2575750610)
explains why upstream switched to a native image for the formatter. The startup time for the native
image, especially in IntelliJ, is >10x faster than spinning up a new JVM process that does the
formatting. However, the throughput of running the native image for a large set of files (e.g.
running `./gradlew spotlessApply`) is considerably slower (e.g. 30s using the Java implementation vs
1m20s using the native image implementation). Therefore, when running the formatter from
`spotlessApply` we default to using the Java implementation (if the Java version >= 21).

The native images are also the strongest argument for this fork — see
[why the build is a black box](#2-the-build-is-a-black-box).

## Future work on the formatter itself

Separate from the infrastructure roadmap above:

- [ ] preserve [NON-NLS markers][] — these are comments used when implementing NLS
  internationalisation, and need to stay on the same line with the strings they come after.

[NON-NLS markers]: https://stackoverflow.com/a/40266605

---

## License and attribution

This project is a fork of [palantir-java-format](https://github.com/palantir/palantir-java-format),
which is itself a fork of [google-java-format](https://github.com/google/google-java-format). It is
distributed under the same [Apache 2.0 License](./LICENSE), and it keeps every upstream copyright
notice — from both projects — permanently.

Neither Palantir Technologies Inc. nor Google LLC endorses, sponsors or is affiliated with this
fork. "Palantir" and "Google" are trademarks of their respective owners, and Apache 2.0 §6 grants no
rights to them; the rename described in this README exists in part for that reason.

```text
(c) Copyright 2019 Palantir Technologies Inc. All rights reserved.

Licensed under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License. You may obtain a copy of
the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under
the License.
```

Original work copyrighted by Google under the same license:

```text
Copyright 2015 Google Inc.

Licensed under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License. You may obtain a copy of
the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under
the License.
```
