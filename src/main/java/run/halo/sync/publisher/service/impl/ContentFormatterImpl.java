package run.halo.sync.publisher.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.halo.sync.publisher.adapter.dto.ArticleContent;
import run.halo.sync.publisher.service.ContentFormatter;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内容格式化服务实现
 * 
 * 参考原 wechat-formatter-fixed.ts 实现
 * - Mac 风格代码块
 * - 语法高亮（github-dark-dimmed）
 * - 列表处理（微信不支持 list-style）
 * - 表格、图片、引用等样式
 */
@Slf4j
@Service
public class ContentFormatterImpl implements ContentFormatter {

    // ==================== 主题配置 ====================
    
    private static final Map<String, ThemeConfig> THEMES = new HashMap<>();
    
    static {
        THEMES.put("default", new ThemeConfig("经典", "#0F4C81", "#f0f5fa"));
        THEMES.put("roseGold", new ThemeConfig("熏衣紫", "#8B7BA8", "#f5f0f8"));
        THEMES.put("classicBlue", new ThemeConfig("经典蓝", "#3585e0", "#ecf5ff"));
        THEMES.put("jadeGreen", new ThemeConfig("翡翠绿", "#009874", "#e8f5f0"));
        THEMES.put("vibrantOrange", new ThemeConfig("活力橘", "#FA5151", "#fff0f0"));
    }

    // ==================== 基础样式 ====================
    
    private static final String FONT_FAMILY = "-apple-system-font, BlinkMacSystemFont, " +
        "'Helvetica Neue', 'PingFang SC', 'Hiragino Sans GB', " +
        "'Microsoft YaHei UI', 'Microsoft YaHei', Arial, sans-serif";
    
    private static final String SECTION_STYLE = 
        "padding: 20px 15px; font-size: 14px; line-height: 1.75; " +
        "color: #333; font-family: %s; text-align: left; letter-spacing: 0.1em;";
    
    // ==================== 代码高亮样式（github-dark-dimmed） ====================
    
    private static final Map<String, String> HLJS_STYLES = new HashMap<>();
    
    static {
        HLJS_STYLES.put("hljs", "color: #adbac7; background: #22272e;");
        HLJS_STYLES.put("hljs-comment", "color: #768390; font-style: italic;");
        HLJS_STYLES.put("hljs-keyword", "color: #f47067; font-weight: bold;");
        HLJS_STYLES.put("hljs-string", "color: #96d0ff;");
        HLJS_STYLES.put("hljs-number", "color: #6cb6ff;");
        HLJS_STYLES.put("hljs-title", "color: #dcbdfb;");
        HLJS_STYLES.put("hljs-function", "color: #dcbdfb;");
        HLJS_STYLES.put("hljs-params", "color: #adbac7;");
        HLJS_STYLES.put("hljs-built_in", "color: #f69d50;");
        HLJS_STYLES.put("hljs-class", "color: #dcbdfb;");
        HLJS_STYLES.put("hljs-variable", "color: #6cb6ff;");
        HLJS_STYLES.put("hljs-attr", "color: #6cb6ff;");
        HLJS_STYLES.put("hljs-symbol", "color: #f69d50;");
        HLJS_STYLES.put("hljs-name", "color: #8ddb8c;");
        HLJS_STYLES.put("hljs-addition", "color: #b4f1b4; background-color: #1b4721;");
        HLJS_STYLES.put("hljs-deletion", "color: #ffd8d3; background-color: #78191b;");
    }

    @Override
    public Mono<String> formatForWeChat(ArticleContent content) {
        return Mono.fromCallable(() -> {
            String markdown = content.getRawContent();
            if (markdown == null || markdown.isEmpty()) {
                return "";
            }

            ThemeConfig theme = THEMES.get("default");
            
            // 1. 处理代码块（Mac 风格 + 行号）
            String html = processCodeBlocks(markdown);
            
            // 2. 处理标题
            html = processHeadings(html, theme);
            
            // 3. 处理列表（手动添加前缀）
            html = processLists(html);
            
            // 4. 处理引用
            html = processBlockquotes(html, theme);
            
            // 5. 处理表格
            html = processTables(html);
            
            // 6. 处理图片
            html = processImages(html);
            
            // 7. 处理行内元素
            html = processInlineElements(html, theme);
            
            // 8. 处理分隔线
            html = processHorizontalRules(html);
            
            // 9. 处理 GFM 警告块
            html = processAlerts(html, theme);
            
            // 10. 清理多余空行
            html = cleanupHtml(html);
            
            // 11. 包裹容器
            html = wrapInSection(html);
            
            return html;
        });
    }

    @Override
    public Mono<String> formatForJuejin(ArticleContent content) {
        // 掘金原生支持 Markdown，直接返回原始内容
        return Mono.justOrEmpty(content.getRawContent());
    }

    // ==================== 代码块处理 ====================

    /**
     * 处理代码块（Mac 风格 + 行号）
     */
    private String processCodeBlocks(String markdown) {
        // 匹配 ```language\ncode``` 格式
        Pattern pattern = Pattern.compile("```(\\w*)\\n([\\s\\S]*?)```", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(markdown);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String language = matcher.group(1);
            String code = matcher.group(2);
            
            // 去除末尾空行
            code = code.replaceAll("\\n+$", "");
            
            // 生成 Mac 风格代码块
            String codeBlock = generateMacCodeBlock(code, language);
            matcher.appendReplacement(result, Matcher.quoteReplacement(codeBlock));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * 生成 Mac 风格代码块
     */
    private String generateMacCodeBlock(String code, String language) {
        // Mac 风格头部（三个彩色圆点）
        String header = "<div style=\"height: 30px; background: #1e2128; " +
            "border-radius: 5px 5px 0 0; display: flex; align-items: center; padding-left: 12px;\">" +
            "<span style=\"width: 12px; height: 12px; border-radius: 50%; background: #ff5f56;\"></span>" +
            "<span style=\"width: 12px; height: 12px; border-radius: 50%; background: #ffbd2e; margin-left: 8px;\"></span>" +
            "<span style=\"width: 12px; height: 12px; border-radius: 50%; background: #27c93f; margin-left: 8px;\"></span>" +
            "</div>";

        // 添加行号
        String numberedCode = addLineNumbers(code, language);
        
        // 代码块容器
        String wrapperStyle = "margin: 10px 8px; border-radius: 5px; " +
            "overflow: hidden; background: #22272e;";
        String preStyle = "margin: 0; padding: 16px; overflow-x: auto; background: #22272e;";

        return String.format(
            "<section style=\"%s\">%s<pre style=\"%s\">%s</pre></section>",
            wrapperStyle, header, preStyle, numberedCode
        );
    }

    /**
     * 添加行号
     */
    private String addLineNumbers(String code, String language) {
        String[] lines = code.split("\n");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            int lineNum = i + 1;
            String line = escapeHtml(lines[i]);
            
            // 简单的语法高亮（关键词）
            line = highlightLine(line, language);
            
            // 行号样式
            String lineHtml = String.format(
                "<div style=\"display: flex; line-height: 1.5;\">" +
                "<span style=\"min-width: 40px; padding-right: 10px; text-align: right; " +
                "color: #768390; user-select: none; border-right: 1px solid #373e47; " +
                "margin-right: 10px; font-size: 12.6px;\">%d</span>" +
                "<span style=\"flex: 1; font-size: 12.6px; white-space: pre; " +
                "font-family: Menlo, Monaco, 'Courier New', monospace; color: #adbac7;\">%s</span>" +
                "</div>",
                lineNum, line.isEmpty() ? " " : line
            );
            
            result.append(lineHtml);
        }

        return result.toString();
    }

    /**
     * 简单的行级语法高亮
     */
    private String highlightLine(String line, String language) {
        // 关键词高亮
        line = line.replaceAll("\\b(public|private|protected|class|interface|void|return|if|else|for|while|import|package|new|this|static|final|const|let|var|function|async|await)\\b", 
            "<span style=\"color: #f47067; font-weight: bold;\">$1</span>");
        
        // 字符串高亮
        line = line.replaceAll("\"([^\"]*)\"", 
            "<span style=\"color: #96d0ff;\">\"$1\"</span>");
        line = line.replaceAll("'([^']*)'", 
            "<span style=\"color: #96d0ff;\">'$1'</span>");
        
        // 数字高亮
        line = line.replaceAll("\\b(\\d+)\\b", 
            "<span style=\"color: #6cb6ff;\">$1</span>");
        
        // 注释高亮
        line = line.replaceAll("//(.*)$", 
            "<span style=\"color: #768390; font-style: italic;\">//$1</span>");
        line = line.replaceAll("#(.*)$", 
            "<span style=\"color: #768390; font-style: italic;\">#$1</span>");

        return line;
    }

    // ==================== 标题处理 ====================

    private String processHeadings(String html, ThemeConfig theme) {
        // H1 - 带底部边框
        html = html.replaceAll("(?m)^# (.+)$", 
            String.format("<h1 style=\"display: table; padding: 0 1em; " +
                "border-bottom: 2px solid %s; margin: 2em auto 1em; " +
                "font-size: 16.8px; font-weight: bold; text-align: center; " +
                "color: #333;\">$1</h1>", theme.primary));
        
        // H2 - 带背景色
        html = html.replaceAll("(?m)^## (.+)$", 
            String.format("<h2 style=\"display: table; padding: 0 0.2em; " +
                "margin: 4em auto 2em; background: %s; " +
                "font-size: 16.8px; font-weight: bold; text-align: center; " +
                "color: #fff;\">$1</h2>", theme.primary));
        
        // H3 - 左边框
        html = html.replaceAll("(?m)^### (.+)$", 
            String.format("<h3 style=\"padding-left: 8px; " +
                "border-left: 3px solid %s; margin: 2em 8px 0.75em 0; " +
                "font-size: 15.4px; font-weight: bold; line-height: 1.2; " +
                "color: #333;\">$1</h3>", theme.primary));
        
        // H4 - 简单样式
        html = html.replaceAll("(?m)^#### (.+)$", 
            String.format("<h4 style=\"margin: 2em 8px 0.5em; " +
                "font-size: 14px; font-weight: bold; color: %s;\">$1</h4>", 
                theme.primary));

        return html;
    }

    // ==================== 列表处理 ====================

    /**
     * 处理列表（微信不支持 list-style，手动添加前缀）
     */
    private String processLists(String html) {
        // 无序列表
        html = html.replaceAll("(?m)^- (.+)$", 
            "<li style=\"margin: 0.2em 8px; color: #333;\">" +
            "<span style=\"margin-right: 8px; color: #333;\">•</span>$1</li>");
        
        // 有序列表
        html = processOrderedList(html);
        
        // 包裹列表
        html = wrapLists(html);
        
        return html;
    }

    private String processOrderedList(String html) {
        // 简化处理：将数字列表项转换为带数字的 li
        Pattern pattern = Pattern.compile("^(\\d+)\\. (.+)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(html);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String num = matcher.group(1);
            String text = matcher.group(2);
            String replacement = String.format(
                "<li style=\"margin: 0.2em 8px; color: #333;\">" +
                "<span style=\"margin-right: 8px; font-weight: bold; color: #333;\">%s.</span>%s</li>",
                num, text
            );
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }

    private String wrapLists(String html) {
        // 将连续的 li 包裹在 ul 中
        html = html.replaceAll("((?:<li[^>]*>.*?</li>\\s*)+)", 
            "<ul style=\"list-style-type: none; padding-left: 0; margin-left: 0; color: #333;\">$1</ul>");
        return html;
    }

    // ==================== 引用处理 ====================

    private String processBlockquotes(String html, ThemeConfig theme) {
        // 处理普通引用
        html = html.replaceAll("(?m)^> (.+)$", 
            String.format("<blockquote style=\"font-style: normal; padding: 1em; " +
                "border-left: 4px solid %s; border-radius: 6px; " +
                "color: #333; background: %s; margin: 0 8px 1em;\">" +
                "<p style=\"margin: 0; font-size: 14px;\">$1</p></blockquote>",
                theme.primary, theme.background));
        
        return html;
    }

    // ==================== 表格处理 ====================

    private String processTables(String html) {
        // 简化的表格处理（实际使用时需要更复杂的解析）
        // 这里假设表格已经是 HTML 格式
        
        String tableStyle = "color: #333; border-collapse: collapse; width: 100%; margin: 1em 8px;";
        String thStyle = "border: 1px solid #dfdfdf; padding: 0.5em; " +
            "font-weight: bold; background: rgba(0, 0, 0, 0.05);";
        String tdStyle = "border: 1px solid #dfdfdf; padding: 0.5em;";
        
        html = html.replaceAll("<table>", "<table style=\"" + tableStyle + "\">");
        html = html.replaceAll("<th>", "<th style=\"" + thStyle + "\">");
        html = html.replaceAll("<td>", "<td style=\"" + tdStyle + "\">");
        
        return html;
    }

    // ==================== 图片处理 ====================

    private String processImages(String html) {
        // 带标题的图片
        html = html.replaceAll("!\\[([^\\]]*)\\]\\(([^)]+)\\)(?:\\s*\"([^\"]+)\")?", 
            "<figure style=\"margin: 1.5em 8px;\">" +
            "<img src=\"$2\" alt=\"$1\" style=\"display: block; max-width: 100%; " +
            "margin: 0.1em auto 0.5em; border-radius: 4px;\" />" +
            "<figcaption style=\"text-align: center; color: #888; font-size: 11.2px; " +
            "margin-top: 0.5em;\">$1</figcaption>" +
            "</figure>");
        
        return html;
    }

    // ==================== 行内元素处理 ====================

    private String processInlineElements(String html, ThemeConfig theme) {
        // 粗体
        html = html.replaceAll("\\*\\*([^*]+)\\*\\*", 
            String.format("<strong style=\"color: %s; font-weight: bold;\">$1</strong>", 
                theme.primary));
        
        // 斜体
        html = html.replaceAll("\\*([^*]+)\\*", "<em style=\"font-style: italic;\">$1</em>");
        
        // 行内代码
        html = html.replaceAll("`([^`]+)`", 
            "<code style=\"font-size: 12.6px; color: #d14; " +
            "background: rgba(27, 31, 35, 0.05); padding: 3px 5px; " +
            "border-radius: 4px;\">$1</code>");
        
        // 链接
        html = html.replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", 
            "<a href=\"$2\" style=\"color: #576b95; text-decoration: none;\">$1</a>");
        
        // 段落
        html = html.replaceAll("(?m)^([^<\\n].+)$", 
            "<p style=\"margin: 1.5em 8px; letter-spacing: 0.1em; color: #333;\">$1</p>");
        
        return html;
    }

    // ==================== 分隔线处理 ====================

    private String processHorizontalRules(String html) {
        html = html.replaceAll("(?m)^---$", 
            "<hr style=\"border-style: solid; border-width: 2px 0 0; " +
            "border-color: rgba(0, 0, 0, 0.1); transform-origin: 0 0; " +
            "transform: scale(1, 0.5); height: 0.4em; margin: 1.5em 0;\" />");
        
        return html;
    }

    // ==================== GFM 警告块处理 ====================

    private String processAlerts(String html, ThemeConfig theme) {
        Map<String, AlertConfig> alerts = new HashMap<>();
        alerts.put("NOTE", new AlertConfig("ℹ️", "#478be6", "#e8f4fd"));
        alerts.put("TIP", new AlertConfig("💡", "#57ab5a", "#e8f5e9"));
        alerts.put("IMPORTANT", new AlertConfig("❗", "#986ee2", "#f3e8fd"));
        alerts.put("WARNING", new AlertConfig("⚠️", "#c69026", "#fff8e6"));
        alerts.put("CAUTION", new AlertConfig("🚫", "#e5534b", "#fee"));

        for (Map.Entry<String, AlertConfig> entry : alerts.entrySet()) {
            String type = entry.getKey();
            AlertConfig config = entry.getValue();
            
            html = html.replaceAll(
                "(?m)^> \\[!" + type + "\\]\\s*\\n> (.+)$",
                String.format("<blockquote style=\"margin: 15px 8px; padding: 15px 20px; " +
                    "background: %s; border-left: 4px solid %s; border-radius: 6px;\">" +
                    "<p style=\"margin: 0 0 8px 0; font-weight: bold; color: %s; font-size: 14px;\">" +
                    "<span style=\"margin-right: 8px;\">%s</span>%s</p>" +
                    "<div style=\"margin: 0; color: #333; font-size: 14px;\">$1</div>" +
                    "</blockquote>",
                    config.bg, config.color, config.color, config.icon, 
                    type.charAt(0) + type.substring(1).toLowerCase())
            );
        }
        
        return html;
    }

    // ==================== HTML 清理 ====================

    private String cleanupHtml(String html) {
        // 移除多余空行
        html = html.replaceAll("\\n\\s*\\n\\s*\\n", "\n\n");
        
        // 移除空段落
        html = html.replaceAll("<p[^>]*>\\s*</p>", "");
        
        // 处理换行
        html = html.replaceAll("\n\n", "</p><p>");
        html = html.replaceAll("\n", "<br/>");
        
        return html;
    }

    // ==================== 容器包裹 ====================

    private String wrapInSection(String html) {
        String sectionStyle = String.format(SECTION_STYLE, FONT_FAMILY);
        return String.format("<section style=\"%s\">%s</section>", sectionStyle, html);
    }

    // ==================== 工具方法 ====================

    private String escapeHtml(String text) {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    // ==================== 内部类 ====================

    private static class ThemeConfig {
        final String name;
        final String primary;
        final String background;

        ThemeConfig(String name, String primary, String background) {
            this.name = name;
            this.primary = primary;
            this.background = background;
        }
    }

    private static class AlertConfig {
        final String icon;
        final String color;
        final String bg;

        AlertConfig(String icon, String color, String bg) {
            this.icon = icon;
            this.color = color;
            this.bg = bg;
        }
    }
}
