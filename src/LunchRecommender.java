import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 점심 추천 도우미 프로그램 (석식 파싱 버그 수정 버전)
 */
public class LunchRecommender extends JFrame {

    // 상수 정의
    private static final int WINDOW_WIDTH = 450;
    private static final int WINDOW_HEIGHT = 400;
    private static final int CONNECTION_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 5000;
    
    private static final String FACULTY_MENU_URL = 
        "https://www.hanbat.ac.kr/prog/carteGuidance/kor/sub06_030301/C1/getCalendar.do";
    private static final String DORMITORY_MENU_URL = 
        "https://dorm.hanbat.ac.kr/sub-0205";
    
    private static final String[] RANDOM_MENU_LIST = {
    // 한식 - 밥류
    "제육덮밥", "비빔밥", "김치볶음밥", "오므라이스", "돈까스",
    "치킨까스", "생선까스", "함박스테이크", "불고기덮밥", "카레라이스",
    "하이라이스", "낙지덮밥", "오징어덮밥", "참치마요덮밥", "스팸마요덮밥",
    "닭갈비덮밥", "차슈덮밥", "규동", "가츠동", "텐동",
    
    // 한식 - 국/찌개/탕
    "김치찌개", "된장찌개", "부대찌개", "순두부찌개", "청국장",
    "순대국밥", "설렁탕", "갈비탕", "육개장", "삼계탕",
    "감자탕", "뼈해장국", "콩나물국밥", "돼지국밥", "소머리국밥",
    
    // 한식 - 면류
    "냉면", "비빔냉면", "잔치국수", "칼국수", "수제비",
    "쫄면", "콩국수", "막국수", "밀면",
    
    // 한식 - 분식
    "떡볶이", "라볶이", "김밥", "튀김", "순대",
    
    // 중식
    "짜장면", "짬뽕", "탕수육", "볶음밥", "마파두부",
    "깐풍기", "유린기", "고추잡채", "마라탕", "마라샹궈",
    "훠궈", "양꼬치",
    
    // 일식
    "초밥", "회덮밥", "우동", "라멘", "돈코츠라멘",
    "미소라멘", "규카츠", "타코야키", "오코노미야키", "소바",
    "카츠카레",
    
    // 양식
    "파스타", "피자", "햄버거", "스테이크", "리조또",
    "그라탱", "샌드위치", "브런치", "샐러드",
    
    // 동남아/기타
    "쌀국수", "팟타이", "똠양꿍", "분짜", "반미",
    "카오팟", "나시고랭", "커리", "난", "탄두리치킨",
    "케밥", "타코", "부리또",
    
    // 패스트푸드/프랜차이즈
    "치킨", "서브웨이", "맥도날드", "버거킹", "롯데리아",
    "KFC", "피자헛", "도미노피자"
    };

    // UI 컴포넌트
    private JComboBox<Integer> countComboBox;
    private JTextArea resultTextArea;
    private JButton recommendButton;

    public LunchRecommender() {
        initializeFrame();
        initializeComponents();
    }

    private void initializeFrame() {
        setTitle("점심 추천 도우미");
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
    }

    private void initializeComponents() {
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("학식", createMenuPanel(MenuType.CAFETERIA));
        tabbedPane.addTab("기숙사식", createMenuPanel(MenuType.DORMITORY));
        tabbedPane.addTab("메뉴 추천", createRecommendPanel());
        add(tabbedPane, BorderLayout.CENTER);
    }

    // 메뉴 타입 enum
    private enum MenuType {
        CAFETERIA, DORMITORY
    }

    // 통합된 메뉴 패널 생성 메서드
    private JPanel createMenuPanel(MenuType type) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 날짜 라벨
        JLabel dateLabel = new JLabel(getDateString(type));
        dateLabel.setHorizontalAlignment(SwingConstants.CENTER);
        dateLabel.setFont(new Font("Monospaced", Font.BOLD, 16));
        panel.add(dateLabel, BorderLayout.NORTH);

        // 메뉴 텍스트 영역
        JTextArea menuArea = createMenuTextArea();
        panel.add(new JScrollPane(menuArea), BorderLayout.CENTER);

        // 백그라운드에서 메뉴 로드
        loadMenuAsync(menuArea, type);

        return panel;
    }

    private JTextArea createMenuTextArea() {
        JTextArea menuArea = new JTextArea("메뉴를 불러오는 중...");
        menuArea.setEditable(false);
        menuArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        return menuArea;
    }

    private void loadMenuAsync(JTextArea menuArea, MenuType type) {
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return type == MenuType.CAFETERIA ? 
                    fetchAndParseFacultyMenu() : fetchAndParseDormitoryMenu();
            }

            @Override
            protected void done() {
                try {
                    menuArea.setText(get());
                } catch (Exception e) {
                    menuArea.setText("메뉴를 불러오는데 실패했습니다.");
                }
            }
        }.execute();
    }

    private String getDateString(MenuType type) {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        String mealType;

        if (type == MenuType.CAFETERIA) {
            mealType = getMealTypeForCafeteria(hour);
            if (hour >= 19) {
                now.add(Calendar.DAY_OF_MONTH, 1);
            }
        } else {
            mealType = getMealTypeForDormitory(hour);
            if (hour >= 19 || hour < 4) { // 다음날 조식 로직
                now.add(Calendar.DAY_OF_MONTH, 1);
            }
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy년 MM월 dd일");
        return String.format("%s (%s)", sdf.format(now.getTime()), mealType);
    }

    private String getMealTypeForCafeteria(int hour) {
        if (hour >= 14 && hour < 19) return "석식";
        return "중식";
    }

    private String getMealTypeForDormitory(int hour) {
        if (hour >= 19 || hour < 4) return "다음 날 조식";
        if (hour < 9) return "조식";
        if (hour < 14) return "중식";
        return "석식";
    }

    // HTTP 연결 생성 (공통 로직)
    private HttpURLConnection createConnection(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setConnectTimeout(CONNECTION_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        return conn;
    }

    // HTTP 응답 읽기 (공통 로직)
    private String readResponse(HttpURLConnection conn, String encoding) throws Exception {
        StringBuilder content = new StringBuilder();
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), encoding))) {
            String line;
            while ((line = in.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    private String fetchAndParseFacultyMenu() {
        try {
            HttpURLConnection conn = createConnection(FACULTY_MENU_URL);
            conn.setRequestProperty("Accept", "application/json");
            String json = readResponse(conn, StandardCharsets.UTF_8.name());
            conn.disconnect();
            return parseFacultyJson(json);
        } catch (Exception e) {
            return "메뉴 정보를 가져오는데 실패했습니다.";
        }
    }

    private String fetchAndParseDormitoryMenu() {
        try {
            HttpURLConnection conn = createConnection(DORMITORY_MENU_URL);
            String html = readResponse(conn, StandardCharsets.UTF_8.name());
            conn.disconnect();
            return parseDormitoryMenu(html);
        } catch (Exception e) {
            return "메뉴 정보를 가져오는데 실패했습니다.";
        }
    }

    private String parseFacultyJson(String json) {
        if (json == null || json.isEmpty()) {
            return "메뉴 정보를 가져오는데 실패했습니다.";
        }

        try {
            // [수정] 시간에 따라 중식(type B) 또는 석식(type C)을 찾도록 변경
            Calendar cal = Calendar.getInstance();
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            
            String targetType = "B"; // 기본값 중식
            if (hour >= 14 && hour < 19) {
                targetType = "C"; // 석식
            }

            int typeIndex = json.indexOf("\"type\":\"" + targetType + "\"");
            
            // 만약 해당 타입(예: 석식)이 없으면 중식이라도 보여주기 위해 예외처리 가능하나, 여기선 오류 메시지 반환
            if (typeIndex == -1) {
                return (targetType.equals("C") ? "석식" : "중식") + " 정보를 찾을 수 없습니다.";
            }

            int itemStart = json.lastIndexOf("{", typeIndex);
            int itemEnd = json.indexOf("}", typeIndex);
            String menuJson = json.substring(itemStart, itemEnd + 1);

            // 날짜 계산 로직 (기존 유지)
            if (hour >= 19) {
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
            
            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
            if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                return "주말에는 운영하지 않습니다.";
            }

            int menuIndex = dayOfWeek - 1; // 일=0, 월=1 ...
            String key = "\"menu" + menuIndex + "\":";
            int keyIndex = menuJson.indexOf(key);
            
            if (keyIndex == -1) return "오늘의 메뉴 정보가 없습니다.";

            int valueStart = menuJson.indexOf("\"", keyIndex + key.length()) + 1;
            int valueEnd = menuJson.indexOf("\"", valueStart);
            
            return decodeHtmlEntities(menuJson.substring(valueStart, valueEnd))
                    .replace("\\r\\n", "\n")
                    .replace("\\/", "/");

        } catch (Exception e) {
            return "메뉴 파싱 중 오류가 발생했습니다.";
        }
    }

    private String parseDormitoryMenu(String html) {
        if (html == null || html.isEmpty()) {
            return "메뉴 정보를 가져오는데 실패했습니다.";
        }

        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        
        // 다음 날 조식 로직
        if (hour >= 19 || hour < 4) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        
        String todayStr = new SimpleDateFormat("MM월 dd일").format(cal.getTime());

        try {
            int dateIndex = html.indexOf(todayStr);
            if (dateIndex == -1) return "오늘의 식단 정보가 없습니다.";

            // [수정] 식사 시간대에 따라 건너뛸 <td> 개수 설정 (테이블 구조: 날짜 | 조식 | 중식 | 석식)
            int skipTdCount = 0; // 기본 조식 (바로 다음 td)
            
            if (hour >= 19 || hour < 4) {
                skipTdCount = 0; // 다음날 조식
            } else if (hour < 9) {
                skipTdCount = 0; // 조식
            } else if (hour < 14) {
                skipTdCount = 1; // 중식
            } else {
                skipTdCount = 2; // 석식
            }

            // 날짜 위치부터 시작해서 원하는 칸만큼 <td> 태그 찾기
            int currentPos = dateIndex;
            for (int i = 0; i <= skipTdCount; i++) {
                currentPos = html.indexOf("<td", currentPos);
                if (currentPos == -1) return "식단 정보를 찾을 수 없습니다.";
                // 루프 마지막이 아니면 다음 검색을 위해 현재 태그 뒤로 인덱스 이동
                if (i < skipTdCount) {
                    currentPos++; 
                }
            }
            
            // 현재 currentPos는 목표 <td>의 시작점
            int closeTd = html.indexOf("</td>", currentPos);
            String menuContent = html.substring(currentPos, closeTd)
                    .replaceAll("<td[^>]*>", "") // td 태그 잔여물 제거
                    .replaceAll("<br\\s*/?>", "\n")
                    .replaceAll("<.*?>", "") // 나머지 태그 제거
                    .trim();

            return decodeHtmlEntities(menuContent);

        } catch (Exception e) {
            e.printStackTrace();
            return "메뉴 파싱 중 오류가 발생했습니다.";
        }
    }

    // HTML 엔티티 디코딩 (공통 메서드)
    private String decodeHtmlEntities(String text) {
        return text.replace("&amp;", "&")
                   .replace("&nbsp;", " ")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&quot;", "\"");
    }

    private JPanel createRecommendPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 상단 패널
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        topPanel.add(new JLabel("몇 개의 메뉴를 추천할까요?"));
        
        countComboBox = new JComboBox<>(new Integer[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        topPanel.add(countComboBox);
        panel.add(topPanel, BorderLayout.NORTH);

        // 결과 텍스트 영역
        resultTextArea = new JTextArea();
        resultTextArea.setEditable(false);
        resultTextArea.setFont(new Font("Monospaced", Font.BOLD, 16));
        resultTextArea.setLineWrap(true);
        resultTextArea.setWrapStyleWord(true);
        resultTextArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("추천 결과"),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        panel.add(new JScrollPane(resultTextArea), BorderLayout.CENTER);

        // 하단 버튼
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        recommendButton = new JButton("추천받기!");
        recommendButton.addActionListener(this::onRecommendButtonClick);
        bottomPanel.add(recommendButton);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void onRecommendButtonClick(ActionEvent e) {
        int count = (Integer) countComboBox.getSelectedItem();
        List<String> menuList = new ArrayList<>(Arrays.asList(RANDOM_MENU_LIST));
        Collections.shuffle(menuList, ThreadLocalRandom.current());

        StringBuilder result = new StringBuilder("오늘의 추천 메뉴입니다!\n\n");
        for (int i = 0; i < count; i++) {
            result.append("  ").append(i + 1).append(". ")
                  .append(menuList.get(i)).append("\n");
        }

        resultTextArea.setText(result.toString());
        recommendButton.setText("다시 추천받기");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LunchRecommender().setVisible(true));
    }
}