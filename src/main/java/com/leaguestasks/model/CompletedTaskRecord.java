package com.leaguestasks.model;

/**
 * One entry in the completed-tasks background list. Deserialized by Gson
 * (field-based, matching TaskEntry's pattern), plus a constructor for
 * writing new entries.
 *
 * <p>{@code completedAt} is when the completion was <em>recorded</em>, not
 * when it happened: a task confirmed through Autocomplete Tasks is stamped
 * with that moment even if the quest was finished years ago. The
 * alternative -- leaving it null -- would break ordering on the tracker, so
 * recorded-at time is used even when completion predates tracking.
 */
public class CompletedTaskRecord
{
    /** How a completion got recorded. */
    public enum Source
    {
        /** The player clicked the task done in the panel. */
        MANUAL,
        /** Derived from game state by {@code TaskAutoDetector}. */
        AUTO
    }

    private String taskId;
    private String completedAt;
    /**
     * Null for records written before auto-detection existed, which were
     * all hand-marked -- {@link #getSource()} reads null as
     * {@link Source#MANUAL} rather than migrating the stored blob.
     */
    private Source source;

    public CompletedTaskRecord()
    {
    }

    public CompletedTaskRecord(String taskId, String completedAt)
    {
        this(taskId, completedAt, Source.MANUAL);
    }

    public CompletedTaskRecord(String taskId, String completedAt, Source source)
    {
        this.taskId = taskId;
        this.completedAt = completedAt;
        this.source = source;
    }

    public String getTaskId()
    {
        return taskId;
    }

    public String getCompletedAt()
    {
        return completedAt;
    }

    public Source getSource()
    {
        return source != null ? source : Source.MANUAL;
    }
}
