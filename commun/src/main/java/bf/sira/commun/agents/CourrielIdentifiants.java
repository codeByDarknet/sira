package bf.sira.commun.agents;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.util.HtmlUtils;

/**
 * Le courriel envoyé à un agent dont le compte vient d'être créé (ou dont le mot de passe vient
 * d'être réinitialisé) : identifiant, mot de passe provisoire et marche à suivre. Le logo est joint
 * dans le message (pièce « inline ») : il s'affiche sans aller chercher d'image sur Internet.
 */
public class CourrielIdentifiants {

    private static final Logger JOURNAL = LoggerFactory.getLogger(CourrielIdentifiants.class);
    private static final String LOGO = "logo-sira";

    private final ObjectProvider<JavaMailSender> envois;
    private final ProprietesAgents proprietes;

    /**
     * @param envois le serveur de messagerie ({@code spring.mail.*}) ; absent, rien n'est envoyé
     */
    public CourrielIdentifiants(
            ObjectProvider<JavaMailSender> envois, ProprietesAgents proprietes) {
        this.envois = envois;
        this.proprietes = proprietes;
    }

    /**
     * @return vrai si le serveur de messagerie a accepté le message
     */
    public boolean envoyer(
            String prenom, String nom, String email, String motDePasse, boolean nouveauCompte) {
        JavaMailSender envoi = envois.getIfAvailable();
        if (envoi == null) {
            JOURNAL.warn("courriel d'identifiants non envoyé : aucun serveur de messagerie réglé");
            return false;
        }
        try {
            MimeMessage message = envoi.createMimeMessage();
            MimeMessageHelper aide =
                    new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            aide.setFrom(proprietes.expediteur());
            aide.setTo(email);
            aide.setSubject(
                    nouveauCompte
                            ? "Votre accès à " + proprietes.nom() + " sur Sira"
                            : "Votre nouveau mot de passe provisoire Sira");
            aide.setText(
                    texte(prenom, email, motDePasse, nouveauCompte),
                    html(prenom, nom, email, motDePasse, nouveauCompte));
            aide.addInline(
                    LOGO,
                    new ClassPathResource("bf/sira/commun/agents/logo-sira.png"),
                    "image/png");
            envoi.send(message);
            return true;
        } catch (MessagingException | MailException e) {
            JOURNAL.warn("courriel d'identifiants non envoyé : {}", e.getMessage());
            return false;
        }
    }

    private String texte(String prenom, String email, String motDePasse, boolean nouveau) {
        return """
        Bonjour %s,

        %s

        Adresse de connexion : %s
        Identifiant : %s
        Mot de passe provisoire : %s

        À la première connexion, vous choisirez votre propre mot de passe, puis vous
        enregistrerez Sira dans Google Authenticator (ou une application semblable).

        Ce message est personnel : ne le transférez pas.
        L'équipe Sira
        """
                .formatted(
                        prenom,
                        nouveau
                                ? "Un compte vient de vous être ouvert dans l'espace « "
                                        + proprietes.nom()
                                        + " »."
                                : "Votre mot de passe vient d'être réinitialisé.",
                        proprietes.urlApplication(),
                        email,
                        motDePasse);
    }

    private String html(
            String prenom, String nom, String email, String motDePasse, boolean nouveau) {
        String e = HtmlUtils.htmlEscape(email);
        return """
        <!doctype html>
        <html lang="fr"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1"></head>
        <body style="margin:0;padding:0;background:#f4f6f8;">
        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
               style="background:#f4f6f8;padding:32px 12px;font-family:'Segoe UI',Helvetica,Arial,sans-serif;">
        <tr><td align="center">
        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
               style="max-width:560px;background:#ffffff;border-radius:14px;overflow:hidden;border:1px solid #e6eaee;">
          <tr><td align="center" style="padding:32px 32px 8px;">
            <img src="cid:%s" width="88" height="87" alt="Sira" style="display:block;border:0;">
          </td></tr>
          <tr><td style="padding:8px 40px 0;color:#1d2733;">
            <h1 style="margin:16px 0 8px;font-size:22px;font-weight:700;color:#1d2733;">Bonjour %s,</h1>
            <p style="margin:0 0 20px;font-size:15px;line-height:1.6;color:#4a5563;">%s</p>
          </td></tr>
          <tr><td style="padding:0 40px;">
            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                   style="background:#f5f9fc;border-radius:10px;">
              <tr><td style="padding:18px 22px 6px;font-size:12px;letter-spacing:.06em;text-transform:uppercase;color:#6b7684;">Identifiant</td></tr>
              <tr><td style="padding:0 22px 14px;font-size:16px;font-weight:600;color:#1d2733;">%s</td></tr>
              <tr><td style="padding:4px 22px 6px;font-size:12px;letter-spacing:.06em;text-transform:uppercase;color:#6b7684;">Mot de passe provisoire</td></tr>
              <tr><td style="padding:0 22px 18px;font-size:18px;font-weight:700;letter-spacing:.08em;color:#1d2733;font-family:Consolas,'Courier New',monospace;">%s</td></tr>
            </table>
          </td></tr>
          <tr><td align="center" style="padding:26px 40px 8px;">
            <a href="%s" style="display:inline-block;background:#25AAE2;color:#ffffff;text-decoration:none;font-weight:700;font-size:15px;padding:13px 30px;border-radius:10px;">Se connecter</a>
          </td></tr>
          <tr><td style="padding:18px 40px 4px;color:#1d2733;">
            <p style="margin:0 0 10px;font-size:14px;font-weight:700;">À la première connexion</p>
            <table role="presentation" cellpadding="0" cellspacing="0" style="font-size:14px;line-height:1.55;color:#4a5563;">
              <tr><td valign="top" style="padding:0 10px 8px 0;color:#25AAE2;font-weight:700;">1</td>
                  <td style="padding-bottom:8px;">Choisissez votre propre mot de passe.</td></tr>
              <tr><td valign="top" style="padding:0 10px 8px 0;color:#25AAE2;font-weight:700;">2</td>
                  <td style="padding-bottom:8px;">Scannez le code affiché avec Google Authenticator (ou une application semblable), puis saisissez le code à six chiffres.</td></tr>
            </table>
          </td></tr>
          <tr><td style="padding:14px 40px 30px;">
            <p style="margin:0;font-size:13px;line-height:1.55;color:#6b7684;">Ce message est personnel : ne le transférez pas. Si vous n’attendiez pas ce compte, ignorez-le et prévenez votre administration.</p>
          </td></tr>
          <tr><td style="padding:16px 40px;background:#fafbfc;border-top:1px solid #eef1f4;font-size:12px;color:#8a94a1;">
            %s · Sira, «&nbsp;Dites-le-nous une fois&nbsp;»
          </td></tr>
        </table>
        </td></tr></table>
        </body></html>
        """
                .formatted(
                        LOGO,
                        HtmlUtils.htmlEscape(prenom),
                        nouveau
                                ? "Un compte vient de vous être ouvert dans l’espace <strong>"
                                        + HtmlUtils.htmlEscape(proprietes.nom())
                                        + "</strong>. Voici vos identifiants."
                                : "Votre mot de passe vient d’être réinitialisé. Voici votre"
                                        + " nouveau mot de passe provisoire.",
                        e,
                        HtmlUtils.htmlEscape(motDePasse),
                        HtmlUtils.htmlEscape(proprietes.urlApplication()),
                        HtmlUtils.htmlEscape(proprietes.nom()));
    }
}
