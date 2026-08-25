# STM32MP257F Target

NILE target for STM32MP257F-EV1 using ST BSP kernel/boot stack and NILE user space.

## Files

- Target file: `kas/targets/nile-image-dev_stm32mp257f.yml`
- Machine include: `kas/machines/stm32mp257f.yml`
- ST BSP layer include: `kas/includes/meta-st-stm32mp.yml`
- Validated on the `STM32MP257F-EV1` evaluation board

## Build

```
$ ./kas-container build kas/targets/nile-image-dev_stm32mp257f.yml
```

## Flash (SD Card)

Deploy dir:

`build/tmp/deploy/images/stm32mp25-eval/`

Use a whole-card WIC image:

- `nile-image-dev-stm32mp25-eval.rootfs.wic`
- `nile-image-dev-stm32mp25-eval.rootfs-<timestamp>.wic`

Do not use `splitted-*` for full SD-card flashing.

## Workaround

`kas/machines/stm32mp257f.yml` appends:

- `modprobe.blacklist=etnaviv`
- `initcall_blacklist=etnaviv_init`

This avoids an OP-TEE panic observed during `etnaviv` GPU initialization on STM32MP257F-EV1.
