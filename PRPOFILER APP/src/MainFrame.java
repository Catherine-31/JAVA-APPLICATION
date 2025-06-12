import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.sql.*;
import java.util.*;

public class MainFrame extends JFrame {
    private JComboBox<String> customerCombo, modelCombo, storageCombo, boardTypeCombo;
    private JButton reuseButton, uploadPdfButton, threeDotButton;
    private JTable table;
    private DefaultTableModel tableModel;
    private DefaultListModel<File> pdfListModel;
    private JList<File> pdfList;
    private Map<String, Integer> reuseCountMap = new HashMap<>();
    private File lastSavedFile = null;

    private static final String URL = "jdbc:mysql://localhost:3306/catherine";
    private static final String USER = "root";
    private static final String PASSWORD = "12345";

    public MainFrame() {
        setTitle("Reflow Profile Board Tracking");
        setSize(1300, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        JLabel title = new JLabel("REFLOW PROFILE BOARD TRACKING", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 35));
        title.setForeground(Color.WHITE);

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setBackground(new Color(0, 102, 204));
        titlePanel.setPreferredSize(new Dimension(0, 80));
        titlePanel.add(title, BorderLayout.CENTER);
        add(titlePanel, BorderLayout.NORTH);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(250, 0));
        pdfListModel = new DefaultListModel<>();
        pdfList = new JList<>(pdfListModel);
        pdfList.setCellRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                if (value instanceof File) value = ((File) value).getName();
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }
        });
        JScrollPane pdfScroll = new JScrollPane(pdfList);
        pdfScroll.setBorder(BorderFactory.createTitledBorder("Uploaded PDFs"));
        leftPanel.add(pdfScroll, BorderLayout.CENTER);

        uploadPdfButton = new JButton("Upload PDF");
        uploadPdfButton.addActionListener(e -> {
            FileDialog fd = new FileDialog(this, "Select PDF", FileDialog.LOAD);
            fd.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".pdf"));
            fd.setVisible(true);
            if (fd.getFile() != null) {
                File file = new File(fd.getDirectory(), fd.getFile());
                if (!pdfListModel.contains(file)) pdfListModel.addElement(file);
            }
        });
        leftPanel.add(uploadPdfButton, BorderLayout.SOUTH);

        // 👇 This block is fully updated
        pdfList.addMouseListener(new MouseAdapter() {
            private void showPopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int index = pdfList.locationToIndex(e.getPoint());
                if (index >= 0) {
                    pdfList.setSelectedIndex(index);
                    JPopupMenu popupMenu = new JPopupMenu();
                    JMenuItem deleteItem = new JMenuItem("Delete");

                    deleteItem.addActionListener(ae -> {
                        File selectedFile = pdfList.getSelectedValue();
                        if (selectedFile != null) {
                            pdfListModel.removeElement(selectedFile);
                        }
                    });

                    popupMenu.add(deleteItem);
                    popupMenu.show(pdfList, e.getX(), e.getY());
                }
            }

            public void mousePressed(MouseEvent e) {
                showPopup(e);
                handleDoubleClick(e);
            }

            public void mouseReleased(MouseEvent e) {
                showPopup(e);
            }

            private void handleDoubleClick(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    int index = pdfList.locationToIndex(e.getPoint());
                    if (index != -1) {
                        File selectedFile = pdfListModel.getElementAt(index);
                        try {
                            if (selectedFile.exists()) {
                                Desktop.getDesktop().open(selectedFile);
                            } else {
                                showError("File does not exist.");
                            }
                        } catch (IOException ex) {
                            showError("Cannot open file: " + ex.getMessage());
                        }
                    }
                }
            }
        });

        JPanel rightPanel = new JPanel(new BorderLayout(10, 10));
        rightPanel.setBackground(new Color(240, 248, 255));

        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        inputPanel.setOpaque(false);

        customerCombo = new JComboBox<>();
        modelCombo = new JComboBox<>();
        storageCombo = new JComboBox<>();
        boardTypeCombo = new JComboBox<>();
        Dimension comboSize = new Dimension(200, 35);
        customerCombo.setPreferredSize(comboSize);
        modelCombo.setPreferredSize(comboSize);
        storageCombo.setPreferredSize(comboSize);
        boardTypeCombo.setPreferredSize(comboSize);

        inputPanel.add(new JLabel("Customer"));
        inputPanel.add(customerCombo);
        inputPanel.add(new JLabel("Model"));
        inputPanel.add(modelCombo);
        inputPanel.add(new JLabel("Storage"));
        inputPanel.add(storageCombo);
        inputPanel.add(new JLabel("Board Type"));
        inputPanel.add(boardTypeCombo);

        reuseButton = new JButton("Reuse");
        reuseButton.addActionListener(e -> reuseAction());
        inputPanel.add(reuseButton);

        threeDotButton = new JButton("...");
        JPopupMenu menu = new JPopupMenu();
        JMenuItem newItem = new JMenuItem("New Profiler");
        JMenuItem openItem = new JMenuItem("Open");
        JMenuItem saveItem = new JMenuItem("Save");
        JMenuItem saveAsItem = new JMenuItem("Save As");
        menu.add(newItem);
        menu.add(openItem);
        menu.add(saveItem);
        menu.add(saveAsItem);
        threeDotButton.addActionListener(e -> menu.show(threeDotButton, 0, threeDotButton.getHeight()));
        inputPanel.add(threeDotButton);

        newItem.addActionListener(e -> {
            tableModel.setRowCount(0);
            reuseCountMap.clear();
            customerCombo.setSelectedIndex(-1);
            modelCombo.setSelectedIndex(-1);
            storageCombo.setSelectedIndex(-1);
            boardTypeCombo.setSelectedIndex(-1);
            pdfListModel.clear();
            lastSavedFile = null;
        });

        openItem.addActionListener(e -> {
            FileDialog fd = new FileDialog(this, "Open CSV", FileDialog.LOAD);
            fd.setFile("*.csv");
            fd.setVisible(true);
            if (fd.getFile() != null) {
                File file = new File(fd.getDirectory(), fd.getFile());
                try {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(file);
                    } else {
                        showError("Desktop not supported.");
                    }
                } catch (IOException ex) {
                    showError("Cannot open file: " + ex.getMessage());
                }
            }
        });

        saveItem.addActionListener(e -> {
            if (lastSavedFile != null) {
                saveToFile(lastSavedFile);
            } else {
                FileDialog fd = new FileDialog(this, "Save CSV", FileDialog.SAVE);
                fd.setFile("*.csv");
                fd.setVisible(true);
                if (fd.getFile() != null) {
                    lastSavedFile = new File(fd.getDirectory(), fd.getFile());
                    saveToFile(lastSavedFile);
                }
            }
        });

        saveAsItem.addActionListener(e -> {
            FileDialog fd = new FileDialog(this, "Save CSV As", FileDialog.SAVE);
            fd.setFile("*.csv");
            fd.setVisible(true);
            if (fd.getFile() != null) {
                File newFile = new File(fd.getDirectory(), fd.getFile());
                File oldFile = lastSavedFile;

                lastSavedFile = newFile;
                saveToFile(newFile);

                if (oldFile != null && !oldFile.equals(newFile) && oldFile.exists()) {
                    if (!oldFile.delete()) {
                        showError("Failed to delete old file: " + oldFile.getName());
                    }
                }
            }
        });

        String[] columns = {"Customer", "Model", "Storage", "Board Type", "Reuse Count"};
        tableModel = new DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(tableModel) {
            public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                int reuseCount = 0;
                try {
                    reuseCount = Integer.parseInt(String.valueOf(getValueAt(row, 4)));
                } catch (Exception ignored) {
                }
                c.setBackground(reuseCount >= 60 ? new Color(255, 192, 203) : Color.WHITE);
                return c;
            }
        };
        JScrollPane tableScroll = new JScrollPane(table);

        rightPanel.add(inputPanel, BorderLayout.NORTH);
        rightPanel.add(tableScroll, BorderLayout.CENTER);
        add(leftPanel, BorderLayout.WEST);
        add(rightPanel, BorderLayout.CENTER);

        loadCombosFromDatabase();
        setVisible(true);
    }

    private void reuseAction() {
        String customer = (String) customerCombo.getSelectedItem();
        String model = (String) modelCombo.getSelectedItem();
        String storage = (String) storageCombo.getSelectedItem();
        String boardType = (String) boardTypeCombo.getSelectedItem();

        if (customer == null || model == null || storage == null || boardType == null) {
            showError("Please select all dropdowns.");
            return;
        }

        String key = customer + "|" + model + "|" + storage + "|" + boardType;
        int count = reuseCountMap.getOrDefault(key, 0) + 1;

        if (count > 80) {
            showError("Reuse count limit reached (80). Cannot add more.");
            return;
        }

        reuseCountMap.put(key, count);
        boolean updated = false;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String rowKey = tableModel.getValueAt(i, 0) + "|" + tableModel.getValueAt(i, 1) + "|" +
                    tableModel.getValueAt(i, 2) + "|" + tableModel.getValueAt(i, 3);
            if (rowKey.equals(key)) {
                tableModel.setValueAt(count, i, 4);
                updated = true;
                break;
            }
        }
        if (!updated) {
            tableModel.addRow(new Object[]{customer, model, storage, boardType, count});
        }
        table.repaint();
    }

    private void loadCombosFromDatabase() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            showError("MySQL Driver not found: " + e.getMessage());
            return;
        }

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT DISTINCT customer, model, storage, board_type FROM reflow")) {

            Set<String> customers = new LinkedHashSet<>();
            Set<String> models = new LinkedHashSet<>();
            Set<String> storages = new LinkedHashSet<>();
            Set<String> boardTypes = new LinkedHashSet<>();

            while (rs.next()) {
                customers.add(rs.getString("customer"));
                models.add(rs.getString("model"));
                storages.add(rs.getString("storage"));
                boardTypes.add(rs.getString("board_type"));
            }

            customerCombo.removeAllItems();
            modelCombo.removeAllItems();
            storageCombo.removeAllItems();
            boardTypeCombo.removeAllItems();

            customers.forEach(customerCombo::addItem);
            models.forEach(modelCombo::addItem);
            storages.forEach(storageCombo::addItem);
            boardTypes.forEach(boardTypeCombo::addItem);
        } catch (SQLException e) {
            showError("Database Error: " + e.getMessage());
        }
    }

    private void saveToFile(File file) {
        try (PrintWriter pw = new PrintWriter(file)) {
            for (int i = 0; i < tableModel.getColumnCount(); i++) {
                pw.print(tableModel.getColumnName(i));
                pw.print(",");
            }
            pw.println("Last Updated");

            String lastUpdated = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            for (int r = 0; r < tableModel.getRowCount(); r++) {
                int reuseCount = Integer.parseInt(tableModel.getValueAt(r, 4).toString());
                for (int repeat = 0; repeat < reuseCount; repeat++) {
                    for (int c = 0; c < tableModel.getColumnCount(); c++) {
                        String cell = tableModel.getValueAt(r, c).toString();
                        if (c == 4) cell = String.valueOf(reuseCount);

                        if (cell.contains(",") || cell.contains("\"")) {
                            cell = "\"" + cell.replace("\"", "\"\"") + "\"";
                        }
                        pw.print(cell);
                        pw.print(",");
                    }
                    pw.println(lastUpdated);
                }
            }
            JOptionPane.showMessageDialog(this, "File saved successfully.");
        } catch (IOException ex) {
            showError("Save failed: " + ex.getMessage());
        }
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        FontUIResource f = new FontUIResource("Arial", Font.PLAIN, 18);
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof FontUIResource) {
                UIManager.put(key, f);
            }
        }
        SwingUtilities.invokeLater(MainFrame::new);
    }
}
