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
$ ./has-container build kas/target-name:kas/ni-org.yml
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
