package org.tomstout.hermesvoice;
/** Compatible with legacy 8-byte INFO and the 32-byte v1 diagnostic extension. */
final class RecorderInfo {
    final boolean skip;
    final boolean enhanced,recording,lowBattery,manual;
    final int batteryMv,chargeState,currentMa,micState,level,peak,silenceMs,threshold,freeSlots;
    final long micFrames;
    String batteryText(){return !enhanced?"Battery details require firmware 0.4.0":
        switch(chargeState){case 1->(lowBattery?"Low battery":"On battery");case 2->"Charging";case 3->"USB powered \u00b7 not charging";case 4->"Charging problem";default->"Battery status unavailable";}+
        (batteryMv>0?" \u00b7 "+String.format(java.util.Locale.ROOT,"%.2f V",batteryMv/1000.0):"");}

    final int queued,quarantined;
    final long errors;
    final String text;
    RecorderInfo(byte[] b) {
        if(b.length<8||b[0]!=1)throw new IllegalArgumentException("Unsupported recorder status");
        int mv=(b[4]&255)|((b[5]&255)<<8);
        queued=b[2]&255;recording=b[1]!=0;batteryMv=mv;
        enhanced=b.length>=64&&b[32]==2&&(b[30]&30)==30;
        chargeState=enhanced?b[33]&255:0;lowBattery=enhanced&&b[34]!=0;micState=enhanced?b[35]&255:0;
        currentMa=enhanced?Wire.le32(b,36):0;level=enhanced?(b[40]&255)|((b[41]&255)<<8):0;
        peak=enhanced?(b[42]&255)|((b[43]&255)<<8):0;silenceMs=enhanced?(b[44]&255)|((b[45]&255)<<8):2000;
        threshold=enhanced?(b[46]&255)|((b[47]&255)<<8):40;manual=enhanced&&b[48]!=0;
        freeSlots=enhanced?b[49]&255:-1;micFrames=enhanced?Integer.toUnsignedLong(Wire.le32(b,52)):0;
        String base="Recorder queue: "+queued+"\nCapture active: "+(b[1]!=0)+
                    "\nBattery: "+(mv==0?"unavailable":mv+" mV");
        skip=b.length>=32&&b[8]==1&&(b[30]&1)!=0;
        quarantined=b.length>=32&&b[8]==1?((b[28]&255)|((b[29]&255)<<8)):0;
        errors=b.length>=32&&b[8]==1?Integer.toUnsignedLong(Wire.le32(b,12))+
            Integer.toUnsignedLong(Wire.le32(b,16))+Integer.toUnsignedLong(Wire.le32(b,20))+
            Integer.toUnsignedLong(Wire.le32(b,24)):0;
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
