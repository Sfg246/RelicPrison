import { promises as fs } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import YAML from 'yaml'

const here = path.dirname(fileURLToPath(import.meta.url))
const wikiRoot = path.resolve(here, '..')
const resourcesRoot = path.resolve(wikiRoot, '../src/main/resources')
const outputRoot = path.resolve(wikiRoot, 'generated/config')
const repoBase = 'https://github.com/Sfg246/RelicPrison/blob/main/src/main/resources/'

const SPECIAL = {
  'storage.yml:type': ['SQLITE for a single server; MYSQL for deliberately shared multi-server data.', 'Changing database type without a migration plan can make existing data appear missing.'],
  'storage.yml:mysql.password': ['Use a real secret when MySQL is enabled.', 'Never commit a production database password to Git.'],
  'config.yml:logging.debug': ['false except during targeted troubleshooting.', 'Debug logging can become noisy on active servers.'],
  'config.yml:selection.maximum-volume': ['Keep the packaged limit until reset performance is measured on staging.', 'Very large selections increase reset work and operational risk.'],
  'config.yml:mine-resets.max-concurrent-mines': ['1 until staging proves the server can safely reset more in parallel.', 'Higher concurrency can increase MSPT and block-placement pressure.'],
  'config.yml:mine-resets.pause-above-mspt': ['Keep the packaged default unless profiling proves a different threshold is safer.', 'Setting this too high can let resets compete with gameplay during lag.'],
  'config.yml:mine-resets.resume-below-mspt': ['Keep lower than pause-above-mspt.', 'A bad hysteresis pair can cause repeated pause/resume behavior.'],
  'config.yml:features.update-checker': ['false for controlled RC staging unless you intentionally enable update checks.', 'External update checks add network dependency and should not replace release review.'],
  'integrations.yml:itemsadder.enabled': ['false until ItemsAdder behavior is verified on your staging server.', 'Enabling it without ItemsAdder or valid custom IDs can make custom content unavailable.'],
  'integrations.yml:advanced-enchantments.enabled': ['false until AdvancedEnchantments is installed and its supported mining behavior is staged.', 'Bulk-mining integrations can affect drops, durability, fortune, and replay behavior.'],
  'integrations.yml:combat.provider': ['none unless you have configured a supported combat-tag provider.', 'An incorrect provider can change teleport restrictions unexpectedly.'],
  'backups.yml:retention-count': ['Keep enough verified restore points for your operating schedule and available disk.', 'Too little retention removes rollback options; too much can consume disk.']
}

function slug(file) {
  return file.replaceAll('\\', '/').replace(/\.ya?ml$/i, '').replaceAll('/', '-')
}

function pretty(file) {
  return file.replaceAll('\\', '/')
}

async function walk(dir, prefix = '') {
  const out = []
  for (const entry of await fs.readdir(dir, { withFileTypes: true })) {
    const rel = prefix ? `${prefix}/${entry.name}` : entry.name
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) out.push(...await walk(full, rel))
    else if (/\.ya?ml$/i.test(entry.name)) out.push(rel)
  }
  return out.sort((a, b) => a.localeCompare(b))
}

function flatten(value, prefix = '', rows = []) {
  if (Array.isArray(value)) {
    if (value.length === 0 || value.every(v => v === null || typeof v !== 'object')) {
      rows.push([prefix || '(root)', 'list', value])
    } else {
      value.forEach((v, i) => flatten(v, `${prefix}[${i}]`, rows))
    }
    return rows
  }
  if (value && typeof value === 'object') {
    const entries = Object.entries(value)
    if (entries.length === 0) rows.push([prefix || '(root)', 'object', value])
    else for (const [key, child] of entries) flatten(child, prefix ? `${prefix}.${key}` : key, rows)
    return rows
  }
  rows.push([prefix || '(root)', value === null ? 'null' : typeof value, value])
  return rows
}

function escapeCell(value) {
  const raw = typeof value === 'string' ? value : JSON.stringify(value)
  const compact = (raw ?? 'null').replaceAll('\n', '\\n')
  const shown = compact.length > 220 ? `${compact.slice(0, 217)}...` : compact
  return `\`${shown.replaceAll('`', '\\`').replaceAll('|', '\\|')}\``
}

function describe(file, key, value) {
  const leaf = key.split('.').at(-1).replace(/\[\d+\]$/, '')
  if (leaf === 'enabled' || leaf.endsWith('-enabled')) return 'Turns this feature or behavior on or off.'
  if (leaf.includes('timeout')) return 'Controls how long RelicPrison waits before the operation/session is treated as timed out.'
  if (leaf.includes('interval')) return 'Controls how often the related task or action is scheduled.'
  if (leaf.includes('limit') || leaf.includes('maximum') || leaf.includes('max-')) return 'Sets a safety or capacity limit for the related system.'
  if (leaf.includes('minimum') || leaf.includes('min-')) return 'Sets the lower bound used by the related system.'
  if (leaf.includes('multiplier')) return 'Changes the multiplier used by the related economy or progression calculation.'
  if (leaf.includes('cost') || leaf.includes('price')) return 'Sets an economy amount used by the related action.'
  if (leaf.includes('permission')) return 'Names the permission node checked for the related action.'
  if (leaf.includes('sound')) return 'Selects the Minecraft sound used for this notification or GUI action.'
  if (leaf.includes('material') || leaf === 'block') return 'Selects the Minecraft/custom material or block identifier used here.'
  if (leaf.includes('message') || leaf.includes('description') || leaf === 'motd') return 'Controls player-facing text for this part of the plugin.'
  if (leaf.includes('seconds') || leaf.includes('millis')) return 'Controls a duration or timing value for this behavior.'
  if (leaf === 'type' && file === 'storage.yml') return 'Chooses the persistence backend used by RelicPrison.'
  if (leaf === 'weight') return 'Controls this entry’s relative share in a weighted selection such as mine composition.'
  if (leaf === 'x' || leaf === 'y' || leaf === 'z') return 'Stores one coordinate of a configured world position or cuboid.'
  if (leaf === 'uuid' || leaf.endsWith('-id') || leaf === 'id') return 'Stores the stable identifier used to reference this object.'
  return `Configures \`${leaf}\` for this section of ${file}.`
}

function advice(file, key) {
  const special = SPECIAL[`${file}:${key}`]
  if (special) return special
  if (key.includes('password')) return ['Never use the packaged placeholder for a real MySQL server.', 'Treat this value as a secret and do not publish it.']
  if (key.includes('.commands.') || key.endsWith('.commands')) return ['Start empty, then add only commands you have tested for duplicate/retry behavior.', 'Third-party commands cannot participate in RelicPrison’s SQL transaction and may not be exactly-once.']
  if (key.includes('minimum.') || key.includes('maximum.') || key.includes('.world.')) return ['Use values created by the mine tools unless you intentionally edit offline.', 'Wrong world/coordinates can target the wrong physical area.']
  if (key.endsWith('.weight')) return ['Keep composition weights understandable and normalize after editing.', 'Bad or missing weights can produce an invalid or unintended composition.']
  if (key.endsWith('.enabled') || key.endsWith('-enabled')) return ['Begin with the packaged default, then enable features one at a time on staging.', 'Enabling a dependency-backed feature before its dependency is ready can fail validation or behavior checks.']
  return ['Keep the packaged default for the first successful staging run; tune only with a specific reason.', 'Wrong type or unsupported value can fail validation/reload or change gameplay unexpectedly.']
}

function sectionHint(file) {
  if (file === 'mines.yml') return 'This file contains all packaged mine definitions. Coordinate, composition, access, spawn, reset, and hook leaves are listed below, including repeated values for every mine.'
  if (file.startsWith('guis/')) return 'GUI YAML controls presentation. The visual gallery explains how these files map to the in-game inventories.'
  if (file === 'plugin.yml') return 'plugin.yml is packaged metadata, command registration, aliases, and permission defaults. Server owners normally do not edit it inside the JAR.'
  if (file === 'messages.yml') return 'Message keys are safe to style, but keep placeholders/tokens expected by the code.'
  return 'The table is generated from the current packaged YAML, so defaults stay synchronized with the source.'
}

await fs.rm(outputRoot, { recursive: true, force: true })
await fs.mkdir(outputRoot, { recursive: true })
const files = await walk(resourcesRoot)

let index = '# Generated YAML Reference\n\n'
index += '> **Source-driven reference.** These pages are regenerated during every documentation build from `src/main/resources`. The hand-written guides explain *why* and *when* to change settings; these pages make sure no packaged YAML leaf disappears from the reference.\n\n'
index += '| File | Purpose |\n| --- | --- |\n'

for (const file of files) {
  const raw = await fs.readFile(path.join(resourcesRoot, file), 'utf8')
  let data
  try { data = YAML.parse(raw) }
  catch (error) { throw new Error(`Cannot parse ${file}: ${error.message}`) }
  const rows = flatten(data)
  const out = []
  out.push(`# ${pretty(file)}`)
  out.push('')
  out.push(sectionHint(file))
  out.push('')
  out.push(`**Source:** [\`src/main/resources/${pretty(file)}\`](${repoBase}${encodeURI(pretty(file))})`)
  out.push('')
  out.push(`**Documented leaves:** ${rows.length}`)
  out.push('')
  out.push('| Setting | Type | Packaged default | Recommended starting point | What it changes | Risk / common mistake |')
  out.push('| --- | --- | --- | --- | --- | --- |')
  for (const [key, type, value] of rows) {
    const [recommended, risk] = advice(file, key)
    out.push(`| \`${key}\` | ${type} | ${escapeCell(value)} | ${recommended.replaceAll('|', '\\|')} | ${describe(file, key, value).replaceAll('|', '\\|')} | ${risk.replaceAll('|', '\\|')} |`)
  }
  out.push('')
  out.push('::: tip Safe editing loop')
  out.push('Back up first, change one idea at a time, reload only supported modules, then run `/rp validate` and `/rp diagnose`. Use a full restart for storage/backend changes or anything the plugin documents as startup-only.')
  out.push(':::')
  await fs.writeFile(path.join(outputRoot, `${slug(file)}.md`), `${out.join('\n')}\n`)
  index += `| [\`${pretty(file)}\`](./${slug(file)}) | ${sectionHint(file).split('.')[0]}. |\n`
}

index += '\n## Why generated pages exist\n\nA normal wiki can silently fall behind the plugin. RelicPrison instead treats the packaged YAML as the source of truth for defaults. When a new key is committed, the next docs build exposes it automatically.\n'
await fs.writeFile(path.join(outputRoot, 'index.md'), index)
console.log(`Generated ${files.length} YAML reference pages.`)
