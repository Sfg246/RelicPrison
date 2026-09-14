import { mkdir, readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'

const wikiRoot = process.cwd()
const repoRoot = path.resolve(wikiRoot, '..')

async function read(relativePath) {
  return readFile(path.join(repoRoot, relativePath), 'utf8')
}

const changelog = await read('CHANGELOG.md')
await mkdir(path.join(wikiRoot, 'changelog'), { recursive: true })
await writeFile(
  path.join(wikiRoot, 'changelog', 'index.md'),
  `# Changelog\n\n> This page is generated from the repository root \`CHANGELOG.md\` during every documentation build. Do not edit the generated history here.\n\n${changelog.replace(/^# Changelog\s*/u, '')}`,
  'utf8'
)

const limitationSources = [
  'KNOWN-LIMITATIONS-1.0.0-rc6-stage6.md',
  'KNOWN-LIMITATIONS-1.0.0-rc6-stage6-command-ux.md'
]

let limitations = '# Known Limitations\n\n'
limitations += '> Generated from the current RC limitation reports during every documentation build. Verify this page before production use.\n\n'
for (const source of limitationSources) {
  const body = await read(source)
  limitations += `## ${source.replace(/^KNOWN-LIMITATIONS-/u, '').replace(/\.md$/u, '')}\n\n`
  limitations += body.replace(/^# .*?\n+/u, '')
  limitations += `\n\n_Source: \`${source}\`_\n\n`
}
limitations += '## What this means\n\nThe current documented build is a release candidate for controlled staging. A limitation marked here should be treated as unverified or intentionally incomplete until the repository staging matrix proves it on the real server/client/integration combination you intend to run.\n'

await writeFile(path.join(wikiRoot, 'known-limitations.md'), limitations, 'utf8')

console.log('Generated changelog and known-limitations pages.')
