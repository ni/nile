/******************************************************************************
 *
 *   Copyright (C) 2026 National Instruments Corporation.
 *
 *   SPDX-License-Identifier: GPL-2.0
 *
 *****************************************************************************/

/*
 * This kernel module supports a proof-of-concept data path where the host DMA
 * engine transfers data for consumption by the PS userspace demo application
 * host-arm-dma-wait. The module receives PL interrupts when a DMA transaction
 * is ready to process, then notifies userspace through an eventfd registered
 * via a custom ioctl for lower-latency signaling. After processing, userspace
 * writes back to this device node to signal to the host that processing is
 * complete.
 */

#include <linux/module.h>
#include <linux/platform_device.h>
#include <linux/of.h>
#include <linux/interrupt.h>
#include <linux/io.h>
#include <linux/ktime.h>
#include <linux/fs.h>
#include <linux/miscdevice.h>
#include <linux/slab.h>
#include <linux/uaccess.h>
#include <linux/ioctl.h>
#include <linux/eventfd.h>
#include <linux/gpio/consumer.h>
#include <linux/of_address.h>
#include <linux/ioport.h>

#define DRIVER_NAME "host-arm-dma"
#define DRIVER_VERSION "0.1.0"

#define HOST_ARM_DMA_IOC_MAGIC 'H'
#define HOST_ARM_DMA_IOC_SET_EVENTFD \
	_IOW(HOST_ARM_DMA_IOC_MAGIC, 0x01, int)
#define HOST_ARM_DMA_IOC_CLR_EVENTFD \
	_IO(HOST_ARM_DMA_IOC_MAGIC, 0x02)

/* Matches drivers/gpio/gpio-xilinx.c register layout */
#define XGPIO_CHANNEL1_OFFSET	0x8
#define XGPIO_GIER_OFFSET	0x11c
#define XGPIO_GPIER_OFFSET	0x128

struct host_arm_dma_dev {
	struct device *dev;
	void __iomem *host_irq_out_data_reg;
	struct gpio_desc *irq_in_gpio;
	int irq_in;
	bool irq_enabled;
	atomic_long_t irq_count;
	u64 last_isr_ns;
	spinlock_t eventfd_lock;
	struct eventfd_ctx *eventfd;
	struct miscdevice miscdev;
};

static void host_arm_dma_set_irqs_enabled(struct host_arm_dma_dev *dma,
					  bool enable)
{
	if (dma->irq_enabled == enable)
		return;

	if (dma->irq_in > 0) {
		if (enable)
			enable_irq(dma->irq_in);
		else
			disable_irq(dma->irq_in);
	}

	dma->irq_enabled = enable;
}

static void host_arm_dma_iounmap_action(void *data)
{
	iounmap(data);
}

static int host_arm_dma_init_host_irq_out(struct platform_device *pdev,
					  struct host_arm_dma_dev *dma)
{
	struct device_node *host_irq_out_np;
	struct resource res;
	void __iomem *regs;
	int ret;

	host_irq_out_np = of_parse_phandle(pdev->dev.of_node,
					  "host-irq-out-gpios", 0);
	if (!host_irq_out_np) {
		dev_err(&pdev->dev,
			"Failed to parse host-irq-out-gpios[0]\n");
		return -EINVAL;
	}

	ret = of_address_to_resource(host_irq_out_np, 0, &res);
	of_node_put(host_irq_out_np);
	if (ret) {
		dev_err(&pdev->dev,
			"Failed to get host-irq-out GPIO resource: %d\n", ret);
		return ret;
	}

	regs = ioremap(res.start, resource_size(&res));
	if (!regs) {
		dev_err(&pdev->dev,
			"Failed to map host-irq-out GPIO register space\n");
		return -ENOMEM;
	}

	ret = devm_add_action_or_reset(&pdev->dev,
					 host_arm_dma_iounmap_action,
					 regs);
	if (ret)
		return ret;

	dma->host_irq_out_data_reg = regs + XGPIO_CHANNEL1_OFFSET;

	return 0;
}

static int host_arm_dma_open(struct inode *inode, struct file *file)
{
	struct miscdevice *miscdev = file->private_data;
	struct host_arm_dma_dev *dma =
		container_of(miscdev, struct host_arm_dma_dev, miscdev);

	file->private_data = dma;

	return 0;
}

static int host_arm_dma_release(struct inode *inode, struct file *file)
{
	file->private_data = NULL;
	return 0;
}

static ssize_t host_arm_dma_write(struct file *file, const char __user *buf,
				  size_t count, loff_t *ppos)
{
	struct host_arm_dma_dev *dma = file->private_data;
	u32 value;
	u64 last_isr_ns;
	u64 write_trigger_ns;
	u64 latency_ns;

	if (!dma)
		return -EINVAL;

	if (count < sizeof(value))
		return -EINVAL;

	if (copy_from_user(&value, buf, sizeof(value)))
		return -EFAULT;

	if (!dma->host_irq_out_data_reg)
		return -EINVAL;

	writel(value, dma->host_irq_out_data_reg);

	write_trigger_ns = ktime_get_ns();
	last_isr_ns = READ_ONCE(dma->last_isr_ns);

	if (last_isr_ns && write_trigger_ns >= last_isr_ns) {
		latency_ns = write_trigger_ns - last_isr_ns;
		dev_dbg(dma->dev,
			 "Interrupt sent (value: %u, dma->irq_count=%ld), ISR->write latency: %llu ns\n",
			 value, atomic_long_read(&dma->irq_count), latency_ns);
	} else {
		dev_dbg(dma->dev,
			 "Interrupt sent (value: %u, dma->irq_count=%ld), ISR->write latency unavailable\n",
			 value, atomic_long_read(&dma->irq_count));
	}

	return sizeof(value);
}

static long host_arm_dma_ioctl(struct file *file, unsigned int cmd,
			       unsigned long arg)
{
	struct host_arm_dma_dev *dma = file->private_data;
	struct eventfd_ctx *new_eventfd;
	struct eventfd_ctx *old_eventfd;
	unsigned long flags;
	int eventfd_fd;

	if (!dma)
		return -EINVAL;

	switch (cmd) {
	case HOST_ARM_DMA_IOC_SET_EVENTFD:
		if (copy_from_user(&eventfd_fd, (int __user *)arg,
				   sizeof(eventfd_fd)))
			return -EFAULT;

		new_eventfd = eventfd_ctx_fdget(eventfd_fd);
		if (IS_ERR(new_eventfd))
			return PTR_ERR(new_eventfd);

		spin_lock_irqsave(&dma->eventfd_lock, flags);
		old_eventfd = dma->eventfd;
		dma->eventfd = new_eventfd;
		spin_unlock_irqrestore(&dma->eventfd_lock, flags);

		if (old_eventfd)
			eventfd_ctx_put(old_eventfd);

		host_arm_dma_set_irqs_enabled(dma, true);

		return 0;

	case HOST_ARM_DMA_IOC_CLR_EVENTFD:
		spin_lock_irqsave(&dma->eventfd_lock, flags);
		old_eventfd = dma->eventfd;
		dma->eventfd = NULL;
		spin_unlock_irqrestore(&dma->eventfd_lock, flags);

		if (old_eventfd)
			eventfd_ctx_put(old_eventfd);

		host_arm_dma_set_irqs_enabled(dma, false);

		return 0;

	default:
		return -ENOTTY;
	}
}

static const struct file_operations host_arm_dma_fops = {
	.owner = THIS_MODULE,
	.open = host_arm_dma_open,
	.release = host_arm_dma_release,
	.write = host_arm_dma_write,
	.unlocked_ioctl = host_arm_dma_ioctl,
	.llseek = default_llseek,
};

static irqreturn_t host_arm_dma_isr(int irq, void *data)
{
	struct host_arm_dma_dev *dma = data;
	struct eventfd_ctx *eventfd;
	unsigned long flags;

	WRITE_ONCE(dma->last_isr_ns, ktime_get_ns());
	atomic_long_inc(&dma->irq_count);

	spin_lock_irqsave(&dma->eventfd_lock, flags);
	eventfd = dma->eventfd;
	if (eventfd)
		eventfd_signal(eventfd);
	spin_unlock_irqrestore(&dma->eventfd_lock, flags);

	return IRQ_HANDLED;
}

static int host_arm_dma_probe(struct platform_device *pdev)
{
	struct host_arm_dma_dev *dma;
	int ret;

	dma = devm_kzalloc(&pdev->dev, sizeof(*dma), GFP_KERNEL);
	if (!dma)
		return -ENOMEM;

	dma->dev = &pdev->dev;
	platform_set_drvdata(pdev, dma);
	dma->irq_enabled = false;
	atomic_long_set(&dma->irq_count, 0);
	spin_lock_init(&dma->eventfd_lock);
	dma->eventfd = NULL;
	WRITE_ONCE(dma->last_isr_ns, 0);

	dma->irq_in_gpio = devm_gpiod_get_index_optional(&pdev->dev,
								"dma-irq", 0,
								GPIOD_IN);
	if (IS_ERR(dma->irq_in_gpio)) {
		dev_err(&pdev->dev, "Failed to acquire dma-irq-gpios[0] descriptor: %ld\n", PTR_ERR(dma->irq_in_gpio));
		return PTR_ERR(dma->irq_in_gpio);
	}

	if (!dma->irq_in_gpio) {
		dev_err(&pdev->dev, "dma-irq-gpios[0] not defined in device tree\n");
		return -EINVAL;
	}

	dma->irq_in = gpiod_to_irq(dma->irq_in_gpio);
	if (dma->irq_in < 0) {
		dev_err(&pdev->dev, "Failed to get IRQ number for dma-irq-gpios[0]: %d\n", dma->irq_in);
		return dma->irq_in;
	}

	ret = devm_request_irq(&pdev->dev, dma->irq_in, host_arm_dma_isr,
					IRQF_NO_AUTOEN | IRQF_TRIGGER_RISING |
					IRQF_TRIGGER_FALLING, DRIVER_NAME, dma);
	if (ret) {
		dev_err(&pdev->dev, "Failed to request IRQ %d: %d\n",
			dma->irq_in, ret);
		return ret;
	}

	/* Drive host completion by direct write to gpio-xilinx GPIO_DATA register. */
	ret = host_arm_dma_init_host_irq_out(pdev, dma);
	if (ret)
		return ret;

	dma->miscdev.minor = MISC_DYNAMIC_MINOR;
	dma->miscdev.name = devm_kasprintf(&pdev->dev, GFP_KERNEL,
		"%s", DRIVER_NAME);
	if (!dma->miscdev.name)
		return -ENOMEM;
	dma->miscdev.fops = &host_arm_dma_fops;
	dma->miscdev.parent = &pdev->dev;

	ret = misc_register(&dma->miscdev);
	if (ret) {
		dev_err(&pdev->dev, "Failed to register misc device: %d\n", ret);
		return ret;
	}

	dev_info(&pdev->dev, "Eventfd control interface at /dev/%s\n", dma->miscdev.name);

	return 0;
}

static void host_arm_dma_remove(struct platform_device *pdev)
{
	struct host_arm_dma_dev *dma = platform_get_drvdata(pdev);
	struct eventfd_ctx *eventfd;
	unsigned long flags;

	if (!dma)
		return;

	host_arm_dma_set_irqs_enabled(dma, false);

	misc_deregister(&dma->miscdev);

	spin_lock_irqsave(&dma->eventfd_lock, flags);
	eventfd = dma->eventfd;
	dma->eventfd = NULL;
	spin_unlock_irqrestore(&dma->eventfd_lock, flags);

	if (eventfd)
		eventfd_ctx_put(eventfd);

	dev_info(dma->dev, "Unregistered DMA driver (received %ld interrupts)\n",
		 atomic_long_read(&dma->irq_count));
}

static const struct of_device_id host_arm_dma_of_match[] = {
	{ .compatible = "ni,host-arm-dma" },
	{ }
};
MODULE_DEVICE_TABLE(of, host_arm_dma_of_match);

static struct platform_driver host_arm_dma_driver = {
	.driver = {
		.name = DRIVER_NAME,
		.of_match_table = host_arm_dma_of_match,
	},
	.probe = host_arm_dma_probe,
	.remove = host_arm_dma_remove,
};

static int __init host_arm_dma_init(void)
{
	pr_info("Host ARM DMA interrupt driver v%s\n", DRIVER_VERSION);
	return platform_driver_register(&host_arm_dma_driver);
}

static void __exit host_arm_dma_exit(void)
{
	platform_driver_unregister(&host_arm_dma_driver);
}

module_init(host_arm_dma_init);
module_exit(host_arm_dma_exit);

MODULE_AUTHOR("National Instruments");
MODULE_DESCRIPTION("Host ARM DMA interrupt-driven device driver");
MODULE_LICENSE("GPL");
MODULE_VERSION(DRIVER_VERSION);
