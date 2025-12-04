package io.github.movebrickschi.codestatistic.code_statistic.service;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import io.github.movebrickschi.codestatistic.code_statistic.model.CommitStatistic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Git 统计服务
 * 负责执行 Git 命令并解析提交历史，统计各作者的代码提交情况
 */
public class GitStatisticService {
    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(GitStatisticService.class);

    /** 当前项目实例 */
    private final Project project;

    /**
     * 构造函数
     * @param project 当前项目
     */
    public GitStatisticService(Project project) {
        this.project = project;
    }

    /**
     * 获取指定日期范围内的 Git 提交统计
     * 执行 git log 命令，解析提交历史并统计各作者的代码变更情况
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param indicator 进度指示器，用于显示进度和支持取消操作
     * @return 按总变更行数降序排列的统计结果列表
     */
    public List<CommitStatistic> getStatistics(LocalDate startDate, LocalDate endDate, ProgressIndicator indicator) {
        // 使用 HashMap 存储每个作者的统计数据
        Map<String, CommitStatistic> statisticsMap = new HashMap<>();

        try {
            // 获取项目根目录路径
            String basePath = project.getBasePath();
            if (basePath == null) {
                return Collections.emptyList();
            }

            // 更新进度条：初始化阶段
            if (indicator != null) {
                indicator.setText("正在初始化 Git 统计...");
                indicator.setIndeterminate(false);
                indicator.setFraction(0.05);
            }

            // 获取当前 Git 分支名
            String currentBranch = getCurrentBranch(basePath);
            if (currentBranch == null) {
                return Collections.emptyList();
            }

            // 更新进度条：拉取最新提交记录
            if (indicator != null) {
                indicator.setText("正在拉取最新提交记录 (分支: " + currentBranch + ")...");
                indicator.setFraction(0.1);
            }

            // 拉取最新的提交记录
            if (!fetchLatestCommits(basePath, currentBranch, indicator)) {
                log.warn("拉取最新提交记录失败，将使用本地现有数据进行统计");
            }

            // 格式化日期为 Git 命令所需的格式
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String since = startDate.format(formatter) + " 00:00:00";
            String until = endDate.format(formatter) + " 23:59:59";

            // 更新进度条：准备执行 Git 统计命令
            if (indicator != null) {
                indicator.setText("正在执行 Git 统计命令 (分支: " + currentBranch + ")...");
                indicator.setFraction(0.25);
            }

            // 构建 Git 命令：git log --numstat 显示每次提交的文件变更统计
            // --no-merges 参数用于排除合并提交，只统计真正的代码提交
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "git", "log",
                    currentBranch,  // 指定当前分支
                    "--since=" + since,
                    "--until=" + until,
                    "--no-merges",  // 排除合并提交
                    "--numstat",
                    "--pretty=format:COMMIT:%an"  // 自定义格式：COMMIT: + 作者名
            );
            processBuilder.directory(new java.io.File(basePath));
            Process process = processBuilder.start();

            // 更新进度条：开始读取提交记录
            if (indicator != null) {
                indicator.setText("正在读取提交记录...");
                indicator.setFraction(0.35);
            }

            // 读取 Git 命令输出
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String line;
            String currentAuthor = null;  // 当前正在处理的提交作者
            int lineCount = 0;  // 已处理的行数，用于更新进度

            // 逐行解析 Git 输出
            while ((line = reader.readLine()) != null) {
                // 检查用户是否取消操作
                if (indicator != null && indicator.isCanceled()) {
                    process.destroy();
                    reader.close();
                    return Collections.emptyList();
                }

                log.info("当前的line:{}", line);

                // 处理提交标记行：COMMIT:作者名
                if (line.startsWith("COMMIT:")) {
                    currentAuthor = line.substring(7);  // 提取作者名
                    statisticsMap.putIfAbsent(currentAuthor, new CommitStatistic(currentAuthor));
                    // 每次遇到新的提交时，增加提交次数（一次提交算一次）
                    statisticsMap.get(currentAuthor).incrementCommitCount();
                }
                // 处理文件变更统计行：增加行数 \t 删除行数 \t 文件名
                else if (currentAuthor != null && !line.isEmpty()) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2) {
                        try {
                            // 解析增加和删除的行数（"-" 表示二进制文件，按 0 处理）
                            int additions = parts[0].equals("-") ? 0 : Integer.parseInt(parts[0]);
                            int deletions = parts[1].equals("-") ? 0 : Integer.parseInt(parts[1]);
                            // 只累加代码变更行数，不增加提交次数
                            statisticsMap.get(currentAuthor).addChanges(additions, deletions);
                        } catch (NumberFormatException ignored) {
                            // 忽略解析错误的行
                        }
                    }
                }

                // 每处理 100 行更新一次进度条
                lineCount++;
                if (indicator != null && lineCount % 100 == 0) {
                    indicator.setText("正在处理提交记录... (已处理 " + lineCount + " 行)");
                    indicator.setFraction(0.35 + (0.5 * Math.min(lineCount / 1000.0, 1.0)));
                }
            }

            // 更新进度条：排序阶段
            if (indicator != null) {
                indicator.setText("正在排序统计结果...");
                indicator.setFraction(0.9);
            }

            // 等待 Git 进程结束并关闭流
            process.waitFor();
            reader.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        // 将统计结果转换为列表并排序（按总变更行数降序）
        List<CommitStatistic> result = new ArrayList<>(statisticsMap.values());
        Collections.sort(result);

        // 更新进度条：完成
        if (indicator != null) {
            indicator.setText("统计完成");
            indicator.setFraction(1.0);
        }

        return result;
    }

    /**
     * 获取当前 Git 分支名
     * 执行 git rev-parse --abbrev-ref HEAD 命令获取当前分支
     *
     * @param basePath 项目根目录路径
     * @return 当前分支名，获取失败返回 null
     */
    private String getCurrentBranch(String basePath) {
        try {
            // 构建 Git 命令获取当前分支
            ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD");
            pb.directory(new java.io.File(basePath));
            Process process = pb.start();

            // 读取命令输出
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );
            String branch = reader.readLine();
            reader.close();
            process.waitFor();

            return branch;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 拉取最新的提交记录
     * 执行 git fetch 命令从远程仓库获取最新的提交历史
     *
     * @param basePath 项目根目录路径
     * @param branch 当前分支名
     * @param indicator 进度指示器
     * @return 拉取是否成功
     */
    private boolean fetchLatestCommits(String basePath, String branch, ProgressIndicator indicator) {
        try {
            log.info("开始拉取最新提交记录，分支: {}", branch);

            // 构建 git fetch 命令
            ProcessBuilder pb = new ProcessBuilder("git", "fetch", "origin", branch);
            pb.directory(new java.io.File(basePath));
            Process process = pb.start();

            // 读取错误输出（git fetch 的进度信息通常输出到 stderr）
            BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream())
            );

            // 读取标准输出
            BufferedReader outputReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String line;
            // 读取并记录输出信息
            while ((line = errorReader.readLine()) != null) {
                log.info("Git fetch: {}", line);
                // 检查用户是否取消操作
                if (indicator != null && indicator.isCanceled()) {
                    process.destroy();
                    errorReader.close();
                    outputReader.close();
                    return false;
                }
            }

            while ((line = outputReader.readLine()) != null) {
                log.info("Git fetch: {}", line);
            }

            // 等待进程结束
            int exitCode = process.waitFor();
            errorReader.close();
            outputReader.close();

            if (exitCode == 0) {
                log.info("成功拉取最新提交记录");
                return true;
            } else {
                log.warn("拉取提交记录失败，退出码: {}", exitCode);
                return false;
            }
        } catch (Exception e) {
            log.error("拉取最新提交记录时发生异常", e);
            return false;
        }
    }

    /**
     * 获取指定日期范围内的统计数据（不带进度指示器）
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 统计结果列表
     */
    public List<CommitStatistic> getStatistics(LocalDate startDate, LocalDate endDate) {
        return getStatistics(startDate, endDate, null);
    }

    /**
     * 获取今日统计数据（不带进度指示器）
     *
     * @return 今日统计结果列表
     */
    public List<CommitStatistic> getTodayStatistics() {
        LocalDate today = LocalDate.now();
        return getStatistics(today, today);
    }

    /**
     * 获取今日统计数据（带进度指示器）
     *
     * @param indicator 进度指示器
     * @return 今日统计结果列表
     */
    public List<CommitStatistic> getTodayStatistics(ProgressIndicator indicator) {
        LocalDate today = LocalDate.now();
        return getStatistics(today, today, indicator);
    }
}
