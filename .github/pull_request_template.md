**IMPORTANT: Where possible all PRs must be linked to a Github issue**

Resolves [link to issue]

**Engineer Checklist**

- [ ] I have written **Unit tests** for any new feature(s) and edge cases for
      bug fixes
- [ ] I have added documentation for any new feature(s) and configuration
      option(s) on the `README.md`
- [ ] I have run `mvn spotless:check` to check my code follows the project's
      style guide
- [ ] I have run `mvn clean test jacoco:report` to confirm the coverage report
      was generated at `plugins/target/site/jacoco/index.html`
- [ ] I ran `mvn clean package` right before creating this pull request.
