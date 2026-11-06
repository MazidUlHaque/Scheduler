# Contributing

Thanks for your interest in Scheduler!

## Ground rules
- By contributing, you agree that your contribution is licensed to the project
  owner under the same terms as the project ([LICENSE](LICENSE)), and that the
  owner may also relicense it, including under commercial terms.
- Be respectful. See the [Code of Conduct](CODE_OF_CONDUCT.md).

## Reporting bugs / requesting features
Use the issue templates. For bugs, include your device, Android version and steps to reproduce.

## Development workflow
1. Fork the repo and create a branch: `feature/<short-name>` or `fix/<short-name>`.
2. Follow [Conventional Commits](https://www.conventionalcommits.org/), e.g. `feat(parser): support "day after tomorrow"`.
3. Run checks locally before pushing:
   ```bash
   ./gradlew spotlessCheck detekt lint testDebugUnitTest
   ```
4. Open a pull request using the template. CI must pass.

## Code style
- Kotlin official style (enforced by ktlint via Spotless) and detekt rules.
- Compose: stateless composables + state hoisting; previews for every screen component.
- Every parser change must come with unit tests.
