struct device {int dummy;};
extern struct device host_flash_device;
#define DT_NODELABEL(x) 0
#define DEVICE_DT_GET(x) (&host_flash_device)
static inline int device_is_ready(const struct device *d){return d!=0;}
