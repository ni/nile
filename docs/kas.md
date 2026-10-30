# NI Linux Embedded kas README

## Introduction

NI Linux Embedded uses [kas](https://kas.readthedocs.io/en/latest/intro.html)
for build tooling.

kas provides an extensive toolset for OpenEmbedded layer management,
using a configuration-based approach to handle layer retrieval and
bitbake setup.

It replaces our earlier git-submodule based approach, which required
extra checkout steps and was more error-prone.

## Usage

First, build the container image:

```
$ bash ./docker/create-build-nile.sh --base kas
```

Then you can use the `kas-container` script
[as directed in the kas documentation](https://kas.readthedocs.io/en/latest/userguide/kas-container.html):

```
$ ./kas-container dump kas/target-name.yml
$ ./kas-container build kas/target-name:kas/ni-org.yml
$ ./kas-container shell kas/target-name.yml
```

## Running Virtual Machines

Because `qemu` doesn't work well within the kas build container, we have
another script, `./kas-runqemu`, which has a command-line interface similar
to the [yocto upstream runqemu](https://docs.yoctoproject.org/dev-manual/qemu.html#qemu-command-line-syntax),
but takes as its first argument a kas project configuration yaml string,
which is then used to find the appropriate qemu configuration options.

```
$ ./kas-runqemu kas/target-name.yml
$ ./kas-runqemu kas/target-name.yml serial nographic
```

## Conventions

All kas configuration files should be under a `kas/` subdirectory.

(We will likely have to develop an organization under this subdirectory
as we onboard more targets.)

## Internal builders

In order to efficiently support options relevant to internal builders,
we have an `ni-org.yml` snippet that supplies additional configuration
for utilizing corporate network resources (such as the internally-hosted
source mirror).

External builders _should not_ add `ni-org.yml`.

### Shared sstate cache

The NILE PR pipeline publishes its sstate cache to the Argo `rnd` share.
Internal builders can reuse it, so that a cold build fetches prebuilt objects
instead of building them from source.

1. Mount the Argo `rnd` share on the build host. Use a `cifs` mount: a WSL2
   `drvfs` mount mangles the `:` in sstate file names, so nothing matches.
2. Set `ARGO_RND_MOUNT` to the mount point and add
   `kas/includes/argo-sstate.yml` to the build:

```
$ export ARGO_RND_MOUNT=/mnt/argo/RnD
$ ./kas-container build kas/target-name.yml:kas/includes/argo-sstate.yml
```

`kas-container` mounts `ARGO_RND_MOUNT` read-only into the build container;
the path must not contain spaces. The include uses the newest five cache
snapshots as sstate mirrors, and does nothing if `ARGO_RND_MOUNT` is unset.
Only tasks whose signatures match the pipeline's build come from the cache,
so recipes you have changed still build locally.

## Building images from IPK feeds

Use this to install packages from a prebuilt IPK feed during image assembly,
instead of building those packages from source.

### HTTP(S) feed server

1. Include `kas/includes/image-from-feeds.yml` in your target configuration:

```yaml
header:
  version: 19
  includes:
    - kas/includes/base-config.yml
    - kas/machines/<machine>.yml
    - kas/includes/image-from-feeds.yml
```

2. Set `NILE_FEEDS_URI` to your feed base URI. The feed should provide
`all/`, `${MACHINE}/`, and `${TUNE_PKGARCH}/` subdirectories:

```conf
NILE_FEEDS_URI = "http://nibuild-feed-server/path/to/ipk/export"
```

If unset, `NILE_FEEDS_URI` defaults to `file://${DEPLOY_DIR_IPK}`.

### Optional: feeds in shared filesystem path

Use this only when `NILE_FEEDS_URI` is a `file://` path outside the build
directory (for example, a local path or mounted fileshare on the host).
Mount that host path with `KAS_EXTRA_ARGS` and set `NILE_FEEDS_URI` to the
container-side path:

```bash
KAS_EXTRA_ARGS="-v /host/path/to/feeds:/feeds" \
  NILE_FEEDS_URI="file:///feeds" \
  ./kas-container build kas/your-target.yml:kas/includes/image-from-feeds.yml
```

### Optional: feed-only packages

If a package exists only in the feed (no local provider in your current build
graph), append it via:

```conf
IMAGE_INSTALL_NODEPS:append = " <feed-only-package>"
```
