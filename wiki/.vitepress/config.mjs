import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'RelicPrison Docs',
  description: 'The complete setup, administration, configuration, integration, and developer guide for RelicPrison.',
  lang: 'en-US',
  cleanUrls: true,
  lastUpdated: true,
  base: process.env.DOCS_BASE || '/',
  head: [
    ['meta', { name: 'theme-color', content: '#7c3aed' }],
    ['meta', { property: 'og:title', content: 'RelicPrison Docs' }],
    ['meta', { property: 'og:description', content: 'Build and operate a complete RelicPrison server, one step at a time.' }]
  ],
  markdown: {
    lineNumbers: true
  },
  themeConfig: {
    siteTitle: 'RelicPrison',
    search: { provider: 'local' },
    nav: [
      { text: 'Setup Journey', link: '/guide/installation' },
      { text: 'Systems', link: '/systems/mines' },
      { text: 'Reference', link: '/reference/commands' },
      { text: 'Admin', link: '/admin/operations' },
      { text: 'Developer API', link: '/developers/api' }
    ],
    sidebar: [
      {
        text: 'Start Here',
        items: [
          { text: 'Welcome', link: '/' },
          { text: '1. Install RelicPrison', link: '/guide/installation' },
          { text: '2. Understand the Plugin', link: '/guide/how-it-works' },
          { text: '3. First Server Setup', link: '/guide/first-server' },
          { text: '4. Create Your First Mine', link: '/guide/first-mine' }
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
          { text: 'Integrations', link: '/configuration/integrations' }
        ]
      },
      {
        text: 'Reference',
        collapsed: false,
        items: [
          { text: 'Commands', link: '/reference/commands' },
          { text: 'Permissions', link: '/reference/permissions' },
          { text: 'Placeholders', link: '/reference/placeholders' }
        ]
      },
      {
        text: 'Operations',
        collapsed: false,
        items: [
          { text: 'Admin & Recovery', link: '/admin/operations' },
          { text: 'Troubleshooting', link: '/troubleshooting/' },
          { text: 'RC6 Staging Status', link: '/releases/rc6' }
        ]
      },
      {
        text: 'For Developers',
        items: [
          { text: 'API & Thread Safety', link: '/developers/api' }
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
      message: 'RelicPrison documentation',
      copyright: 'Built from the live RelicPrison code and configuration.'
    }
  }
})
