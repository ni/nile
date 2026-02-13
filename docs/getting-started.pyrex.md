> NOTE: The pyrex build flow is deprecated and will be removed in the future.

Getting Started (Pyrex build flow)
----------------------------------

1. Clone the git repository:

    $ git clone https://github.com/ni/nile.git

2. Check out the appropriate branch (default scarthgap based branch is OK for now):

    $ cd nile

3. Update the submodules:

    $ git submodule update --init --recursive

4. Build docker image for pyrex

    $ bash ./docker/create-build-nile.sh

	Verify the image was created:
    $ docker images build-nile

5. Initialize the build system:

    $ source ./nile-oe-init-build-env

6. Build an image:

    $ bitbake nile-image-dev

7. Test it in qemu with:

    $ runqemu nile-image-dev

8. Build an sdk:

    $ bitbake -c populate_sdk nile-image-dev

