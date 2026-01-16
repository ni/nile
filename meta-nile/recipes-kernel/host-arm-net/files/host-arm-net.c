/******************************************************************************
 *
 *   Copyright (C) 2025  National Instruments Corporation. All rights reserved.
 *
 *   SPDX-License-Identifier: GPL-2.0
 *
 *****************************************************************************/

#include <linux/module.h>
#include <linux/netdevice.h>
#include <linux/etherdevice.h>
#include <linux/skbuff.h>
#include <linux/interrupt.h>
#include <linux/io.h>
#include <linux/platform_device.h>
#include <linux/of.h>
#include <linux/of_irq.h>
#include <linux/dma-mapping.h>

#define DRIVER_NAME "host-arm-net"
#define DRIVER_VERSION "0.1.14"

/* Shared memory base address */
#define SHARED_MEM_BASE 0x70000000
#define SHARED_MEM_SIZE 0x100000  /* 1MB total */

/* Mailbox base address */
#define MBOX_BASE_ADDR 0x20180000000
#define MBOX_SIZE 0x100

/* Mailbox register offsets (from xmbox_hw.h) */
#define XMB_WRITE_REG_OFFSET	0x00	/**< Mbox write register */
#define XMB_READ_REG_OFFSET	0x08	/**< Mbox read register */
#define XMB_STATUS_REG_OFFSET	0x10	/**< Mbox status reg  */
#define XMB_ERROR_REG_OFFSET	0x14	/**< Mbox Error reg  */
#define XMB_SIT_REG_OFFSET	0x18	/**< Mbox send interrupt threshold register */
#define XMB_RIT_REG_OFFSET	0x1C	/**< Mbox receive interrupt threshold register */
#define XMB_IS_REG_OFFSET	0x20	/**< Mbox interrupt status register */
#define XMB_IE_REG_OFFSET	0x24	/**< Mbox interrupt enable register */
#define XMB_IP_REG_OFFSET	0x28	/**< Mbox interrupt pending register */
#define XMB_CTRL_REG_OFFSET	0x2C	/**< Mbox control register */

/* Status register bit definitions */
#define XMB_STATUS_FIFO_EMPTY	0x00000001 /**< Receive FIFO is Empty */
#define XMB_STATUS_FIFO_FULL	0x00000002 /**< Send FIFO is Full */
#define XMB_STATUS_STA		0x00000004 /**< Send FIFO Threshold Status */
#define XMB_STATUS_RTA		0x00000008 /**< Receive FIFO Threshold Status */

/* Interrupt register bit definitions */
#define XMB_IX_STA		0x01 /**< Send Threshold Active */
#define XMB_IX_RTA		0x02 /**< Receive Threshold Active */
#define XMB_IX_ERR		0x04 /**< Mailbox Error */

/* Error bits definition */
#define XMB_ERROR_FIFO_EMPTY	0x00000001 /**< Receive FIFO is Empty */
#define XMB_ERROR_FIFO_FULL	0x00000002 /**< Send FIFO is Full */

/* Control register bits definition */
#define XMB_CTRL_RESET_SEND_FIFO	0x00000001 /**< Clear Send FIFO */
#define XMB_CTRL_RESET_RECV_FIFO	0x00000002 /**< Clear Receive FIFO */

/* Memory layout offsets */
#define TX_RING_OFFSET    0x00000
#define RX_RING_OFFSET    0x20000
#define CONTROL_OFFSET    0x40000

/* Ring buffer configuration */
#define TX_RING_SIZE 64
#define RX_RING_SIZE 64
#define MAX_PACKET_SIZE 1518
#define RING_SPACING 0x600

/* Mailbox message format - 32-bit packed message */
/* Upper 16 bits: packet length, Lower 16 bits: ring buffer index */
#define MBOX_MSG_LENGTH_SHIFT	16
#define MBOX_MSG_LENGTH_MASK	0xFFFF0000
#define MBOX_MSG_INDEX_MASK	0x0000FFFF

#define MBOX_MSG_PACK(len, index) \
	((((len) & 0xFFFF) << MBOX_MSG_LENGTH_SHIFT) | ((index) & 0xFFFF))

#define MBOX_MSG_GET_LENGTH(msg) \
	(((msg) & MBOX_MSG_LENGTH_MASK) >> MBOX_MSG_LENGTH_SHIFT)

#define MBOX_MSG_GET_INDEX(msg) \
	((msg) & MBOX_MSG_INDEX_MASK)



/* Control structure offsets */
#define CTRL_TX_HEAD     0x00
#define CTRL_TX_TAIL     0x04
#define CTRL_RX_HEAD     0x08
#define CTRL_RX_TAIL     0x0C

/* MAC Layer Header */
struct mac_header {
	__u8 dest_mac[ETH_ALEN];    /* Destination MAC address */
	__u8 src_mac[ETH_ALEN];     /* Source MAC address */
	__be16 ethertype;     /* Ethernet type/length field */
} __attribute__((packed));
#define MAC_HEADER_SIZE sizeof(struct mac_header)

/* Private device structure */
struct host_arm_net_priv {
	struct net_device *ndev;
	void __iomem *shared_mem;
	void __iomem *control_base;
	void __iomem *mbox_base;
	int irq;
	
	/* Ring indices */
	u32 tx_head;
	u32 tx_tail;
	u32 rx_head;
	u32 rx_tail;
	
	/* SKB arrays for tracking */
	struct sk_buff **tx_skb;
	
	/* Statistics */
	struct net_device_stats stats;
	
	/* Lock for synchronization */
	spinlock_t lock;
};

static void host_arm_net_write_reg(struct host_arm_net_priv *priv, u32 offset, u32 value)
{
	writel(value, priv->control_base + offset);
}

static u32 host_arm_net_read_reg(struct host_arm_net_priv *priv, u32 offset)
{
	return readl(priv->control_base + offset);
}

/* Mailbox register access functions (converted from xmbox_hw.h macros) */
static u32 mbox_read_reg(struct host_arm_net_priv *priv, u32 reg_offset)
{
	return readl(priv->mbox_base + reg_offset);
}

static void mbox_write_reg(struct host_arm_net_priv *priv, u32 reg_offset, u32 value)
{
	writel(value, priv->mbox_base + reg_offset);
}

static void mbox_write_data(struct host_arm_net_priv *priv, u32 value)
{
	mbox_write_reg(priv, XMB_WRITE_REG_OFFSET, value);
}

static u32 mbox_read_data(struct host_arm_net_priv *priv)
{
	return mbox_read_reg(priv, XMB_READ_REG_OFFSET);
}

static bool mbox_is_empty(struct host_arm_net_priv *priv)
{
	u32 status = mbox_read_reg(priv, XMB_STATUS_REG_OFFSET);
	return (status & XMB_STATUS_FIFO_EMPTY) != 0;
}

static bool mbox_is_full(struct host_arm_net_priv *priv)
{
	u32 status = mbox_read_reg(priv, XMB_STATUS_REG_OFFSET);
	return (status & XMB_STATUS_FIFO_FULL) != 0;
}

static void mbox_reset_fifos(struct host_arm_net_priv *priv)
{
	mbox_write_reg(priv, XMB_CTRL_REG_OFFSET, 
		      XMB_CTRL_RESET_SEND_FIFO | XMB_CTRL_RESET_RECV_FIFO);
}

static void mbox_enable_interrupts(struct host_arm_net_priv *priv)
{
	mbox_write_reg(priv, XMB_IE_REG_OFFSET, 
		      XMB_IX_STA | XMB_IX_RTA | XMB_IX_ERR);
}

static void mbox_disable_interrupts(struct host_arm_net_priv *priv)
{
	mbox_write_reg(priv, XMB_IE_REG_OFFSET, 0);
}

static u32 mbox_get_interrupt_status(struct host_arm_net_priv *priv)
{
	return mbox_read_reg(priv, XMB_IS_REG_OFFSET);
}

static void mbox_clear_interrupt_status(struct host_arm_net_priv *priv, u32 mask)
{
	mbox_write_reg(priv, XMB_IS_REG_OFFSET, mask);
}

static void mbox_set_receive_interrupt_threshold(struct host_arm_net_priv *priv, u32 mask)
{
	mbox_write_reg(priv, XMB_RIT_REG_OFFSET, mask);
}

static u32 mbox_get_receive_interrupt_threshold(struct host_arm_net_priv *priv)
{
	return mbox_read_reg(priv, XMB_RIT_REG_OFFSET);
}

static void mbox_set_send_interrupt_threshold(struct host_arm_net_priv *priv, u32 mask)
{
	mbox_write_reg(priv, XMB_SIT_REG_OFFSET, mask);
}

static u32 mbox_get_send_interrupt_threshold(struct host_arm_net_priv *priv)
{
	return mbox_read_reg(priv, XMB_SIT_REG_OFFSET);
}

static void host_arm_net_update_tx_tail(struct host_arm_net_priv *priv)
{
	host_arm_net_write_reg(priv, CTRL_TX_TAIL, priv->tx_tail);
}

static void host_arm_net_receive_packet(struct host_arm_net_priv *priv,
					u16 pkt_len, u16 pkt_index)
{
	struct net_device *ndev = priv->ndev;
	struct sk_buff *skb;
	void __iomem *pkt_data;
	u32 max_rx_size = CONTROL_OFFSET - RX_RING_OFFSET;
	u32 pkt_offset;
	
	/* Validate packet length */
	if (pkt_len == 0 || pkt_len > MAX_PACKET_SIZE) {
		dev_warn(&ndev->dev, "Invalid packet length: %u\n", pkt_len);
		priv->stats.rx_dropped++;
		return;
	}
	
	/* Calculate offset from ring buffer index */
	pkt_offset = pkt_index * RING_SPACING;
	
	/* Validate packet doesn't overflow into control region */
	if (pkt_offset + pkt_len > max_rx_size) {
		dev_warn(&ndev->dev,
			 "Packet overflow: index=%u offset=%u len=%u max=%u\n",
			 pkt_index, pkt_offset, pkt_len, max_rx_size);
		priv->stats.rx_dropped++;
		return;
	}
	
	/* Allocate SKB */
	skb = netdev_alloc_skb(ndev, pkt_len + NET_IP_ALIGN);
	if (!skb) {
		dev_warn(&ndev->dev, "Failed to allocate SKB for %u bytes\n",
			 pkt_len);
		priv->stats.rx_dropped++;
		return;
	}
	
	skb_reserve(skb, NET_IP_ALIGN);
	
	/* Copy packet data from shared memory */
	pkt_data = priv->shared_mem + RX_RING_OFFSET + pkt_offset;
	memcpy_fromio(skb_put(skb, pkt_len), pkt_data, pkt_len);
	
	/* Set protocol and pass to network stack */
	skb->protocol = eth_type_trans(skb, ndev);
	skb->ip_summed = CHECKSUM_NONE;
	
	netif_rx(skb);
	
	/* Update statistics */
	priv->stats.rx_packets++;
	priv->stats.rx_bytes += pkt_len;
	
	/* Update rx_head to signal buffer consumption to remote processor */
	priv->rx_head = (priv->rx_head + 1) % RX_RING_SIZE;
	host_arm_net_write_reg(priv, CTRL_RX_HEAD, priv->rx_head);

	u32 rxhead = host_arm_net_read_reg(priv, CTRL_RX_HEAD);
	u32 rxtail = host_arm_net_read_reg(priv, CTRL_RX_TAIL);
	dev_info(&ndev->dev, "Received packet: %u bytes, rx_head: %u, rx_tail: %u\n", skb->len, rxhead, rxtail);
}

static int host_arm_net_open(struct net_device *ndev)
{
	struct host_arm_net_priv *priv = netdev_priv(ndev);
	
	/* Initialize ring buffer indices */
	priv->tx_head = 0;
	priv->tx_tail = 0;
	priv->rx_head = 0;
	priv->rx_tail = 0;

	/* Update hardware registers */
	host_arm_net_write_reg(priv, CTRL_TX_HEAD, priv->tx_head);
	host_arm_net_write_reg(priv, CTRL_TX_TAIL, priv->tx_tail);
	host_arm_net_write_reg(priv, CTRL_RX_HEAD, priv->rx_head);
	host_arm_net_write_reg(priv, CTRL_RX_TAIL, priv->rx_tail);

	/* Initialize mailbox */
	mbox_reset_fifos(priv);
	mbox_set_receive_interrupt_threshold(priv, 0);
	mbox_enable_interrupts(priv);
	
	netif_start_queue(ndev);
	
	dev_info(&ndev->dev, "Network interface opened\n");
	
	return 0;
}

static int host_arm_net_stop(struct net_device *ndev)
{
	struct host_arm_net_priv *priv = netdev_priv(ndev);
	
	netif_stop_queue(ndev);
	
	mbox_disable_interrupts(priv);
	
	dev_info(&ndev->dev, "Network interface stopped\n");
	
	return 0;
}

static netdev_tx_t host_arm_net_start_xmit(struct sk_buff *skb, struct net_device *ndev)
{
	struct host_arm_net_priv *priv = netdev_priv(ndev);
	unsigned long flags;
	u32 next_tail;
	struct mac_header mac_hdr;
	u32 total_len;
	
	spin_lock_irqsave(&priv->lock, flags);
	

	/* Calculate total length including MAC header */
	total_len = MAC_HEADER_SIZE + skb->len;

	/* Check if packet is too large */
	if (total_len > MAX_PACKET_SIZE) {
		dev_warn(&ndev->dev, "Packet too large: %u > %u, dropping\n",
			 total_len, MAX_PACKET_SIZE);
		priv->stats.tx_dropped++;
		dev_kfree_skb(skb);
		spin_unlock_irqrestore(&priv->lock, flags);
		return NETDEV_TX_OK;
	}
	
	/* Check if TX ring is full */
	next_tail = (priv->tx_tail + 1) % TX_RING_SIZE;
	if (next_tail == priv->tx_head) {
		netif_stop_queue(ndev);
		spin_unlock_irqrestore(&priv->lock, flags);
		return NETDEV_TX_BUSY;
	}

	/* Build MAC header with broadcast destination and IPv4 ethertype */
	memset(mac_hdr.dest_mac, 0xFF, ETH_ALEN);  /* Broadcast MAC */
	memcpy(mac_hdr.src_mac, ndev->dev_addr, ETH_ALEN);  /* Device MAC */
	mac_hdr.ethertype = htons(ETH_P_IP);  /* IPv4 ethertype (0x0800) */

	/* Copy MAC header and packet data to shared memory buffer */
	u32 buffer_offset = TX_RING_OFFSET + priv->tx_tail * RING_SPACING;
	memcpy_toio(priv->shared_mem + buffer_offset, &mac_hdr, MAC_HEADER_SIZE);
	memcpy_toio(priv->shared_mem + buffer_offset + MAC_HEADER_SIZE, skb->data, skb->len);
	
	/* Store SKB for later cleanup */
	priv->tx_skb[priv->tx_tail] = skb;
	
	/* Update tail pointer */
	u32 tx_index = priv->tx_tail;
	priv->tx_tail = next_tail;
	host_arm_net_update_tx_tail(priv);
	
	/* Notify remote processor via mailbox if not full */
	if (!mbox_is_full(priv)) {
		u32 msg = MBOX_MSG_PACK(total_len, tx_index);
		mbox_write_data(priv, msg);
	}
	
	u32 txhead = host_arm_net_read_reg(priv, CTRL_TX_HEAD);
	u32 txtail = host_arm_net_read_reg(priv, CTRL_TX_TAIL);
	dev_info(&ndev->dev, "Sent packet: %u bytes, tx_head: %u, tx_tail: %u\n", total_len, txhead, txtail);

	/* Update statistics */
	priv->stats.tx_packets++;
	priv->stats.tx_bytes += total_len;
	
	spin_unlock_irqrestore(&priv->lock, flags);
	
	return NETDEV_TX_OK;
}

static irqreturn_t host_arm_net_interrupt(int irq, void *dev_id)
{
	struct net_device *ndev = dev_id;
	struct host_arm_net_priv *priv = netdev_priv(ndev);
	u32 mbox_status;
	unsigned long flags;
	bool handled = false;

	mbox_status = mbox_get_interrupt_status(priv);

	if (!mbox_status)
		return IRQ_NONE;
	
	dev_info_ratelimited(&ndev->dev, 
			     "IRQ: mbox_status=0x%08x\n",
			     mbox_status);

	spin_lock_irqsave(&priv->lock, flags);
	
	/* Handle mailbox interrupts */
	if (mbox_status) {
		handled = true;
		
		if (mbox_status & XMB_IX_RTA) {
			/* Receive threshold active - process mailbox messages */
			while (!mbox_is_empty(priv)) {
				u32 msg = mbox_read_data(priv);
				u16 pkt_len = MBOX_MSG_GET_LENGTH(msg);
				u16 pkt_index = MBOX_MSG_GET_INDEX(msg);
				dev_info(&ndev->dev, "mbox msg: 0x%08x (len=%u, idx=%u)\n", msg, pkt_len, pkt_index);
				host_arm_net_receive_packet(priv, pkt_len, pkt_index);
			}
		}
		
		if (mbox_status & XMB_IX_STA) {
			/* Update TX head from hardware */
			priv->tx_head = host_arm_net_read_reg(priv, CTRL_TX_HEAD);
			
			/* Free completed SKBs */
			while (priv->tx_tail != priv->tx_head) {
				if (priv->tx_skb[priv->tx_tail]) {
					dev_kfree_skb_irq(priv->tx_skb[priv->tx_tail]);
					priv->tx_skb[priv->tx_tail] = NULL;
				}
				priv->tx_tail = (priv->tx_tail + 1) % TX_RING_SIZE;
			}

			netif_wake_queue(ndev);
		}
		
		if (mbox_status & XMB_IX_ERR) {
			u32 error = mbox_read_reg(priv, XMB_ERROR_REG_OFFSET);
			dev_warn(&ndev->dev, "Mailbox error: 0x%08x\n", error);
		}
		
		/* Clear mailbox interrupt status */
		mbox_clear_interrupt_status(priv, mbox_status);
	}
	
	spin_unlock_irqrestore(&priv->lock, flags);
	
	return handled ? IRQ_HANDLED : IRQ_NONE;
}

static struct net_device_stats *host_arm_net_get_stats(struct net_device *ndev)
{
	struct host_arm_net_priv *priv = netdev_priv(ndev);
	return &priv->stats;
}

static const struct net_device_ops host_arm_net_netdev_ops = {
	.ndo_open = host_arm_net_open,
	.ndo_stop = host_arm_net_stop,
	.ndo_start_xmit = host_arm_net_start_xmit,
	.ndo_get_stats = host_arm_net_get_stats,
	.ndo_set_mac_address = eth_mac_addr,
	.ndo_validate_addr = eth_validate_addr,
};

static int host_arm_net_probe(struct platform_device *pdev)
{
	struct net_device *ndev;
	struct host_arm_net_priv *priv;
	int ret;
	
	/* Allocate network device */
	ndev = alloc_etherdev(sizeof(struct host_arm_net_priv));
	if (!ndev)
		return -ENOMEM;
	
	SET_NETDEV_DEV(ndev, &pdev->dev);
	platform_set_drvdata(pdev, ndev);
	
	priv = netdev_priv(ndev);
	priv->ndev = ndev;
	
	/* Initialize lock */
	spin_lock_init(&priv->lock);
	
	/* Map shared memory */
	priv->shared_mem = ioremap(SHARED_MEM_BASE, SHARED_MEM_SIZE);
	if (!priv->shared_mem) {
		dev_err(&pdev->dev, "Failed to map shared memory\n");
		ret = -ENOMEM;
		goto err_free_netdev;
	}
	
	/* Map mailbox memory */
	priv->mbox_base = ioremap(MBOX_BASE_ADDR, MBOX_SIZE);
	if (!priv->mbox_base) {
		dev_err(&pdev->dev, "Failed to map mailbox memory\n");
		ret = -ENOMEM;
		goto err_unmap_shared;
	}
	
	/* Disable and reset mailbox before setting up interrupts */
	// mbox_disable_interrupts(priv);
	// mbox_reset_fifos(priv);
	// mbox_clear_interrupt_status(priv, 0xFF);
	
	/* Get mailbox interrupt from mailbox device node */
	{
		struct device_node *mbox_node;
		int irq_index;
		
		mbox_node = of_find_compatible_node(NULL, NULL, "xlnx,mailbox-2.1");
		if (!mbox_node) {
			dev_err(&pdev->dev, "Failed to find mailbox node in device tree\n");
			ret = -ENODEV;
			goto err_unmap_mbox;
		}
		
		irq_index = of_property_match_string(mbox_node, "interrupt-names",
						     "Interrupt_0");
		if (irq_index < 0) {
			dev_err(&pdev->dev, "Failed to find Interrupt_0 in mailbox node\n");
			of_node_put(mbox_node);
			ret = irq_index;
			goto err_unmap_mbox;
		}
		
		priv->irq = of_irq_get(mbox_node, irq_index);
		of_node_put(mbox_node);
		
		if (priv->irq < 0) {
			ret = priv->irq;
			dev_err(&pdev->dev, "Failed to get mailbox IRQ: %d\n", ret);
			goto err_unmap_mbox;
		}
		
		dev_info(&pdev->dev, "Using mailbox IRQ: %d\n", priv->irq);
	}
	
	/* Request mailbox interrupt */
	ret = devm_request_irq(&pdev->dev, priv->irq, host_arm_net_interrupt,
			       IRQF_SHARED, DRIVER_NAME, ndev);
	if (ret) {
		dev_err(&pdev->dev, "Failed to request IRQ %d: %d\n",
			priv->irq, ret);
		goto err_unmap_mbox;
	}
	
	/* Setup control base */
	priv->control_base = priv->shared_mem + CONTROL_OFFSET;
	
	/* Allocate SKB tracking arrays */
	priv->tx_skb = kcalloc(TX_RING_SIZE, sizeof(struct sk_buff *), GFP_KERNEL);
	if (!priv->tx_skb) {
		ret = -ENOMEM;
		goto err_free_arrays;
	}
	
	/* Setup network device */
	ndev->netdev_ops = &host_arm_net_netdev_ops;
	ndev->mtu = ETH_DATA_LEN;
	ndev->flags |= IFF_NOARP;
	
	/* Generate random MAC address */
	eth_random_addr(ndev->perm_addr);
	dev_addr_set(ndev, ndev->perm_addr);

	/* Register network device */
	ret = register_netdev(ndev);
	if (ret) {
		dev_err(&pdev->dev, "Failed to register network device\n");
		goto err_free_arrays;
	}
	
	dev_info(&pdev->dev, "Host ARM network driver loaded successfully\n");
	dev_info(&pdev->dev, "Shared memory mapped at 0x%llx (virt: %p)\n", 
		 (unsigned long long)SHARED_MEM_BASE, priv->shared_mem);
	dev_info(&pdev->dev, "Mailbox mapped at 0x%llx (virt: %p)\n", 
		 (unsigned long long)MBOX_BASE_ADDR, priv->mbox_base);
	
	return 0;
	
err_free_arrays:
	kfree(priv->tx_skb);
err_unmap_mbox:
	iounmap(priv->mbox_base);
err_unmap_shared:
	iounmap(priv->shared_mem);
err_free_netdev:
	free_netdev(ndev);
	return ret;
}

static void host_arm_net_remove(struct platform_device *pdev)
{
	struct net_device *ndev = platform_get_drvdata(pdev);
	struct host_arm_net_priv *priv = netdev_priv(ndev);
	
	unregister_netdev(ndev);
	
	/* Free SKB arrays */
	kfree(priv->tx_skb);
	
	/* Unmap memory regions */
	iounmap(priv->mbox_base);
	iounmap(priv->shared_mem);
	
	free_netdev(ndev);
	
	dev_info(&pdev->dev, "Host ARM network driver removed\n");
}

static const struct of_device_id host_arm_net_of_match[] = {
	{ .compatible = "ni,host-arm-net", },
	{ }
};
MODULE_DEVICE_TABLE(of, host_arm_net_of_match);

static struct platform_driver host_arm_net_driver = {
	.driver = {
		.name = DRIVER_NAME,
		.of_match_table = host_arm_net_of_match,
	},
	.probe = host_arm_net_probe,
	.remove = host_arm_net_remove,
};

static int __init host_arm_net_init(void)
{
	pr_info("Host ARM Network Driver v%s\n", DRIVER_VERSION);
	return platform_driver_register(&host_arm_net_driver);
}

static void __exit host_arm_net_exit(void)
{
	platform_driver_unregister(&host_arm_net_driver);
	pr_info("Host ARM Network Driver unloaded\n");
}

module_init(host_arm_net_init);
module_exit(host_arm_net_exit);

MODULE_LICENSE("GPL");
MODULE_AUTHOR("National Instruments Corporation");
MODULE_DESCRIPTION("Host ARM Network Interface Driver");
MODULE_VERSION(DRIVER_VERSION);
