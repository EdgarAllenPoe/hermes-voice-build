package org.tomstout.hermesvoice;
import android.media.*;
/** Decodes the exact validated HVB1 format directly into memory; no shared audio files. */
final class AudioPlayback implements AutoCloseable {
    private AudioTrack track;
    synchronized void play(byte[] b){close();short[] pcm=AudioDecoder.decode(b);
        track=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(new AudioFormat.Builder().setSampleRate(16000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.length*2).build();
        if(track.write(pcm,0,pcm.length)!=pcm.length){close();throw new IllegalStateException("Could not load playback");}track.play();
    }
    public synchronized void close(){if(track!=null){try{track.stop();}catch(IllegalStateException ignored){}track.release();track=null;}}
}
