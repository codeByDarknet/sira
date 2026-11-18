<#-- Copie de keycloak.v2 (Keycloak 26.8.1), mise en forme de Sira. Page montrée une seule fois, à la
     première connexion d'un administrateur (ou après « scripts/sira totp-reinitialiser ») : Keycloak
     tire une graine au hasard et l'affiche en QR code ; elle n'est enregistrée qu'avec un premier
     code juste. Le QR code et la clé à saisir sont montrés ensemble (pas de bascule de mode). -->
<#import "template.ftl" as layout>
<#import "field.ftl" as field>
<#import "buttons.ftl" as buttons>
<#import "password-commons.ftl" as passwordCommons>
<#import "qr-code.ftl" as qr>
<@layout.registrationLayout displayRequiredFields=false displayMessage=!messagesPerField.existsError('totp','userLabel'); section>
<!-- template: login-config-totp.ftl -->

    <#if section = "header">
        ${msg("loginTotpTitle")}
    <#elseif section = "form">
        <ol id="kc-totp-settings" class="sira-totp-etapes">
            <li>
                <p>${msg("siraTotpEtape1")}</p>
            </li>
            <li>
                <p>${msg("siraTotpEtape2")}</p>
                <div class="sira-totp-qr">
                    <@qr.qrCode id="kc-totp-secret-qr-code" content=totp.totpSecretQrCode alt=msg("siraTotpEtape2")></@qr.qrCode>
                </div>
                <p class="sira-totp-texte">${msg("siraTotpCle")}</p>
                <p class="sira-totp-cle"><span id="kc-totp-secret-key">${totp.totpSecretEncoded}</span></p>
            </li>
            <li>
                <p>${msg("siraTotpEtape3")}</p>
            </li>
        </ol>

        <form action="${url.loginAction}" class="${properties.kcFormClass!}" id="kc-totp-settings-form" method="post" novalidate="novalidate">
            <@field.input name="totp" label=msg("authenticatorCode") autocomplete="one-time-code" fieldName="totp" />
            <input type="hidden" id="totpSecret" name="totpSecret" value="${totp.totpSecret}" />
            <#if mode??><input type="hidden" id="mode" name="mode" value="${mode}"/></#if>

            <@field.input name="userLabel" label=msg("loginTotpDeviceName") value=msg("siraTotpTelephone") fieldName="userLabel" />

            <div class="${properties.kcFormGroupClass!}">
                <@passwordCommons.logoutOtherSessions/>
            </div>

            <#if isAppInitiatedAction??>
                <@buttons.actionGroup horizontal=true>
                    <@buttons.button id="saveTOTPBtn" label="doSubmit" />
                    <@buttons.button id="cancelTOTPBtn" name="cancel-aia" label="doCancel" type="secondary" value="true" />
                </@buttons.actionGroup>
            <#else>
                <@buttons.actionGroup>
                    <@buttons.button id="saveTOTPBtn" label="doSubmit" />
                </@buttons.actionGroup>
            </#if>
        </form>

        <p class="sira-totp-une-fois">${msg("siraTotpUneFois")}</p>
    </#if>
</@layout.registrationLayout>
