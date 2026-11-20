<script setup lang="ts">
const props = defineProps<{ svg: string, width: number, height: number }>()
const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLDialogElement>()
const stage = ref<HTMLDivElement>()
const scale = ref(1)
const offset = reactive({ x: 0, y: 0 })
const dragging = ref(false)
let previous = { x: 0, y: 0 }
let activePointer: number | undefined

// 使用独立 SVG 图片展示预览，避免与正文图表的 ID、箭头和样式互相影响。
const imageUrl = computed(() => `data:image/svg+xml;charset=utf-8,${encodeURIComponent(props.svg)}`)

function fit() {
  if (!stage.value)
    return
  scale.value = Math.max(0.05, Math.min(
    (stage.value.clientWidth - 32) / props.width,
    (stage.value.clientHeight - 32) / props.height,
    1,
  ))
  offset.x = 0
  offset.y = 0
}

function zoom(factor: number) {
  const next = Math.max(0.05, Math.min(5, scale.value * factor))
  const ratio = next / scale.value
  offset.x *= ratio
  offset.y *= ratio
  scale.value = next
}

function startDrag(event: PointerEvent) {
  if (event.button !== 0 || activePointer !== undefined)
    return
  activePointer = event.pointerId
  dragging.value = true
  previous = { x: event.clientX, y: event.clientY }
  stage.value?.setPointerCapture(event.pointerId)
}

function drag(event: PointerEvent) {
  if (event.pointerId !== activePointer)
    return
  offset.x += event.clientX - previous.x
  offset.y += event.clientY - previous.y
  previous = { x: event.clientX, y: event.clientY }
}

function endDrag() {
  activePointer = undefined
  dragging.value = false
}

function backdrop(event: MouseEvent) {
  if (event.target !== dialog.value)
    return
  const bounds = dialog.value!.getBoundingClientRect()
  if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom)
    emit('close')
}

useResizeObserver(stage, fit)
useScrollLock(document.body, true)

onMounted(async () => {
  dialog.value?.showModal()
  await nextTick()
  fit()
})

onBeforeUnmount(() => {
  dialog.value?.close()
})
</script>

<template>
  <Teleport to="body">
    <dialog ref="dialog" class="mermaid-preview" aria-label="图表放大预览" @cancel.prevent="emit('close')" @click="backdrop">
      <div class="mermaid-preview-toolbar">
        <span class="mermaid-preview-title">图表预览</span>
        <button type="button" aria-label="缩小图表" @click="zoom(1 / 1.25)">
          −
        </button>
        <output aria-live="polite">{{ Math.round(scale * 100) }}%</output>
        <button type="button" aria-label="放大图表" @click="zoom(1.25)">
          +
        </button>
        <button type="button" @click="fit">
          适应窗口
        </button>
        <button type="button" autofocus @click="emit('close')">
          关闭
        </button>
      </div>
      <div
        ref="stage"
        class="mermaid-preview-stage"
        :class="{ dragging }"
        @pointerdown="startDrag"
        @pointermove="drag"
        @pointerup="endDrag"
        @pointercancel="endDrag"
        @lostpointercapture="endDrag"
        @wheel.prevent="zoom($event.deltaY < 0 ? 1.1 : 1 / 1.1)"
      >
        <img
          :src="imageUrl"
          alt="Mermaid 图表放大预览"
          draggable="false"
          :style="{
            width: `${width}px`,
            height: `${height}px`,
            transform: `translate(${offset.x}px, ${offset.y}px) scale(${scale})`,
          }"
        >
      </div>
      <p class="mermaid-preview-hint">
        拖动查看 · 滚轮或按钮缩放 · Esc 关闭
      </p>
    </dialog>
  </Teleport>
</template>

<style scoped>
.mermaid-preview {
  position: fixed;
  inset: 0;
  width: min(1400px, 94vw);
  max-width: none;
  height: 90dvh;
  max-height: none;
  margin: auto;
  padding: 0;
  border: 1px solid #8884;
  border-radius: 0.75rem;
  background: var(--c-bg, #fff);
  color: inherit;
  overflow: hidden;
}

.mermaid-preview[open] {
  display: flex;
  flex-direction: column;
}

.mermaid-preview::backdrop {
  background: #0009;
  backdrop-filter: blur(4px);
}

.mermaid-preview-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
  flex-wrap: wrap;
  padding: 0.75rem;
  border-bottom: 1px solid #8883;
  font-size: 0.875rem;
}

.mermaid-preview-title {
  margin-right: auto;
}

.mermaid-preview-toolbar button {
  min-width: 2.5rem;
  min-height: 2.5rem;
  padding: 0.25rem 0.5rem;
  border: 1px solid #8884;
  border-radius: 0.375rem;
}

.mermaid-preview-toolbar button:hover {
  background: #8882;
}

.mermaid-preview-toolbar output {
  min-width: 3rem;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.mermaid-preview-stage {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  touch-action: none;
  cursor: grab;
}

.mermaid-preview-stage.dragging {
  cursor: grabbing;
}

.mermaid-preview-stage img {
  flex-shrink: 0;
  max-width: none;
  max-height: none;
  user-select: none;
  pointer-events: none;
}

.mermaid-preview-hint {
  padding: 0.5rem;
  margin: 0;
  text-align: center;
  font-size: 0.875rem;
  opacity: 0.65;
}
</style>
