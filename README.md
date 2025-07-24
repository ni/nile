NI Linux Embedded (NILE)
=============================================
This repository provides the necessary configuration and git submodules required
to build the NI Linux Embedded distribution.

The initial implementation sets up the OpenEmbedded build system with
meta-xilinx and other layers required to build images for Xilinx development
boards and some vendor boards.

OpenEmbedded allows the creation of custom Linux distributions for embedded
systems. It is a collection of git repositories known as *layers* each of which
provides *recipes* to build software packages as well as configuration
information.

Information about the branch names is available at
https://wiki.yoctoproject.org/wiki/Releases.


Authentication to internal repos
--------------------------------

This layer contains recipes that depend on NI-internal repositories.
These require `git` to be aware of authentication credentials.

- [Create an AzDO Personal Access Token](https://learn.microsoft.com/en-us/azure/devops/organizations/accounts/use-personal-access-tokens-to-authenticate?view=azure-devops&tabs=Windows)
- Create the following file at `$HOME/.netrc` with the generated token:
    ```
    machine dev.azure.com login api password <azdo access token>
    ```


Getting Started
---------------

1. Clone the git repository:

    $ git clone https://github.com/ni/nile.git

2. Check out the appropriate branch (default scarthgap based branch is OK for now):

    $ cd nile

3. Update the submodules:

    $ git submodule update --init --recursive

4. Initialize the build system:

    $ source ./setup-nile <template_name>

	example:
    $ source ./setup-nile kula

5. Build an image:

    $ bitbake core-image-full-cmdline

6. Test it in qemu with:

    $ runqemu core-image-full-cmdline

7. Build an sdk:

    $ bitbake -c populate_sdk core-image-full-cmdline
