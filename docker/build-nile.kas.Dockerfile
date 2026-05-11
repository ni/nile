ARG KAS_IMAGE
FROM ${KAS_IMAGE} as build-nile

ARG SOURCE_DATE_EPOCH
ARG CACHE_SHARING=locked

USER root

# required software packages
# - libtinfo5 - required by xsct flow in meta-xilinx-tools
RUN --mount=type=cache,target=/var/cache/apt,sharing=${CACHE_SHARING} \
    --mount=type=cache,target=/var/lib/apt,sharing=${CACHE_SHARING} \
    apt-get update && \
    apt-get install --no-install-recommends --assume-yes \
        libtinfo5 && \
    rm -rf /var/log/* /tmp/* /var/tmp/* /var/cache/ldconfig/aux-cache

USER builder
