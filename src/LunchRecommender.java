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
import java.util.stream.Collectors;

/**
 * 점심 추천 도우미 프로그램 (최종 최적화 버전)
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
    
    // 카테고리별 메뉴 관리를 위한 LinkedHashMap (순서 유지)
    private static final Map<String, String[]> MENU_BY_CATEGORY = new LinkedHashMap<>();
    
    static {
        MENU_BY_CATEGORY.put("한식 - 밥류", new String[]{
            "제육덮밥", "비빔밥", "김치볶음밥", "오므라이스", "돈까스",
            "치킨까스", "생선까스", "함박스테이크", "불고기덮밥", "카레라이스",
            "하이라이스", "낙지덮밥", "오징어덮밥", "참치마요덮밥", "스팸마요덮밥",
            "닭갈비덮밥", "차슈덮밥", "규동", "가츠동", "텐동"
        });
        MENU_BY_CATEGORY.put("한식 - 국/찌개/탕", new String[]{
            "김치찌개", "된장찌개", "부대찌개", "순두부찌개", "청국장",
            "순대국밥", "설렁탕", "갈비탕", "육개장", "삼계탕",
            "감자탕", "뼈해장국", "콩나물국밥", "돼지국밥", "소머리국밥"
        });
        MENU_BY_CATEGORY.put("한식 - 면류", new String[]{
            "냉면", "비빔냉면", "잔치국수", "칼국수", "수제비",
            "쫄면", "콩국수", "막국수", "밀면"
        });
        MENU_BY_CATEGORY.put("한식 - 분식", new String[]{
            "떡볶이", "라볶이", "김밥", "튀김", "순대"
        });
        MENU_BY_CATEGORY.put("중식", new String[]{
            "짜장면", "짬뽕", "탕수육", "볶음밥", "마파두부",
            "깐풍기", "유린기", "고추잡채", "마라탕", "마라샹궈",
            "훠궈", "양꼬치"
        });
        MENU_BY_CATEGORY.put("일식", new String[]{
            "초밥", "회덮밥", "우동", "라멘", "돈코츠라멘",
            "미소라멘", "규카츠", "타코야키", "오코노미야키", "소바",
            "카츠카레"
        });
        MENU_BY_CATEGORY.put("양식", new String[]{
            "파스타", "피자", "햄버거", "스테이크", "리조또",
            "그라탱", "샌드위치", "브런치", "샐러드"
        });
        MENU_BY_CATEGORY.put("동남아/기타", new String[]{
            "쌀국수", "팟타이", "똠양꿍", "분짜", "반미",
            "카오팟", "나시고랭", "커리", "난", "탄두리치킨",
            "케밥", "타코", "부리또"
        });
        MENU_BY_CATEGORY.put("패스트푸드", new String[]{
            "치킨", "서브웨이", "맥도날드", "버거킹", "롯데리아",
            "KFC", "피자헛", "도미노피자"
        });
    }

    // 전체 메뉴 리스트 (캐싱)
    private static final List<String> ALL_MENUS;
    
    static {
        ALL_MENUS = MENU_BY_CATEGORY.values().stream()
                .flatMap(Arrays::stream)
                .collect(Collectors.toList());
    }

    // 제외된 메뉴를 저장할 Set
    private final Set<String> excludedItems = new HashSet<>();

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

    private enum MenuType {
        CAFETERIA, DORMITORY
    }

    private JPanel createMenuPanel(MenuType type) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel dateLabel = new JLabel(getDateString(type));
        dateLabel.setHorizontalAlignment(SwingConstants.CENTER);
        dateLabel.setFont(new Font("Monospaced", Font.BOLD, 16));
        panel.add(dateLabel, BorderLayout.NORTH);

        JTextArea menuArea = createMenuTextArea();
        panel.add(new JScrollPane(menuArea), BorderLayout.CENTER);

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

    // 날짜/시간 계산을 위한 헬퍼 클래스
    private static class MealInfo {
        final Calendar date;
        final String mealType;
        final int mealIndex; // 0: 조식, 1: 중식, 2: 석식
        
        MealInfo(Calendar date, String mealType, int mealIndex) {
            this.date = date;
            this.mealType = mealType;
            this.mealIndex = mealIndex;
        }
    }

    private MealInfo getMealInfo(MenuType type) {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        Calendar targetDate = (Calendar) now.clone();
        String mealType;
        int mealIndex;

        if (type == MenuType.CAFETERIA) {
            // 학식: 중식(~14시), 석식(14~19시), 19시 이후는 다음날 중식
            if (hour >= 19) {
                targetDate.add(Calendar.DAY_OF_MONTH, 1);
                mealType = "중식";
                mealIndex = 1;
            } else if (hour >= 14) {
                mealType = "석식";
                mealIndex = 2;
            } else {
                mealType = "중식";
                mealIndex = 1;
            }
        } else {
            // 기숙사: 조식(4~9시), 중식(9~14시), 석식(14~19시), 그 외 다음날 조식
            if (hour >= 19) {
                targetDate.add(Calendar.DAY_OF_MONTH, 1);
                mealType = "다음 날 조식";
                mealIndex = 0;
            } else if (hour < 4) {
                // 새벽 0~4시: 오늘 날짜의 조식 (날짜 변경 없음)
                mealType = "조식";
                mealIndex = 0;
            } else if (hour < 9) {
                mealType = "조식";
                mealIndex = 0;
            } else if (hour < 14) {
                mealType = "중식";
                mealIndex = 1;
            } else {
                mealType = "석식";
                mealIndex = 2;
            }
        }

        return new MealInfo(targetDate, mealType, mealIndex);
    }

    private String getDateString(MenuType type) {
        MealInfo info = getMealInfo(type);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy년 MM월 dd일");
        return String.format("%s (%s)", sdf.format(info.date.getTime()), info.mealType);
    }

    // HTTP 요청 수행 (리소스 정리 개선)
    private String fetchUrl(String urlString, Map<String, String> headers) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(CONNECTION_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            
            // 추가 헤더 설정
            if (headers != null) {
                headers.forEach(conn::setRequestProperty);
            }

            StringBuilder content = new StringBuilder();
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
            return content.toString();
            
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String fetchAndParseFacultyMenu() {
        String json = fetchUrl(FACULTY_MENU_URL, Map.of("Accept", "application/json"));
        return parseFacultyJson(json);
    }

    private String fetchAndParseDormitoryMenu() {
        String html = fetchUrl(DORMITORY_MENU_URL, null);
        return parseDormitoryMenu(html);
    }

    private String parseFacultyJson(String json) {
        if (json == null || json.isEmpty()) {
            return "메뉴 정보를 가져오는데 실패했습니다.";
        }

        try {
            MealInfo info = getMealInfo(MenuType.CAFETERIA);
            
            // B: 중식, C: 석식
            String targetType = (info.mealIndex == 2) ? "C" : "B";
            int typeIndex = json.indexOf("\"type\":\"" + targetType + "\"");
            
            if (typeIndex == -1) {
                return info.mealType + " 정보를 찾을 수 없습니다.";
            }

            int itemStart = json.lastIndexOf("{", typeIndex);
            int itemEnd = json.indexOf("}", typeIndex);
            String menuJson = json.substring(itemStart, itemEnd + 1);

            int dayOfWeek = info.date.get(Calendar.DAY_OF_WEEK);
            if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                return "주말에는 운영하지 않습니다.";
            }

            int menuIndex = dayOfWeek - 1; // Sun=1 -> 0, Mon=2 -> 1, ...
            String key = "\"menu" + menuIndex + "\":";
            int keyIndex = menuJson.indexOf(key);
            
            if (keyIndex == -1) return "오늘의 메뉴 정보가 없습니다.";

            int valueStart = menuJson.indexOf("\"", keyIndex + key.length()) + 1;
            int valueEnd = menuJson.indexOf("\"", valueStart);
            
            return decodeContent(menuJson.substring(valueStart, valueEnd));

        } catch (Exception e) {
            return "메뉴 파싱 중 오류가 발생했습니다.";
        }
    }

    private String parseDormitoryMenu(String html) {
        if (html == null || html.isEmpty()) {
            return "메뉴 정보를 가져오는데 실패했습니다.";
        }

        MealInfo info = getMealInfo(MenuType.DORMITORY);
        String todayStr = new SimpleDateFormat("MM월 dd일").format(info.date.getTime());

        try {
            int dateIndex = html.indexOf(todayStr);
            if (dateIndex == -1) return "오늘의 식단 정보가 없습니다.";

            // mealIndex에 따라 해당 td 찾기 (0: 조식, 1: 중식, 2: 석식)
            int currentPos = dateIndex;
            for (int i = 0; i <= info.mealIndex; i++) {
                currentPos = html.indexOf("<td", currentPos);
                if (currentPos == -1) return "식단 정보를 찾을 수 없습니다.";
                if (i < info.mealIndex) {
                    currentPos++;
                }
            }
            
            int closeTd = html.indexOf("</td>", currentPos);
            String menuContent = html.substring(currentPos, closeTd)
                    .replaceAll("<td[^>]*>", "")
                    .replaceAll("<br\\s*/?>", "\n")
                    .replaceAll("<[^>]+>", "")
                    .trim();

            return decodeContent(menuContent);

        } catch (Exception e) {
            return "메뉴 파싱 중 오류가 발생했습니다.";
        }
    }

    private String decodeContent(String text) {
        return text.replace("\\r\\n", "\n")
                   .replace("\\/", "/")
                   .replace("&amp;", "&")
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
        
        JButton excludeButton = new JButton("제외 항목");
        excludeButton.addActionListener(this::openExclusionDialog);
        bottomPanel.add(excludeButton);

        recommendButton = new JButton("추천받기!");
        recommendButton.addActionListener(this::onRecommendButtonClick);
        bottomPanel.add(recommendButton);

        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    // 카테고리별 제외 항목 다이얼로그 (개선된 UX)
    private void openExclusionDialog(ActionEvent e) {
        JDialog dialog = new JDialog(this, "제외 항목 설정", true);
        dialog.setSize(500, 500);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        // 메인 패널 (카테고리별 그룹)
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        Map<String, List<JCheckBox>> categoryCheckBoxes = new LinkedHashMap<>();

        for (Map.Entry<String, String[]> entry : MENU_BY_CATEGORY.entrySet()) {
            String category = entry.getKey();
            String[] menus = entry.getValue();

            // 카테고리 패널
            JPanel categoryPanel = new JPanel(new BorderLayout());
            categoryPanel.setBorder(BorderFactory.createTitledBorder(category));

            // 카테고리 전체 선택 체크박스
            JCheckBox categoryCheckBox = new JCheckBox("전체 제외");
            
            // 메뉴 체크박스 패널 (3열 그리드)
            JPanel menuPanel = new JPanel(new GridLayout(0, 3, 5, 5));
            List<JCheckBox> menuCheckBoxes = new ArrayList<>();

            for (String menu : menus) {
                JCheckBox checkBox = new JCheckBox(menu);
                checkBox.setSelected(excludedItems.contains(menu));
                menuCheckBoxes.add(checkBox);
                menuPanel.add(checkBox);
                
                // 개별 체크박스 변경 시 카테고리 체크박스 상태 업데이트
                checkBox.addActionListener(evt -> updateCategoryCheckBox(categoryCheckBox, menuCheckBoxes));
            }

            // 카테고리 체크박스 초기 상태 설정
            updateCategoryCheckBox(categoryCheckBox, menuCheckBoxes);
            
            // 카테고리 전체 선택/해제 기능
            categoryCheckBox.addActionListener(evt -> {
                boolean selected = categoryCheckBox.isSelected();
                menuCheckBoxes.forEach(cb -> cb.setSelected(selected));
            });

            categoryPanel.add(categoryCheckBox, BorderLayout.NORTH);
            categoryPanel.add(menuPanel, BorderLayout.CENTER);
            mainPanel.add(categoryPanel);
            mainPanel.add(Box.createVerticalStrut(10));

            categoryCheckBoxes.put(category, menuCheckBoxes);
        }

        dialog.add(new JScrollPane(mainPanel), BorderLayout.CENTER);

        // 버튼 패널
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        JButton resetBtn = new JButton("초기화");
        JButton confirmBtn = new JButton("확인");
        JButton cancelBtn = new JButton("취소");

        resetBtn.addActionListener(evt -> 
            categoryCheckBoxes.values().stream()
                .flatMap(List::stream)
                .forEach(cb -> cb.setSelected(false))
        );

        confirmBtn.addActionListener(evt -> {
            excludedItems.clear();
            categoryCheckBoxes.values().stream()
                .flatMap(List::stream)
                .filter(JCheckBox::isSelected)
                .map(JCheckBox::getText)
                .forEach(excludedItems::add);
            dialog.dispose();
        });

        cancelBtn.addActionListener(evt -> dialog.dispose());

        btnPanel.add(resetBtn);
        btnPanel.add(confirmBtn);
        btnPanel.add(cancelBtn);

        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void updateCategoryCheckBox(JCheckBox categoryCheckBox, List<JCheckBox> menuCheckBoxes) {
        boolean allSelected = menuCheckBoxes.stream().allMatch(JCheckBox::isSelected);
        boolean noneSelected = menuCheckBoxes.stream().noneMatch(JCheckBox::isSelected);
        
        categoryCheckBox.setSelected(allSelected);
        // 일부만 선택된 경우 시각적 표시 (선택 해제 상태로)
        if (!allSelected && !noneSelected) {
            categoryCheckBox.setSelected(false);
        }
    }

    private void onRecommendButtonClick(ActionEvent e) {
        int count = (Integer) countComboBox.getSelectedItem();
        
        // 스트림을 사용한 필터링
        List<String> availableMenus = ALL_MENUS.stream()
                .filter(menu -> !excludedItems.contains(menu))
                .collect(Collectors.toCollection(ArrayList::new));

        if (availableMenus.isEmpty()) {
            resultTextArea.setText("모든 메뉴가 제외되었습니다!\n제외 항목을 해제해주세요.");
            return;
        }

        if (availableMenus.size() < count) {
            resultTextArea.setText(String.format(
                "추천 가능한 메뉴가 부족합니다.\n(사용 가능: %d개, 요청: %d개)",
                availableMenus.size(), count));
            return;
        }

        Collections.shuffle(availableMenus, ThreadLocalRandom.current());

        StringBuilder result = new StringBuilder("오늘의 추천 메뉴입니다!\n\n");
        
        if (!excludedItems.isEmpty()) {
            result.append(String.format("[제외: %d개 / 전체: %d개]\n\n", 
                excludedItems.size(), ALL_MENUS.size()));
        }

        for (int i = 0; i < count; i++) {
            result.append(String.format("  %d. %s\n", i + 1, availableMenus.get(i)));
        }

        resultTextArea.setText(result.toString());
        recommendButton.setText("다시 추천받기");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LunchRecommender().setVisible(true));
    }
}