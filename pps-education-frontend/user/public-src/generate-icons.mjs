import sharp from "sharp";
import { readFileSync } from "fs";
import { resolve, dirname } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const publicDir = resolve(__dirname, "..", "public");

const standard = readFileSync(resolve(__dirname, "icon-source.svg"));
const maskable = readFileSync(resolve(__dirname, "icon-source-maskable.svg"));

const targets = [
  { src: standard, size: 192, out: "pwa-192.png" },
  { src: standard, size: 512, out: "pwa-512.png" },
  { src: maskable, size: 512, out: "pwa-maskable-512.png" },
  { src: standard, size: 180, out: "apple-touch-icon.png" },
  { src: standard, size: 32, out: "favicon-32.png" },
  { src: standard, size: 16, out: "favicon-16.png" }
];

for (const t of targets) {
  await sharp(t.src, { density: 384 })
    .resize(t.size, t.size)
    .png()
    .toFile(resolve(publicDir, t.out));
  console.log("generated", t.out);
}
