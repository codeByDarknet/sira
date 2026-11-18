<#-- Copie de keycloak.v2 (Keycloak 26.8.1). Ajouts de Sira : une phrase d'aide sur l'application
     d'authentification et la marche à suivre en cas de téléphone perdu (bouton « nouveau QR code »
     envoyé à l'étape sira-code-totp, sans autre vérification). Le QR code n'est montré
     qu'à la première connexion, par login-config-totp.ftl. -->
<#import "template.ftl" as layout>
<#import "field.ftl" as field>
<#import "buttons.ftl" as buttons>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('totp'); section>
<!-- template: login-otp.ftl -->

    <#if section="header">
        ${msg("doLogIn")}
    <#elseif section="form">
        <form id="kc-otp-login-form" class="${properties.kcFormClass!}" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
            <input id="selectedCredentialId" type="hidden" name="selectedCredentialId" value="${otpLogin.selectedCredentialId!''}">
            <#if otpLogin.userOtpCredentials?size gt 1>
                <div class="${properties.kcFormGroupClass!}">
                    <div class="${properties.kcInputWrapperClass!}">
                        <#list otpLogin.userOtpCredentials as otpCredential>
                            <div id="kc-otp-credential-${otpCredential?index}" class="${properties.kcLoginOTPListClass!}"
                                    onclick="toggleOTP(${otpCredential?index}, '${otpCredential.id}')">
                                <span class="${properties.kcLoginOTPListItemHeaderClass!}">
                                    <span class="${properties.kcLoginOTPListItemIconBodyClass!}">
                                      <i class="${properties.kcLoginOTPListItemIconClass!}" aria-hidden="true"></i>
                                    </span>
                                    <span class="${properties.kcLoginOTPListItemTitleClass!}">${otpCredential.userLabel}</span>
                                </span>
                            </div>
                        </#list>
                    </div>
                </div>
            </#if>

            <p class="sira-otp-aide">${msg("siraOtpAide")}</p>

            <@field.input name="otp" label=msg("loginOtpOneTime") autocomplete="one-time-code" fieldName="totp" autofocus=true />

            <@buttons.loginButton />
        </form>

        <#if siraNouveauQr??>
            <form class="sira-otp-perdu" action="${url.loginAction}" method="post">
                <p>${msg("siraOtpPerduDirect")}</p>
                <button class="${properties.kcButtonClass!} ${properties.kcButtonSecondaryClass!} sira-otp-qr" type="submit" name="sira-nouveau-qr" value="oui">${msg("siraOtpNouveauQr")}</button>
            </form>
        <#elseif realm.resetPasswordAllowed>
            <div class="sira-otp-perdu">
                <p>${msg("siraOtpPerduLien")}</p>
                <a class="${properties.kcButtonClass!} ${properties.kcButtonSecondaryClass!} sira-otp-qr" href="${url.loginResetCredentialsUrl}">${msg("siraOtpNouveauQr")}</a>
            </div>
        <#else>
            <p class="sira-otp-perdu">${msg("siraOtpPerdu")}</p>
        </#if>

        <script>
            <#outputformat "JavaScript">
            function toggleOTP(index, value) {
                // select the clicked OTP credential
                document.getElementById("selectedCredentialId").value = value;
                // remove selected class from all OTP credentials
                Array.from(document.getElementsByClassName(${properties.kcLoginOTPListSelectedClass!?c})).map(i => i.classList.remove(${properties.kcLoginOTPListSelectedClass!?c}));
                // add selected class to the clicked OTP credential
                document.getElementById("kc-otp-credential-" + index).classList.add(${properties.kcLoginOTPListSelectedClass!?c});
            }
            </#outputformat>
        </script>
    </#if>
</@layout.registrationLayout>
