import java.nio.file.*;
import org.tomstout.hermesvoice.Wire;
import org.tomstout.hermesvoice.Endpoint;
public final class WireTest {
 private static int count=0;
 private static void check(boolean b){if(!b)throw new AssertionError("Check "+count+" failed");count++;}
 public static void main(String[] args)throws Exception{
  byte[] b=Files.readAllBytes(Path.of(args[0]));String id=Wire.validate(b);
  check(id.equals("aa72fd6b-2400-4c9b-87ca-81d303e780f1"));check(Wire.hash(b).length()==64);
  check(Wire.id(Wire.uuidBytes(id),0).equals(id));check(Wire.readCommand(1234)[0]==2);check(Wire.le32(Wire.readCommand(1234),1)==1234);
  b[b.length-1]^=1;try{Wire.validate(b);throw new AssertionError("CRC accepted");}catch(IllegalArgumentException expected){count++;}
  check(Endpoint.parse("http://100.90.2.3:8765/v1/voice").getPort()==8765);
  check(Endpoint.parse("https://hermes.example.ts.net/v1/voice").getProtocol().equals("https"));
  String[] invalid={"http://8.8.8.8:8765/v1/voice","http://100.1.2.3:8765/v1/voice","http://100.90.2.3:80/v1/voice","https://evil.example/v1/voice","https://u:p@h.example.ts.net/v1/voice","https://h.example.ts.net/v1/voice?q=1","http://100.90.999.2:8765/v1/voice"};
  for(String x:invalid)try{Endpoint.parse(x);throw new AssertionError("Bad endpoint accepted: "+x);}catch(IllegalArgumentException|java.net.URISyntaxException expected){count++;}
  System.out.println("Pure Java protocol/endpoint checks passed: "+count+" (not an Android app build)");
 }
}
