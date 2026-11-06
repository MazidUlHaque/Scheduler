# Contributing

Thanks for your interest in Scheduler!

## Ground rules
- This is an **All Rights Reserved** project. By submitting a contribution, you
  agree to the contribution terms in section 4 of [LICENSE](LICENSE).
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
