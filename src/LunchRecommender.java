import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 점심 추천 도우미 프로그램
 */
public class LunchRecommender extends JFrame {

    private JButton recommendButton;
    private JComboBox<Integer> countComboBox;
    private JTextArea resultTextArea;

    private final String[] randomMenuList = {
            "제육덮밥", "돈까스", "순대국밥", "부대찌개", "닭갈비덮밥",
            "김치찌개", "된장찌개", "짜장면", "짬뽕", "피자",
            "치킨", "햄버거", "서브웨이", "쌀국수", "마라탕",
            "초밥", "파스타", "카레", "냉면", "비빔밥"
    };

    public LunchRecommender() {
        setTitle("점심 추천 도우미");
        setSize(450, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("학식", createCafeteriaPanel());
        tabbedPane.addTab("기숙사식", createDormitoryPanel());
        tabbedPane.addTab("메뉴 추천", createRecommendPanel());

        add(tabbedPane, BorderLayout.CENTER);
    }

    private String getCurrentDateString(String panelType) {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        String mealType;
        Date dateToFormat = now.getTime();

        if (panelType.equals("cafeteria")) {
            if (hour >= 14 && hour < 19) {
                mealType = "석식";
            } else {
                mealType = "중식";
                if (hour >= 19) {
                    now.add(Calendar.DAY_OF_MONTH, 1);
                    dateToFormat = now.getTime();
                }
            }
        } else {
            if (hour >= 19 || hour < 4) {
                mealType = "다음 날 조식";
                if (hour >= 19) {
                    now.add(Calendar.DAY_OF_MONTH, 1);
                    dateToFormat = now.getTime();
                }
            } else if (hour >= 4 && hour < 9) {
                mealType = "조식";
            } else if (hour >= 9 && hour < 14) {
                mealType = "중식";
            } else {
                mealType = "석식";
            }
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy년 MM월 dd일");
        String dateStr = sdf.format(dateToFormat);
        return String.format("%s (%s)", dateStr, mealType);
    }

    private String fetchMenu(String urlString, String encoding) {
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

    private String fetchFacultyMenuJson() {
        StringBuilder content = new StringBuilder();
        try {
            URL url = new URL("https://www.hanbat.ac.kr/prog/carteGuidance/kor/sub06_030301/C1/getCalendar.do");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Accept", "application/json");

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

    private String parseFacultyJson(String json) {
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

    private String parseDormitoryMenu(String html) {
        if (html == null)
            return "메뉴 정보를 가져오는데 실패했습니다.";

        SimpleDateFormat sdf = new SimpleDateFormat("MM월 dd일");
        String todayStr = sdf.format(new Date());

        try {
            // Find today's date in the HTML
            // Example: <th scope="row">11월 26일(화)</th>
            // We search for "11월 26일"
            int dateIndex = html.indexOf(todayStr);
            if (dateIndex == -1)
                return "오늘의 식단 정보가 없습니다.";

            // The structure is:
            // <tr><th>Date</th><td>Breakfast</td><td>Lunch</td><td>Dinner</td></tr>
            // We need the 2nd <td> (Lunch)

            int firstTd = html.indexOf("<td", dateIndex);
            int secondTd = html.indexOf("<td", firstTd + 1); // Lunch

            if (secondTd == -1)
                return "식단 정보를 찾을 수 없습니다.";

            int closeTd = html.indexOf("</td>", secondTd);
            String menuContent = html.substring(secondTd, closeTd);

            // Remove tags and clean up
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

    private JPanel createCafeteriaPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel dateLabel = new JLabel(getCurrentDateString("cafeteria"));
        dateLabel.setHorizontalAlignment(SwingConstants.CENTER);
        dateLabel.setFont(new Font("Monospaced", Font.BOLD, 16));
        panel.add(dateLabel, BorderLayout.NORTH);

        JTextArea menuArea = new JTextArea();
        menuArea.setEditable(false);
        menuArea.setFont(new Font("Monospaced", Font.PLAIN, 14));

        // Fetch menu in background
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                String json = fetchFacultyMenuJson();
                return parseFacultyJson(json);
            }

            @Override
            protected void done() {
                try {
                    String menu = get();
                    menuArea.setText(menu);
                } catch (Exception e) {
                    menuArea.setText("메뉴를 불러오는데 실패했습니다.");
                    e.printStackTrace();
                }
            }
        }.execute();

        menuArea.setText("메뉴를 불러오는 중...");

        JScrollPane scrollPane = new JScrollPane(menuArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createDormitoryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel dateLabel = new JLabel(getCurrentDateString("dormitory"));
        dateLabel.setHorizontalAlignment(SwingConstants.CENTER);
        dateLabel.setFont(new Font("Monospaced", Font.BOLD, 16));
        panel.add(dateLabel, BorderLayout.NORTH);

        JTextArea menuArea = new JTextArea();
        menuArea.setEditable(false);
        menuArea.setFont(new Font("Monospaced", Font.PLAIN, 14));

        // Fetch menu in background
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                String html = fetchMenu("https://dorm.hanbat.ac.kr/sub-0205", "UTF-8");
                return parseDormitoryMenu(html);
            }

            @Override
            protected void done() {
                try {
                    String menu = get();
                    menuArea.setText(menu);
                } catch (Exception e) {
                    menuArea.setText("메뉴를 불러오는데 실패했습니다.");
                    e.printStackTrace();
                }
            }
        }.execute();

        menuArea.setText("메뉴를 불러오는 중...");

        JScrollPane scrollPane = new JScrollPane(menuArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createRecommendPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        topPanel.add(new JLabel("몇 개의 메뉴를 추천할까요?"));

        Integer[] numbers = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
        countComboBox = new JComboBox<>(numbers);
        topPanel.add(countComboBox);

        panel.add(topPanel, BorderLayout.NORTH);

        resultTextArea = new JTextArea();
        resultTextArea.setEditable(false);
        resultTextArea.setFont(new Font("Monospaced", Font.BOLD, 16));
        resultTextArea.setLineWrap(true);
        resultTextArea.setWrapStyleWord(true);
        resultTextArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("추천 결과"),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        panel.add(new JScrollPane(resultTextArea), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        recommendButton = new JButton("추천받기!");
        bottomPanel.add(recommendButton);

        recommendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int count = (Integer) countComboBox.getSelectedItem();
                List<String> menuList = new ArrayList<>(List.of(randomMenuList));
                Collections.shuffle(menuList);

                StringBuilder resultText = new StringBuilder();
                resultText.append("오늘의 추천 메뉴입니다!\n\n");
                for (int i = 0; i < count; i++) {
                    resultText.append("  ").append(i + 1).append(". ").append(menuList.get(i)).append("\n");
                }

                resultTextArea.setText(resultText.toString());
                recommendButton.setText("다시 추천받기");
            }
        });

        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new LunchRecommender().setVisible(true);
            }
        });
    }
}