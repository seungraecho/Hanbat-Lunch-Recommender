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
 * 점심 추천 도우미 프로그램 (제외 항목 기능 추가 버전)
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

    // 제외된 메뉴를 저장할 Set (중복 방지 및 빠른 검색)
    private final Set<String> excludedItems = new HashSet<>();

    // UI 컴포넌트
    private JComboBox<Integer> countComboBox;
    private JTextArea resultTextArea;
    private JButton recommendButton;
    private JButton excludeButton; // 제외 항목 버튼 추가

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
            Calendar cal = Calendar.getInstance();
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            
            String targetType = "B"; 
            if (hour >= 14 && hour < 19) {
                targetType = "C"; 
            }

            int typeIndex = json.indexOf("\"type\":\"" + targetType + "\"");
            
            if (typeIndex == -1) {
                return (targetType.equals("C") ? "석식" : "중식") + " 정보를 찾을 수 없습니다.";
            }

            int itemStart = json.lastIndexOf("{", typeIndex);
            int itemEnd = json.indexOf("}", typeIndex);
            String menuJson = json.substring(itemStart, itemEnd + 1);

            if (hour >= 19) {
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
            
            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
            if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                return "주말에는 운영하지 않습니다.";
            }

            int menuIndex = dayOfWeek - 1;
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
        
        if (hour >= 19 || hour < 4) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        
        String todayStr = new SimpleDateFormat("MM월 dd일").format(cal.getTime());

        try {
            int dateIndex = html.indexOf(todayStr);
            if (dateIndex == -1) return "오늘의 식단 정보가 없습니다.";

            int skipTdCount = 0; 
            
            if (hour >= 19 || hour < 4) {
                skipTdCount = 0;
            } else if (hour < 9) {
                skipTdCount = 0;
            } else if (hour < 14) {
                skipTdCount = 1;
            } else {
                skipTdCount = 2;
            }

            int currentPos = dateIndex;
            for (int i = 0; i <= skipTdCount; i++) {
                currentPos = html.indexOf("<td", currentPos);
                if (currentPos == -1) return "식단 정보를 찾을 수 없습니다.";
                if (i < skipTdCount) {
                    currentPos++; 
                }
            }
            
            int closeTd = html.indexOf("</td>", currentPos);
            String menuContent = html.substring(currentPos, closeTd)
                    .replaceAll("<td[^>]*>", "") 
                    .replaceAll("<br\\s*/?>", "\n")
                    .replaceAll("<.*?>", "") 
                    .trim();

            return decodeHtmlEntities(menuContent);

        } catch (Exception e) {
            return "메뉴 파싱 중 오류가 발생했습니다.";
        }
    }

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

        // 하단 버튼 패널
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        // [추가] 제외 항목 버튼
        excludeButton = new JButton("제외 항목");
        excludeButton.addActionListener(this::openExclusionDialog);
        bottomPanel.add(excludeButton);

        recommendButton = new JButton("추천받기!");
        recommendButton.addActionListener(this::onRecommendButtonClick);
        bottomPanel.add(recommendButton);

        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    // [추가] 제외 항목 설정 다이얼로그
    private void openExclusionDialog(ActionEvent e) {
        JDialog dialog = new JDialog(this, "제외 항목 설정", true); // 모달 창
        dialog.setSize(400, 400);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        // 체크박스 패널 (스크롤 가능)
        JPanel checkboxPanel = new JPanel(new GridLayout(0, 2, 10, 10)); // 2열 그리드
        checkboxPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        List<JCheckBox> checkBoxes = new ArrayList<>();
        
        // 전체 메뉴에 대해 체크박스 생성
        for (String menu : RANDOM_MENU_LIST) {
            JCheckBox checkBox = new JCheckBox(menu);
            // 이미 제외 목록에 있다면 체크 상태로 표시
            if (excludedItems.contains(menu)) {
                checkBox.setSelected(true);
            }
            checkBoxes.add(checkBox);
            checkboxPanel.add(checkBox);
        }

        dialog.add(new JScrollPane(checkboxPanel), BorderLayout.CENTER);

        // 버튼 패널 (확인, 적용, 취소)
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        JButton confirmBtn = new JButton("확인");
        JButton applyBtn = new JButton("적용");
        JButton cancelBtn = new JButton("취소");

        // 확인 버튼: 저장하고 닫기
        confirmBtn.addActionListener(event -> {
            applyExclusions(checkBoxes);
            dialog.dispose();
        });

        // 적용 버튼: 저장하고 창 유지
        applyBtn.addActionListener(event -> {
            applyExclusions(checkBoxes);
            JOptionPane.showMessageDialog(dialog, "제외 항목이 적용되었습니다.");
        });

        // 취소 버튼: 저장하지 않고 닫기
        cancelBtn.addActionListener(event -> dialog.dispose());

        btnPanel.add(confirmBtn);
        btnPanel.add(applyBtn);
        btnPanel.add(cancelBtn);

        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    // 체크박스 상태를 excludedItems Set에 반영하는 헬퍼 메서드
    private void applyExclusions(List<JCheckBox> checkBoxes) {
        excludedItems.clear();
        for (JCheckBox box : checkBoxes) {
            if (box.isSelected()) {
                excludedItems.add(box.getText());
            }
        }
    }

    private void onRecommendButtonClick(ActionEvent e) {
        int count = (Integer) countComboBox.getSelectedItem();
        
        // [수정] 제외된 항목을 필터링하여 후보 리스트 생성
        List<String> availableMenus = new ArrayList<>();
        for (String menu : RANDOM_MENU_LIST) {
            if (!excludedItems.contains(menu)) {
                availableMenus.add(menu);
            }
        }

        // 모든 메뉴가 제외되었을 경우 처리
        if (availableMenus.isEmpty()) {
            resultTextArea.setText("모든 메뉴가 제외되었습니다!\n제외 항목을 해제해주세요.");
            return;
        }

        // 요청 수보다 가능한 메뉴가 적을 경우 처리
        if (availableMenus.size() < count) {
            resultTextArea.setText("추천 가능한 메뉴가 부족합니다.\n(제외 항목이 너무 많습니다)");
            return;
        }

        // 셔플 후 추천
        Collections.shuffle(availableMenus, ThreadLocalRandom.current());

        StringBuilder result = new StringBuilder("오늘의 추천 메뉴입니다!\n\n");
        // 제외된 항목 정보 표시 (선택사항)
        if (!excludedItems.isEmpty()) {
            result.append("[제외된 항목 수: ").append(excludedItems.size()).append("개]\n\n");
        }

        for (int i = 0; i < count; i++) {
            result.append("  ").append(i + 1).append(". ")
                  .append(availableMenus.get(i)).append("\n");
        }

        resultTextArea.setText(result.toString());
        recommendButton.setText("다시 추천받기");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LunchRecommender().setVisible(true));
    }
}