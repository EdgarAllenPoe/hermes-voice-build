int flash_write(const struct device*,off_t,const void*,size_t);
int flash_read(const struct device*,off_t,void*,size_t);
int flash_erase(const struct device*,off_t,size_t);
