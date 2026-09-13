import { defineConfig } from 'vitepress'

const base = process.env.DOCS_BASE || '/'

export default defineConfig({
  title: 'RelicPrison Docs',
  description: 'Complete setup, configuration, command, recovery, compatibility, and developer documentation for RelicPrison.',
  lang: 'en-US',
  cleanUrls: true,
  lastUpdated: true,
  base,
  sitemap: { hostname: 'https://sfg246.github.io/RelicPrison/' },
  head: [
    ['link', { rel: 'icon', type: 'image/svg+xml', href: `${base}plugin-icon-live.svg` }],
    ['link', { rel: 'apple-touch-icon', href: `${base}plugin-icon-live.svg` }],
    ['meta', { name: 'theme-color', content: '#7c3aed' }],
    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { property: 'og:title', content: 'RelicPrison Docs' }],
    ['meta', { property: 'og:description', content: 'Build, operate, troubleshoot, and extend RelicPrison without guessing.' }],
    ['meta', { property: 'og:image', content: 'https://sfg246.github.io/RelicPrison/plugin-icon-live.svg' }],
    ['meta', { name: 'twitter:card', content: 'summary_large_image' }],
    ['meta', { name: 'twitter:image', content: 'https://sfg246.github.io/RelicPrison/plugin-icon-live.svg' }]
  ],
  markdown: { lineNumbers: true },
  themeConfig: {
    logo: `${base}plugin-icon-live.svg`,
    siteTitle: 'RelicPrison',
    search: { provider: 'local' },
    socialLinks: [{ icon: 'github', link: 'https://github.com/Sfg246/RelicPrison' }],
    nav: [
      { text: 'Setup', items: [
        { text: 'Setup Journey', link: '/guide/installation' },
        { text: 'Zero-to-Launch Course', link: '/course/' },
        { text: 'Copy-Paste Recipes', link: '/recipes/' }
      ]},
      { text: 'Systems', link: '/systems/mines' },
      { text: 'Reference', items: [
        { text: 'Command Explorer', link: '/reference/commands' },
        { text: 'Permissions', link: '/reference/permissions' },
        { text: 'Placeholders', link: '/reference/placeholders' },
        { text: 'Compatibility Matrix', link: '/reference/compatibility' },
        { text: 'All YAML Settings', link: '/generated/config/' }
      ]},
      { text: 'Visuals', link: '/visuals/' },
      { text: 'Developers', items: [
        { text: 'API Overview', link: '/developers/api' },
        { text: 'Services', link: '/developers/services' },
        { text: 'Events', link: '/developers/events' },
        { text: 'Examples', link: '/developers/examples' }
      ]},
      { text: 'RC6 Stage 6', items: [
        { text: 'Release Status', link: '/releases/' },
        { text: 'Versioning', link: '/versions/' },
        { text: 'RC6 Staging Matrix', link: '/releases/rc6' }
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
          { text: 'Copy-Paste Recipes', link: '/recipes/' }
        ]
      },
      {
        text: 'Game Systems',
        collapsed: false,
        items: [
          { text: 'Mines & Resets', link: '/systems/mines' },
          { text: 'Ranks & Prestiges', link: '/systems/progression' },
          { text: 'Selling & Boosters', link: '/systems/economy' },
          { text: 'Gangs', link: '/systems/gangs' }
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
          { text: 'GUI Gallery', link: '/visuals/guis' }
        ]
      },
      {
        text: 'Operations',
        collapsed: false,
        items: [
          { text: 'Admin & Recovery', link: '/admin/operations' },
          { text: 'Troubleshooting', link: '/troubleshooting/' },
          { text: 'Error Encyclopedia', link: '/troubleshooting/errors' },
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
