#include "recorder_config.h"
#include <zephyr/kernel.h>
#include <zephyr/settings/settings.h>
#include <zephyr/sys/byteorder.h>
#include <errno.h>
#include <string.h>
static struct hvb_config config={2000,40,0};
K_MUTEX_DEFINE(config_lock);
static int decode(const uint8_t *b,struct hvb_config *out){
    out->silence_ms=sys_get_le16(b);out->threshold=sys_get_le16(b+2);out->manual=b[4];
    return ((out->silence_ms!=2000&&out->silence_ms!=4000&&out->silence_ms!=6000)||
            out->threshold<10||out->threshold>1000||out->manual>1||b[5])?-EINVAL:0;
}
static int load(const char *key,size_t len,settings_read_cb cb,void *arg){
    if(strcmp(key,"capture"))return -ENOENT;
    uint8_t b[6];struct hvb_config next;
    if(len!=sizeof(b)||cb(arg,b,sizeof(b))!=sizeof(b)||decode(b,&next))return -EINVAL;
    config=next;return 0;
}
SETTINGS_STATIC_HANDLER_DEFINE(hvb,"hvb",NULL,load,NULL,NULL);
void hvb_config_get(struct hvb_config *out){k_mutex_lock(&config_lock,K_FOREVER);*out=config;k_mutex_unlock(&config_lock);}
void hvb_config_bytes(uint8_t out[6]){struct hvb_config c;hvb_config_get(&c);sys_put_le16(c.silence_ms,out);sys_put_le16(c.threshold,out+2);out[4]=c.manual;out[5]=0;}
int hvb_config_set(const uint8_t b[6]){
    struct hvb_config next;int rc=decode(b,&next);if(rc)return rc;
    k_mutex_lock(&config_lock,K_FOREVER);
    rc=settings_save_one("hvb/capture",b,6);if(!rc)config=next;
    k_mutex_unlock(&config_lock);return rc;
}
