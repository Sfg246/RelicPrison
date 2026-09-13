import { promises as fs } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const wikiRoot = path.resolve(here, '..')
const apiRoot = path.resolve(wikiRoot, '../src/main/java/site/mcrelicworld/relicprison/api')
const eventRoot = path.join(apiRoot, 'event')
const outputRoot = path.resolve(wikiRoot, 'generated/api')
const repoBase = 'https://github.com/Sfg246/RelicPrison/blob/main/src/main/java/site/mcrelicworld/relicprison/api/'

function stripComments(source) {
  return source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '')
}

function signatures(source) {
  const clean = stripComments(source)
  const out = []
  const method = /^\s*(?:(?:public|protected|default|static|final|synchronized)\s+)*(?:[\w.?<>\[\],]+\s+)+([A-Za-z_$][\w$]*)\s*\([^;{}]*?\)\s*(?:throws\s+[^;{]+)?;/gm
  let match
  while ((match = method.exec(clean))) {
    const text = match[0].replace(/\s+/g, ' ').trim()
    if (!text.startsWith('package ') && !text.startsWith('import ')) out.push(text)
  }
  return [...new Set(out)]
}

function eventSignatures(source, className) {
  const clean = stripComments(source)
  const out = signatures(source)
  const ctor = new RegExp(`public\\s+${className}\\s*\\([^{}]*?\\)`, 'g')
  for (const match of clean.matchAll(ctor)) out.unshift(match[0].replace(/\s+/g, ' ').trim())
  return [...new Set(out)]
}

async function javaFiles(dir) {
  return (await fs.readdir(dir, { withFileTypes: true }))
    .filter(entry => entry.isFile() && entry.name.endsWith('.java'))
    .map(entry => entry.name)
    .sort((a, b) => a.localeCompare(b))
}

await fs.rm(outputRoot, { recursive: true, force: true })
await fs.mkdir(outputRoot, { recursive: true })

const services = await javaFiles(apiRoot)
let serviceDoc = '# Generated API Service Reference\n\n'
serviceDoc += '> Generated from the public Java API package during every documentation build. Threading guidance on the hand-written developer pages still takes precedence over guesswork.\n\n'
for (const file of services) {
  const source = await fs.readFile(path.join(apiRoot, file), 'utf8')
  const name = file.replace(/\.java$/, '')
  const sigs = signatures(source)
  serviceDoc += `## ${name}\n\n[View source](${repoBase}${file})\n\n`
  if (sigs.length) {
    serviceDoc += '```java\n' + sigs.join('\n') + '\n```\n\n'
  } else {
    serviceDoc += '_No interface-style method declarations were extracted automatically. Use the source link for the authoritative definition._\n\n'
  }
}
await fs.writeFile(path.join(outputRoot, 'services.md'), serviceDoc)

const events = await javaFiles(eventRoot)
let eventDoc = '# Generated Event Reference\n\n'
eventDoc += '> Generated from `api/event`. Event names are never hand-maintained here, so newly committed public events automatically appear in the docs build.\n\n'
for (const file of events) {
  const source = await fs.readFile(path.join(eventRoot, file), 'utf8')
  const name = file.replace(/\.java$/, '')
  const sigs = eventSignatures(source, name)
  eventDoc += `## ${name}\n\n[View source](${repoBase}event/${file})\n\n`
  if (sigs.length) eventDoc += '```java\n' + sigs.join('\n') + '\n```\n\n'
}
await fs.writeFile(path.join(outputRoot, 'events.md'), eventDoc)
console.log(`Generated API reference for ${services.length} service files and ${events.length} events.`)
