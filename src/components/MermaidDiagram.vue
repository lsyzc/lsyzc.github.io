<script setup lang="ts">
import { isDark } from '~/logics'

const props = defineProps<{ source: string }>()
const diagram = ref<HTMLDivElement>()
const svg = ref('')
const failed = ref(false)
const mounted = ref(false)
const previewOpen = ref(false)
const previewSize = ref({ width: 1, height: 1 })
let revision = 0
let nextId = 0
const instanceId = useId().replace(/[^\w-]/g, '')

function openPreview() {
  const bounds = diagram.value?.querySelector('svg')?.viewBox.baseVal
  if (!bounds?.width || !bounds.height)
    return
  previewSize.value = { width: bounds.width, height: bounds.height }
  previewOpen.value = true
}

onMounted(() => {
  mounted.value = true
})

onBeforeUnmount(() => {
  revision++
})

watch([mounted, () => props.source, isDark], async ([ready, source, dark]) => {
  if (!ready)
    return

  const current = ++revision
  failed.value = false

  try {
    const { default: mermaid } = await import('mermaid')
    await document.fonts.ready
    if (current !== revision)
      return

    mermaid.initialize({
      startOnLoad: false,
      securityLevel: 'strict',
      suppressErrorRendering: true,
      theme: dark ? 'dark' : 'default',
      fontFamily: 'Inter, system-ui, sans-serif',
    })
    const result = await mermaid.render(`mermaid-${instanceId}-${nextId++}`, source)
    if (current !== revision)
      return

    svg.value = result.svg
    await nextTick()
    if (current === revision && diagram.value) {
      // 保留图中文字的自然尺寸，小屏幕通过容器滚动阅读，避免整图压缩。
      const element = diagram.value.querySelector('svg')
      if (element?.viewBox.baseVal.width)
        element.style.minWidth = `${element.viewBox.baseVal.width}px`
      result.bindFunctions?.(diagram.value)
    }
  }
  catch (error) {
    if (current === revision) {
      failed.value = true
      console.warn('Mermaid diagram could not be rendered:', error)
    }
  }
})
</script>

<template>
  <figure class="mermaid-diagram" aria-label="Mermaid 图表，可横向滚动" tabindex="0" :aria-busy="!svg && !failed">
    <!-- 原展示方式保留供回退：
    <div v-if="svg && !failed" ref="diagram" class="mermaid-diagram-content" v-html="svg" />
    -->
    <template v-if="svg && !failed">
      <button type="button" class="mermaid-expand" @click="openPreview">
        点击图表放大
      </button>
      <div
        ref="diagram"
        class="mermaid-diagram-content mermaid-clickable"
        role="button"
        tabindex="0"
        aria-label="放大查看 Mermaid 图表"
        aria-haspopup="dialog"
        @click="openPreview"
        @keydown.enter.prevent="openPreview"
        @keydown.space.prevent="openPreview"
        v-html="svg"
      />
    </template>
    <!-- 原 Mermaid 代码保留为加载及失败时的回退内容，避免丢失图表信息。 -->
    <template v-else>
      <p v-if="failed" role="status">
        图表暂时无法渲染，以下为原始代码。
      </p>
      <pre><code>{{ source }}</code></pre>
    </template>
  </figure>
  <MermaidPreview
    v-if="previewOpen"
    :svg="svg"
    :width="previewSize.width"
    :height="previewSize.height"
    @close="previewOpen = false"
  />
</template>

<style scoped>
.mermaid-diagram {
  margin: 1.5em 0;
  padding: 1rem;
  overflow-x: auto;
  border: 1px solid #8883;
  border-radius: 0.375rem;
}

.mermaid-diagram-content {
  display: flex;
  justify-content: center;
  min-width: fit-content;
}

.mermaid-clickable {
  cursor: zoom-in;
}

.mermaid-expand {
  display: block;
  margin: 0 0 0.75rem auto;
  padding: 0.25rem 0.5rem;
  border: 1px solid #8884;
  border-radius: 0.25rem;
  font-size: 0.875rem;
  color: inherit;
  cursor: zoom-in;
}

.mermaid-diagram:focus-visible {
  outline: 2px solid currentColor;
  outline-offset: 2px;
}

.mermaid-diagram-content :deep(svg) {
  display: block;
  height: auto;
  flex-shrink: 0;
}

.mermaid-diagram pre {
  white-space: pre;
}

.mermaid-diagram p {
  font-size: 0.875rem;
}
</style>
