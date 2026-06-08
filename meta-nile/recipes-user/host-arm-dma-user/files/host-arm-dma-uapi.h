/* SPDX-License-Identifier: GPL-2.0 */
#ifndef HOST_ARM_DMA_UAPI_H
#define HOST_ARM_DMA_UAPI_H

#include <sys/ioctl.h>

#define HOST_ARM_DMA_IOC_MAGIC 'H'
#define HOST_ARM_DMA_IOC_SET_EVENTFD \
	_IOW(HOST_ARM_DMA_IOC_MAGIC, 0x01, int)
#define HOST_ARM_DMA_IOC_CLR_EVENTFD \
	_IO(HOST_ARM_DMA_IOC_MAGIC, 0x02)

#endif /* HOST_ARM_DMA_UAPI_H */
