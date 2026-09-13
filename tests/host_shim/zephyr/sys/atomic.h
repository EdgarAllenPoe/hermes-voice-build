#include <stdatomic.h>
typedef atomic_int atomic_t;
#define atomic_get(p) atomic_load(p)
