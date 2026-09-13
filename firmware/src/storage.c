/* Owns first 7.5 MiB of external flash. Last 512 KiB is reserved for settings.
 * Atomic commit: body first, header tail next, magic last. ACK never erases a
 * recording before the Android application has durably accepted it.
 */
#include "hvb.h"
#include <zephyr/device.h>
#include <zephyr/drivers/flash.h>
#include <zephyr/random/random.h>
#include <zephyr/sys/byteorder.h>
#include <zephyr/sys/crc.h>
#include <string.h>
#include <errno.h>
static const struct device *const flash=DEVICE_DT_GET(DT_NODELABEL(py25q64));
K_MUTEX_DEFINE(lock);
enum slot_state {FREE,FULL,WRITING,DIRTY,ERASING,BROKEN};
static struct {enum slot_state state;uint8_t hdr[64];uint32_t length,crc,erase_at;} slots[HVB_SLOTS];
static uint64_t sequence;
static unsigned allocate_next;
static off_t base(int s){return (off_t)s*HVB_SLOT_SIZE;}
static int wr(int s,uint32_t off,const void *p,size_t n){return flash_write(flash,base(s)+off,p,n);}
static int rd(int s,uint32_t off,void *p,size_t n){return flash_read(flash,base(s)+off,p,n);}
static int marker(int s,uint32_t value){uint8_t b[4];sys_put_le32(value,b);return wr(s,HVB_SLOT_SIZE-4,b,4);}
static int erased(int s){
    uint8_t buf[256];
    for(uint32_t o=0;o<HVB_SLOT_SIZE;o+=sizeof(buf)){
        if(rd(s,o,buf,sizeof(buf)))return -EIO;
        for(unsigned k=0;k<sizeof(buf);k++)if(buf[k]!=0xff)return 0;
    }return 1;
}
int hvb_store_init(void){
    if(!device_is_ready(flash))return -ENODEV;
    for(int s=0;s<HVB_SLOTS;s++){
        uint8_t tail[4];if(rd(s,0,slots[s].hdr,64)||rd(s,HVB_SLOT_SIZE-4,tail,4))return -EIO;
        uint32_t m=sys_get_le32(tail);
        if(m==0xfffffffeu&&!memcmp(slots[s].hdr,"HVB1",4)){
            uint32_t n=sys_get_le32(slots[s].hdr+16);
            slots[s].length=n;
            slots[s].state=(n>0&&n<=492000&&n%164==0)?FULL:BROKEN;
            uint64_t seq=sys_get_le64(slots[s].hdr+48);if(seq>sequence)sequence=seq;
        }else if(m==0xffffffffu){
            int clean=erased(s);if(clean<0)return clean;
            slots[s].state=clean?FREE:DIRTY;
        }else slots[s].state=DIRTY; /* interrupted/uncommitted/acknowledged */
    }
    return 0;
}
int hvb_store_begin(uint8_t header[64]){
    k_mutex_lock(&lock,K_FOREVER);int s=-1;
    for(unsigned k=0;k<HVB_SLOTS;k++){int i=(allocate_next+k)%HVB_SLOTS;if(slots[i].state==FREE){s=i;break;}}
    if(s<0){k_mutex_unlock(&lock);return -ENOSPC;}
    slots[s].state=WRITING;slots[s].length=slots[s].crc=0;allocate_next=(s+1)%HVB_SLOTS;
    uint8_t *h=slots[s].hdr;memset(h,0,64);memcpy(h,"HVB1",4);
    h[4]=1;h[5]=1;sys_put_le16(64,h+6);sys_put_le32(16000,h+8);
    int rc=sys_csrand_get(h+24,16);
    h[30]=(h[30]&15)|64;h[32]=(h[32]&63)|128;
    sys_put_le32(k_uptime_get_32(),h+44);sys_put_le64(++sequence,h+48);
    if(!rc)rc=marker(s,0xfffffffeu);
    if(rc){slots[s].state=DIRTY;s=rc;}else memcpy(header,h,64);
    k_mutex_unlock(&lock);return s;
}
int hvb_store_append(int s,const uint8_t frame[164]){
    k_mutex_lock(&lock,K_FOREVER);int rc=-EINVAL;
    if(s>=0&&s<HVB_SLOTS&&slots[s].state==WRITING&&slots[s].length+164<=492000){
        rc=wr(s,64+slots[s].length,frame,164);
        if(!rc){slots[s].crc=crc32_ieee_update(slots[s].crc,frame,164);slots[s].length+=164;}
    }
    k_mutex_unlock(&lock);return rc;
}
int hvb_store_finish(int s,uint32_t flags){
    k_mutex_lock(&lock,K_FOREVER);int rc=-EINVAL;
    if(s>=0&&s<HVB_SLOTS&&slots[s].state==WRITING&&slots[s].length){
        uint8_t *h=slots[s].hdr;sys_put_le32(slots[s].length/164*320,h+12);
        sys_put_le32(slots[s].length,h+16);sys_put_le32(slots[s].crc,h+20);sys_put_le32(flags,h+40);
        rc=wr(s,4,h+4,60);if(!rc)rc=wr(s,0,h,4);
        slots[s].state=rc?BROKEN:FULL;
    }
    k_mutex_unlock(&lock);return rc;
}
void hvb_store_abort(int s){k_mutex_lock(&lock,K_FOREVER);if(s>=0&&s<HVB_SLOTS&&slots[s].state==WRITING)slots[s].state=DIRTY;k_mutex_unlock(&lock);}
int hvb_store_oldest(uint8_t meta[20]){
    k_mutex_lock(&lock,K_FOREVER);int chosen=-1;uint64_t oldest=UINT64_MAX;memset(meta,0,20);
    for(int s=0;s<HVB_SLOTS;s++)if(slots[s].state==FULL){uint64_t n=sys_get_le64(slots[s].hdr+48);if(n<oldest){oldest=n;chosen=s;}}
    if(chosen>=0){memcpy(meta,slots[chosen].hdr+24,16);sys_put_le32(64+slots[chosen].length,meta+16);}
    k_mutex_unlock(&lock);return chosen;
}
int hvb_store_read(int s,uint32_t o,void *data,size_t n){
    k_mutex_lock(&lock,K_FOREVER);int rc=-EINVAL;
    if(s>=0&&s<HVB_SLOTS&&slots[s].state==FULL&&o<=64+slots[s].length&&n<=64+slots[s].length-o)rc=rd(s,o,data,n);
    k_mutex_unlock(&lock);return rc;
}
int hvb_store_ack(int s,const uint8_t id[16]){
    k_mutex_lock(&lock,K_FOREVER);int rc=-EINVAL;
    if(s>=0&&s<HVB_SLOTS&&slots[s].state==FULL&&!memcmp(id,slots[s].hdr+24,16)){
        rc=marker(s,0xfffffffcu);if(!rc)slots[s].state=DIRTY;
    }
    k_mutex_unlock(&lock);return rc;
}
int hvb_store_count(void){int n=0;k_mutex_lock(&lock,K_FOREVER);for(int s=0;s<HVB_SLOTS;s++)if(slots[s].state==FULL)n++;k_mutex_unlock(&lock);return n;}
void hvb_store_gc_step(void){
    if(atomic_get(&hvb_recording))return;
    k_mutex_lock(&lock,K_FOREVER);
    for(int s=0;s<HVB_SLOTS;s++){
        if(slots[s].state==DIRTY){slots[s].state=ERASING;slots[s].erase_at=0;}
        if(slots[s].state!=ERASING)continue;
        /* Trailer sector erased LAST; interrupted erase never looks free. */
        int rc=flash_erase(flash,base(s)+slots[s].erase_at,4096);
        if(rc)slots[s].state=BROKEN;
        else {slots[s].erase_at+=4096;if(slots[s].erase_at==HVB_SLOT_SIZE)slots[s].state=FREE;}
        break;
    }
    k_mutex_unlock(&lock);
}
