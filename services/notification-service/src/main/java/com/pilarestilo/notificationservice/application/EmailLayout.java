package com.pilarestilo.notificationservice.application;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a message in the shop's own look, in the subset of HTML that email clients agree on.
 *
 * <p>Cormorant Garamond over Montserrat, rose on parchment — the storefront palette, so an email
 * and the site read as one place. Tables not flexbox (Outlook renders through Word), inline styles
 * (Gmail strips {@code <style>}), no web fonts, 600px, explicit colours everywhere. No clickable
 * elements: where the reader must act, the copy names the site and the menu path as plain text.
 */
public final class EmailLayout {

    private static final String PARCHMENT = "#F5F1EB";
    private static final String SURFACE = "#FFFFFF";
    private static final String INK = "#1A1A1A";
    private static final String BODY_INK = "#3A3A3A";
    private static final String ROSE = "#B76E79";
    private static final String ROSE_DEEP = "#8E4F58";
    private static final String DASH = "#C9A9AC";
    private static final String BORDER = "#E7DED0";
    private static final String FOOT_RULE = "#EFE7DC";
    private static final String MUTED = "#8A8078";
    private static final String INSET = "#FBF8F3";

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

    /** One order line in {@link Builder#orderSummary}. */
    public record Line(String name, String variantAndQty, String price) {
    }

    public static final class Builder {

        private final String title;
        private final List<String> blocks = new ArrayList<>();

        private Builder(String title) {
            this.title = title;
        }

        /** The small uppercase line above the heading that says what this message is. */
        public Builder eyebrow(String text) {
            blocks.add("<div style=\"font-family:" + SANS + ";font-size:10px;letter-spacing:2.5px;"
                    + "text-transform:uppercase;color:" + ROSE + ";margin:0 0 10px;\">"
                    + escape(text) + "</div>");
            return this;
        }

        /** A paragraph of ordinary prose. */
        public Builder paragraph(String text) {
            blocks.add("<p style=\"margin:0 0 18px;font-family:" + SANS + ";font-size:15px;"
                    + "line-height:1.7;color:" + BODY_INK + ";\">" + escape(text) + "</p>");
            return this;
        }

        /**
         * The one fact the message is about — an order reference, an amount — set apart so it can
         * be found without reading the paragraph around it.
         */
        public Builder highlight(String label, String value) {
            blocks.add("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                    + "width=\"100%\" style=\"margin:0 0 18px;\"><tr>"
                    + "<td style=\"background-color:" + INSET + ";border:1px solid " + BORDER + ";"
                    + "padding:16px 20px;\">"
                    + "<div style=\"font-family:" + SANS + ";font-size:10px;letter-spacing:1.5px;"
                    + "text-transform:uppercase;color:" + MUTED + ";margin-bottom:6px;\">"
                    + escape(label) + "</div>"
                    + "<div style=\"font-family:" + SANS + ";font-size:20px;font-weight:600;"
                    + "color:" + INK + ";\">" + escape(value) + "</div>"
                    + "</td></tr></table>");
            return this;
        }

        /** A code the reader types elsewhere — a reset code, a coupon. {@code caption} may be null. */
        public Builder code(String value, String caption) {
            StringBuilder b = new StringBuilder(
                    "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                            + "width=\"100%\" style=\"margin:4px 0 18px;\"><tr><td align=\"center\" "
                            + "style=\"border:1px dashed " + DASH + ";background-color:" + INSET + ";"
                            + "padding:22px 20px;\">"
                            + "<div style=\"font-family:" + SERIF + ";font-size:32px;letter-spacing:8px;"
                            + "color:" + ROSE_DEEP + ";\">" + escape(value) + "</div>");
            if (caption != null && !caption.isBlank()) {
                b.append("<div style=\"font-family:").append(SANS).append(";font-size:12px;color:")
                        .append(MUTED).append(";margin-top:8px;\">").append(escape(caption)).append("</div>");
            }
            b.append("</td></tr></table>");
            blocks.add(b.toString());
            return this;
        }

        /**
         * "How to get there", as text — never a link. {@code site} (e.g. {@code pilarestilo.com})
         * is bolded when present; {@code crumbPath} is the menu trail (already contains its own
         * {@code ›} separators).
         */
        public Builder route(String label, String leadText, String site, String crumbPath) {
            String siteSpan = site == null || site.isBlank()
                    ? ""
                    : " <span style=\"font-weight:600;\">" + escape(site) + "</span>,";
            blocks.add("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                    + "width=\"100%\" style=\"margin:4px 0 18px;\"><tr>"
                    + "<td style=\"border:1px solid " + BORDER + ";background-color:" + INSET + ";"
                    + "padding:15px 20px;\">"
                    + "<div style=\"font-family:" + SANS + ";font-size:10px;letter-spacing:1.5px;"
                    + "text-transform:uppercase;color:" + MUTED + ";margin-bottom:5px;\">"
                    + escape(label) + "</div>"
                    + "<p style=\"margin:0;font-family:" + SANS + ";font-size:13px;line-height:1.6;"
                    + "color:" + BODY_INK + ";\">" + escape(leadText) + siteSpan
                    + " <span style=\"color:" + ROSE_DEEP + ";font-weight:600;\">"
                    + escape(crumbPath) + "</span></p>"
                    + "</td></tr></table>");
            return this;
        }

        /**
         * Order reference + date, the item lines, then the totals with a bold Total row. Two-cell
         * rows (not {@code float:right}) so the price column holds up in Gmail and Outlook.
         */
        public Builder orderSummary(String reference, String dateText, List<Line> lines,
                                    List<String[]> totals) {
            String leftCell = "<td style=\"padding:12px 20px;font-family:" + SANS + ";";
            String rightCell = "<td align=\"right\" valign=\"top\" style=\"padding:12px 20px;"
                    + "font-family:" + SANS + ";white-space:nowrap;";

            StringBuilder b = new StringBuilder(
                    "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                            + "width=\"100%\" style=\"margin:0 0 18px;border:1px solid " + BORDER + ";\">");

            b.append("<tr style=\"background-color:").append(INSET).append(";\">")
                    .append(leftCell).append("border-bottom:1px solid ").append(BORDER)
                    .append(";font-size:13px;font-weight:600;color:").append(INK)
                    .append(";letter-spacing:.5px;\">Pedido ").append(escape(reference)).append("</td>")
                    .append(rightCell).append("border-bottom:1px solid ").append(BORDER)
                    .append(";font-size:12px;font-weight:400;color:").append(MUTED).append(";\">")
                    .append(escape(dateText)).append("</td></tr>");

            for (Line line : lines) {
                b.append("<tr>")
                        .append(leftCell).append("border-bottom:1px solid ").append(BORDER)
                        .append(";font-size:13px;color:").append(INK).append(";line-height:1.5;\">")
                        .append(escape(line.name()))
                        .append("<br><span style=\"color:").append(MUTED).append(";font-size:12px;\">")
                        .append(escape(line.variantAndQty())).append("</span></td>")
                        .append(rightCell).append("border-bottom:1px solid ").append(BORDER)
                        .append(";font-size:13px;color:").append(INK).append(";\">")
                        .append(escape(line.price())).append("</td></tr>");
            }

            for (String[] row : totals) {
                boolean isTotal = "Total".equalsIgnoreCase(row[0]);
                String rowStyle = isTotal
                        ? "font-size:15px;font-weight:600;color:" + INK + ";border-top:1px solid " + BORDER
                        : "font-size:12px;font-weight:400;color:" + MUTED;
                String pad = isTotal ? "10px 20px" : "5px 20px";
                b.append("<tr>")
                        .append("<td style=\"padding:").append(pad).append(";font-family:").append(SANS)
                        .append(";").append(rowStyle).append(";\">").append(escape(row[0])).append("</td>")
                        .append("<td align=\"right\" style=\"padding:").append(pad)
                        .append(";font-family:").append(SANS).append(";white-space:nowrap;")
                        .append(rowStyle).append(";\">").append(escape(row[1])).append("</td></tr>");
            }

            b.append("</table>");
            blocks.add(b.toString());
            return this;
        }

        /** Label-and-value rows, for things like bank details that get copied one line at a time. */
        public Builder details(List<String[]> rows) {
            StringBuilder table = new StringBuilder(
                    "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                            + "width=\"100%\" style=\"margin:0 0 18px;border:1px solid " + BORDER + ";\">");
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
        public Builder note(String label, String text) {
            blocks.add("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                    + "width=\"100%\" style=\"margin:0 0 18px;\"><tr>"
                    + "<td style=\"border:1px solid " + BORDER + ";background-color:" + INSET + ";"
                    + "padding:16px 20px;\">"
                    + "<div style=\"font-family:" + SANS + ";font-size:10px;letter-spacing:1.5px;"
                    + "text-transform:uppercase;color:" + MUTED + ";margin-bottom:6px;\">"
                    + escape(label) + "</div>"
                    + "<p style=\"margin:0;font-family:" + SANS + ";font-size:13px;line-height:1.6;"
                    + "color:" + BODY_INK + ";\">" + escape(text) + "</p>"
                    + "</td></tr></table>");
            return this;
        }

        public String build() {
            return "<!DOCTYPE html><html lang=\"es\"><head>"
                    + "<meta charset=\"utf-8\">"
                    + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                    + "<meta name=\"color-scheme\" content=\"light dark\">"
                    + "<meta name=\"supported-color-schemes\" content=\"light dark\">"
                    + "<style>@media (prefers-color-scheme: dark){"
                    + ".pe-bg{background-color:#211E1B!important}"
                    + ".pe-card{background-color:#2A2622!important;border-color:#3A352F!important}"
                    + ".pe-ink{color:#F2ECE3!important}.pe-body{color:#D9D1C5!important}}"
                    + "</style>"
                    + "<title>" + escape(title) + "</title>"
                    + "</head>"
                    + "<body class=\"pe-bg\" style=\"margin:0;padding:0;background-color:" + PARCHMENT + ";\">"
                    + "<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;\">"
                    + escape(title) + "</div>"
                    + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                    + "width=\"100%\" class=\"pe-bg\" style=\"background-color:" + PARCHMENT + ";\"><tr>"
                    + "<td align=\"center\" style=\"padding:32px 16px;\">"
                    + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                    + "width=\"600\" class=\"pe-card\" style=\"width:100%;max-width:600px;"
                    + "background-color:" + SURFACE + ";border:1px solid " + BORDER + ";\">"
                    + header()
                    + "<tr><td style=\"padding:12px 40px 10px;\">"
                    + "<h1 class=\"pe-ink\" style=\"margin:0 0 14px;font-family:" + SERIF + ";font-size:26px;"
                    + "font-weight:300;line-height:1.25;color:" + INK + ";\">" + escape(title) + "</h1>"
                    + String.join("", blocks)
                    + "</td></tr>"
                    + footer()
                    + "</table></td></tr></table></body></html>";
        }

        private String header() {
            return "<tr><td align=\"center\" style=\"padding:34px 40px 20px;\">"
                    + "<img src=\"cid:" + LOGO_CONTENT_ID + "\" alt=\"Pilar Estilo\" "
                    + "width=\"200\" height=\"67\" "
                    + "style=\"display:block;width:200px;max-width:56%;height:auto;border:0;"
                    + "font-family:" + SERIF + ";font-size:20px;letter-spacing:3px;color:" + INK + ";\">"
                    + "<div style=\"height:1px;width:44px;background-color:" + DASH + ";margin:15px auto 0;\"></div>"
                    + "</td></tr>";
        }

        private String footer() {
            return "<tr><td align=\"center\" style=\"padding:22px 40px 28px;border-top:1px solid "
                    + FOOT_RULE + ";\">"
                    + "<div style=\"font-family:" + SERIF + ";font-size:14px;letter-spacing:3px;color:"
                    + MUTED + ";\">PILAR ESTILO</div>"
                    + "<p style=\"margin:8px 0 0;font-family:" + SANS + ";font-size:11px;line-height:1.7;"
                    + "color:" + MUTED + ";\">Valle de Aconcagua, Chile &nbsp;&middot;&nbsp; "
                    + "¿Dudas? Responde este correo y te contestamos.</p></td></tr>";
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
