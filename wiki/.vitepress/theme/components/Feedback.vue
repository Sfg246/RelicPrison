<script setup>
import { computed } from 'vue'
import { useRoute } from 'vitepress'

const route = useRoute()
const page = computed(() => route.path)
const issue = computed(() => `https://github.com/Sfg246/RelicPrison/issues/new?title=${encodeURIComponent(`Docs feedback: ${page.value}`)}&body=${encodeURIComponent(`Page: https://sfg246.github.io/RelicPrison${page.value}\n\nWhat should be clearer or corrected?\n`)}`)
</script>

<template>
  <section class="docs-feedback">
    <strong>Was this page useful?</strong>
    <span> If something is missing, unclear, or wrong, open a pre-filled documentation issue.</span>
    <a :href="issue" target="_blank" rel="noreferrer">Report docs feedback</a>
  </section>
</template>
