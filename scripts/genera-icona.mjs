// Genera jobpilot.png (256x256) e jobpilot.ico dall'SVG, usando Edge in modalità headless per il rendering.
// Uso: node scripts/genera-icona.mjs
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const icons = resolve("backend/src/main/resources/icons");
const svg = resolve(icons, "jobpilot.svg");
const png = resolve(icons, "jobpilot.png");
const ico = resolve(icons, "jobpilot.ico");

const edge = [
    "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
    "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
].find(existsSync);
if (!edge) throw new Error("Microsoft Edge non trovato");

// Profilo utente separato: così funziona anche se Edge è già aperto
const profilo = resolve(process.env.TEMP ?? icons, "jobpilot-edge-icona");
execFileSync(edge, [
    "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run",
    "--user-data-dir=" + profilo,
    "--window-size=256,256", "--default-background-color=00000000",
    "--screenshot=" + png, "file:///" + svg.replace(/\\/g, "/"),
], { cwd: icons, stdio: "ignore" });
if (!existsSync(png)) throw new Error("Edge non ha prodotto " + png);

// Formato ICO: header + 1 voce che punta a un PNG incorporato (supportato da Windows Vista in poi)
const data = readFileSync(png);
const header = Buffer.alloc(6);
header.writeUInt16LE(0, 0);   // riservato
header.writeUInt16LE(1, 2);   // tipo: icona
header.writeUInt16LE(1, 4);   // numero di immagini
const entry = Buffer.alloc(16);
entry.writeUInt8(0, 0);       // larghezza 256 -> 0
entry.writeUInt8(0, 1);       // altezza 256 -> 0
entry.writeUInt8(0, 2);       // palette
entry.writeUInt8(0, 3);       // riservato
entry.writeUInt16LE(1, 4);    // piani colore
entry.writeUInt16LE(32, 6);   // bit per pixel
entry.writeUInt32LE(data.length, 8);
entry.writeUInt32LE(22, 12);  // offset dei dati
writeFileSync(ico, Buffer.concat([header, entry, data]));
console.log(`Creati ${png} (${data.length} byte) e ${ico}`);
