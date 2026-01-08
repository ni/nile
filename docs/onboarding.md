# Onboarding onto NI Linux Embedded

_NOTE: This document is a work in progress, and is intended for internal NI clients._

## Introduction

NI Linux Embedded is intended to provide a foundation for multiple embedded
products developed by NI, and is intended to present a unified approach to
many of the challenges faced by NI products that have "rolled their own"
OpenEmbedded-based product firmwares in the past.

This document aims to describes the requirements and expectations for product
teams to be able to use NILE as a base.

### Conventions used by this document

This document will use
[`xyzzy`](https://en.wikipedia.org/wiki/Xyzzy_%28computing%29) as an example
product name, and is not intended to represent any particular product or
codename.

### Differences from NI Linux RT

At present, NI Linux Embedded is primarily differentiated by use cases:

NI Linux Embedded is intended for devices with a fixed personality that
serve as connected peripherals to larger systems, and where that personality
is not intended to be runtime-modifiable; firmware updates are distributed
as atomic updates.

NI Linux RT is intended for devices running the full NI software ecosystem,
which are open and programmable by end users though LabVIEW RT, LabVIEW
FPGA, DAQmx, and other such toolkits. Software updates are distributed as
independent packages.

## Layer organization

Product-specific changes should do into their own layer repository. This
should be hosted under the NI GitHub organization, with a name like
`meta-xyzzy`.

This should in turn be organized as follows:
```
meta-xyzzy.git
|-- meta-xyzzy/
|   |-- classes/
|   |-- conf/
|   |   |-- machine/
|   |   |   |-- xyzzy.conf
|   |   |-- layer.conf
|   |-- recipes-core/
|   |-- recipes-ni/
|   ...
|-- LICENSE
|-- README.md
...
```

A repo that covers multiple products related enough to be in the same repo
but dissimilar enough that they need different recipes may have multiple
`meta-` layer subdirectories.

It is recommended that your branch name be something like
`nile/<version>/<oename>`, e.g., `nile/26.0/scarthgap`.

## kas Configuration

You should add one or more kas configuration yamls to the `kas` directory in
nile:

```
nile.git/
|-- kas/
|   |-- includes/
|   |   |-- meta-xyzzy.yml
|   |   |-- meta-*.yml
|   |-- machines/
|   |   |-- xyzzy.yml
|   |-- targets/
|   |   |-- xyzzy-firmware_xyzzy.yml
... ... ...
```

### Repo Includes

You should add common repo configuration (and other necessary settings) into
a common YAML that can be shared amongst multiple build targets. Generally
this should be one repo url/path per yml, although these may be combined if
repos are related ("meta-xilinx" and "meta-xilinx-tools" might be in a
single `meta-xilinx.yml`, for example.)

```yaml
# kas/includes/meta-xyzzy.yml

header:
  version: 19

local_conf_header:
  some-feature-specific-to-these-repos: |
    # if there are specific local.conf fragments for these repos, put
    # them here (for example, setting LICENSE_FLAGS_ACCEPTED on xilinx
    # repos)

repos:
  meta-xyzzy:
    url: "https://github.com/ni/meta-xyzzy.git"
    path: "layers/meta-xyzzy"
    branch: "nile/26.0/scarthgap"
```

### Machine Includes

YAML configuration under `machines/` should include all repo ymls necessary,
as well as define any additional layers needed for this machine type.

```yaml
# kas/machines/xyzzy.yml

header:
  version: 19
  includes:
    - kas/includes/base-config.yml
    - kas/includes/meta-xyzzy.yml
    # add includes for any other repos

local_conf_header:
  some-feature-specific-to-this-machine: |
    # if there are specific local.conf fragments for this machine, put
    # them here (for example, setting OLDEST_KERNEL, if this machine
    # needs to use a kernel version below OE's requirements.)

machine: xyzzy

repos:
  meta-xyzzy:
    layers:
      meta-xyzzy:
```

**NOTE**: [kas documentation](https://kas.readthedocs.io/en/latest/userguide/project-configuration.html#configuration-reference)
says that if `layers` is omitted, then the repo itself is added as a layer.
However, the actual merge logic will consider your `meta-xyzzy` key as
having a null value, which will not merge with the include's definition in
the expected way. The correct way to handle this case is by adding `.`:

```
repos:
  meta-xyzzy:
    layers:
      .:
```

### Target Includes

Meanwhile, YAML configuration under `targets/` should correspond one-to-one to
pipeline build targets (see next section). It is recommended that this file
be named `<target>_<machine>.yml`.

```yaml
# kas/targets/xyzzy-firmware_xyzzy.yml

header:
  version: 19
  includes:
    - kas/machines/xyzzy.yml

target:
  - xyzzy-firmware
```

## Build Pipeline

_TODO. Since this is largely internal-focused should this link to a document
on the internal wiki?_

## Integrating NI-proprietary Software

_TODO, needs to be fleshed out with actual process steps, but will largely
look like nilrt's implementation?_

An expected need for most NI products is that the firmware images will need
to include some number of NI-proprietary binaries that are not open-source.

The preferred way that this be handled is:

1. Build your software using an external build system, packaged into IPKs.
2. Import a feed containing those IPKs, along with the NILE package feed, into your final image build.
