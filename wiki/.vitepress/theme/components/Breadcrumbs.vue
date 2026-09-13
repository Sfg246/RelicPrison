<script setup>
import { computed } from 'vue'
import { useRoute, withBase } from 'vitepress'

const route = useRoute()
const parts = computed(() => route.path.split('/').filter(Boolean))
const crumbs = computed(() => parts.value.map((part, index) => ({
  label: part.replace(/[-_]/g, ' ').replace(/\b\w/g, c => c.toUpperCase()),
  href: '/' + parts.value.slice(0, index + 1).join('/')
})))
</script>

<template>
  <nav v-if="crumbs.length" class="rp-breadcrumbs" aria-label="Breadcrumb">
    <a :href="withBase('/')">Docs</a>
    <template v-for="crumb in crumbs" :key="crumb.href">
      <span>/</span><a :href="withBase(crumb.href)">{{ crumb.label }}</a>
    </template>
  </nav>
</template>
