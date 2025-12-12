package-index target should be built separately after all packages have been built
https://docs.yoctoproject.org/dev/dev-manual/packages.html#build-considerations

For example:

```
kas-container build $(KAS_COMBINED_CONFIG)
kas-container build --target package-index $(KAS_COMBINED_CONFIG)
```

Internal feed build conventions are:

```
KAS_CONFIG_DIR = $(NILE_ROOT)/kas
KAS_CONFIG = $(KAS_CONFIG_DIR)/$(SUBCOMPONENT).yml
```

So, for example, `kas/nile_oe_feed_arm64.yml` will be used for a subcomponent named
`nile_oe_feed_arm64`.
