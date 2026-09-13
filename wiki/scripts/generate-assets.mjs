import { readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const here = path.dirname(fileURLToPath(import.meta.url))
const wikiRoot = path.resolve(here, '..')
const encodedPath = path.join(wikiRoot, 'public', 'plugin-icon-transparent.webp')
const outputPath = path.join(wikiRoot, 'public', 'plugin-icon-live.webp')

const encoded = (await readFile(encodedPath, 'utf8')).trim()
const bytes = Buffer.from(encoded, 'base64')

if (bytes.length < 10000) {
  throw new Error('Decoded RelicPrison icon is unexpectedly small; refusing to publish it.')
}

await writeFile(outputPath, bytes)
console.log(`Generated ${path.relative(wikiRoot, outputPath)} (${bytes.length} bytes)`)
