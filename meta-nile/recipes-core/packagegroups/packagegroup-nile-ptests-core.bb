# The normal way that one adds ptests to a build is by using
#    EXTRA_IMAGE_FEATURES += "ptest-pkgs"
# This will add every *-ptest package that corresponds to a package
# that goes into the image.
#
# Unfortunately, "every ptest package" can take a while to execute,
# and it is often more useful to have a minimal set of tests to run.
# This packagegroup provides the list of such tests.

SUMMARY = "Core package tests"

inherit packagegroup

RDEPENDS:${PN} += "ptest-runner "

RDEPENDS:${PN} += "\
    xz-ptest \
    "
