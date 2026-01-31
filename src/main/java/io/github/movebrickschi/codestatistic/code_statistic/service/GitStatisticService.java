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
import java.util.regex.Pattern;

/**
 * Git 统计服务
 * 负责执行 Git 命令并解析提交历史，统计各作者的代码提交情况
 * 只统计有效代码变更，排除空行和注释行
 */
public class GitStatisticService {
    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(GitStatisticService.class);

    /** 当前项目实例 */
    private final Project project;

    /** 匹配空行或只包含空白字符的行 */
    private static final Pattern EMPTY_LINE_PATTERN = Pattern.compile("^\\s*$");

    /** 匹配单行注释：// 或 # 开头的注释 */
    private static final Pattern SINGLE_LINE_COMMENT_PATTERN = Pattern.compile("^\\s*(//|#).*$");

    /** 匹配多行注释开始：/* 或 /** */
    private static final Pattern MULTI_LINE_COMMENT_START_PATTERN = Pattern.compile("^\\s*/\\*.*$");

    /** 匹配多行注释结束 */
    private static final Pattern MULTI_LINE_COMMENT_END_PATTERN = Pattern.compile("^.*\\*/\\s*$");

    /** 匹配多行注释中间行（以星号开头但不以星号斜杠结尾）或纯注释行 */
    private static final Pattern MULTI_LINE_COMMENT_MIDDLE_PATTERN = Pattern.compile("^\\s*\\*(?!/).*$");

    /** 匹配 XML/HTML 注释 <!-- --> */
    private static final Pattern XML_COMMENT_PATTERN = Pattern.compile("^\\s*<!--.*-->\\s*$|^\\s*<!--.*$|^.*-->\\s*$");

    /** 匹配 Java 文档注释标签行 */
    private static final Pattern JAVADOC_TAG_PATTERN = Pattern.compile("^\\s*\\*\\s*@.*$");

    /** 匹配只包含括号的行（如单独的 { 或 }） */
    private static final Pattern ONLY_BRACKETS_PATTERN = Pattern.compile("^\\s*[{}()\\[\\]]+\\s*$");

    /** 匹配 import 语句 */
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+.*$");

    /** 匹配 package 语句 */
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+.*$");

    /** 匹配只包含分号的行 */
    private static final Pattern ONLY_SEMICOLON_PATTERN = Pattern.compile("^\\s*;\\s*$");

    /** 匹配注解行（如 @Override, @Deprecated 等） */
    private static final Pattern ANNOTATION_PATTERN = Pattern.compile("^\\s*@\\w+.*$");

    /** 匹配空的方法体或类体 */
    private static final Pattern EMPTY_BODY_PATTERN = Pattern.compile("^\\s*\\{\\s*}\\s*$");

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
     * 只统计有效代码变更，排除空行和注释行
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

            // 构建 Git 命令：git log -p 显示每次提交的详细 diff 内容
            // --no-merges 参数用于排除合并提交，只统计真正的代码提交
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "git", "log",
                    currentBranch,  // 指定当前分支
                    "--since=" + since,
                    "--until=" + until,
                    "--no-merges",  // 排除合并提交
                    "-p",  // 显示详细 diff 内容
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
            boolean inMultiLineComment = false;  // 是否在多行注释内
            boolean inXmlComment = false;  // 是否在 XML 注释内

            // 逐行解析 Git 输出
            while ((line = reader.readLine()) != null) {
                // 检查用户是否取消操作
                if (indicator != null && indicator.isCanceled()) {
                    process.destroy();
                    reader.close();
                    return Collections.emptyList();
                }

                log.debug("当前的line:{}", line);

                // 处理提交标记行：COMMIT:作者名
                if (line.startsWith("COMMIT:")) {
                    currentAuthor = line.substring(7);  // 提取作者名
                    statisticsMap.putIfAbsent(currentAuthor, new CommitStatistic(currentAuthor));
                    // 每次遇到新的提交时，增加提交次数（一次提交算一次）
                    statisticsMap.get(currentAuthor).incrementCommitCount();
                    // 每次新提交重置多行注释状态
                    inMultiLineComment = false;
                    inXmlComment = false;
                }
                // 处理 diff 行：以 + 或 - 开头表示新增或删除的代码行
                else if (currentAuthor != null && (line.startsWith("+") || line.startsWith("-"))) {
                    // 跳过 diff 头部行（如 +++ a/file 或 --- b/file）
                    if (line.startsWith("+++") || line.startsWith("---")) {
                        continue;
                    }

                    // 获取实际代码内容（去掉开头的 + 或 -）
                    String codeContent = line.substring(1);

                    // 判断是新增还是删除
                    boolean isAddition = line.startsWith("+");

                    // 检测并更新多行注释状态
                    boolean wasInMultiLineComment = inMultiLineComment;
                    boolean wasInXmlComment = inXmlComment;

                    // 检测多行注释开始
                    if (MULTI_LINE_COMMENT_START_PATTERN.matcher(codeContent).matches()) {
                        inMultiLineComment = true;
                        // 检查是否同一行结束
                        if (MULTI_LINE_COMMENT_END_PATTERN.matcher(codeContent).matches()) {
                            inMultiLineComment = false;
                        }
                    }
                    // 检测多行注释结束
                    if (wasInMultiLineComment && MULTI_LINE_COMMENT_END_PATTERN.matcher(codeContent).matches()) {
                        inMultiLineComment = false;
                        continue; // 跳过注释结束行
                    }

                    // 检测 XML 注释开始
                    if (codeContent.contains("<!--") && !codeContent.contains("-->")) {
                        inXmlComment = true;
                    }
                    // 检测 XML 注释结束
                    if (wasInXmlComment && codeContent.contains("-->")) {
                        inXmlComment = false;
                        continue; // 跳过注释结束行
                    }

                    // 如果当前在多行注释或 XML 注释内，跳过
                    if (wasInMultiLineComment || wasInXmlComment) {
                        continue;
                    }

                    // 检查是否为有效代码行（非空行、非注释行）
                    if (isEffectiveCodeLine(codeContent)) {
                        if (isAddition) {
                            statisticsMap.get(currentAuthor).addChanges(1, 0);
                        } else {
                            statisticsMap.get(currentAuthor).addChanges(0, 1);
                        }
                    }
                }

                // 每处理 100 行更新一次进度条
                lineCount++;
                if (indicator != null && lineCount % 100 == 0) {
                    indicator.setText("正在处理提交记录... (已处理 " + lineCount + " 行)");
                    indicator.setFraction(0.35 + (0.5 * Math.min(lineCount / 5000.0, 1.0)));
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
     * 判断一行代码是否为有效代码行
     * 排除空行、注释行、以及无实际代码产出的行（如格式调整、import语句等）
     *
     * @param line 代码行内容
     * @return 如果是有效代码行返回 true，否则返回 false
     */
    private boolean isEffectiveCodeLine(String line) {
        // 空行或只包含空白字符
        if (EMPTY_LINE_PATTERN.matcher(line).matches()) {
            return false;
        }

        // 单行注释：// 或 # 开头
        if (SINGLE_LINE_COMMENT_PATTERN.matcher(line).matches()) {
            return false;
        }

        // 多行注释开始行：/* 或 /**
        if (MULTI_LINE_COMMENT_START_PATTERN.matcher(line).matches()) {
            return false;
        }

        // 多行注释中间行：以 * 开头
        if (MULTI_LINE_COMMENT_MIDDLE_PATTERN.matcher(line).matches()) {
            return false;
        }

        // 多行注释结束行：*/
        if (MULTI_LINE_COMMENT_END_PATTERN.matcher(line).matches()) {
            return false;
        }

        // Java 文档注释标签行：* @param 等
        if (JAVADOC_TAG_PATTERN.matcher(line).matches()) {
            return false;
        }

        // XML/HTML 注释
        if (XML_COMMENT_PATTERN.matcher(line).matches()) {
            return false;
        }

        // 只包含括号的行（如单独的 { 或 }）
        if (ONLY_BRACKETS_PATTERN.matcher(line).matches()) {
            return false;
        }

        // import 语句
        if (IMPORT_PATTERN.matcher(line).matches()) {
            return false;
        }

        // package 语句
        if (PACKAGE_PATTERN.matcher(line).matches()) {
            return false;
        }

        // 只包含分号的行
        if (ONLY_SEMICOLON_PATTERN.matcher(line).matches()) {
            return false;
        }

        // 注解行（如 @Override, @Deprecated 等）
        if (ANNOTATION_PATTERN.matcher(line).matches()) {
            return false;
        }

        // 空的方法体或类体 {}
        if (EMPTY_BODY_PATTERN.matcher(line).matches()) {
            return false;
        }

        return true;
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
