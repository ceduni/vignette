<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import createGlobe from 'cobe'

const props = defineProps({
  speed: { type: Number, default: 0.003 },
})

const canvasRef = ref(null)
let pointerInteracting = null
let dragOffset = { phi: 0, theta: 0 }
let phiOffset = 0
let thetaOffset = 0
let isPaused = false
let globe = null
let animationId = null

function onPointerDown(e) {
  pointerInteracting = { x: e.clientX, y: e.clientY }
  canvasRef.value.style.cursor = 'grabbing'
  isPaused = true
}

function onPointerMove(e) {
  if (pointerInteracting !== null) {
    dragOffset = {
      phi:   (e.clientX - pointerInteracting.x) / 300,
      theta: (e.clientY - pointerInteracting.y) / 1000,
    }
  }
}

function onPointerUp() {
  if (pointerInteracting !== null) {
    phiOffset   += dragOffset.phi
    thetaOffset += dragOffset.theta
    dragOffset   = { phi: 0, theta: 0 }
  }
  pointerInteracting = null
  if (canvasRef.value) canvasRef.value.style.cursor = 'grab'
  isPaused = false
}

onMounted(() => {
  window.addEventListener('pointermove', onPointerMove, { passive: true })
  window.addEventListener('pointerup',   onPointerUp,   { passive: true })

  const canvas = canvasRef.value
  let phi = 0

  function init() {
    const width = canvas.offsetWidth
    if (width === 0 || globe) return

    globe = createGlobe(canvas, {
      devicePixelRatio: Math.min(window.devicePixelRatio || 1, 2),
      width, height: width,
      phi: 0, theta: 0.2,
      dark: 0, diffuse: 1.2,
      mapSamples: 16000, mapBrightness: 6,
      baseColor:   [0.99, 0.97, 0.96],
      markerColor: [0.36, 0.10, 0.16],
      glowColor:   [0.96, 0.91, 0.89],
      markers: [],
    })

    function animate() {
      if (!isPaused) phi += props.speed
      globe.update({
        phi:   phi + phiOffset   + dragOffset.phi,
        theta: 0.2 + thetaOffset + dragOffset.theta,
      })
      animationId = requestAnimationFrame(animate)
    }
    animate()
    setTimeout(() => { if (canvas) canvas.style.opacity = '1' })
  }

  if (canvas.offsetWidth > 0) {
    init()
  } else {
    const ro = new ResizeObserver(entries => {
      if (entries[0]?.contentRect.width > 0) { ro.disconnect(); init() }
    })
    ro.observe(canvas)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup',   onPointerUp)
  if (animationId) cancelAnimationFrame(animationId)
  if (globe) globe.destroy()
})
</script>

<template>
  <div class="globe-wrap">
    <canvas
      ref="canvasRef"
      class="globe-canvas"
      @pointerdown="onPointerDown"
    />
  </div>
</template>

<style scoped>
.globe-wrap {
  position: relative;
  aspect-ratio: 1;
  width: 100%;
  user-select: none;
}

.globe-canvas {
  width: 100%;
  height: 100%;
  cursor: grab;
  opacity: 0;
  transition: opacity 1.2s ease;
  border-radius: 50%;
  touch-action: none;
  display: block;
}
</style>
