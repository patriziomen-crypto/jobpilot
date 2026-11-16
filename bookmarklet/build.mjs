// Genera salva-in-jobpilot.min.txt: la versione "javascript:..." da incollare come indirizzo del segnalibro.
// Uso: node bookmarklet/build.mjs
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const dir = dirname(fileURLToPath(import.meta.url));
const src = readFileSync(join(dir, "salva-in-jobpilot.js"), "utf8")
    .split("\n")
    .filter((line) => !line.trim().startsWith("//"))
    .join("\n")
    .replace(/\s+/g, " ")
    .trim();

const out = "javascript:" + encodeURIComponent(src);
writeFileSync(join(dir, "salva-in-jobpilot.min.txt"), out, "utf8");
console.log(`Scritto salva-in-jobpilot.min.txt (${out.length} caratteri)`);
