<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const directions = ['right', 'down-right', 'down', 'down-left', 'left', 'up-left', 'up', 'up-right'] as const
const frames = ['center', ...directions, 'blink'] as const
type Frame = typeof frames[number]

const avatar = ref<HTMLButtonElement>()
const gaze = ref<Frame>('center')
const blinking = ref(false)
const celebrating = ref(false)
const message = ref('')
const mounted = ref(false)
const loaded = ref(new Set<Frame>(['center']))
const currentFrame = computed(() => blinking.value && loaded.value.has('blink') ? 'blink' : gaze.value)
const source = (frame: Frame) => `/avatar/gaze/${frame}.webp`
const greetings = ['你好，我是 SaturnTu！', '欢迎来到我的博客 👋', '今天也要保持好奇心！']
let greetingIndex = 0
let blinkTimer: ReturnType<typeof setTimeout> | undefined
let openEyesTimer: ReturnType<typeof setTimeout> | undefined
let messageTimer: ReturnType<typeof setTimeout> | undefined
let celebrationTimer: ReturnType<typeof setTimeout> | undefined
let animationFrame = 0
let pointerX = 0
let pointerY = 0
let reducedMotion: MediaQueryList | undefined
let observer: IntersectionObserver | undefined
let visible = true
let disposed = false

function blink() {
  if (reducedMotion?.matches || !loaded.value.has('blink'))
    return
  clearTimeout(openEyesTimer)
  blinking.value = true
  openEyesTimer = setTimeout(() => {
    blinking.value = false
  }, 140)
}

function scheduleBlink() {
  clearTimeout(blinkTimer)
  if (disposed || reducedMotion?.matches)
    return
  blinkTimer = setTimeout(() => {
    if (visible && document.visibilityState === 'visible')
      blink()
    scheduleBlink()
  }, 3000 + Math.random() * 3000)
}

function resetGaze() {
  cancelAnimationFrame(animationFrame)
  animationFrame = 0
  gaze.value = 'center'
}

function updateGaze() {
  animationFrame = 0
  const element = avatar.value
  if (!element || !visible || document.visibilityState !== 'visible' || reducedMotion?.matches)
    return
  const rect = element.getBoundingClientRect()
  const dx = pointerX - (rect.left + rect.width * 0.5)
  const dy = pointerY - (rect.top + rect.height * 0.43)
  const index = (Math.round(Math.atan2(dy, dx) / (Math.PI / 4)) + 8) % 8
  const frame = Math.hypot(dx, dy) < rect.width * 0.12 ? 'center' : directions[index]
  gaze.value = loaded.value.has(frame) ? frame : 'center'
}

function onPointerMove(event: PointerEvent) {
  if (event.pointerType !== 'mouse' && event.pointerType !== 'pen')
    return
  pointerX = event.clientX
  pointerY = event.clientY
  if (!animationFrame && !reducedMotion?.matches)
    animationFrame = requestAnimationFrame(updateGaze)
}

function onMotionChange() {
  resetGaze()
  blinking.value = false
  clearTimeout(openEyesTimer)
  scheduleBlink()
}

function sayHello() {
  message.value = greetings[greetingIndex++ % greetings.length]
  clearTimeout(messageTimer)
  messageTimer = setTimeout(() => {
    message.value = ''
  }, 3200)
  blink()
  if (!reducedMotion?.matches) {
    celebrating.value = true
    clearTimeout(celebrationTimer)
    celebrationTimer = setTimeout(() => {
      celebrating.value = false
    }, 650)
  }
}

async function onFrameLoad(event: Event, frame: Frame) {
  const image = event.target as HTMLImageElement
  try {
    await image.decode()
    if (!disposed)
      loaded.value.add(frame)
  }
  catch {
    // Keep the current frame when an optional image cannot be decoded.
  }
}

onMounted(() => {
  mounted.value = true
  reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')
  reducedMotion.addEventListener('change', onMotionChange)
  window.addEventListener('pointermove', onPointerMove, { passive: true })
  window.addEventListener('blur', resetGaze)
  window.addEventListener('scroll', resetGaze, { passive: true })
  document.documentElement.addEventListener('pointerleave', resetGaze)
  if ('IntersectionObserver' in window) {
    observer = new IntersectionObserver(([entry]) => {
      visible = entry.isIntersecting
      if (!visible)
        resetGaze()
    })
    if (avatar.value)
      observer.observe(avatar.value)
  }
  scheduleBlink()
})

onBeforeUnmount(() => {
  disposed = true
  clearTimeout(blinkTimer)
  clearTimeout(openEyesTimer)
  clearTimeout(messageTimer)
  clearTimeout(celebrationTimer)
  cancelAnimationFrame(animationFrame)
  observer?.disconnect()
  reducedMotion?.removeEventListener('change', onMotionChange)
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('blur', resetGaze)
  window.removeEventListener('scroll', resetGaze)
  document.documentElement.removeEventListener('pointerleave', resetGaze)
})
</script>

<template>
  <div class="interactive-avatar">
    <button ref="avatar" class="avatar-button" type="button" aria-label="和 SaturnTu 打个招呼" @click="sayHello">
      <span class="avatar-bubble" :class="{ 'is-speaking': message }" aria-hidden="true">
        {{ message || '点我打个招呼' }}
      </span>
      <span class="avatar-breathe">
        <span class="avatar-stage" :class="{ 'is-celebrating': celebrating }">
          <img
            v-for="frame in frames" :key="frame"
            :src="frame === 'center' || mounted ? source(frame) : undefined"
            :fetchpriority="frame === 'center' ? 'high' : 'low'"
            :style="{ opacity: currentFrame === frame ? 1 : 0 }"
            class="avatar-frame" width="512" height="512"
            alt="" aria-hidden="true" :draggable="false" decoding="async"
            @load="onFrameLoad($event, frame)"
          >
        </span>
      </span>
    </button>
    <span class="avatar-announcement" role="status" aria-live="polite" aria-atomic="true">{{ message }}</span>
  </div>
</template>

<style scoped>
.interactive-avatar {
  position: relative;
  width: min(13rem, 65vw);
  margin: 0 0 1.75rem;
}

.avatar-button {
  position: relative;
  display: block;
  width: 100%;
  aspect-ratio: 1;
  padding: 0;
  border: 0;
  border-radius: 1.5rem;
  color: inherit;
  background: transparent;
  cursor: pointer;
  touch-action: manipulation;
  -webkit-tap-highlight-color: transparent;
}

.avatar-button:focus-visible {
  outline: 2px solid currentColor;
  outline-offset: 6px;
}

.avatar-breathe,
.avatar-stage {
  position: absolute;
  inset: 0;
  display: block;
  transform-origin: 50% 100%;
}

.avatar-breathe {
  animation: avatar-breathe 4.8s ease-in-out infinite;
}

.avatar-stage.is-celebrating {
  animation: avatar-greet 650ms ease-in-out;
}

.avatar-frame {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  margin: 0 !important;
  object-fit: contain;
  pointer-events: none;
  user-select: none;
}

.avatar-bubble {
  position: absolute;
  top: 4%;
  left: 68%;
  z-index: 1;
  width: max-content;
  max-width: min(14rem, 48vw);
  padding: 0.5rem 0.75rem;
  border: 1px solid #8884;
  border-radius: 1rem 1rem 1rem 0.15rem;
  background: var(--c-bg, white);
  font-size: 0.875rem;
  line-height: 1.5;
  text-align: left;
  opacity: 0;
  transform: translateY(4px);
  transition:
    opacity 180ms,
    transform 180ms;
  pointer-events: none;
}

.avatar-bubble.is-speaking,
.avatar-button:focus-visible .avatar-bubble {
  opacity: 1;
  transform: translateY(0);
}

.avatar-announcement {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}

@media (hover: hover) {
  .avatar-button:hover .avatar-bubble {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (pointer: coarse) {
  .avatar-bubble {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes avatar-breathe {
  0%,
  100% {
    transform: translateY(0) scale(1);
  }
  50% {
    transform: translateY(-3px) scale(1.008);
  }
}

@keyframes avatar-greet {
  0%,
  100% {
    transform: rotate(0);
  }
  25% {
    transform: translateY(-5px) rotate(-3deg);
  }
  65% {
    transform: translateY(-3px) rotate(3deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .avatar-breathe,
  .avatar-stage.is-celebrating {
    animation: none;
  }

  .avatar-bubble {
    transition: none;
  }
}
</style>
