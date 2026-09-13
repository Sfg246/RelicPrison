import { h } from 'vue'
import DefaultTheme from 'vitepress/theme'
import CommandExplorer from './components/CommandExplorer.vue'
import Breadcrumbs from './components/Breadcrumbs.vue'
import Feedback from './components/Feedback.vue'
import './custom.css'

export default {
  extends: DefaultTheme,
  enhanceApp({ app }) {
    app.component('CommandExplorer', CommandExplorer)
  },
  Layout() {
    return h(DefaultTheme.Layout, null, {
      'doc-before': () => h(Breadcrumbs),
      'doc-after': () => h(Feedback)
    })
  }
}
