#ifndef TEST_KERNEL_H
#define TEST_KERNEL_H
/* Test-only host shim. Never add this directory to a Zephyr target build. */
#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>
#include <sys/types.h>
#include <pthread.h>
#define K_FOREVER -1
#define K_MUTEX_DEFINE(x) static pthread_mutex_t x=PTHREAD_MUTEX_INITIALIZER
#define k_mutex_lock(m,t) pthread_mutex_lock(m)
#define k_mutex_unlock(m) pthread_mutex_unlock(m)
static inline uint32_t k_uptime_get_32(void){return 1000;}
#endif
