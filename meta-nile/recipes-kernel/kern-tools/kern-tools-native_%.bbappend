# backport for scarthgap in order to support "depends on X if Y" syntax
# with kernel 7.0 backports into 6.18 kernels, which prevents us from using
# latest stable 6.18 kernels with scarthgap.
#
# https://git.openembedded.org/openembedded-core/commit/meta/recipes-kernel/kern-tools/kern-tools-native_git.bb?h=wrynose&id=a96e190960a5bb2a3448e1078afadf8ef839e194
#
# We can remove this workaround once main is on wrynose.
SRCREV = "96a8d58bcd260f104c193a81b727818424d7b263"
