/* Encrypted, authenticated BLE pull protocol. See docs/05-protocol.md. */
#include "hvb.h"
#include "device_config.h"
#include <zephyr/bluetooth/bluetooth.h>
#include <zephyr/bluetooth/conn.h>
#include <zephyr/bluetooth/gatt.h>
#include <zephyr/bluetooth/hci.h>
#include <zephyr/settings/settings.h>
#include <zephyr/sys/byteorder.h>
#include <string.h>
#include <errno.h>
#define UUID(N) BT_UUID_128_ENCODE(0x58ef0000u+(N),0x35c8,0x4c31,0x89aa,0x81f763051da1)
static struct bt_uuid_128 ctrl_uuid=BT_UUID_INIT_128(UUID(2));
static struct bt_uuid_128 meta_uuid=BT_UUID_INIT_128(UUID(3));
static struct bt_uuid_128 data_uuid=BT_UUID_INIT_128(UUID(4));
static struct bt_uuid_128 info_uuid=BT_UUID_INIT_128(UUID(5));
static uint16_t skipped;
static int selected=-1;static uint8_t meta[20],chunk[180];static size_t chunk_len;
static struct bt_conn *peer;static bool advertising;static int64_t pair_until;
K_MUTEX_DEFINE(peer_lock);
K_MUTEX_DEFINE(pair_lock);
static bool pairing_open(void){
    k_mutex_lock(&pair_lock,K_FOREVER);
    bool open=k_uptime_get()<pair_until;
    k_mutex_unlock(&pair_lock);
    return open;
}
/* BT_DATA_BYTES uses compound literals. File scope gives the payload arrays
 * static storage duration, as required by these static advertisement records. */
static const struct bt_data ad[]={BT_DATA_BYTES(BT_DATA_FLAGS,(BT_LE_AD_GENERAL|BT_LE_AD_NO_BREDR)),BT_DATA_BYTES(BT_DATA_UUID128_ALL,UUID(1))};
static const struct bt_data sd[]={BT_DATA(BT_DATA_NAME_COMPLETE,"Hermes Voice",12)};
/* Zephyr 4.4 removed BT_LE_ADV_CONN. Keep a single connectable advertiser
 * and explicitly retain the stable identity used by Android association.
 * Nearby scanners can recognize this identity; that privacy tradeoff is documented. */
static const struct bt_le_adv_param advertising_parameters = BT_LE_ADV_PARAM_INIT(
    BT_LE_ADV_OPT_CONN | BT_LE_ADV_OPT_USE_IDENTITY,
    BT_GAP_ADV_FAST_INT_MIN_2, BT_GAP_ADV_FAST_INT_MAX_2, NULL);

static ssize_t read_meta(struct bt_conn *c,const struct bt_gatt_attr *a,void *b,uint16_t n,uint16_t o){return bt_gatt_attr_read(c,a,b,n,o,meta,sizeof(meta));}
static ssize_t read_data(struct bt_conn *c,const struct bt_gatt_attr *a,void *b,uint16_t n,uint16_t o){return bt_gatt_attr_read(c,a,b,n,o,chunk,chunk_len);}
static ssize_t read_info(struct bt_conn *c,const struct bt_gatt_attr *a,void *b,uint16_t n,uint16_t o){
    uint8_t info[32]={1,0,0,0,0,0,0,0};info[1]=atomic_get(&hvb_recording)?1:0;
    info[2]=(uint8_t)hvb_store_count();sys_put_le16(hvb_battery_mv(),info+4);
    info[8]=1;info[9]=0;info[10]=3;info[11]=3; /* diagnostic schema and firmware version */
    hvb_capture_diagnostics(info+12);hvb_store_diagnostics(info+24);
    sys_put_le16(1,info+30); /* capability bit 0: connection-local SKIP */
    return bt_gatt_attr_read(c,a,b,n,o,info,sizeof(info));
}
static ssize_t control(struct bt_conn *c,const struct bt_gatt_attr *a,const void *v,uint16_t n,uint16_t o,uint8_t flags){
    ARG_UNUSED(c);ARG_UNUSED(a);ARG_UNUSED(flags);const uint8_t *p=v;
    if(o||!n)return BT_GATT_ERR(BT_ATT_ERR_INVALID_ATTRIBUTE_LEN);
    if(p[0]==1&&n==1){selected=hvb_store_next(meta,skipped);chunk_len=0;return n;}
    if(p[0]==2&&n==5&&selected>=0){
        uint32_t pos=sys_get_le32(p+1),total=sys_get_le32(meta+16);
        if(pos>=total)return BT_GATT_ERR(BT_ATT_ERR_INVALID_OFFSET);
        chunk_len=MIN(sizeof(chunk),total-pos);
        if(hvb_store_read(selected,pos,chunk,chunk_len)){chunk_len=0;return BT_GATT_ERR(BT_ATT_ERR_UNLIKELY);}
        return n;
    }
    if(p[0]==4&&n==17&&selected>=0){
        if(memcmp(meta,p+1,16))return BT_GATT_ERR(BT_ATT_ERR_VALUE_NOT_ALLOWED);
        skipped|=(uint16_t)(1u<<selected);selected=-1;memset(meta,0,20);chunk_len=0;
        return n; /* No flash write or deletion; reset on the next connection. */
    }
    if(p[0]==3&&n==17&&selected>=0){
        if(hvb_store_ack(selected,p+1))return BT_GATT_ERR(BT_ATT_ERR_UNLIKELY);
        selected=-1;memset(meta,0,20);chunk_len=0;return n;
    }
    return BT_GATT_ERR(BT_ATT_ERR_VALUE_NOT_ALLOWED);
}
BT_GATT_SERVICE_DEFINE(voice_service,
    BT_GATT_PRIMARY_SERVICE(BT_UUID_DECLARE_128(UUID(1))),
    BT_GATT_CHARACTERISTIC(&ctrl_uuid.uuid,BT_GATT_CHRC_WRITE,BT_GATT_PERM_WRITE_AUTHEN,NULL,control,NULL),
    BT_GATT_CHARACTERISTIC(&meta_uuid.uuid,BT_GATT_CHRC_READ,BT_GATT_PERM_READ_AUTHEN,read_meta,NULL,NULL),
    BT_GATT_CHARACTERISTIC(&data_uuid.uuid,BT_GATT_CHRC_READ,BT_GATT_PERM_READ_AUTHEN,read_data,NULL,NULL),
    BT_GATT_CHARACTERISTIC(&info_uuid.uuid,BT_GATT_CHRC_READ,BT_GATT_PERM_READ_AUTHEN,read_info,NULL,NULL)
);
static void connected(struct bt_conn *c,uint8_t err){
    if(err)return;
    k_mutex_lock(&peer_lock,K_FOREVER);peer=bt_conn_ref(c);advertising=false;k_mutex_unlock(&peer_lock);
    skipped=0;selected=-1;chunk_len=0;memset(meta,0,20);bt_conn_set_security(c,BT_SECURITY_L4);
}
static void disconnected(struct bt_conn *c,uint8_t reason){
    ARG_UNUSED(c);ARG_UNUSED(reason);k_mutex_lock(&peer_lock,K_FOREVER);
    if(peer){bt_conn_unref(peer);peer=NULL;}selected=-1;k_mutex_unlock(&peer_lock);
}
BT_CONN_CB_DEFINE(callbacks)={.connected=connected,.disconnected=disconnected};
static enum bt_security_err accept_pair(struct bt_conn *c,const struct bt_conn_pairing_feat *f){
    ARG_UNUSED(c);ARG_UNUSED(f);return pairing_open()?BT_SECURITY_ERR_SUCCESS:BT_SECURITY_ERR_PAIR_NOT_ALLOWED;
}
static void display_passkey(struct bt_conn *c,unsigned int passkey){ARG_UNUSED(c);ARG_UNUSED(passkey);/* Use locally printed pairing card, never advertise the code. */}
static void cancel_pair(struct bt_conn *c){ARG_UNUSED(c);}
static struct bt_conn_auth_cb auth={.passkey_display=display_passkey,.cancel=cancel_pair,.pairing_accept=accept_pair};
int hvb_ble_init(void){
    int rc=bt_enable(NULL);if(rc)return rc;
    rc=settings_load();if(rc)return rc;
    rc=bt_conn_auth_cb_register(&auth);if(rc)return rc;
    return bt_passkey_set(HVB_PAIRING_CODE);
}
void hvb_ble_pair_window(void){k_mutex_lock(&pair_lock,K_FOREVER);pair_until=k_uptime_get()+60000;k_mutex_unlock(&pair_lock);}
void hvb_ble_forget_phone(void){
    k_mutex_lock(&peer_lock,K_FOREVER);if(peer)bt_conn_disconnect(peer,BT_HCI_ERR_REMOTE_USER_TERM_CONN);k_mutex_unlock(&peer_lock);
    bt_unpair(BT_ID_DEFAULT,BT_ADDR_LE_ANY);hvb_ble_pair_window();
}
void hvb_ble_maintenance(void){
    bool want=hvb_store_count()>0||pairing_open();
    k_mutex_lock(&peer_lock,K_FOREVER);
    if(want&&!peer&&!advertising){int rc=bt_le_adv_start(&advertising_parameters,ad,ARRAY_SIZE(ad),sd,ARRAY_SIZE(sd));if(!rc||rc==-EALREADY)advertising=true;}
    if(!want){
        if(advertising){bt_le_adv_stop();advertising=false;}
        if(peer)bt_conn_disconnect(peer,BT_HCI_ERR_REMOTE_USER_TERM_CONN);
    }
    k_mutex_unlock(&peer_lock);
}
