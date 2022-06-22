# @guardian/cdk Typescript app

*Generated from: github.com/guardian/cdk-app-ts.*

Some example commands are:

    $ npx projen synth           // synthesise your CDK project
    $ npx projen dependencies    // install dependencies based on lockfile e.g. during CI
    $ npx projen --help          // see all available commands (test, lint, etc.)
    $ npx projen                 // update the scaffold itself

Reminder: this starter-kit uses projen (https://github.com/projen/projen).

Unlike most starter-kits, projen is not a one-off generator, and synthesized
files should not be manually edited. The only files you should edit directly
are:

- 'lib/' - your Typescript CDK files and tests
- '.projenrc.js' - to update settings, e.g. to add extra dev dependencies (run
  'npx projen' to re-synth after any changes)

All commands should be executed via `npx projen` rather than targeting `yarn`
(or `npm`) directly.

## Getting started with CDK

If you are new to CDK the following resources are helpful:

* https://github.com/guardian/cdk/
* https://docs.aws.amazon.com/cdk/v2/guide/getting_started.html