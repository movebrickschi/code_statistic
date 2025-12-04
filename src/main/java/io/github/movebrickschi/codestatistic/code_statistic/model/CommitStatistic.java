package io.github.movebrickschi.codestatistic.code_statistic.model;

/**
 * Git 提交统计数据模型
 * 用于存储单个作者的代码提交统计信息，包括提交次数、新增行数、删除行数等
 */
public class CommitStatistic implements Comparable<CommitStatistic> {
    /** 作者名称 */
    private final String author;

    /** 新增代码行数 */
    private int additions;

    /** 删除代码行数 */
    private int deletions;

    /** 提交次数 */
    private int commitCount;

    /**
     * 构造函数
     * @param author 作者名称
     */
    public CommitStatistic(String author) {
        this.author = author;
        this.additions = 0;
        this.deletions = 0;
        this.commitCount = 0;
    }

    /**
     * 添加一次提交的统计数据（包含提交次数、新增和删除行数）
     * @param additions 新增行数
     * @param deletions 删除行数
     */
    public void addCommit(int additions, int deletions) {
        this.additions += additions;
        this.deletions += deletions;
        this.commitCount++;
    }

    /**
     * 增加提交次数（不累加代码行数）
     * 每遇到一次新的提交时调用此方法
     */
    public void incrementCommitCount() {
        this.commitCount++;
    }

    /**
     * 只累加代码变更行数，不增加提交次数
     * 用于处理同一次提交中的多个文件变更
     * @param additions 新增行数
     * @param deletions 删除行数
     */
    public void addChanges(int additions, int deletions) {
        this.additions += additions;
        this.deletions += deletions;
    }

    /**
     * 获取作者名称
     * @return 作者名称
     */
    public String getAuthor() {
        return author;
    }

    /**
     * 获取新增代码行数
     * @return 新增行数
     */
    public int getAdditions() {
        return additions;
    }

    /**
     * 获取删除代码行数
     * @return 删除行数
     */
    public int getDeletions() {
        return deletions;
    }

    /**
     * 获取提交次数
     * @return 提交次数
     */
    public int getCommitCount() {
        return commitCount;
    }

    /**
     * 获取总变更行数（新增+删除）
     * @return 总变更行数
     */
    public int getTotalChanges() {
        return additions + deletions;
    }

    /**
     * 比较两个统计对象，用于排序
     * 按总变更行数降序排列
     * @param other 另一个统计对象
     * @return 比较结果
     */
    @Override
    public int compareTo(CommitStatistic other) {
        return Integer.compare(other.getTotalChanges(), this.getTotalChanges());
    }
}
