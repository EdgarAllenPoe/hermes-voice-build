/* First prototype favors fast System-ON idle wake, not unmeasured System-OFF claims. */
#include "hvb.h"
#include "codec.h"
#include "button.h"
#include "charge_indicator.h"
#include "recorder_config.h"
#include <zephyr/drivers/led.h>
#include <zephyr/drivers/sensor/npm13xx_charger.h>
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
/* Pinned Seeed BSP removed the unconnected power_en GPIO; BUCK2 is the real supply. */
static const struct device *const main_power=DEVICE_DT_GET(DT_NODELABEL(vsys_3v3));
static const struct device *const rail=DEVICE_DT_GET(DT_NODELABEL(dmic_vdd));
static const struct device *const charger=DEVICE_DT_GET(DT_COMPAT_GET_ANY_STATUS_OKAY(nordic_npm1300_charger));
static const struct device *const charge_led=DEVICE_DT_GET(DT_NODELABEL(pmic_leds));
static atomic_t charge_indicator_on,charge_indicator_error,charge_indicator_samples;
static bool charge_led_known;
static struct gpio_callback ext_cb,int_cb;
struct edge {int64_t at;bool down;};
K_MSGQ_DEFINE(edges,sizeof(struct edge),16,4);
K_MEM_SLAB_DEFINE_STATIC(audio_slab,640,16,4);
static struct pcm_stream_cfg stream={.pcm_rate=16000,.pcm_width=16,.block_size=640,.mem_slab=&audio_slab};
static struct dmic_cfg config={.io={.min_pdm_clk_freq=1000000,.max_pdm_clk_freq=3500000,.min_pdm_clk_dc=40,.max_pdm_clk_dc=60},.streams=&stream,.channel={.req_num_streams=1,.req_num_chan=1}};
static atomic_t battery,charge_state,charge_current_ma,low_battery;
static atomic_t mic_test_request,mic_test_state,mic_test_deadline,mic_level,mic_peak,mic_frames;
static atomic_t led_base,led_event,led_event_at;
static struct hvb_config capture_config;
static void render_led(void){
    unsigned color=(unsigned)atomic_get(&led_base);uint32_t now=k_uptime_get_32();
    unsigned event=(unsigned)atomic_get(&led_event),age=now-(uint32_t)atomic_get(&led_event_at);
    if(!color&&event&&age<2400){unsigned pulse=(age%1200)/200;
        if(age%200<100&&pulse<(event==HVB_SIGNAL_SAVED?2u:3u))color=event==HVB_SIGNAL_SAVED?2u:event==HVB_SIGNAL_TRANSFERRED?4u:1u;
    }else if(!color&&atomic_get(&low_battery)&&now%10000<80)color=1;
    for(unsigned j=0;j<3;j++)gpio_pin_set_dt(&leds[j],(color&(1u<<j))!=0);
}
void hvb_signal(unsigned event){if(event==HVB_SIGNAL_SAVED)hvb_ble_recording_ready();atomic_set(&led_event_at,k_uptime_get_32());atomic_set(&led_event,event);}
int hvb_mic_test(bool start){
    if(start&&atomic_get(&hvb_recording))return -EBUSY;
    if(start){atomic_set(&mic_peak,0);atomic_set(&mic_frames,0);atomic_set(&mic_test_state,1);atomic_set(&mic_test_deadline,k_uptime_get_32()+60000);}
    if(!start&&atomic_get(&mic_test_state)==1)atomic_clear(&mic_test_state);
    atomic_set(&mic_test_request,start?1:0);return 0;
}
void hvb_live_diagnostics(uint8_t out[32]){
    memset(out,0,32);out[0]=2;out[1]=(uint8_t)atomic_get(&charge_state);out[2]=(uint8_t)atomic_get(&low_battery);
    out[3]=(uint8_t)atomic_get(&mic_test_state);sys_put_le32((uint32_t)atomic_get(&charge_current_ma),out+4);
    sys_put_le16((uint16_t)atomic_get(&mic_level),out+8);sys_put_le16((uint16_t)atomic_get(&mic_peak),out+10);
    hvb_config_bytes(out+12);out[17]=(uint8_t)hvb_store_free();sys_put_le32((uint32_t)atomic_get(&mic_frames),out+20);
}
static atomic_t mic_failures,audio_failures,dropped_edges;
static int16_t pre[12][320];
static unsigned pre_count,pre_index;
static bool mic_on;
void hvb_led(unsigned r,unsigned g,unsigned b){atomic_set(&led_base,(r?1:0)|(g?2:0)|(b?4:0));render_led();}
uint16_t hvb_battery_mv(void){return (uint16_t)atomic_get(&battery);}
static void edge_cb(const struct device *port,struct gpio_callback *cb,uint32_t pins){
    ARG_UNUSED(pins);const struct gpio_dt_spec *s=cb==&ext_cb?&button:&onboard;
    struct edge e={.at=k_uptime_get(),.down=gpio_pin_get_dt(s)>0};ARG_UNUSED(port);
    if(k_msgq_put(&edges,&e,K_NO_WAIT))atomic_inc(&dropped_edges);
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
static void update_charge_indicator(void){
    struct sensor_value v={0},i={0},bus={0},status={0},error={0};
    int rc=device_is_ready(charger)?sensor_sample_fetch(charger):-ENODEV;
    if(!rc)rc=sensor_channel_get(charger,SENSOR_CHAN_GAUGE_VOLTAGE,&v);
    if(!rc)atomic_set(&battery,(uint16_t)sensor_value_to_milli(&v));
    if(!rc)rc=sensor_channel_get(charger,SENSOR_CHAN_GAUGE_AVG_CURRENT,&i);
    if(!rc)rc=sensor_channel_get(charger,(enum sensor_channel)SENSOR_CHAN_NPM13XX_CHARGER_VBUS_STATUS,&bus);
    if(!rc)rc=sensor_channel_get(charger,(enum sensor_channel)SENSOR_CHAN_NPM13XX_CHARGER_STATUS,&status);
    if(!rc)rc=sensor_channel_get(charger,(enum sensor_channel)SENSOR_CHAN_NPM13XX_CHARGER_ERROR,&error);
    bool on=hvb_charge_indicator(rc==0,(uint8_t)bus.val1,(int32_t)sensor_value_to_milli(&v),
                                 sensor_value_to_micro(&i),(uint8_t)status.val1,(uint8_t)error.val1,
                                 DT_PROP(DT_COMPAT_GET_ANY_STATUS_OKAY(nordic_npm1300_charger),current_microamp));
    if(!rc){
        atomic_inc(&charge_indicator_samples);atomic_set(&charge_current_ma,sensor_value_to_milli(&i));
        bool usb=(bus.val1&1)!=0;
        atomic_set(&charge_state,(error.val1||(bus.val1&0x1c)||(status.val1&0x40))?4:usb?(on?2:3):1);
        atomic_set(&low_battery,!usb&&sensor_value_to_milli(&v)>=2000&&sensor_value_to_milli(&v)<=3500);
    }else{atomic_set(&charge_state,0);atomic_clear(&low_battery);}
    if(!device_is_ready(charge_led)){
        charge_led_known=false;rc=-ENODEV;
    }else if(!charge_led_known||on!=(bool)atomic_get(&charge_indicator_on)){
        int led_rc=on?led_on(charge_led,1):led_off(charge_led,1);
        if(led_rc){charge_led_known=false;rc=led_rc;}
        else{charge_led_known=true;atomic_set(&charge_indicator_on,on);}
    }
    atomic_set(&charge_indicator_error,rc);
}
static void background(void *a,void *b,void *c){
    ARG_UNUSED(a);ARG_UNUSED(b);ARG_UNUSED(c);unsigned ticks=0;
    while(true){
        hvb_ble_maintenance();hvb_store_gc_step();render_led();
        /* Low-priority sampling keeps the indicator current even during capture.
         * Only this thread accesses charger samples; audio remains on main. */
        if(ticks++%10==0)update_charge_indicator();
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
    k_msleep(20); /* Conservative settle after checking the actual always-on 3.3 V rail. */
    if(setup_button(&button,&ext_cb)||setup_button(&onboard,&int_cb)||hvb_store_init()||hvb_ble_init()){
        hvb_led(1,0,0);return -EIO;
    }
    k_thread_create(&bg_thread,bg_stack,K_THREAD_STACK_SIZEOF(bg_stack),background,NULL,NULL,NULL,7,0,K_NO_WAIT);
    hvb_led(0,0,0);
    struct hvb_button buttons;hvb_button_init(&buttons);
    int slot=-1;struct hvb_gate gate={0};
    while(true){
        if(atomic_get(&mic_test_request)&&!atomic_get(&hvb_recording)){
            if(!mic_on){if(start_mic()){atomic_inc(&mic_failures);atomic_set(&mic_test_state,3);atomic_clear(&mic_test_request);continue;}atomic_set(&mic_test_state,2);hvb_led(0,1,1);}
            struct edge test_edge;while(k_msgq_get(&edges,&test_edge,K_NO_WAIT)==0)if(test_edge.down)atomic_clear(&mic_test_request);
            void *test_buf=NULL;size_t test_size=0;int test_rc=dmic_read(mic,0,&test_buf,&test_size,100);
            if(!test_rc&&test_size==640){unsigned level=hvb_audio_level(test_buf);atomic_set(&mic_level,level);if(level>(unsigned)atomic_get(&mic_peak))atomic_set(&mic_peak,level);atomic_inc(&mic_frames);}
            else{atomic_inc(&audio_failures);atomic_set(&mic_test_state,3);atomic_clear(&mic_test_request);}
            if(test_buf)k_mem_slab_free(&audio_slab,test_buf);
            if((int32_t)(k_uptime_get_32()-(uint32_t)atomic_get(&mic_test_deadline))>=0)atomic_clear(&mic_test_request);
            if(!atomic_get(&mic_test_request)){stop_mic();if(atomic_get(&mic_test_state)!=3)atomic_clear(&mic_test_state);hvb_led(0,0,0);hvb_button_init(&buttons);}
            continue;
        }
        if(atomic_get(&mic_test_state)==2){stop_mic();atomic_clear(&mic_test_state);hvb_led(0,0,0);hvb_button_init(&buttons);}
        struct edge e;
        /* Drain queued bounce edges before confirming a release. Microphone
         * warmup can delay this loop by 100 ms, longer than debounce. */
        while(k_msgq_get(&edges,&e,mic_on||buttons.held?K_NO_WAIT:K_MSEC(buttons.release_pending?10:100))==0){
            enum hvb_button_action edge_action=hvb_button_edge(&buttons,e.at,e.down,slot>=0);
            if(edge_action==HVB_BUTTON_STOP){
                int rc=hvb_store_finish(slot,2);slot=-1;stop_mic();hvb_led(0,0,0);hvb_signal(rc?HVB_SIGNAL_ERROR:HVB_SIGNAL_SAVED);
            }
            if(edge_action==HVB_BUTTON_WAKE){
                stop_mic();hvb_config_get(&capture_config);atomic_set(&hvb_recording,1);
                if(start_mic()){atomic_inc(&mic_failures);buttons.tentative=false;atomic_clear(&hvb_recording);hvb_led(1,0,0);}
            }
        }
        enum hvb_button_action action=hvb_button_poll(&buttons,k_uptime_get(),slot>=0);
        if(action==HVB_BUTTON_START&&mic_on){
            uint8_t header[64];slot=hvb_store_begin(header);
            if(slot<0){stop_mic();hvb_led(0,0,0);hvb_signal(HVB_SIGNAL_FULL);continue;}
            memset(&gate,0,sizeof(gate));int rc=0;
            for(unsigned i=0;i<pre_count;i++){
                unsigned j=(pre_index+12-pre_count+i)%12;
                if((rc=add_frame(slot,pre[j])))break;
                (void)hvb_gate_configured(&gate,pre[j],capture_config.threshold,capture_config.silence_ms,capture_config.manual);
            }
            if(rc){hvb_store_abort(slot);slot=-1;stop_mic();hvb_led(1,0,0);}else hvb_led(0,1,0);
        }
        if(action==HVB_BUTTON_PAIR){stop_mic();hvb_ble_pair_window();hvb_led(0,0,1);}
        if(action==HVB_BUTTON_FORGET){stop_mic();hvb_ble_forget_phone();hvb_led(1,0,1);}
        if(!mic_on){if(!buttons.held&&buttons.pair_shown)hvb_led(0,0,0);if(buttons.held)k_msleep(10);continue;}
        void *buf=NULL;size_t size=0;int rc=dmic_read(mic,0,&buf,&size,100);
        if(rc||size!=640){atomic_inc(&audio_failures);if(buf)k_mem_slab_free(&audio_slab,buf);hvb_store_abort(slot);slot=-1;stop_mic();buttons.tentative=false;hvb_led(1,0,0);continue;}
        const int16_t *pcm=buf;
        if(buttons.tentative){memcpy(pre[pre_index],pcm,640);pre_index=(pre_index+1)%12;if(pre_count<12)pre_count++;}
        else if(slot>=0){
            rc=add_frame(slot,pcm);int end=hvb_gate_configured(&gate,pcm,capture_config.threshold,capture_config.silence_ms,capture_config.manual);
            if(rc||end){
                if(rc||end==2)hvb_store_abort(slot);
                else rc=hvb_store_finish(slot,end==3?1:0);
                slot=-1;stop_mic();hvb_led(0,0,0);if(rc)hvb_signal(HVB_SIGNAL_ERROR);else if(end!=2)hvb_signal(HVB_SIGNAL_SAVED);
            }
        }
        k_mem_slab_free(&audio_slab,buf);
    }
    return 0;
}

void hvb_capture_diagnostics(uint8_t out[12]){
    sys_put_le32((uint32_t)atomic_get(&mic_failures),out);
    sys_put_le32((uint32_t)atomic_get(&audio_failures),out+4);
    sys_put_le32((uint32_t)atomic_get(&dropped_edges),out+8);
}
