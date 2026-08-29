package com.pilarestilo.notificationservice.application;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a message in the shop's own look, in the subset of HTML that email clients agree on.
 *
 * <p>Same palette and typographic pairing as the storefront — Cormorant Garamond over Montserrat,
 * rose on cream — so the email and the site read as one place. Tables not flexbox (Outlook renders
 * through Word), inline styles (Gmail strips {@code <style>}), no web fonts (declared with real
 * fallbacks), 600px, explicit colours everywhere.
 */
public final class EmailLayout {

    private static final String CREAM = "#F8F4EF";
    private static final String SURFACE = "#FFFFFF";
    private static final String INK = "#1A1A1A";
    private static final String BODY_INK = "#3A3A3A";
    private static final String ROSE = "#B76E79";
    private static final String BORDER = "#EDE3D8";
    private static final String MUTED = "#8A8078";

    private static final String SERIF = "'Cormorant Garamond', Georgia, 'Times New Roman', serif";
    private static final String SANS = "Montserrat, 'Helvetica Neue', Helvetica, Arial, sans-serif";

    /**
     * Ties the {@code <img src="cid:…">} in the header to the part the sender attaches. Both sides
     * have to agree on this string, so it lives here and the adapter reads it.
     */
    public static final String LOGO_CONTENT_ID = "pilar-estilo-logo";

    /** Classpath location of that part. */
    public static final String LOGO_RESOURCE = "email/pilar-estilo-logo.png";

    private EmailLayout() {
    }

    /** Starts a message. The title becomes the one large line at the top. */
    public static Builder titled(String title) {
        return new Builder(title);
    }

    public static final class Builder {

        private final String title;
        private final List<String> blocks = new ArrayList<>();

        private Builder(String title) {
            this.title = title;
        }

        /** A paragraph of ordinary prose. */
        public Builder paragraph(String text) {
            blocks.add("<p style=\"margin:0 0 16px;font-family:" + SANS + ";font-size:15px;"
                    + "line-height:1.65;color:" + BODY_INK + ";\">" + escape(text) + "</p>");
            return this;
        }

        /**
         * The one fact the message is about — an order reference, an amount — set apart so it can be
         * found without reading the paragraph around it.
         */
        public Builder highlight(String label, String value) {
            blocks.add("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                    + "width=\"100%\" style=\"margin:0 0 20px;\"><tr>"
                    + "<td style=\"background-color:" + CREAM + ";border:1px solid " + BORDER + ";"
                    + "padding:16px 20px;\">"
                    + "<div style=\"font-family:" + SANS + ";font-size:10px;letter-spacing:1.5px;"
                    + "text-transform:uppercase;color:" + MUTED + ";margin-bottom:6px;\">"
                    + escape(label) + "</div>"
                    + "<div style=\"font-family:" + SANS + ";font-size:20px;font-weight:600;"
                    + "color:" + INK + ";\">" + escape(value) + "</div>"
                    + "</td></tr></table>");
            return this;
        }

        /** Label-and-value rows, for things like bank details that get copied one line at a time. */
        public Builder details(List<String[]> rows) {
            StringBuilder table = new StringBuilder(
                    "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                            + "width=\"100%\" style=\"margin:0 0 20px;border:1px solid " + BORDER + ";\">");
            for (String[] row : rows) {
                table.append("<tr>")
                        .append("<td style=\"padding:10px 16px;border-bottom:1px solid ").append(BORDER)
                        .append(";font-family:").append(SANS).append(";font-size:13px;color:").append(MUTED)
                        .append(";white-space:nowrap;\">").append(escape(row[0])).append("</td>")
                        .append("<td style=\"padding:10px 16px;border-bottom:1px solid ").append(BORDER)
                        .append(";font-family:").append(SANS).append(";font-size:14px;color:").append(INK)
                        .append(";font-weight:500;\">").append(escape(row[1])).append("</td>")
                        .append("</tr>");
            }
            table.append("</table>");
            blocks.add(table.toString());
            return this;
        }

        /**
         * A bordered note for a caveat — a deadline, a way out. Not a colour-only signal: it carries
         * its own words, so a client that drops the border loses nothing but decoration.
         */
        public Builder note(String text) {
            blocks.add("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                    + "width=\"100%\" style=\"margin:0 0 20px;\"><tr>"
                    + "<td style=\"border-left:3px solid " + ROSE + ";background-color:" + CREAM + ";"
                    + "padding:14px 18px;font-family:" + SANS + ";font-size:14px;line-height:1.6;"
                    + "color:" + BODY_INK + ";\">" + escape(text) + "</td>"
                    + "</tr></table>");
            return this;
        }

        public String build() {
            return "<!DOCTYPE html><html lang=\"es\"><head>"
                    + "<meta charset=\"utf-8\">"
                    + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                    + "<meta name=\"color-scheme\" content=\"light dark\">"
                    + "<meta name=\"supported-color-schemes\" content=\"light dark\">"
                    + "<title>" + escape(title) + "</title>"
                    + "</head>"
                    + "<body style=\"margin:0;padding:0;background-color:" + CREAM + ";\">"
                    + "<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;\">"
                    + escape(title) + "</div>"
                    + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                    + "width=\"100%\" style=\"background-color:" + CREAM + ";\"><tr>"
                    + "<td align=\"center\" style=\"padding:32px 16px;\">"
                    + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                    + "width=\"600\" style=\"width:100%;max-width:600px;background-color:" + SURFACE + ";"
                    + "border:1px solid " + BORDER + ";\">"
                    + header()
                    + "<tr><td style=\"padding:32px 32px 8px;\">"
                    + "<h1 style=\"margin:0 0 20px;font-family:" + SERIF + ";font-size:28px;"
                    + "font-weight:400;line-height:1.25;color:" + INK + ";\">" + escape(title) + "</h1>"
                    + String.join("", blocks)
                    + "</td></tr>"
                    + footer()
                    + "</table></td></tr></table></body></html>";
        }

        private String header() {
            return "<tr><td style=\"padding:28px 32px 24px;border-bottom:1px solid " + BORDER + ";\">"
                    + "<img src=\"cid:" + LOGO_CONTENT_ID + "\" alt=\"Pilar Estilo\" "
                    + "width=\"220\" height=\"74\" "
                    + "style=\"display:block;width:220px;max-width:60%;height:auto;border:0;"
                    + "font-family:" + SERIF + ";font-size:20px;letter-spacing:3px;color:" + INK + ";\">"
                    + "</td></tr>";
        }

        private String footer() {
            return "<tr><td style=\"padding:20px 32px 28px;border-top:1px solid " + BORDER + ";\">"
                    + "<p style=\"margin:0;font-family:" + SANS + ";font-size:12px;line-height:1.6;"
                    + "color:" + MUTED + ";\">"
                    + "Este mensaje se envió automáticamente desde Pilar Estilo. "
                    + "Si tienes dudas, respóndelo y te contestamos.</p></td></tr>";
        }

        private static String escape(String value) {
            if (value == null) {
                return "";
            }
            return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
        }
    }
}
