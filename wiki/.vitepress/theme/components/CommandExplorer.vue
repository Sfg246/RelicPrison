<script setup>
import { computed, ref } from 'vue'
import commands from '../../data/commands.js'

const query = ref('')
const category = ref('All')
const audience = ref('All')
const categories = ['All', ...new Set(commands.map(c => c.category))]
const audiences = ['All', 'Player', 'Admin']

const filtered = computed(() => {
  const q = query.value.trim().toLowerCase()
  return commands.filter(c => {
    if (category.value !== 'All' && c.category !== category.value) return false
    if (audience.value !== 'All' && c.audience !== audience.value) return false
    if (!q) return true
    return [c.syntax, c.description, c.permission, c.aliases, c.category, c.audience]
      .join(' ').toLowerCase().includes(q)
  })
})

async function copy(text) {
  try { await navigator.clipboard.writeText(text) } catch (_) {}
}
</script>

<template>
  <div class="command-explorer">
    <div class="explorer-controls">
      <input v-model="query" class="rp-input" type="search" placeholder="Search command, permission, alias, or purpose…" aria-label="Search commands">
      <select v-model="category" class="rp-select" aria-label="Command category">
        <option v-for="item in categories" :key="item">{{ item }}</option>
      </select>
      <select v-model="audience" class="rp-select" aria-label="Command audience">
        <option v-for="item in audiences" :key="item">{{ item }}</option>
      </select>
    </div>
    <p class="explorer-count">Showing {{ filtered.length }} of {{ commands.length }} documented command forms.</p>
    <div class="command-results">
      <article v-for="command in filtered" :key="command.syntax" class="command-card rp-command-card">
        <div class="command-card-head">
          <code>{{ command.syntax }}</code>
          <button class="copy-command" type="button" @click="copy(command.example)">Copy example</button>
        </div>
        <p>{{ command.description }}</p>
        <div class="command-meta">
          <span class="status-pill">{{ command.audience }}</span>
          <span class="status-pill">{{ command.category }}</span>
        </div>
        <dl>
          <template v-if="command.permission"><dt>Permission</dt><dd><code>{{ command.permission }}</code></dd></template>
          <template v-if="command.aliases"><dt>Aliases</dt><dd>{{ command.aliases }}</dd></template>
          <dt>Example</dt><dd><code>{{ command.example }}</code></dd>
        </dl>
      </article>
    </div>
  </div>
</template>
