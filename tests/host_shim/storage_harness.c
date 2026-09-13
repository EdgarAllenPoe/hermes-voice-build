/* Host simulated NOR flash, original firmware storage.c included unchanged. */
#include "../../firmware/src/storage.c"
struct device host_flash_device;
atomic_t hvb_recording;
static unsigned char memory[8*1024*1024];
static int failure=-1;
static unsigned random_counter;
int flash_read(const struct device*d,off_t o,void*p,size_t n){(void)d;if(o<0||(size_t)o+n>sizeof(memory))return -1;memcpy(p,memory+o,n);return 0;}
int flash_write(const struct device*d,off_t o,const void*p,size_t n){
 (void)d;if(o<0||(size_t)o+n>sizeof(memory)||o%4||n%4)return -1;
 if(failure==0)return -EIO;if(failure>0)failure--;
 const uint8_t *src=p;for(size_t i=0;i<n;i++)if((memory[o+i]&src[i])!=src[i])return -EIO;
 for(size_t i=0;i<n;i++)memory[o+i]&=src[i];return 0;
}
int flash_erase(const struct device*d,off_t o,size_t n){(void)d;if(o<0||(size_t)o+n>sizeof(memory)||o%4096||n%4096)return -1;memset(memory+o,255,n);return 0;}
int sys_csrand_get(void*p,size_t n){uint8_t *b=p;unsigned x=++random_counter;for(size_t i=0;i<n;i++){x=x*1664525u+1013904223u;b[i]=x>>24;}return 0;}
uint32_t crc32_ieee_update(uint32_t crc,const uint8_t*p,size_t n){crc=~crc;for(size_t i=0;i<n;i++){crc^=p[i];for(int j=0;j<8;j++)crc=(crc>>1)^((crc&1)?0xedb88320u:0);}return ~crc;}
void test_reboot(void){memset(slots,0,sizeof(slots));sequence=0;allocate_next=0;atomic_store(&hvb_recording,0);failure=-1;}
void test_clean(void){memset(memory,255,sizeof(memory));test_reboot();}
void test_fail_after(int n){failure=n;}
int test_state(int s){return slots[s].state;}
