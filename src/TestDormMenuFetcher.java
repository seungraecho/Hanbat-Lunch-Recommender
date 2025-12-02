import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TestDormMenuFetcher {

    public static void main(String[] args) {
        System.out.println("Testing Dormitory Menu Fetching...");
        String html = fetchMenu("https://dorm.hanbat.ac.kr/sub-0205", "UTF-8");
        System.out.println("HTML Length: " + (html != null ? html.length() : "null"));
        if (html != null) {
            saveToFile(html, "dorm_dump.html");
            String menu = parseDormitoryMenu(html);
            System.out.println("Parsed Menu: " + menu);
        } else {
            System.out.println("Failed to fetch HTML.");
        }
    }

    private static void saveToFile(String content, String filename) {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filename), StandardCharsets.UTF_8))) {
            writer.write(content);
            System.out.println("Data saved to " + filename);
        } catch (Exception e) {
            System.out.println("Failed to save file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String fetchMenu(String urlString, String encoding) {
        StringBuilder content = new StringBuilder();
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), encoding));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine).append("\n");
            }
            in.close();
            conn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return content.toString();
    }

    private static String parseDormitoryMenu(String html) {
        if (html == null)
            return "메뉴 정보를 가져오는데 실패했습니다.";

        SimpleDateFormat sdf = new SimpleDateFormat("MM월 dd일");
        String todayStr = sdf.format(new Date());
        System.out.println("Searching for date: " + todayStr);

        try {
            int dateIndex = html.indexOf(todayStr);
            if (dateIndex == -1)
                return "오늘의 식단 정보가 없습니다.";

            int firstTd = html.indexOf("<td", dateIndex);
            int secondTd = html.indexOf("<td", firstTd + 1); // Lunch

            if (secondTd == -1)
                return "식단 정보를 찾을 수 없습니다.";

            int closeTd = html.indexOf("</td>", secondTd);
            String menuContent = html.substring(secondTd, closeTd);

            menuContent = menuContent.replaceAll("<br\\s*/?>", "\n");
            menuContent = menuContent.replaceAll("<.*?>", "");
            menuContent = menuContent.replaceAll("&amp;", "&");
            menuContent = menuContent.replaceAll("&nbsp;", " ");
            menuContent = menuContent.trim();

            return menuContent;

        } catch (Exception e) {
            e.printStackTrace();
            return "메뉴 파싱 중 오류가 발생했습니다.";
        }
    }
}
