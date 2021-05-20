import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

public class Utilities {

    static Date getYesterday() {
        Date in = new Date();
        LocalDateTime ldt = LocalDateTime.ofInstant(in.toInstant(), ZoneOffset.UTC);
        Date out = Date.from(ldt.minusDays(1).atZone(ZoneOffset.UTC).toInstant());
        return out;
    }
}
