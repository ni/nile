# Image Self-Test

It is generally expected that most NILE-based products will have some
service or set of services to provide product-specific functionality.
NILE maintainers are interested in being able to assess that changes to
shared infrastructure do not break that product-specific behavior, but
we need a common way to determine if product-specific behavior is okay.

Therefore, NILE presents a self-test hook for clients to implement.

## Implementing the Self-Test

Clients are expected to have a `nile-selftest.bbappend` that installs a
script or executable of their choosing as `/usr/bin/nile-selftest`.

`nile-selftest` is currently run through a ptest runner, and as such, any
output it has should follow [ptest output format](https://docs.yoctoproject.org/scarthgap/test-manual/ptest.html#testing-packages-with-ptest).
The ptest runner also checks the exit code of `nile-selftest` and reports
a failure on non-zero return.

See [the meta-nile-test implementation](/meta-nile-test/recipes-test/nile-selftest)
for an example.

Note that failure to implement a self-test is not treated as a build
failure, but it will be considered a self-test failure.

nile-selftest is implemented with a recipe that clients can .bbappend if
they need custom rules (such as to compile an executable), but it is
recommended that you NOT have an RDEPEND in nile-selftest.bbappend on any
packages that you expect to be in your image, so as to avoid the
nile-selftest package from becoming load-bearing.

## What should Self-Test cover?

The self-test should be generally pretty simple, and operate "quickly".
Things that would be appropriate for a self-test:
- ensure that particular drivers have loaded
- check to see if a service or set of services are running
- perform a "ping" against a local service to ensure it's responding

More rigorous testing should be handled in product-specific test suites,
and not the self-test.
