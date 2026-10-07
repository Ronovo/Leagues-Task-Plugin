package com.leaguestasks.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JPanel;

/**
 * Static text card (Welcome, About, Tracker App) loaded from a plain-text
 * resource. The file is read as blocks separated by blank lines:
 *
 * <ul>
 *   <li>A run of consecutive lines that each start with {@code "- "} becomes
 *       a real bulleted list ({@code <ul>}), so it reads as a list rather
 *       than run-on text.</li>
 *   <li>Any other run of non-blank lines becomes one paragraph: the source
 *       may be hard-wrapped for readability, but it is re-flowed and wraps
 *       to the panel width when shown, so a line break in the file never
 *       lands mid-sentence on screen.</li>
 * </ul>
 *
 * <p>Links are clickable (via {@link SwingComponents#wrappingCenteredHtmlPane()})
 * two ways: a bare http(s) URL is auto-linked as-is, or
 * {@code [Label](https://...)} shows "Label" instead -- prefer the labeled
 * form for long URLs.
 */
public class InfoCard extends JPanel
{
    // Swing's HTML renderer only wraps at whitespace and ignores the CSS
    // word-wrap/overflow-wrap properties, so a long unbroken token (e.g. a
    // URL) would otherwise run straight off the panel. Inserting a
    // zero-width space every MAX_UNBROKEN_RUN characters gives it a legal,
    // invisible place to break.
    private static final int MAX_UNBROKEN_RUN = 20;
    private static final char ZERO_WIDTH_SPACE = '\u200B';
    // Group 1/2: [Label](url) form. Group 3: bare url fallback.
    private static final Pattern LINK_PATTERN =
        Pattern.compile("\\[([^\\]]+)\\]\\((https?://[^\\s)]+)\\)|(https?://\\S+)");
    // A list item: optional leading whitespace, a single dash, then a space.
    private static final Pattern BULLET_PATTERN = Pattern.compile("^\\s*-\\s+(.*)$");

    public InfoCard(String resourceName, Navigator navigator)
    {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        SwingComponents.stylePanel(this);

        JButton backButton = SwingComponents.centeredButton("Back");
        backButton.addActionListener(e -> navigator.showCard(Navigator.CARD_START));

        JEditorPane textPane = SwingComponents.wrappingCenteredHtmlPane();
        // Left-aligned, same exception TaskDetailsView's requirements list
        // makes to the panel's center-everything convention -- centering
        // free-form/bulleted text makes every wrapped line jump around.
        textPane.setText(SwingComponents.centeredHtml(
            "<div style='text-align:left'>" + loadContentHtml(resourceName) + "</div>"));

        add(textPane);
        add(Box.createVerticalStrut(10));
        add(backButton);
    }

    static String loadContentHtml(String resourceName)
    {
        List<String> lines = readLines(resourceName);
        if (lines == null)
        {
            return "<i>" + TextUtil.escapeHtml(resourceName) + " not found</i>";
        }
        return renderHtml(lines);
    }

    private static List<String> readLines(String resourceName)
    {
        try (InputStream is = InfoCard.class.getResourceAsStream(resourceName))
        {
            if (is == null)
            {
                return null;
            }
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    lines.add(line);
                }
            }
            return lines;
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Failed to load " + resourceName, e);
        }
    }

    /**
     * Turns the file's lines into block-level HTML: bullet runs become a
     * {@code <ul>}, everything else becomes a re-flowed paragraph. Blank
     * lines separate blocks. The first block, when it is a single line, is
     * rendered as a centered, bold, accent-coloured page title (matching the
     * card section headers). Package-private for tests.
     */
    static String renderHtml(List<String> lines)
    {
        List<String> blocks = new ArrayList<>();
        int i = 0;
        boolean firstBlock = true;
        while (i < lines.size())
        {
            if (lines.get(i).trim().isEmpty())
            {
                i++;
                continue;
            }

            if (BULLET_PATTERN.matcher(lines.get(i)).matches())
            {
                StringBuilder list = new StringBuilder(
                    "<ul style='margin:4px 0 10px 0; padding-left:16px'>");
                while (i < lines.size())
                {
                    Matcher m = BULLET_PATTERN.matcher(lines.get(i));
                    if (!m.matches())
                    {
                        break;
                    }
                    list.append("<li style='margin:0 0 6px 0'>")
                        .append(linkify(m.group(1).trim()))
                        .append("</li>");
                    i++;
                }
                blocks.add(list.append("</ul>").toString());
            }
            else
            {
                StringBuilder para = new StringBuilder();
                int paraLines = 0;
                while (i < lines.size()
                    && !lines.get(i).trim().isEmpty()
                    && !BULLET_PATTERN.matcher(lines.get(i)).matches())
                {
                    if (para.length() > 0)
                    {
                        para.append(' ');
                    }
                    para.append(linkify(lines.get(i).trim()));
                    paraLines++;
                    i++;
                }

                if (firstBlock && paraLines == 1)
                {
                    blocks.add("<div style='text-align:center; color:"
                        + SwingComponents.hex(SwingComponents.ACCENT_COLOR) + "; margin:0 0 12px 0'>"
                        + "<b>" + para + "</b></div>");
                }
                else
                {
                    blocks.add("<div style='margin:0 0 10px 0'>" + para + "</div>");
                }
            }

            firstBlock = false;
        }
        return String.join("", blocks);
    }

    /**
     * Wraps {@code [Label](url)} and bare http(s) URLs in a clickable
     * {@code <a>}; everything else is escaped and break-tokenized as plain
     * text.
     */
    private static String linkify(String text)
    {
        StringBuilder out = new StringBuilder();
        Matcher m = LINK_PATTERN.matcher(text);
        int last = 0;
        while (m.find())
        {
            out.append(TextUtil.escapeHtml(breakLongTokens(text.substring(last, m.start()))));

            String url = m.group(2) != null ? m.group(2) : m.group(3);
            // A labeled link's display text is ordinary words, so it wraps
            // at spaces on its own -- no need to break-tokenize it like a
            // bare, space-less URL does.
            String display = m.group(1) != null ? m.group(1) : breakLongTokens(url);

            out.append("<a href=\"").append(TextUtil.escapeHtml(url)).append("\" style=\"color:#8ab4f8\">")
                .append(TextUtil.escapeHtml(display)).append("</a>");
            last = m.end();
        }
        out.append(TextUtil.escapeHtml(breakLongTokens(text.substring(last))));
        return out.toString();
    }

    private static String breakLongTokens(String text)
    {
        StringBuilder out = new StringBuilder(text.length());
        int run = 0;
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (Character.isWhitespace(c))
            {
                run = 0;
            }
            else
            {
                if (run == MAX_UNBROKEN_RUN)
                {
                    out.append(ZERO_WIDTH_SPACE);
                    run = 0;
                }
                run++;
            }
            out.append(c);
        }
        return out.toString();
    }
}
