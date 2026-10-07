package com.leaguestasks.ui;

import com.google.gson.Gson;
import com.leaguestasks.model.TaskEntry;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TaskDetailsViewTest
{
    private final Gson gson = new Gson();

    @Test
    public void structuredRequirementItemsRenderAsList()
    {
        TaskEntry t = gson.fromJson(
            "{\"name\":\"Client of Kourend\",\"requirementItems\":["
                + "{\"text\":\"20 Thieving\",\"subItems\":[\"not boostable\"]},"
                + "{\"text\":\"Client of Kourend\",\"subItems\":[]}"
                + "]}",
            TaskEntry.class);

        String html = TaskDetailsView.buildRequirementsHtml(t);
        assertTrue(html.contains("<ul"));
        assertTrue(html.contains("20 Thieving"));
        assertTrue(html.contains("not boostable"));
        assertTrue(html.contains("Client of Kourend"));
    }

    @Test
    public void flatRequirementsFallback()
    {
        TaskEntry t = gson.fromJson(
            "{\"requirements\":\"50 Attack\"}",
            TaskEntry.class);
        assertEquals("Requirements: 50 Attack", TaskDetailsView.buildRequirementsHtml(t));
    }

    @Test
    public void nullRequirementsRenderEmpty()
    {
        TaskEntry t = gson.fromJson("{\"name\":\"X\"}", TaskEntry.class);
        assertEquals("", TaskDetailsView.buildRequirementsHtml(t));
    }

    @Test
    public void htmlInRequirementsIsEscaped()
    {
        TaskEntry t = gson.fromJson(
            "{\"requirements\":\"Kill <b>foo</b> & bar\"}",
            TaskEntry.class);
        String html = TaskDetailsView.buildRequirementsHtml(t);
        assertTrue(html.contains("&lt;b&gt;"));
        assertTrue(html.contains("&amp;"));
    }

    @Test
    public void formatTaskMetaIsRegionDifficultyPoints()
    {
        TaskEntry t = gson.fromJson(
            "{\"region\":\"Kourend\",\"difficulty\":\"hard\",\"points\":80}",
            TaskEntry.class);
        assertEquals("Kourend — Hard (80 pts)", TaskDetailsView.formatTaskMeta(t));
    }
}
