import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.*;
import javax.swing.*;
import org.example.models.ConnectDB;

// ... [imports tetap sama]
public class MemoryGame {
    enum Level { EASY, MEDIUM, HARD }

    class Card {
        String cardName;
        ImageIcon cardImageIcon;

        Card(String cardName, ImageIcon cardImageIcon) {
            this.cardName = cardName;
            this.cardImageIcon = cardImageIcon;
        }
    }

    Level currentLevel;
    String[] cardList;
    int rows, columns;
    ArrayList<Card> cardSet;
    ImageIcon cardBackImageIcon;

    JFrame frame = new JFrame("Memory Card Game");
    JLabel timerLabel = new JLabel("00:00");
    JLabel levelLabel = new JLabel("Level: EASY");
    JLabel bestTimeLabel = new JLabel("Best Time: 00:00");
    JPanel topPanel = new JPanel(new BorderLayout());
    JPanel centerInfoPanel = new JPanel(new BorderLayout());
    JPanel boardPanel = new JPanel();
    JPanel restartGamePanel = new JPanel();
    JButton restartButton = new JButton();

    int elapsedSeconds = 0;
    boolean firstCardFlipped = false;
    javax.swing.Timer gameTimer;
    javax.swing.Timer initialRevealTimer;
    javax.swing.Timer mismatchTimer;

    ArrayList<JButton> board;
    boolean gameReady = false;
    JButton card1Selected;
    JButton card2Selected;

    Map<Level, Integer> bestTimes = new HashMap<>();
    Connection dbConnection;

    ConfettiPanel normalConfetti = new ConfettiPanel(100);
    ConfettiPanel epicConfetti = new ConfettiPanel(300);

    public MemoryGame() {
        ConnectDB db = new ConnectDB();
        dbConnection = db.getConnection();

        try {
            Statement s = dbConnection.createStatement();
            s.executeUpdate("CREATE TABLE IF NOT EXISTS scores (level TEXT PRIMARY KEY, best_time INTEGER)");
            s.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.setResizable(false);

        levelLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        levelLabel.setHorizontalAlignment(SwingConstants.LEFT);
        timerLabel.setFont(new Font("Segoe UI", Font.BOLD, 36));
        timerLabel.setHorizontalAlignment(SwingConstants.CENTER);
        bestTimeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        bestTimeLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.add(levelLabel, BorderLayout.WEST);
        centerInfoPanel.add(timerLabel, BorderLayout.NORTH);
        centerInfoPanel.add(bestTimeLabel, BorderLayout.SOUTH);
        headerRow.add(centerInfoPanel, BorderLayout.CENTER);
        headerRow.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        topPanel.add(headerRow, BorderLayout.CENTER);
        frame.add(topPanel, BorderLayout.NORTH);

        restartButton.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        restartButton.setText("Restart Game");
        restartButton.setFocusable(false);
        restartButton.setEnabled(false);
        restartButton.addActionListener(e -> restartGame());

        restartGamePanel.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
        restartGamePanel.setBorder(BorderFactory.createEmptyBorder(2, 0, 5, 0));
        restartGamePanel.add(restartButton);
        frame.add(restartGamePanel, BorderLayout.SOUTH);

        // Timer: 1 detik untuk kartu tidak cocok
        mismatchTimer = new javax.swing.Timer(1000, e -> hideCards());
        mismatchTimer.setRepeats(false);

        // Timer game utama
        gameTimer = new javax.swing.Timer(1000, e -> {
            elapsedSeconds++;
            timerLabel.setText(formatTime(elapsedSeconds));
        });

        // Timer: 5 detik awal untuk lihat semua kartu
        initialRevealTimer = new javax.swing.Timer(5000, e -> hideCards());
        initialRevealTimer.setRepeats(false);

        normalConfetti.setBounds(0, 0, 1000, 800);
        epicConfetti.setBounds(0, 0, 1000, 800);
        normalConfetti.setVisible(false);
        epicConfetti.setVisible(false);
        frame.setGlassPane(normalConfetti);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (dbConnection != null && !dbConnection.isClosed()) {
                    dbConnection.close();
                    System.out.println("Database connection closed.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }));

        setLevel(Level.EASY);
        startLevel();

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    void setLevel(Level level) {
        this.currentLevel = level;
        bestTimeLabel.setText("Best Time: --:--");

        try {
            PreparedStatement ps = dbConnection.prepareStatement("SELECT best_time FROM scores WHERE level = ?");
            ps.setString(1, level.name());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int best = rs.getInt("best_time");
                bestTimes.put(level, best);
                bestTimeLabel.setText("Best Time: " + formatTime(best));
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        levelLabel.setText("Level: " + level.name());

        switch (level) {
            case EASY:
                columns = 4;
                cardList = new String[]{"TTS", "BBP", "BC", "TT", "BlC", "CA", "BA", "CB"};
                break;
            case MEDIUM:
                columns = 6;
                cardList = new String[]{"python", "css", "c", "cplus", "php", "sql", "xml", "perl", "js", "go", "java", "html"};
                break;
            case HARD:
                columns = 8;
                cardList = new String[]{"sushi", "gacoan", "cimol", "naspad", "ramen", "mavera", "kwetiau", "seblak", "boci", "dimsum", "nasgor", "bebek", "wingstop", "toppoki", "mieayam", "satepadang"};
                break;
        }

        int totalCards = cardList.length * 2;
        rows = totalCards / columns;
    }

    void startLevel() {
        card1Selected = null;
        card2Selected = null;
        firstCardFlipped = false;
        elapsedSeconds = 0;
        timerLabel.setText("00:00");
        gameTimer.stop();

        setupCards();
        shuffleCards();

        if (boardPanel != null) frame.remove(boardPanel);

        board = new ArrayList<>();
        boardPanel = new JPanel(new GridLayout(rows, columns, 10, 10));
        boardPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        for (int i = 0; i < cardSet.size(); i++) {
            JButton tile = new JButton();
            tile.setIcon(cardSet.get(i).cardImageIcon);
            tile.setFocusable(false);
            tile.setMargin(new Insets(0, 0, 0, 0));
            tile.setBorder(null);
            tile.setContentAreaFilled(false);
            tile.setOpaque(true);
            tile.setHorizontalAlignment(SwingConstants.CENTER);
            tile.setVerticalAlignment(SwingConstants.CENTER);
            tile.addActionListener(new CardClickListener());
            board.add(tile);
            boardPanel.add(tile);
        }

        int panelHeight = rows * 138 + (rows - 1) * 10 + 20;
        int panelWidth = columns * 100 + (columns - 1) * 10 + 20;
        boardPanel.setPreferredSize(new Dimension(panelWidth, panelHeight));

        frame.add(boardPanel, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.revalidate();
        frame.repaint();

        gameReady = false;
        restartButton.setEnabled(true);

        // Mulai timer 5 detik untuk awal game
        initialRevealTimer.start();
    }

    void setupCards() {
        cardSet = new ArrayList<>();
        for (String cardName : cardList) {
            Image cardImg = new ImageIcon(getClass().getResource("./img/" + cardName + ".jpg")).getImage();
            ImageIcon cardImageIcon = new ImageIcon(cardImg.getScaledInstance(90, 128, Image.SCALE_SMOOTH));
            cardSet.add(new Card(cardName, cardImageIcon));
        }
        cardSet.addAll(new ArrayList<>(cardSet));
        Image cardBackImg = new ImageIcon(getClass().getResource("./img/back.jpg")).getImage();
        cardBackImageIcon = new ImageIcon(cardBackImg.getScaledInstance(90, 128, Image.SCALE_SMOOTH));
    }

    void shuffleCards() {
        Collections.shuffle(cardSet);
    }

    void hideCards() {
        if (gameReady && card1Selected != null && card2Selected != null) {
            card1Selected.setIcon(cardBackImageIcon);
            card2Selected.setIcon(cardBackImageIcon);
            card1Selected = null;
            card2Selected = null;
        } else {
            for (JButton tile : board) tile.setIcon(cardBackImageIcon);
            gameReady = true;
            restartButton.setEnabled(true);
        }
    }

    boolean isGameCompleted() {
        for (JButton tile : board) {
            if (tile.getIcon() == cardBackImageIcon) return false;
        }
        return true;
    }

    String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    void restartGame() {
        startLevel();
    }

    void showConfetti(ConfettiPanel panel) {
        frame.setGlassPane(panel);
        panel.setVisible(true);
        panel.start();
    }

    void stopConfetti(ConfettiPanel panel) {
        panel.stop();
    }

    class CardClickListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            if (!gameReady) return;
            JButton tile = (JButton) e.getSource();
            if (tile.getIcon() == cardBackImageIcon) {
                if (!firstCardFlipped) {
                    firstCardFlipped = true;
                    gameTimer.start();
                }
                if (card1Selected == null) {
                    card1Selected = tile;
                    int index = board.indexOf(card1Selected);
                    card1Selected.setIcon(cardSet.get(index).cardImageIcon);
                } else if (card2Selected == null && tile != card1Selected) {
                    card2Selected = tile;
                    int index = board.indexOf(card2Selected);
                    card2Selected.setIcon(cardSet.get(index).cardImageIcon);

                    int index1 = board.indexOf(card1Selected);
                    int index2 = board.indexOf(card2Selected);
                    String name1 = cardSet.get(index1).cardName;
                    String name2 = cardSet.get(index2).cardName;

                    if (!name1.equals(name2)) {
                        mismatchTimer.start(); // 1 detik tampil lalu tutup
                    } else {
                        card1Selected = null;
                        card2Selected = null;
                        if (isGameCompleted()) {
                            gameTimer.stop();
                            JOptionPane.showMessageDialog(frame, "Level " + currentLevel.name() + " selesai dalam " + formatTime(elapsedSeconds));

                            if (!bestTimes.containsKey(currentLevel) || elapsedSeconds < bestTimes.get(currentLevel)) {
                                bestTimes.put(currentLevel, elapsedSeconds);
                                try {
                                    PreparedStatement upsert = dbConnection.prepareStatement(
                                            "INSERT INTO scores (level, best_time) VALUES (?, ?) " +
                                                    "ON CONFLICT(level) DO UPDATE SET best_time = excluded.best_time"
                                    );
                                    upsert.setString(1, currentLevel.name());
                                    upsert.setInt(2, elapsedSeconds);
                                    upsert.executeUpdate();
                                    upsert.close();
                                } catch (SQLException ex) {
                                    ex.printStackTrace();
                                }

                                showConfetti(normalConfetti);
                                JOptionPane.showMessageDialog(frame, "Best time baru untuk level " + currentLevel.name() + ": " + formatTime(elapsedSeconds));
                                stopConfetti(normalConfetti);
                            }

                            if (currentLevel == Level.EASY) {
                                setLevel(Level.MEDIUM);
                                startLevel();
                            } else if (currentLevel == Level.MEDIUM) {
                                setLevel(Level.HARD);
                                startLevel();
                            } else {
                                showConfetti(epicConfetti);
                                JOptionPane.showMessageDialog(frame, "Selamat! Kamu telah menyelesaikan semua level!");
                                stopConfetti(epicConfetti);
                                System.exit(0);
                            }
                        }
                    }
                }
            }
        }
    }

    // ConfettiPanel tetap sama



    class ConfettiPanel extends JPanel {
        private final Random random = new Random();
        private java.util.List<Point> confettiPoints;
        private javax.swing.Timer confettiTimer;
        private int density;

        public ConfettiPanel(int density) {
            setOpaque(false);
            this.density = density;
            generateConfetti();
        }

        private void generateConfetti() {
            confettiPoints = new ArrayList<>();
            for (int i = 0; i < density; i++) {
                confettiPoints.add(new Point(random.nextInt(800), random.nextInt(600)));
            }
            confettiTimer = new javax.swing.Timer(30, e -> {
                for (Point p : confettiPoints) {
                    p.y += random.nextInt(5) + 2;
                    if (p.y > getHeight()) p.y = 0;
                }
                repaint();
            });
        }

        public void start() {
            confettiTimer.start();
        }

        public void stop() {
            confettiTimer.stop();
            setVisible(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            for (Point p : confettiPoints) {
                g.setColor(new Color(random.nextFloat(), random.nextFloat(), random.nextFloat()));
                g.fillOval(p.x, p.y, 5, 5);
            }
        }
    }
}
