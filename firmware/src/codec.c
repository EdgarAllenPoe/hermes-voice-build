/* Original HVB1 independent-frame IMA ADPCM implementation. MIT license. */
#include "codec.h"
#include <string.h>
static const int steps[89]={7,8,9,10,11,12,13,14,16,17,19,21,23,25,28,31,34,37,41,45,50,55,60,66,73,80,88,97,107,118,130,143,157,173,190,209,230,253,279,307,337,371,408,449,494,544,598,658,724,796,876,963,1060,1166,1282,1411,1552,1707,1878,2066,2272,2499,2749,3024,3327,3660,4026,4428,4871,5358,5894,6484,7132,7845,8630,9493,10442,11487,12635,13899,15289,16818,18500,20350,22385,24623,27086,29794,32767};
static const int changes[8]={-1,-1,-1,-1,2,4,6,8};
static int clamp(int x,int lo,int hi){return x<lo?lo:(x>hi?hi:x);}
void hvb_encode_frame(const int16_t pcm[320],uint8_t out[164]){
    memset(out,0,164);out[0]=(uint8_t)pcm[0];out[1]=(uint16_t)pcm[0]>>8;
    int pred=pcm[0],index=0;
    for(unsigned i=1;i<320;i++){
        int diff=pcm[i]-pred,n=diff<0?8:0,step=steps[index],recon=step>>3;
        if(diff<0)diff=-diff;
        if(diff>=step){n|=4;diff-=step;recon+=step;}
        if(diff>=step>>1){n|=2;diff-=step>>1;recon+=step>>1;}
        if(diff>=step>>2){n|=1;recon+=step>>2;}
        pred=clamp(pred+((n&8)?-recon:recon),-32768,32767);
        index=clamp(index+changes[n&7],0,88);
        out[4+(i-1)/2]|=(uint8_t)(n<<(4*((i-1)%2)));
    }
}
void hvb_decode_frame(const uint8_t in[164],int16_t pcm[320]){
    int pred=(int16_t)((uint16_t)in[0]|((uint16_t)in[1]<<8));
    int index=in[2]>88?88:in[2];pcm[0]=(int16_t)pred;
    for(unsigned i=1;i<320;i++){
        int n=(in[4+(i-1)/2]>>(4*((i-1)%2)))&15,step=steps[index],d=step>>3;
        if(n&1)d+=step>>2;
        if(n&2)d+=step>>1;
        if(n&4)d+=step;
        pred=clamp(pred+((n&8)?-d:d),-32768,32767);index=clamp(index+changes[n&7],0,88);
        pcm[i]=(int16_t)pred;
    }
}
int hvb_gate_update(struct hvb_gate *g,const int16_t *pcm,unsigned threshold){
    int32_t sum=0;uint32_t dev=0;
    for(unsigned i=0;i<320;i++)sum+=pcm[i];
    int32_t mean=sum/320;
    for(unsigned i=0;i<320;i++){int32_t d=pcm[i]-mean;dev+=(uint32_t)(d<0?-d:d);}
    int voiced=(dev/320)>=threshold;g->frames++;
    if(voiced){g->voiced_run++;g->silence=0;if(g->voiced_run>=3)g->heard=1;}
    else {g->voiced_run=0;g->silence++;}
    if(g->frames>=3000)return 3;
    if(g->heard&&g->silence>=60)return 1;
    if(!g->heard&&g->frames>=250)return 2;
    return 0;
}
