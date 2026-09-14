import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;

/** Sign a compiled personal APK locally; require the original APK's signer identity. */
public final class SignPersonalApk {
    static String fingerprint(X509Certificate cert) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
    }
    static ApkVerifier.Result verify(File apk) throws Exception {
        return new ApkVerifier.Builder(apk).build().verify();
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=4)throw new IllegalArgumentException("Usage: unsigned.apk output.apk retained.keystore previous-personal.apk");
        File input=new File(args[0]),output=new File(args[1]),keyFile=new File(args[2]),previous=new File(args[3]);
        if(output.exists())throw new IllegalArgumentException("Output already exists; choose a new file");
        KeyStore store=KeyStore.getInstance("JKS");
        try(InputStream stream=new FileInputStream(keyFile)){store.load(stream,"android".toCharArray());}
        PrivateKey key=(PrivateKey)store.getKey("androiddebugkey","android".toCharArray());
        X509Certificate cert=(X509Certificate)store.getCertificate("androiddebugkey");
        if(key==null||cert==null)throw new IllegalArgumentException("Retained signing identity is incomplete");
        ApkVerifier.Result old=verify(previous);
        if(!old.isVerified()||old.getSignerCertificates().size()!=1||
           !fingerprint(cert).equals(fingerprint(old.getSignerCertificates().get(0))))
            throw new SecurityException("The key does not match the previous personal APK");
        if(verify(input).isVerified())throw new IllegalArgumentException("Expected an unsigned personal build");
        ApkSigner.SignerConfig signer=new ApkSigner.SignerConfig.Builder("hermes-personal",key,List.of(cert)).build();
        new ApkSigner.Builder(List.of(signer)).setInputApk(input).setOutputApk(output)
            .setV1SigningEnabled(false).setV2SigningEnabled(true).setV3SigningEnabled(true)
            .setV4SigningEnabled(false).build().sign();
        ApkVerifier.Result result=verify(output);
        if(!result.isVerified()||result.getSignerCertificates().size()!=1||
           !fingerprint(cert).equals(fingerprint(result.getSignerCertificates().get(0))))
            throw new SecurityException("Output signature verification failed");
        System.out.println("APK signature verified; signer matches the previous personal APK.");
        System.out.println("Signer certificate SHA-256: "+fingerprint(cert));
        System.out.println("APK SHA-256: "+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(output.toPath()))));
    }
}
