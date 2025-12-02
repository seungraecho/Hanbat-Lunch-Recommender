import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class TestMenuFetcher {

    public static void main(String[] args) {
        System.out.println("Testing Faculty Menu Fetching...");
        String json = fetchFacultyMenuJson();
        System.out.println("JSON Length: " + (json != null ? json.length() : "null"));
        if (json != null) {
            saveToFile(json, "menu_dump.json");
            String menu = parseFacultyJson(json);
            System.out.println("Parsed Menu: " + menu);
        } else {
            System.out.println("Failed to fetch JSON.");
        }
    }

    private static void saveToFile(String content, String filename) {
        try (java.io.BufferedWriter writer = new java.io.BufferedWriter(
                new java.io.OutputStreamWriter(new java.io.FileOutputStream(filename), StandardCharsets.UTF_8))) {
            writer.write(content);
            System.out.println("Data saved to " + filename);
        } catch (Exception e) {
            System.out.println("Failed to save file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String fetchFacultyMenuJson() {
        StringBuilder content = new StringBuilder();
        try {
            // Calculate Monday of the current week
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            String bgnde = sdf.format(cal.getTime());
            System.out.println("Requesting menu for week starting: " + bgnde);

            URL url = new URL("https://www.hanbat.ac.kr/prog/carteGuidance/kor/sub06_030301/C1/getCalendar.do");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Accept", "application/json");

            int responseCode = conn.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();
            conn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return content.toString();
    }

    private static String parseFacultyJson(String json) {
        if (json == null)
            return "메뉴 정보를 가져오는데 실패했습니다.";

        try {
            // New Parsing Logic
            // 1. Find the item with "type":"B" (Lunch)
            int typeBIndex = json.indexOf("\"type\":\"B\"");
            if (typeBIndex == -1)
                return "중식 정보를 찾을 수 없습니다.";

            // Find the start of this item object
            int itemStart = json.lastIndexOf("{", typeBIndex);
            int itemEnd = json.indexOf("}", typeBIndex);
            String lunchJson = json.substring(itemStart, itemEnd + 1);

            // 2. Determine today's day of week
            Calendar cal = Calendar.getInstance();
            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK); // Sun=1, Mon=2, ...
            System.out.println("Day of week: " + dayOfWeek);

            if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                return "주말에는 운영하지 않습니다.";
            }

            int menuIndex = dayOfWeek - 1; // Mon=1, Tue=2, ...
            String key = "\"menu" + menuIndex + "\":";

            int keyIndex = lunchJson.indexOf(key);
            if (keyIndex == -1)
                return "오늘의 메뉴 정보가 없습니다.";

            int valueStart = lunchJson.indexOf("\"", keyIndex + key.length()) + 1;
            int valueEnd = lunchJson.indexOf("\"", valueStart);
            String menuContent = lunchJson.substring(valueStart, valueEnd);

            // Clean up content
            menuContent = menuContent.replace("\\r\\n", "\n");
            menuContent = menuContent.replace("&amp;", "&");
            menuContent = menuContent.replace("&nbsp;", " ");
            menuContent = menuContent.replace("&lt;", "<");
            menuContent = menuContent.replace("&gt;", ">");
            menuContent = menuContent.replace("\\/", "/"); // Handle escaped slashes

            return menuContent;

        } catch (Exception e) {
            e.printStackTrace();
            return "메뉴 파싱 중 오류가 발생했습니다.";
        }
    }
}
