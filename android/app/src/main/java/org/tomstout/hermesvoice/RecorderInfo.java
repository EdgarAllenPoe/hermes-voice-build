package org.tomstout.hermesvoice;
/** Compatible with legacy 8-byte INFO and the 32-byte v1 diagnostic extension. */
final class RecorderInfo {
    final boolean skip;
    final int queued;
    final String text;
    RecorderInfo(byte[] b) {
        if(b.length<8||b[0]!=1)throw new IllegalArgumentException("Unsupported recorder status");
        int mv=(b[4]&255)|((b[5]&255)<<8);
        queued=b[2]&255;
        String base="Recorder queue: "+queued+"\nCapture active: "+(b[1]!=0)+
                    "\nBattery: "+(mv==0?"unavailable":mv+" mV");
        skip=b.length>=32&&b[8]==1&&(b[30]&1)!=0;
        if(b.length>=32&&b[8]==1) {
            text=base+"\nFirmware: "+(b[9]&255)+"."+(b[10]&255)+"."+(b[11]&255)+
                 "\nMicrophone start errors: "+Integer.toUnsignedLong(Wire.le32(b,12))+
                 "\nAudio read errors: "+Integer.toUnsignedLong(Wire.le32(b,16))+
                 "\nDropped button edges: "+Integer.toUnsignedLong(Wire.le32(b,20))+
                 "\nFlash errors: "+Integer.toUnsignedLong(Wire.le32(b,24))+
                 "\nQuarantined recorder slots: "+((b[28]&255)|((b[29]&255)<<8));
        } else text=base+"\nLegacy firmware diagnostics";
    }
}
