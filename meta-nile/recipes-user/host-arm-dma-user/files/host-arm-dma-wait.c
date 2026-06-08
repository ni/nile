/******************************************************************************
 *
 *   Copyright (C) 2026 National Instruments Corporation.
 *
 *   SPDX-License-Identifier: GPL-2.0
 *
 *****************************************************************************/

#include <errno.h>
#include <inttypes.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/epoll.h>
#include <sys/eventfd.h>
#include <sys/ioctl.h>

#include "host-arm-dma-uapi.h"

/*
 * High-level module note:
 * This userspace helper supports a proof-of-concept flow where the host DMA
 * engine places data for consumption by the demo application
 * (host-arm-dma-wait) running in the PS userspace.
 *
 * The flow is:
 * 1) The PL raises an interrupt when a DMA transaction is ready to process.
 * 2) The kernel driver notifies userspace through an eventfd registered with
 *    a custom ioctl, providing lower-latency signaling to this application.
 * 3) After processing, this application writes back to the device to signal
 *    to the host side that processing is complete.
 */
#define SHARED_MEM_BASE 0x70100000UL
#define SHARED_MEM_SIZE 0x100000UL

static volatile sig_atomic_t stop_requested;

static void handle_stop_signal(int signo)
{
	(void)signo;
	stop_requested = 1;
}

int main(int argc, char *argv[])
{
	const char *dev_path = "/dev/host-arm-dma";
	int verbose = 0;
	struct sigaction sa = { 0 };
	int fd;
	int efd = -1;
	int epfd = -1;
	int memfd = -1;
	int ret;
	uint8_t *shared_mem = MAP_FAILED;

	for (int i = 1; i < argc; i++) {
		if (strcmp(argv[i], "-v") == 0 || strcmp(argv[i], "--verbose") == 0) {
			verbose = 1;
		} else {
			fprintf(stderr, "Unknown argument: %s\n", argv[i]);
			fprintf(stderr, "Usage: %s [-v|--verbose]\n", argv[0]);
			return EXIT_FAILURE;
		}
	}

	sa.sa_handler = handle_stop_signal;
	sigemptyset(&sa.sa_mask);
	if (sigaction(SIGINT, &sa, NULL) < 0 || sigaction(SIGTERM, &sa, NULL) < 0) {
		fprintf(stderr, "Failed to install signal handlers: %s\n", strerror(errno));
		return EXIT_FAILURE;
	}

	fd = open(dev_path, O_RDWR);
	if (fd < 0) {
		fprintf(stderr, "Failed to open %s: %s\n", dev_path, strerror(errno));
		return EXIT_FAILURE;
	}

	/*
	 * Use semaphore mode so each read consumes one signal instead of draining
	 * the full counter value, which preserves pending wakeups across loops.
	 */
	efd = eventfd(0, EFD_CLOEXEC | EFD_SEMAPHORE);
	if (efd < 0) {
		fprintf(stderr, "Failed to create eventfd: %s\n", strerror(errno));
		close(fd);
		return EXIT_FAILURE;
	}

	memfd = open("/dev/mem", O_RDONLY | O_SYNC);
	if (memfd < 0) {
		fprintf(stderr, "Failed to open /dev/mem: %s\n", strerror(errno));
		close(efd);
		close(fd);
		return EXIT_FAILURE;
	}

	shared_mem = mmap(NULL, SHARED_MEM_SIZE, PROT_READ, MAP_SHARED, memfd,
			  SHARED_MEM_BASE);
	if (shared_mem == MAP_FAILED) {
		fprintf(stderr,
			"Failed to map shared memory at 0x%lx (size 0x%lx): %s\n",
			(unsigned long)SHARED_MEM_BASE,
			(unsigned long)SHARED_MEM_SIZE,
			strerror(errno));
		close(memfd);
		close(efd);
		close(fd);
		return EXIT_FAILURE;
	}

	ret = ioctl(fd, HOST_ARM_DMA_IOC_SET_EVENTFD, &efd);
	if (ret < 0) {
		fprintf(stderr, "Failed to register eventfd with driver: %s\n",
			strerror(errno));
		munmap(shared_mem, SHARED_MEM_SIZE);
		close(memfd);
		close(efd);
		close(fd);
		return EXIT_FAILURE;
	}

	epfd = epoll_create1(EPOLL_CLOEXEC);
	if (epfd < 0) {
		fprintf(stderr, "Failed to create epoll fd: %s\n", strerror(errno));
		(void)ioctl(fd, HOST_ARM_DMA_IOC_CLR_EVENTFD);
		munmap(shared_mem, SHARED_MEM_SIZE);
		close(memfd);
		close(efd);
		close(fd);
		return EXIT_FAILURE;
	}

	{
		struct epoll_event ev = { 0 };

		ev.events = EPOLLIN;
		ev.data.fd = efd;
		if (epoll_ctl(epfd, EPOLL_CTL_ADD, efd, &ev) < 0) {
			fprintf(stderr, "Failed to add eventfd to epoll: %s\n",
				strerror(errno));
			close(epfd);
			(void)ioctl(fd, HOST_ARM_DMA_IOC_CLR_EVENTFD);
			munmap(shared_mem, SHARED_MEM_SIZE);
			close(memfd);
			close(efd);
			close(fd);
			return EXIT_FAILURE;
		}
	}

	if (verbose) {
		printf("Waiting for interrupts on %s via epoll/eventfd (Ctrl-C to exit)\n",
		       dev_path);
	}

	uint64_t irq_count = 0;
	while (!stop_requested) {
		struct epoll_event ev;
		uint64_t signaled = 0;
		int nready = epoll_wait(epfd, &ev, 1, -1);

		if (nready < 0 && errno == EINTR)
			continue;
		if (nready < 0) {
			printf("epoll_wait failed: %s\n", strerror(errno));
			break;
		}
		if (nready == 0) {
			continue;
		}

		if (ev.data.fd == efd && (ev.events & EPOLLIN)) {
			++irq_count;
			uint8_t *shared_mem_snapshot = NULL;
			uint32_t shared_mem_read_bytes = 0;
			struct timespec memcpy_start;
			struct timespec memcpy_end;
			double memcpy_us = -1.0;

			if (SHARED_MEM_SIZE < sizeof(shared_mem_read_bytes)) {
				printf("Shared memory size too small to read length field\n");
				break;
			}

			/* Read length field as volatile uint32_t to ensure proper access */
			volatile uint32_t *len_ptr = (volatile uint32_t *)shared_mem;
			shared_mem_read_bytes = *len_ptr;

			if (shared_mem_read_bytes > (SHARED_MEM_SIZE - sizeof(shared_mem_read_bytes))) {
				printf("Invalid shared memory length %" PRIu32
				       " (max payload %lu)\n",
				       shared_mem_read_bytes,
				       (unsigned long)(SHARED_MEM_SIZE - sizeof(shared_mem_read_bytes)));
				break;
			}

			if (shared_mem_read_bytes > 0) {
				shared_mem_snapshot = malloc(shared_mem_read_bytes);
				if (!shared_mem_snapshot) {
					printf("Failed to allocate %" PRIu32 " bytes for shared memory snapshot\n",
					       shared_mem_read_bytes);
					break;
				}
			}

			clock_gettime(CLOCK_MONOTONIC, &memcpy_start);
			if (shared_mem_read_bytes > 0) {
				/* Read payload in 4-byte aligned chunks using volatile access */
				volatile uint32_t *src = (volatile uint32_t *)(shared_mem + sizeof(shared_mem_read_bytes));
				uint32_t *dst = (uint32_t *)shared_mem_snapshot;
				uint32_t remaining = shared_mem_read_bytes;

				/* Copy 4-byte aligned chunks */
				while (remaining >= sizeof(uint32_t)) {
					*dst++ = *src++;
					remaining -= sizeof(uint32_t);
				}

				/* Copy any remaining bytes (< 4) */
				if (remaining > 0) {
					uint8_t *src_bytes = (uint8_t *)src;
					uint8_t *dst_bytes = (uint8_t *)dst;
					for (uint32_t i = 0; i < remaining; i++) {
						dst_bytes[i] = src_bytes[i];
					}
				}
			}
			clock_gettime(CLOCK_MONOTONIC, &memcpy_end);
			memcpy_us =
				((double)(memcpy_end.tv_sec - memcpy_start.tv_sec) * 1000000.0) +
				((double)(memcpy_end.tv_nsec - memcpy_start.tv_nsec) / 1000.0);

			uint32_t trigger_value = (uint32_t)irq_count;
			ssize_t wn = write(fd, &trigger_value, sizeof(trigger_value));
			if (wn != (ssize_t)sizeof(trigger_value)) {
				if (wn < 0)
					printf("Trigger write failed: %s\n", strerror(errno));
				else
					printf("Trigger short write: got %zd bytes, expected %zu\n",
						wn, sizeof(trigger_value));
				free(shared_mem_snapshot);
				break;
			}

			ssize_t rn = read(efd, &signaled, sizeof(signaled));
			if (rn != (ssize_t)sizeof(signaled)) {
				if (rn < 0)
					printf("eventfd read failed: %s\n", strerror(errno));
				else
					printf("eventfd short read: got %zd bytes, expected %zu\n",
						rn, sizeof(signaled));
				free(shared_mem_snapshot);
				break;
			}

			if (verbose) {
				printf("Interrupt received, count=%" PRIu64 " (batch=%" PRIu64 ")\n",
				       irq_count, signaled);
				printf("Shared memory [0x%lx] payload (%" PRIu32 " bytes):",
				       (unsigned long)SHARED_MEM_BASE,
				       shared_mem_read_bytes);
				printf("\n");
				printf("memcpy time: %.3f us\n", memcpy_us);
			}
			free(shared_mem_snapshot);

			fflush(stdout);
			continue;
		}

		fprintf(stderr, "Unexpected epoll event: 0x%x\n", ev.events);
		break;
	}

	(void)ioctl(fd, HOST_ARM_DMA_IOC_CLR_EVENTFD);
	munmap(shared_mem, SHARED_MEM_SIZE);
	close(memfd);
	close(epfd);
	close(efd);
	close(fd);
	printf("Exiting.\n");

	return EXIT_SUCCESS;
}
