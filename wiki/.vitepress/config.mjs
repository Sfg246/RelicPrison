import { defineConfig } from 'vitepress'

const base = process.env.DOCS_BASE || '/'
const productionOrigin = 'https://sfg246.github.io'
const productionBase = '/RelicPrison'

function canonicalUrl(relativePath) {
  let route = relativePath
    .replace(/(^|\/)index\.md$/u, '$1')
    .replace(/\.md$/u, '')

  if (!route) route = '/'
  if (!route.startsWith('/')) route = `/${route}`

  return `${productionOrigin}${productionBase}${route}`
}

export default defineConfig({
  title: 'RelicPrison Docs',
  description: 'Complete setup, configuration, command, recovery, compatibility, upgrade, and developer documentation for RelicPrison.',
  lang: 'en-US',
  cleanUrls: true,
  lastUpdated: true,
  base,
  sitemap: { hostname: 'https://sfg246.github.io/RelicPrison/' },
  transformPageData(pageData) {
    if (pageData.relativePath === '404.md') return

    const canonical = canonicalUrl(pageData.relativePath)
    pageData.frontmatter.head ??= []
    pageData.frontmatter.head.push(
      ['link', { rel: 'canonical', href: canonical }],
      ['meta', { property: 'og:url', content: canonical }]
    )
  },
  head: [
    ['link', { rel: 'icon', type: 'image/webp', href: `${base}plugin-icon-live.webp` }],
    ['link', { rel: 'apple-touch-icon', href: `${base}plugin-icon-live.webp` }],
    ['link', { rel: 'manifest', href: `${base}site.webmanifest` }],
    ['meta', { name: 'theme-color', content: '#7c3aed' }],
    ['meta', { name: 'robots', content: 'index,follow,max-image-preview:large' }],
    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { property: 'og:title', content: 'RelicPrison Docs' }],
    ['meta', { property: 'og:description', content: 'Build, operate, troubleshoot, upgrade, and extend RelicPrison without guessing.' }],
    ['meta', { property: 'og:image', content: 'https://sfg246.github.io/RelicPrison/social-card.svg' }],
    ['meta', { property: 'og:image:alt', content: 'RelicPrison documentation' }],
    ['meta', { name: 'twitter:card', content: 'summary_large_image' }],
    ['meta', { name: 'twitter:title', content: 'RelicPrison Docs' }],
    ['meta', { name: 'twitter:description', content: 'Build, operate, troubleshoot, upgrade, and extend RelicPrison without guessing.' }],
    ['meta', { name: 'twitter:image', content: 'https://sfg246.github.io/RelicPrison/social-card.svg' }]
  ],
  markdown: { lineNumbers: true },
  themeConfig: {
    logo: '/plugin-icon-live.webp',
    siteTitle: 'RelicPrison',
    search: { provider: 'local' },
    socialLinks: [{ icon: 'github', link: 'https://github.com/Sfg246/RelicPrison' }],
    nav: [
      { text: 'Setup', items: [
        { text: 'Setup Journey', link: '/guide/installation' },
        { text: 'Zero-to-Launch Course', link: '/course/' },
        { text: 'Copy-Paste Recipes', link: '/recipes/' },
        { text: 'FAQ / I Want To…', link: '/faq' }
      ]},
      { text: 'Systems', link: '/systems/' },
      { text: 'Reference', items: [
        { text: 'Command Explorer', link: '/reference/commands' },
        { text: 'Permissions', link: '/reference/permissions' },
        { text: 'Placeholders', link: '/reference/placeholders' },
        { text: 'Compatibility Matrix', link: '/reference/compatibility' },
        { text: 'All YAML Settings', link: '/generated/config/' }
      ]},
      { text: 'Visuals', items: [
        { text: 'Architecture & Flows', link: '/visuals/' },
        { text: 'GUI Gallery', link: '/visuals/guis' },
        { text: 'Visual Walkthroughs', link: '/visuals/tutorials' }
      ]},
      { text: 'Upgrade', link: '/upgrading/' },
      { text: 'Developers', items: [
        { text: 'API Overview', link: '/developers/api' },
        { text: 'Services', link: '/developers/services' },
        { text: 'Events', link: '/developers/events' },
        { text: 'Examples', link: '/developers/examples' }
      ]},
      { text: 'RelicPrison 1.0.0', items: [
        { text: 'Release Status', link: '/releases/' },
        { text: 'Changelog', link: '/changelog/' },
        { text: 'Known Limitations', link: '/known-limitations' },
        { text: 'Versioning', link: '/versions/' },
        { text: '1.0.0 Release', link: '/releases/1.0.0' },
        { text: 'RC6 History', link: '/releases/rc6' }
      ]},
      { text: 'Support', link: 'https://github.com/Sfg246/RelicPrison/issues' }
    ],
    sidebar: [
      {
        text: 'Start Here',
        items: [
          { text: 'Welcome', link: '/' },
          { text: '1. Install RelicPrison', link: '/guide/installation' },
          { text: '2. Understand the Plugin', link: '/guide/how-it-works' },
          { text: '3. First Server Setup', link: '/guide/first-server' },
          { text: '4. Create Your First Mine', link: '/guide/first-mine' },
          { text: 'Zero-to-Launch Course', link: '/course/' },
          { text: 'Copy-Paste Recipes', link: '/recipes/' },
          { text: 'FAQ / I Want To…', link: '/faq' }
        ]
      },
      {
        text: 'Game Systems',
        collapsed: false,
        items: [
          { text: 'All Systems', link: '/systems/' },
          { text: 'Mines & Resets', link: '/systems/mines' },
          { text: 'Mining Pipeline', link: '/systems/mining' },
          { text: 'Ranks & Prestiges', link: '/systems/progression' },
          { text: 'Selling & Economy', link: '/systems/economy' },
          { text: 'Boosters', link: '/systems/boosters' },
          { text: 'Block Events', link: '/systems/block-events' },
          { text: 'Gangs', link: '/systems/gangs' },
          { text: 'Leaderboards', link: '/systems/leaderboards' },
          { text: 'Rewards & Recovery', link: '/systems/rewards' },
          { text: 'Statistics', link: '/systems/statistics' },
          { text: 'GUIs & Admin Editors', link: '/systems/guis' }
        ]
      },
      {
        text: 'Configuration',
        collapsed: false,
        items: [
          { text: 'Configuration Map', link: '/configuration/' },
          { text: 'config.yml Explained', link: '/configuration/core' },
          { text: 'Integrations', link: '/configuration/integrations' },
          { text: 'Every YAML Setting', link: '/generated/config/' },
          { text: 'storage.yml', link: '/generated/config/storage' },
          { text: 'mines.yml', link: '/generated/config/mines' },
          { text: 'ranks.yml', link: '/generated/config/ranks' },
          { text: 'prestiges.yml', link: '/generated/config/prestiges' },
          { text: 'mining.yml', link: '/generated/config/mining' },
          { text: 'boosters.yml', link: '/generated/config/boosters' },
          { text: 'block-events.yml', link: '/generated/config/block-events' },
          { text: 'leaderboards.yml', link: '/generated/config/leaderboards' },
          { text: 'gangs.yml', link: '/generated/config/gangs' },
          { text: 'messages.yml', link: '/generated/config/messages' },
          { text: 'GUI YAML', link: '/visuals/guis' }
        ]
      },
      {
        text: 'Reference',
        collapsed: false,
        items: [
          { text: 'Interactive Commands', link: '/reference/commands' },
          { text: 'Permissions', link: '/reference/permissions' },
          { text: 'Placeholders', link: '/reference/placeholders' },
          { text: 'Compatibility Matrix', link: '/reference/compatibility' }
        ]
      },
      {
        text: 'Visual Guides',
        items: [
          { text: 'Architecture & Flows', link: '/visuals/' },
          { text: 'GUI Gallery', link: '/visuals/guis' },
          { text: 'Visual Walkthroughs', link: '/visuals/tutorials' }
        ]
      },
      {
        text: 'Operations & Lifecycle',
        collapsed: false,
        items: [
          { text: 'Admin & Recovery', link: '/admin/operations' },
          { text: 'Upgrade / Migration / Rollback', link: '/upgrading/' },
          { text: 'Troubleshooting', link: '/troubleshooting/' },
          { text: 'Error Encyclopedia', link: '/troubleshooting/errors' },
          { text: 'Known Limitations', link: '/known-limitations' },
          { text: 'Changelog', link: '/changelog/' },
          { text: 'Release / Download Status', link: '/releases/' },
          { text: 'RC6 Staging Status', link: '/releases/rc6' },
          { text: 'Versioned Docs', link: '/versions/' }
        ]
      },
      {
        text: 'For Developers',
        items: [
          { text: 'API & Thread Safety', link: '/developers/api' },
          { text: 'Services', link: '/developers/services' },
          { text: 'Events', link: '/developers/events' },
          { text: 'Examples', link: '/developers/examples' },
          { text: 'Generated Services', link: '/generated/api/services' },
          { text: 'Generated Events', link: '/generated/api/events' }
        ]
      }
    ],
    outline: { level: [2, 3], label: 'On this page' },
    editLink: {
      pattern: 'https://github.com/Sfg246/RelicPrison/edit/main/wiki/:path',
      text: 'Edit this page on GitHub'
    },
    lastUpdated: { text: 'Updated' },
    docFooter: { prev: 'Previous', next: 'Next' },
    footer: {
      message: 'Source-driven RelicPrison documentation',
      copyright: 'Defaults and generated references are built from the live repository.'
    }
  }
})
