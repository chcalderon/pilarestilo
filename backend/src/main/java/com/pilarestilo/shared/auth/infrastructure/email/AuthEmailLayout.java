package com.pilarestilo.shared.auth.infrastructure.email;

import java.util.ArrayList;
import java.util.List;

/**
 * The email look, duplicated small on purpose. The password-reset mailer must not depend on
 * {@code notification-service} — recovery has to work when the notification pipeline is down — so
 * it cannot reach the richer {@code EmailLayout} that lives there. This is the subset the reset
 * email needs; a change to the shared look is a two-file edit (here and there).
 *
 * <p>Same rules as its twin: table layout not flexbox, inline styles, 600px, no web fonts, logo
 * via {@code cid:}, explicit colours, no clickable elements.
 */
final class AuthEmailLayout {

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

    static final String LOGO_CONTENT_ID = "pilar-estilo-logo";
    static final String LOGO_RESOURCE = "email/pilar-estilo-logo.png";

    private AuthEmailLayout() {
    }

    static Builder titled(String title) {
        return new Builder(title);
    }

    static final class Builder {

        private final String title;
        private final List<String> blocks = new ArrayList<>();

        private Builder(String title) {
            this.title = title;
        }

        Builder eyebrow(String text) {
            blocks.add("<div style=\"font-family:" + SANS + ";font-size:10px;letter-spacing:2.5px;"
                    + "text-transform:uppercase;color:" + ROSE + ";margin:0 0 10px;\">"
                    + escape(text) + "</div>");
            return this;
        }

        Builder paragraph(String text) {
            blocks.add("<p style=\"margin:0 0 18px;font-family:" + SANS + ";font-size:15px;"
                    + "line-height:1.7;color:" + BODY_INK + ";\">" + escape(text) + "</p>");
            return this;
        }

        Builder code(String value, String caption) {
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

        Builder route(String label, String leadText, String site, String crumbPath) {
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

        Builder note(String label, String text) {
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

        String build() {
            return "<!DOCTYPE html><html lang=\"es\"><head>"
                    + "<meta charset=\"utf-8\">"
                    + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                    + "<meta name=\"color-scheme\" content=\"light dark\">"
                    + "<title>" + escape(title) + "</title>"
                    + "</head>"
                    + "<body style=\"margin:0;padding:0;background-color:" + PARCHMENT + ";\">"
                    + "<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;\">"
                    + escape(title) + "</div>"
                    + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                    + "width=\"100%\" style=\"background-color:" + PARCHMENT + ";\"><tr>"
                    + "<td align=\"center\" style=\"padding:32px 16px;\">"
                    + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                    + "width=\"600\" style=\"width:100%;max-width:600px;background-color:" + SURFACE + ";"
                    + "border:1px solid " + BORDER + ";\">"
                    + header()
                    + "<tr><td style=\"padding:12px 40px 10px;\">"
                    + "<h1 style=\"margin:0 0 14px;font-family:" + SERIF + ";font-size:26px;"
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
                    + "Este correo es automático.</p></td></tr>";
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
