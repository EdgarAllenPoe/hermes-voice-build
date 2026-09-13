/* First prototype favors fast System-ON idle wake, not unmeasured System-OFF claims. */
#include "hvb.h"
#include "codec.h"
#include <zephyr/device.h>
#include <zephyr/audio/dmic.h>
#include <zephyr/drivers/gpio.h>
#include <zephyr/drivers/regulator.h>
#include <zephyr/drivers/sensor.h>
#include <zephyr/sys/byteorder.h>
#include <string.h>
#include <errno.h>
atomic_t hvb_recording;
static const struct gpio_dt_spec button=GPIO_DT_SPEC_GET(DT_ALIAS(voice_button),gpios);
static const struct gpio_dt_spec onboard=GPIO_DT_SPEC_GET(DT_ALIAS(sw0),gpios);
static const struct gpio_dt_spec leds[]={GPIO_DT_SPEC_GET(DT_ALIAS(led1),gpios),GPIO_DT_SPEC_GET(DT_ALIAS(led2),gpios),GPIO_DT_SPEC_GET(DT_ALIAS(led0),gpios)};
static const struct device *const mic=DEVICE_DT_GET(DT_NODELABEL(pdm20));
static const struct device *const main_power=DEVICE_DT_GET(DT_NODELABEL(power_en));
static const struct device *const rail=DEVICE_DT_GET(DT_NODELABEL(dmic_vdd));
static const struct device *const charger=DEVICE_DT_GET(DT_COMPAT_GET_ANY_STATUS_OKAY(nordic_npm1300_charger));
static struct gpio_callback ext_cb,int_cb;
struct edge {int64_t at;bool down;};
K_MSGQ_DEFINE(edges,sizeof(struct edge),16,4);
K_MEM_SLAB_DEFINE_STATIC(audio_slab,640,16,4);
static struct pcm_stream_cfg stream={.pcm_rate=16000,.pcm_width=16,.block_size=640,.mem_slab=&audio_slab};
static struct dmic_cfg config={.io={.min_pdm_clk_freq=1000000,.max_pdm_clk_freq=3500000,.min_pdm_clk_dc=40,.max_pdm_clk_dc=60},.streams=&stream,.channel={.req_num_streams=1,.req_num_chan=1}};
static uint16_t battery;
static int16_t pre[12][320];
static unsigned pre_count,pre_index;
static bool mic_on;
void hvb_led(unsigned r,unsigned g,unsigned b){unsigned v[]={r,g,b};for(unsigned i=0;i<3;i++)gpio_pin_set_dt(&leds[i],v[i]?1:0);}
uint16_t hvb_battery_mv(void){return battery;}
static void edge_cb(const struct device *port,struct gpio_callback *cb,uint32_t pins){
    ARG_UNUSED(pins);const struct gpio_dt_spec *s=cb==&ext_cb?&button:&onboard;
    struct edge e={.at=k_uptime_get(),.down=gpio_pin_get_dt(s)>0};ARG_UNUSED(port);
    (void)k_msgq_put(&edges,&e,K_NO_WAIT);
}
static int setup_button(const struct gpio_dt_spec *s,struct gpio_callback *cb){
    if(!gpio_is_ready_dt(s))return -ENODEV;
    int rc=gpio_pin_configure_dt(s,GPIO_INPUT);if(rc)return rc;
    gpio_init_callback(cb,edge_cb,BIT(s->pin));rc=gpio_add_callback(s->port,cb);if(rc)return rc;
    return gpio_pin_interrupt_configure_dt(s,GPIO_INT_EDGE_BOTH);
}
static int start_mic(void){
    if(!device_is_ready(rail)||!device_is_ready(mic))return -ENODEV;
    int rc=regulator_enable(rail);if(rc&&rc!=-EALREADY)return rc;
    k_msleep(100); /* Measured board/mic settling must precede optimization. */
    config.channel.req_chan_map_lo=dmic_build_channel_map(0,0,PDM_CHAN_LEFT);
    rc=dmic_configure(mic,&config);if(!rc)rc=dmic_trigger(mic,DMIC_TRIGGER_START);
    if(rc){regulator_disable(rail);return rc;}
    mic_on=true;pre_count=pre_index=0;return 0;
}
static void stop_mic(void){
    if(!mic_on)return;
    dmic_trigger(mic,DMIC_TRIGGER_STOP);
    void *buf;size_t size;
    while(!dmic_read(mic,0,&buf,&size,0))k_mem_slab_free(&audio_slab,buf);
    regulator_disable(rail);mic_on=false;atomic_clear(&hvb_recording);
}
static int add_frame(int slot,const int16_t *pcm){uint8_t encoded[164];hvb_encode_frame(pcm,encoded);return hvb_store_append(slot,encoded);}
static void background(void *a,void *b,void *c){
    ARG_UNUSED(a);ARG_UNUSED(b);ARG_UNUSED(c);unsigned ticks=0;
    while(true){
        hvb_ble_maintenance();hvb_store_gc_step();
        if(++ticks%100==0&&!atomic_get(&hvb_recording)&&device_is_ready(charger)){
            struct sensor_value v;
            if(!sensor_sample_fetch(charger)&&!sensor_channel_get(charger,SENSOR_CHAN_GAUGE_VOLTAGE,&v))
                battery=(uint16_t)(v.val1*1000+v.val2/1000);
        }
        k_msleep(100);
    }
}
K_THREAD_STACK_DEFINE(bg_stack,3072);static struct k_thread bg_thread;
int main(void){
    for(unsigned i=0;i<3;i++){if(!gpio_is_ready_dt(&leds[i]))return -ENODEV;gpio_pin_configure_dt(&leds[i],GPIO_OUTPUT_INACTIVE);}
    hvb_led(0,0,1);
    if(!device_is_ready(main_power)){hvb_led(1,0,0);return -ENODEV;}
    int power_rc=regulator_enable(main_power);
    if(power_rc && power_rc!=-EALREADY){hvb_led(1,0,0);return power_rc;}
    k_msleep(20); /* Vendor board power rail must settle before BLE initialization. */
    if(setup_button(&button,&ext_cb)||setup_button(&onboard,&int_cb)||hvb_store_init()||hvb_ble_init()){
        hvb_led(1,0,0);return -EIO;
    }
    k_thread_create(&bg_thread,bg_stack,K_THREAD_STACK_SIZEOF(bg_stack),background,NULL,NULL,NULL,7,0,K_NO_WAIT);
    hvb_led(0,0,0);
    bool tentative=false,released=false,held=false,pair_shown=false,forget_shown=false;
    int64_t first=0,held_since=0,last_down=-1000;int slot=-1;struct hvb_gate gate={0};
    while(true){
        struct edge e;
        while(k_msgq_get(&edges,&e,mic_on||held?K_NO_WAIT:K_MSEC(100))==0){
            if(!e.down){held=false;released=true;continue;}
            if(e.at-last_down<60)continue;
            last_down=e.at;held=true;held_since=e.at;pair_shown=forget_shown=false;
            if(slot>=0){
                int rc=hvb_store_finish(slot,2);slot=-1;stop_mic();tentative=false;hvb_led(rc?1:0,0,0);continue;
            }
            if(tentative&&released&&e.at-first<=500){
                uint8_t header[64];slot=hvb_store_begin(header);
                if(slot<0){stop_mic();tentative=false;hvb_led(1,0,0);continue;}
                memset(&gate,0,sizeof(gate));int rc=0;
                for(unsigned i=0;i<pre_count;i++){
                    unsigned j=(pre_index+12-pre_count+i)%12;
                    if((rc=add_frame(slot,pre[j])))break;
                    (void)hvb_gate_update(&gate,pre[j],HVB_VAD_THRESHOLD);
                }
                if(rc){hvb_store_abort(slot);slot=-1;stop_mic();hvb_led(1,0,0);}else hvb_led(0,1,0);
                tentative=false;continue;
            }
            if(tentative) { stop_mic(); tentative=false; }
            first=e.at;released=false;tentative=true;atomic_set(&hvb_recording,1);
            if(start_mic()){tentative=false;atomic_clear(&hvb_recording);hvb_led(1,0,0);}
        }
        int64_t now=k_uptime_get();
        if(held&&now-held_since>1500&&!pair_shown&&slot<0){stop_mic();tentative=false;hvb_ble_pair_window();pair_shown=true;hvb_led(0,0,1);}
        if(held&&now-held_since>10000&&!forget_shown&&slot<0){hvb_ble_forget_phone();forget_shown=true;hvb_led(1,0,1);}
        if(tentative&&now-first>500){stop_mic();tentative=false;hvb_led(0,0,0);}
        if(!mic_on){if(!held&&pair_shown)hvb_led(0,0,0);if(held)k_msleep(10);continue;}
        void *buf=NULL;size_t size=0;int rc=dmic_read(mic,0,&buf,&size,100);
        if(rc||size!=640){if(buf)k_mem_slab_free(&audio_slab,buf);hvb_store_abort(slot);slot=-1;stop_mic();tentative=false;hvb_led(1,0,0);continue;}
        const int16_t *pcm=buf;
        if(tentative){memcpy(pre[pre_index],pcm,640);pre_index=(pre_index+1)%12;if(pre_count<12)pre_count++;}
        else if(slot>=0){
            rc=add_frame(slot,pcm);int end=hvb_gate_update(&gate,pcm,HVB_VAD_THRESHOLD);
            if(rc||end){
                if(rc||end==2)hvb_store_abort(slot);
                else rc=hvb_store_finish(slot,end==3?1:0);
                slot=-1;stop_mic();hvb_led(rc?1:0,0,0);
            }
        }
        k_mem_slab_free(&audio_slab,buf);
    }
    return 0;
}
