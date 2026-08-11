<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import createGlobe from 'cobe'
import { fetchScenarios, fetchScenarioThumbnails } from '../api/scenarios'
import { fetchLanguage } from '../api/languages'
import { buildApiUrl } from '../api/rest'
import { useScenarioReader } from '../composables/useScenarioReader'
import ScenarioReaderModal from './scenario/ScenarioReaderModal.vue'

const props = defineProps({
  speed: { type: Number, default: 0.003 },
  maxMarkers: { type: Number, default: 8 },
})

const { openReader, activeScenario, closeReader } = useScenarioReader()

const canvasRef = ref(null)
let pointerInteracting = null
let dragOffset = { phi: 0, theta: 0 }
let phiOffset = 0
let thetaOffset = 0
let isPaused = false
let globe = null
let animationId = null

let currentPhi = 0
let currentTheta = 0.2


const scenarios = ref([])
const languageMap = ref({}) // id -> { latitude, longitude, name }
const thumbMap = ref({})    // scenarioId -> thumbnail id

function scenarioSortKey(s) {
  return s.publishedAt ?? s.createdAt ?? s.id
}

async function loadData() {
  try {
    const scenarioData = await fetchScenarios()
    const all = Array.isArray(scenarioData) ? scenarioData : (scenarioData.content ?? [])
    const publishedAll = all.filter(s => s.visibilityStatus === 'PUBLISHED' && s.languageId)

    publishedAll.sort((a, b) => {
      const ka = scenarioSortKey(a)
      const kb = scenarioSortKey(b)
      return ka < kb ? 1 : ka > kb ? -1 : 0
    })

    const candidatePool = publishedAll.slice(0, props.maxMarkers * 5)
    const languageIds = [...new Set(candidatePool.map(s => String(s.languageId)))]

    const langMap = {}
    await Promise.all(
      languageIds.map(async (id) => {
        try {
          const lang = await fetchLanguage(id)
          if (lang.latitude != null && lang.longitude != null) {
            langMap[id] = { latitude: lang.latitude, longitude: lang.longitude, name: lang.name }
          }
        } catch {
        }
      })
    )
    languageMap.value = langMap

    const published = candidatePool.filter(s => langMap[String(s.languageId)])
    const selected = published.slice(0, props.maxMarkers)
    scenarios.value = selected

    const tmap = {}
    await Promise.all(
      selected.map(async (s) => {
        try {
          const thumbs = await fetchScenarioThumbnails(s.id)
          tmap[s.id] = thumbs?.[0]?.id ?? null
        } catch {
          tmap[s.id] = null
        }
      })
    )
    thumbMap.value = tmap
  } catch {
    scenarios.value = []
  }
}

function thumbnailUrl(scenarioId) {
  const id = thumbMap.value[scenarioId]
  return id ? buildApiUrl(`/api/thumbnails/${id}/content`) : null
}

function rotationFor(scenarioId) {
  return ((Number(scenarioId) * 37) % 11) - 5
}

const markers = computed(() =>
  scenarios.value.map((s) => {
    const lang = languageMap.value[String(s.languageId)]
    return {
      id: `scenario-${s.id}`,
      scenario: s,
      location: [lang.latitude, lang.longitude],
      caption: s.title || 'Untitled',
      rotate: rotationFor(s.id),
    }
  })
)

const cobeMarkers = computed(() =>
  markers.value.map((m) => ({ location: m.location, size: 0.035 }))
)

watch(cobeMarkers, (val) => {
  if (globe) globe.update({ markers: val })
})


const markerEls = new Map()
function setMarkerRef(id) {
  return (el) => {
    if (el) markerEls.set(id, el)
    else markerEls.delete(id)
  }
}

const COBE_SPHERE_RADIUS = 0.8      // cobe's internal constant ("ee")
const COBE_MARKER_ELEVATION = 0.05  // cobe's default `markerElevation`
const MARKER_RADIUS = COBE_SPHERE_RADIUS + COBE_MARKER_ELEVATION

function markerUnitVector(lat, lon) {
  const latRad = (lat * Math.PI) / 180
  const lonRad = (lon * Math.PI) / 180
  const cosLat = Math.cos(latRad)
  return [cosLat * Math.cos(lonRad), Math.sin(latRad), -cosLat * Math.sin(lonRad)]
}

function project(lat, lon, phi, theta) {
  const [ux, uy, uz] = markerUnitVector(lat, lon)
  const x = ux * MARKER_RADIUS
  const y = uy * MARKER_RADIUS
  const z = uz * MARKER_RADIUS

  const cosPhi = Math.cos(phi), sinPhi = Math.sin(phi)
  const cosTheta = Math.cos(theta), sinTheta = Math.sin(theta)

  const screenXNorm = cosPhi * x + sinPhi * z
  const screenYNorm = sinPhi * sinTheta * x + cosTheta * y - cosPhi * sinTheta * z
  const depth = -sinPhi * cosTheta * x + sinTheta * y + cosPhi * cosTheta * z

  return { x: screenXNorm, y: screenYNorm, z: depth }
}

function updateMarkerPositions(canvas) {
  if (!canvas) return
  const width = canvas.offsetWidth
  const height = canvas.offsetHeight
  if (!width || !height) return

  for (const m of markers.value) {
    const el = markerEls.get(m.id)
    if (!el) continue

    const { x, y, z } = project(m.location[0], m.location[1], currentPhi, currentTheta)

    if (z < 0.02) {
      el.style.opacity = '0'
      el.style.pointerEvents = 'none'
      continue
    }

    const screenX = ((x + 1) / 2) * width
    const screenY = ((-y + 1) / 2) * height
    const fade = Math.min(1, z / 0.25)

    el.style.opacity = String(fade)
    el.style.pointerEvents = 'auto'
    el.style.transform =
      `translate(${screenX}px, ${screenY}px) translate(-50%, calc(-100% - 8px)) rotate(${m.rotate}deg)`
  }
}


function onPointerDown(e) {
  pointerInteracting = { x: e.clientX, y: e.clientY }
  canvasRef.value.style.cursor = 'grabbing'
  isPaused = true
}

function onPointerMove(e) {
  if (pointerInteracting !== null) {
    dragOffset = {
      phi: (e.clientX - pointerInteracting.x) / 300,
      theta: (e.clientY - pointerInteracting.y) / 1000,
    }
  }
}

function onPointerUp() {
  if (pointerInteracting !== null) {
    phiOffset += dragOffset.phi
    thetaOffset += dragOffset.theta
    dragOffset = { phi: 0, theta: 0 }
  }
  pointerInteracting = null
  if (canvasRef.value) canvasRef.value.style.cursor = 'grab'
  isPaused = false
}

function handleMarkerClick(scenario) {
  openReader(scenario)
}


onMounted(() => {
  loadData()

  window.addEventListener('pointermove', onPointerMove, { passive: true })
  window.addEventListener('pointerup', onPointerUp, { passive: true })

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
      baseColor: [0.99, 0.97, 0.96],
      markerColor: [0.36, 0.10, 0.16],
      glowColor: [0.96, 0.91, 0.89],
      markers: cobeMarkers.value,
    })

    function animate() {
      if (!isPaused) phi += props.speed
      currentPhi = phi + phiOffset + dragOffset.phi
      currentTheta = 0.2 + thetaOffset + dragOffset.theta

      globe.update({ phi: currentPhi, theta: currentTheta })
      updateMarkerPositions(canvas)

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
  window.removeEventListener('pointerup', onPointerUp)
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

    <button
      v-for="m in markers"
      :key="m.id"
      type="button"
      class="globe-polaroid"
      :ref="setMarkerRef(m.id)"
      @click="handleMarkerClick(m.scenario)"
    >
      <img
        v-if="thumbnailUrl(m.scenario.id)"
        :src="thumbnailUrl(m.scenario.id)"
        :alt="m.caption"
        class="globe-polaroid__img"
      />
      <div v-else class="globe-polaroid__placeholder">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3"
             stroke-linecap="round" stroke-linejoin="round">
          <rect x="3" y="3" width="7" height="7" rx="1"/>
          <rect x="14" y="3" width="7" height="7" rx="1"/>
          <rect x="3" y="14" width="7" height="7" rx="1"/>
          <rect x="14" y="14" width="7" height="7" rx="1"/>
        </svg>
      </div>
      <span class="globe-polaroid__caption">{{ m.caption }}</span>
    </button>

    <ScenarioReaderModal :scenario="activeScenario" @close="closeReader" />
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

.globe-polaroid {
  position: absolute;
  top: 0;
  left: 0;
  margin: 0;
  border: 0;
  background: #fff;
  padding: 6px 6px 20px;
  box-shadow: 0 2px 8px rgba(42, 21, 0, 0.15), 0 1px 2px rgba(42, 21, 0, 0.1);
  cursor: pointer;
  opacity: 0;
  transition: opacity 200ms ease;
  will-change: transform, opacity;
  pointer-events: none;
}

.globe-polaroid__img,
.globe-polaroid__placeholder {
  display: block;
  width: 52px;
  height: 52px;
  object-fit: cover;
  background: var(--surface-alt);
}

.globe-polaroid__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-soft);
}
.globe-polaroid__placeholder svg { width: 20px; height: 20px; }

.globe-polaroid__caption {
  position: absolute;
  bottom: 4px;
  left: 0;
  right: 0;
  text-align: center;
  font-size: 0.5rem;
  font-weight: 700;
  color: var(--text);
  letter-spacing: 0.02em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  padding: 0 4px;
}
</style>
