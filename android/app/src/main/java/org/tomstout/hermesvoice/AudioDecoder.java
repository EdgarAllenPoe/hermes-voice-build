package org.tomstout.hermesvoice;
/** Validated, independent-frame HVB1 decoder shared by playback and host tests. */
final class AudioDecoder {
    private static final int[] STEPS={7,8,9,10,11,12,13,14,16,17,19,21,23,25,28,31,34,37,41,45,50,55,60,66,73,80,88,97,107,118,130,143,157,173,190,209,230,253,279,307,337,371,408,449,494,544,598,658,724,796,876,963,1060,1166,1282,1411,1552,1707,1878,2066,2272,2499,2749,3024,3327,3660,4026,4428,4871,5358,5894,6484,7132,7845,8630,9493,10442,11487,12635,13899,15289,16818,18500,20350,22385,24623,27086,29794,32767};
    private static final int[] CHANGE={-1,-1,-1,-1,2,4,6,8};
    static short[] decode(byte[] b){Wire.validate(b);short[] pcm=new short[Wire.le32(b,12)];int at=0;
        for(int p=64;p<b.length;p+=164){int pred=(short)((b[p]&255)|((b[p+1]&255)<<8)),index=b[p+2]&255;pcm[at++]=(short)pred;
            for(int j=1;j<320;j++){int n=((b[p+4+(j-1)/2]&255)>>(4*((j-1)%2)))&15,step=STEPS[index],diff=step>>3;
                if((n&1)!=0)diff+=step>>2;if((n&2)!=0)diff+=step>>1;if((n&4)!=0)diff+=step;
                pred=Math.max(-32768,Math.min(32767,pred+((n&8)!=0?-diff:diff)));index=Math.max(0,Math.min(88,index+CHANGE[n&7]));pcm[at++]=(short)pred;
            }
        }return pcm;
    }
}
