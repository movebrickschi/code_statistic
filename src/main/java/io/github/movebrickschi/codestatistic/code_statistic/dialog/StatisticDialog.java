package io.github.movebrickschi.codestatistic.code_statistic.dialog;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import io.github.movebrickschi.codestatistic.code_statistic.model.CommitStatistic;
import io.github.movebrickschi.codestatistic.code_statistic.service.GitStatisticService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;

/**
 * Git 代码提交统计对话框
 * 提供可视化界面展示 Git 提交统计数据，包括作者的提交次数、新增/删除行数等信息
 */
public class StatisticDialog extends DialogWrapper {
    /** 当前项目实例 */
    private final Project project;

    /** 统计数据展示表格 */
    private JTable table;

    /** 开始日期输入框 */
    private JTextField startDateField;

    /** 结束日期输入框 */
    private JTextField endDateField;

    /** 对话框主面板，用于居中显示提示弹框 */
    private JPanel mainPanel;

    /**
     * 构造函数
     * @param project 当前项目
     */
    public StatisticDialog(Project project) {
        super(project);
        this.project = project;
        setTitle("Git 代码提交统计");
        init();
    }

    /**
     * 创建对话框的中心面板
     * 包含日期选择区域、查询按钮和统计数据表格
     * @return 中心面板组件
     */
    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        // 创建主面板，使用边界布局
        mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setPreferredSize(new Dimension(700, 400));

        // 创建日期选择面板（顶部区域）
        JPanel datePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        // 开始日期选择
        datePanel.add(new JLabel("开始日期:"));
        startDateField = new JTextField(LocalDate.now().toString(), 10);
        datePanel.add(startDateField);
        
        // 结束日期选择
        datePanel.add(new JLabel("结束日期:"));
        endDateField = new JTextField(LocalDate.now().toString(), 10);
        datePanel.add(endDateField);

        // 查询按钮 - 按日期范围查询统计数据
        JButton queryButton = new JButton("查询");
        queryButton.addActionListener(e -> loadStatistics());
        datePanel.add(queryButton);

        // 今日统计按钮 - 快速查询今天的统计数据
        JButton todayButton = new JButton("今日统计");
        todayButton.addActionListener(e -> loadTodayStatistics());
        datePanel.add(todayButton);

        mainPanel.add(datePanel, BorderLayout.NORTH);

        // 创建统计数据表格（中心区域）
        String[] columnNames = {"排名", "作者", "提交次数", "新增行数", "删除行数", "总变更行数"};
        DefaultTableModel model = new DefaultTableModel(columnNames, 0);
        table = new JTable(model);

        // 设置表格数据居中显示
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }

        JScrollPane scrollPane = new JScrollPane(table);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // 不自动加载数据，等待用户点击查询按钮
        return mainPanel;
    }

    /**
     * 加载指定日期范围的统计数据
     * 从日期输入框读取开始和结束日期，在后台线程执行统计查询
     * 查询过程中显示进度条，完成后更新表格显示
     */
    private void loadStatistics() {
        try {
            // 解析用户输入的日期
            LocalDate start = LocalDate.parse(startDateField.getText());
            LocalDate end = LocalDate.parse(endDateField.getText());
            
            // 在后台任务中执行统计查询，避免阻塞UI线程
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "加载Git统计数据", true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    // 调用 Git 统计服务获取数据
                    GitStatisticService service = new GitStatisticService(project);
                    List<CommitStatistic> statistics = service.getStatistics(start, end, indicator);

                    // 在 UI 线程中更新表格
                    SwingUtilities.invokeLater(() -> updateTable(statistics));
                }
            });
        } catch (Exception e) {
            // 日期格式错误时显示错误消息（相对于主面板居中显示）
            JOptionPane.showMessageDialog(mainPanel, "日期格式错误: " + e.getMessage());
        }
    }

    /**
     * 加载今日统计数据
     * 快速查询今天的提交统计，在后台线程执行
     * 查询过程中显示进度条，完成后更新表格显示
     */
    private void loadTodayStatistics() {
        // 在后台任务中执行今日统计查询
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "加载今日统计数据", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                // 调用 Git 统计服务获取今日数据
                GitStatisticService service = new GitStatisticService(project);
                List<CommitStatistic> statistics = service.getTodayStatistics(indicator);

                // 在 UI 线程中更新表格
                SwingUtilities.invokeLater(() -> updateTable(statistics));
            }
        });
    }

    /**
     * 更新表格显示统计数据
     * 将统计结果填充到表格中，包括排名、作者、提交次数、新增/删除行数等信息
     * @param statistics 统计数据列表，已按总变更行数降序排列
     */
    private void updateTable(List<CommitStatistic> statistics) {
        // 获取表格模型
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        // 清空现有数据
        model.setRowCount(0);
        
        // 检查是否有数据
        if (statistics == null || statistics.isEmpty()) {
            // 没有查询到数据时显示提示弹框（相对于主面板居中显示）
            JOptionPane.showMessageDialog(
                mainPanel,
                "未查询到提交数据，请检查：\n1. 选择的日期范围内是否有提交记录\n2. 当前目录是否为 Git 仓库\n3. Git 是否已正确安装",
                "提示",
                JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        // 遍历统计数据，逐行添加到表格
        int rank = 1;
        for (CommitStatistic stat : statistics) {
            model.addRow(new Object[]{
                rank++,                      // 排名
                stat.getAuthor(),            // 作者名称
                stat.getCommitCount(),       // 提交次数
                stat.getAdditions(),         // 新增行数
                stat.getDeletions(),         // 删除行数
                stat.getTotalChanges()       // 总变更行数（新增+删除）
            });
        }
    }
}
