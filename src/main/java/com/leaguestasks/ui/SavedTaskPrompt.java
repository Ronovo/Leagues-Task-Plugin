package com.leaguestasks.ui;

import com.leaguestasks.model.SavedTaskStore;
import com.leaguestasks.model.TaskEntry;
import java.awt.Component;
import javax.swing.JOptionPane;

final class SavedTaskPrompt
{
    private SavedTaskPrompt()
    {
    }

    /**
     * @return true if the task was saved; false if the player declined to
     *     replace the task already in that slot
     */
    static boolean saveConfirmingReplace(Component parent, SavedTaskStore savedTaskStore, TaskEntry task)
    {
        if (savedTaskStore.hasSaved(task.getDifficulty()))
        {
            String difficulty = TextUtil.capitalize(task.getDifficulty());
            int choice = JOptionPane.showConfirmDialog(parent,
                "Replace your saved " + difficulty + " task with this one?",
                "Replace saved task?",
                JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION)
            {
                return false;
            }
        }

        savedTaskStore.save(task);
        return true;
    }
}
