<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true; section>
  <#if section = "header">
    ${msg("siraCodeTitre")}
  <#elseif section = "form">
    <form id="sira-code" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
      <div class="${properties.kcFormGroupClass!}">
        <div class="${properties.kcFormGroupLabelClass!}">
          <label for="code" class="${properties.kcFormLabelClass!}">
            <span class="${properties.kcFormLabelTextClass!}">${msg("siraCodeLibelle")}</span>
          </label>
        </div>
        <span class="${properties.kcInputClass!}">
          <input id="code" name="code" type="text" placeholder=" " inputmode="numeric" pattern="[0-9]*" maxlength="6"
                 autocomplete="one-time-code" autofocus/>
        </span>
      </div>
      <div class="${properties.kcFormGroupClass!}">
        <div class="${properties.kcFormActionGroupClass!}">
          <button class="${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!}"
                  type="submit">${msg("siraCodeValider")}</button>
          <button class="${properties.kcButtonSecondaryClass!} ${properties.kcButtonBlockClass!}"
                  type="submit" name="renvoyer" value="1" formnovalidate>${msg("siraCodeRenvoyer")}</button>
        </div>
      </div>
    </form>
    <#if properties.siraTelephone?has_content && !properties.siraTelephone?starts_with("$")>
      <a class="sira-code-telephone" href="${properties.siraTelephone}" target="_blank" rel="noopener">
        <span class="sira-code-telephone__icone" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="5" y="2" width="14" height="20" rx="2"/><path d="M12 18h.01"/></svg>
        </span>
        <span>
          <strong>${msg("siraCodeTelephone")}</strong>
          <span class="sira-code-telephone__aide">${msg("siraCodeTelephoneAide")}</span>
        </span>
      </a>
    </#if>
  </#if>
</@layout.registrationLayout>
