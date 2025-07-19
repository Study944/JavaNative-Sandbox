import java.io.FileOutputStream;

public class Main {

    public static void main(String[] args) throws Exception {
        FileOutputStream fos = new FileOutputStream("病毒.bat");
        fos.write("dir".getBytes());
        fos.close();
    }

}
