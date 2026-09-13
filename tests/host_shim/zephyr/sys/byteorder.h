static inline uint32_t sys_get_le32(const uint8_t *p){return (uint32_t)p[0]|(uint32_t)p[1]<<8|(uint32_t)p[2]<<16|(uint32_t)p[3]<<24;}
static inline uint64_t sys_get_le64(const uint8_t *p){return (uint64_t)sys_get_le32(p)|(uint64_t)sys_get_le32(p+4)<<32;}
static inline void sys_put_le16(uint16_t n,uint8_t *p){p[0]=n;p[1]=n>>8;}
static inline void sys_put_le32(uint32_t n,uint8_t *p){for(int i=0;i<4;i++)p[i]=n>>(8*i);}
static inline void sys_put_le64(uint64_t n,uint8_t *p){for(int i=0;i<8;i++)p[i]=n>>(8*i);}
