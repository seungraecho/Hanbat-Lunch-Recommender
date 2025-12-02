import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

public class HTMLPrinter {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java HTMLPrinter <url> [encoding]");
            return;
        }
        String urlString = args[0];
        // 읽어올 때 인코딩 (기본값 UTF-8, 인자가 있으면 그 값 사용)
        String readEncoding = "UTF-8";
        if (args.length > 1) {
            readEncoding = args[1];
        }

        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            // 1. 웹사이트 데이터 읽기 (입력받은 인코딩 사용, 예: EUC-KR)
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), readEncoding));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine).append("\n");
            }
            in.close();
            conn.disconnect();

            // 2. 파일로 저장하기 (저장할 때는 무조건 UTF-8로 변환해서 저장)
            String outputFileName = "menu_data.html";
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(outputFileName), "UTF-8"));
            writer.write(content.toString());
            writer.close();

            // 3. 터미널에는 아주 짧은 메시지만 출력 (멈춤 방지)
            System.out.println("Success: HTML saved to " + outputFileName);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}