# Contributing

The team welcomes contributions!  To make changes:

- Fork the repo and make a branch
- Write your code (ideally with tests) and make sure the GitHub Actions build passes
  (`mise run build` locally, `mise run ci:build` to exercise the workflow itself)
- Open a PR (optionally linking to a github issue)

## Local development

We recommend using [Intellij IDEA Community Edition](https://www.jetbrains.com/idea/) for Java projects.

Install the tooling with [mise](https://mise.jdx.dev/) — it pins the bootstrap JDK and `act`:

1. Fork the repository
1. `mise trust && mise install`
1. `mise run build`
1. Import the project into IntelliJ

See [Building and testing locally](README.md#building-and-testing-locally) for the full set of
tasks, how to run the CI workflows under `act`, and which JDKs the build needs.

Tips:

- run `mise run format` before pushing; the build fails if the repo is not formatted with the
  formatter it builds.

## Working on `:idea-plugin`

Tip: run `mise run idea` to spin up an instance of IntelliJ with the plugin applied.
