Change Log
==========

Version 1.4.0 *(2019-05-05)*
----------------------------

 * Fix: Prefer the `jvm` directory for multiplatform builds.
 * Fix: Ignore directories that don't have Javadoc.
 * Fix: Use classic endpoint for Maven Central.


Version 1.3.0 *(2018-06-28)*
----------------------------

 * New: Find index.html wherever it is in the Javadoc. This improves behavior for Dokka.
 * New: CLI arguments `--force` to publish already-published files.
 * New: CLI arguments `--dry-run` to not push docs up to GitHub.
 * Fix: Wait for process to finish before requesting its exit value.


Version 1.2.0 *(2016-03-01)*
----------------------------

 * New: Add simple index page for multi-artifact projects.
 * Fix: Propagate git client failures to fail the deploy.


Version 1.1.0 *(2015-10-26)*
----------------------------

 * New: Publish only major versions.


Version 1.0.0 *(2015-10-25)*
----------------------------

Initial release.
