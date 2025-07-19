import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {
        List<Byte[]> list = new ArrayList<>();
        while (true) {
            list.add(new Byte[1024 * 1024 * 10]);
        }
    }

}
