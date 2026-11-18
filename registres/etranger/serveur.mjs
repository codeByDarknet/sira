// « Registre d'une autre équipe » : volontairement différent des registres simulés de Sira.
// Autre pile (Node, sans dépendance), autres noms de champs (anglais, imbriqués), autre forme
// d'URL, HTTP simple protégé par une clé d'API. Sert à prouver qu'on raccorde un registre
// existant sans le modifier : seul un fichier de raccordement est écrit côté Sira.
//
//   GET /registry/v2/citizens/lookup?nin=<identifiant>   (en-tête X-Api-Key obligatoire)
//     200 {"data": {"citizen": {...}}}   404 {"error": "not_found"}   401 {"error": "unauthorized"}
//   GET /healthz                                          200 {"status": "up"}
//
// Variables : PORT (8080), CLE_API_FICHIER (fichier contenant la clé), DONNEES (citizens.json).
import { createServer } from "node:http";
import { readFileSync } from "node:fs";
import { timingSafeEqual, createHash } from "node:crypto";

const port = Number(process.env.PORT ?? 8080);
const cle = readFileSync(process.env.CLE_API_FICHIER ?? "/run/secrets/cle-api", "utf8").trim();
if (cle.length < 16) throw new Error("clé d'API absente ou trop courte");
const donnees = JSON.parse(readFileSync(process.env.DONNEES ?? new URL("./citizens.json", import.meta.url), "utf8"));
const citoyens = new Map(donnees.citizens.map((c) => [c.nin, c]));

// Comparaison à temps constant fournie par Node (empreintes de même longueur).
const empreinte = (texte) => createHash("sha256").update(texte).digest();
const cleValide = (recue) => typeof recue === "string" && timingSafeEqual(empreinte(recue), empreinte(cle));

function repondre(res, statut, corps) {
  res.writeHead(statut, { "content-type": "application/json", "cache-control": "no-store" });
  res.end(JSON.stringify(corps));
}

createServer((req, res) => {
  const url = new URL(req.url, "http://registre");
  if (req.method === "GET" && url.pathname === "/healthz") return repondre(res, 200, { status: "up" });
  if (req.method !== "GET" || url.pathname !== "/registry/v2/citizens/lookup") {
    return repondre(res, 404, { error: "not_found" });
  }
  if (!cleValide(req.headers["x-api-key"])) return repondre(res, 401, { error: "unauthorized" });
  const citoyen = citoyens.get(url.searchParams.get("nin") ?? "");
  console.log(`${new Date().toISOString()} lookup ${citoyen ? 200 : 404}`); // jamais l'identifiant
  if (!citoyen) return repondre(res, 404, { error: "not_found" });
  repondre(res, 200, { data: { citizen: citoyen }, meta: { source: "registre-etranger", version: "2.3.1" } });
}).listen(port, () => console.log(`registre étranger à l'écoute sur ${port} (${citoyens.size} citoyens)`));
