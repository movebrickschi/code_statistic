package io.github.movebrickschi.codestatistic.code_statistic.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import io.github.movebrickschi.codestatistic.code_statistic.dialog.StatisticDialog;
import org.jetbrains.annotations.NotNull;

/**
 * 显示统计信息的 Action
 * 当用户触发该 Action 时，显示 Git 代码提交统计对话框
 */
public class ShowStatisticAction extends AnAction {
    /**
     * 执行 Action 的逻辑
     * 创建并显示统计对话框
     *
     * @param e Action 事件，包含项目上下文等信息
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        // 获取当前项目
        Project project = e.getProject();
        if (project != null) {
            // 创建并显示统计对话框
            StatisticDialog dialog = new StatisticDialog(project);
            dialog.show();
        }
    }
}
