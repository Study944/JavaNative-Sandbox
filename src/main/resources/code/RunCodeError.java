import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class Main {

    public static void main(String[] args) throws Exception {
        String root = System.getProperty("user.dir");
        String path = root + File.separator +"病毒.bat";
        Process process = Runtime.getRuntime().exec(path);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"));
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            // 拼接输出结果为一行
            stringBuilder.append(line).append("\n");
        }
        System.out.println(stringBuilder);
    }

}
