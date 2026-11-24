# Release notes

One file per release, named after its tag: `21.1.0.md` for the tag `21.1.0`. It goes onto the release line in a
pull request before the tag is pushed, and the release workflow publishes nothing for a tag without one.

The file is used twice, as it is written:

- **The GitHub release.** JReleaser makes it the text of the draft release, which is then published by hand.
- **The IntelliJ plugin's change notes**, which the JetBrains Marketplace shows for the version and the IDE shows
  under **What's New**. The IDE does not render Markdown, so the workflow renders the file to HTML with GitHub's
  Markdown API, as GitHub renders a release. The Marketplace never lets the change notes of an uploaded version
  change.

For the notes to read the same in both places:

- Write each paragraph and each list item on one line. GitHub renders a release with a line break at every newline,
  and so does the API here.
- Write every link in full, `[#259](https://github.com/openjavaformat/open-java-format/pull/259)`. The IDE gets no
  link for a bare `#259` or `@name`, and a relative link leads nowhere outside the repository.
- Keep to paragraphs, `##` headings, lists, links, `code` and **bold**: what the IDE's plugin page shows.

The release workflow, [`.github/workflows/release.yml`](../.github/workflows/release.yml), has the rest.
