package bf.sira.commun.agents;

import bf.sira.commun.agents.AnnuaireKeycloak.Compte;
import bf.sira.commun.agents.DepotRoles.Role;
import bf.sira.commun.donnees.Page;
import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.commun.erreurs.SaisieRefusee;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Les agents de l'administration et leurs rôles. Chaque route est protégée par un droit (voir
 * {@link RoutesAgents}) ; ici, on garde en plus l'agent connecté de se retirer lui-même l'accès.
 */
@RestController
class AgentsControleur {

    private static final String NOM = "[\\p{L}\\p{M} '’.-]{1,60}";
    private static final SecureRandom HASARD = new SecureRandom();

    record Utilisateur(
            String id,
            String identifiant,
            String prenom,
            String nom,
            String email,
            boolean actif,
            boolean premiereConnexion,
            Instant creeLe,
            String role,
            String libelleRole) {}

    record SaisieUtilisateur(
            @NotBlank(message = "Saisissez le prénom.")
                    @Pattern(regexp = NOM, message = "Lettres, espaces, tirets et apostrophes.")
                    String prenom,
            @NotBlank(message = "Saisissez le nom.")
                    @Pattern(regexp = NOM, message = "Lettres, espaces, tirets et apostrophes.")
                    String nom,
            @NotBlank(message = "Saisissez l’adresse électronique.")
                    @Email(
                            message =
                                    "Saisissez une adresse valide. Exemple : awa.ouedraogo@exemple.bf")
                    @Size(max = 64, message = "64 caractères au plus.")
                    String email,
            @NotBlank(message = "Choisissez un rôle.") String role,
            Boolean actif) {}

    record Creation(Utilisateur utilisateur, boolean courrielEnvoye) {}

    record SaisieRole(
            @NotBlank(message = "Saisissez le nom du rôle.")
                    @Size(max = 60, message = "60 caractères au plus.")
                    String libelle,
            @Size(max = 300, message = "300 caractères au plus.") String description,
            @NotNull(message = "Choisissez au moins un droit.")
                    @Size(min = 1, message = "Choisissez au moins un droit.")
                    Set<String> permissions) {}

    private final AnnuaireKeycloak annuaire;
    private final DepotRoles roles;
    private final AccesAgents acces;
    private final CourrielIdentifiants courriel;

    AgentsControleur(
            AnnuaireKeycloak annuaire,
            DepotRoles roles,
            AccesAgents acces,
            CourrielIdentifiants courriel) {
        this.annuaire = annuaire;
        this.roles = roles;
        this.acces = acces;
        this.courriel = courriel;
    }

    // --- Utilisateurs --------------------------------------------------------------------------

    @GetMapping("/api/utilisateurs")
    Page<Utilisateur> lister(
            @RequestParam(defaultValue = "1") @Min(1) @Max(10_000) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int taille,
            @RequestParam(required = false) @Size(max = 80) String recherche,
            @RequestParam(required = false) @Size(max = 60) String role) {
        String cherche = normaliser(recherche == null ? "" : recherche);
        List<Compte> comptes = annuaire.lister();
        Map<String, String> affectations =
                roles.rolesDe(comptes.stream().map(Compte::identifiant).toList());
        Map<String, String> libelles = libellesRoles();
        List<Utilisateur> tous =
                comptes.stream()
                        .map(c -> utilisateur(c, affectations.get(c.identifiant()), libelles))
                        .filter(u ->
                                cherche.isEmpty()
                                        || normaliser(u.prenom() + " " + u.nom() + " " + u.email())
                                                .contains(cherche))
                        .filter(u -> role == null || role.isBlank() || role.equals(u.role()))
                        .toList();
        int debut = Math.min((page - 1) * taille, tous.size());
        return new Page<>(
                tous.subList(debut, Math.min(debut + taille, tous.size())),
                page,
                taille,
                tous.size());
    }

    @GetMapping("/api/utilisateurs/{id}")
    Utilisateur lire(@PathVariable String id) {
        Compte compte = annuaire.lire(id);
        return utilisateur(
                compte, roles.roleDe(compte.identifiant()).orElse(null), libellesRoles());
    }

    @PostMapping("/api/utilisateurs")
    @ResponseStatus(HttpStatus.CREATED)
    Creation creer(@Valid @RequestBody SaisieUtilisateur saisie) {
        exigerRole(saisie.role());
        String email = saisie.email().strip().toLowerCase(Locale.ROOT);
        String motDePasse = motDePasse();
        String id =
                annuaire.creer(saisie.prenom().strip(), saisie.nom().strip(), email, motDePasse);
        roles.affecter(email, saisie.role());
        boolean envoye =
                courriel.envoyer(
                        saisie.prenom().strip(), saisie.nom().strip(), email, motDePasse, true);
        return new Creation(lire(id), envoye);
    }

    @PutMapping("/api/utilisateurs/{id}")
    Utilisateur modifier(
            @PathVariable String id,
            @Valid @RequestBody SaisieUtilisateur saisie,
            Authentication connecte) {
        exigerRole(saisie.role());
        Compte avant = annuaire.lire(id);
        boolean actif = saisie.actif() == null || saisie.actif();
        if (avant.identifiant().equals(acces.identifiant(connecte))
                && (!actif
                        || !saisie.role().equals(roles.roleDe(avant.identifiant()).orElse(null)))) {
            throw new SaisieRefusee(
                    "role",
                    "Vous ne pouvez pas changer votre propre rôle ni désactiver votre compte.");
        }
        annuaire.modifier(
                id,
                saisie.prenom().strip(),
                saisie.nom().strip(),
                saisie.email().strip().toLowerCase(Locale.ROOT),
                actif);
        roles.affecter(avant.identifiant(), saisie.role());
        return lire(id);
    }

    /** Nouveau mot de passe provisoire, envoyé par courriel. */
    @PostMapping("/api/utilisateurs/{id}/reinitialisation")
    Map<String, Boolean> reinitialiser(@PathVariable String id) {
        Compte compte = annuaire.lire(id);
        String motDePasse = motDePasse();
        annuaire.reinitialiser(id, motDePasse);
        boolean envoye =
                courriel.envoyer(compte.prenom(), compte.nom(), compte.email(), motDePasse, false);
        return Map.of("courriel_envoye", envoye);
    }

    @DeleteMapping("/api/utilisateurs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void supprimer(@PathVariable String id, Authentication connecte) {
        Compte compte = annuaire.lire(id);
        if (compte.identifiant().equals(acces.identifiant(connecte))) {
            throw ErreurMetier.conflit("Vous ne pouvez pas supprimer votre propre compte.");
        }
        annuaire.supprimer(id);
        roles.retirer(compte.identifiant());
    }

    // --- Rôles et droits -------------------------------------------------------------------------

    @GetMapping("/api/permissions")
    List<Permission> permissions() {
        return acces.catalogue();
    }

    @GetMapping("/api/roles")
    List<Role> roles() {
        return roles.lister();
    }

    @GetMapping("/api/roles/{id}")
    Role role(@PathVariable UUID id) {
        return roles.lireParId(id).orElseThrow(ErreurMetier::introuvable);
    }

    @PostMapping("/api/roles")
    @ResponseStatus(HttpStatus.CREATED)
    Role creerRole(@Valid @RequestBody SaisieRole saisie) {
        Set<String> permissions = permissionsConnues(saisie.permissions());
        String code = code(saisie.libelle());
        if (!roles.creer(code, saisie.libelle().strip(), description(saisie), permissions, false)) {
            throw new SaisieRefusee("libelle", "Un rôle porte déjà ce nom.");
        }
        return roles.lire(code).orElseThrow(ErreurMetier::introuvable);
    }

    @PutMapping("/api/roles/{id}")
    Role modifierRole(@PathVariable UUID id, @Valid @RequestBody SaisieRole saisie) {
        Role existant = role(id);
        String code = existant.code();
        if (existant.systeme() && code.equals(AmorcageAgents.ADMINISTRATEUR)) {
            throw ErreurMetier.conflit("Le rôle Administrateur garde tous les droits.");
        }
        roles.modifier(
                code,
                saisie.libelle().strip(),
                description(saisie),
                permissionsConnues(saisie.permissions()));
        return role(id);
    }

    @DeleteMapping("/api/roles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void supprimerRole(@PathVariable UUID id) {
        Role existant = role(id);
        String code = existant.code();
        if (existant.systeme()) {
            throw ErreurMetier.conflit("Ce rôle est fourni avec l’application : il reste.");
        }
        if (existant.utilisateurs() > 0) {
            throw ErreurMetier.conflit(
                    "Ce rôle est encore attribué. Changez d’abord le rôle de ces utilisateurs.");
        }
        roles.supprimer(code);
    }

    // --- Aides
    // -------------------------------------------------------------------------------------

    private Utilisateur utilisateur(Compte c, String role, Map<String, String> libelles) {
        return new Utilisateur(
                c.id(),
                c.identifiant(),
                c.prenom(),
                c.nom(),
                c.email(),
                c.actif(),
                c.premiereConnexion(),
                c.creeLe(),
                role,
                role == null ? null : libelles.get(role));
    }

    private Map<String, String> libellesRoles() {
        return roles.lister().stream().collect(Collectors.toMap(Role::code, Role::libelle));
    }

    private void exigerRole(String code) {
        if (roles.lire(code).isEmpty()) {
            throw new SaisieRefusee("role", "Choisissez un rôle de la liste.");
        }
    }

    private Set<String> permissionsConnues(Set<String> demandees) {
        Set<String> connues =
                acces.catalogue().stream().map(Permission::code).collect(Collectors.toSet());
        if (!connues.containsAll(demandees)) {
            throw new SaisieRefusee("permissions", "Un des droits choisis n’existe pas.");
        }
        return new LinkedHashSet<>(demandees);
    }

    private static String description(SaisieRole saisie) {
        return saisie.description() == null ? "" : saisie.description().strip();
    }

    /** Code d'un rôle tiré de son nom : « Chef de service » donne {@code chef-de-service}. */
    private static String code(String libelle) {
        String code = normaliser(libelle).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (code.isEmpty()) {
            throw new SaisieRefusee("libelle", "Le nom doit contenir des lettres.");
        }
        return code.length() > 40 ? code.substring(0, 40) : code;
    }

    private static final String MAJUSCULES = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String MINUSCULES = "abcdefghijkmnopqrstuvwxyz";
    private static final String CHIFFRES = "23456789";
    private static final String SIGNES = "@#%+=!?";

    /**
     * Mot de passe provisoire conforme à la politique de l'annuaire (12 caractères au moins, une
     * majuscule, une minuscule, un chiffre, un signe), sans caractère ambigu (0/O, 1/l/I).
     */
    private static String motDePasse() {
        java.util.List<Character> lettres = new java.util.ArrayList<>();
        for (String famille : List.of(MAJUSCULES, MINUSCULES, CHIFFRES, SIGNES)) {
            lettres.add(famille.charAt(HASARD.nextInt(famille.length())));
        }
        String tous = MAJUSCULES + MINUSCULES + CHIFFRES;
        while (lettres.size() < 14) {
            lettres.add(tous.charAt(HASARD.nextInt(tous.length())));
        }
        java.util.Collections.shuffle(lettres, HASARD);
        StringBuilder mot = new StringBuilder();
        lettres.forEach(mot::append);
        return mot.toString();
    }

    private static String normaliser(String texte) {
        return Normalizer.normalize(texte, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .strip();
    }
}
