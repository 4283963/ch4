<template>
  <div class="app-container">
    <header class="header">
      <div class="logo">
        <div class="logo-icon">🚗</div>
        <div class="logo-text">
          <h1>智能垂直循环式立体车库</h1>
          <p>写字楼取车终端系统</p>
        </div>
      </div>
      <div class="header-info">
        <div class="available-spots">
          <span class="spots-number">{{ availableSpots }}</span>
          <span class="spots-label">空闲车位</span>
        </div>
      </div>
    </header>

    <main class="main-content">
      <div class="left-panel">
        <div class="card pickup-card">
          <h2>取车服务</h2>
          <p class="subtitle">请输入车牌号提取您的车辆</p>

          <div class="input-group">
            <label>车牌号</label>
            <input
              v-model="licensePlate"
              type="text"
              class="input"
              placeholder="例如：京A12345"
              @keyup.enter="handlePickup"
            />
          </div>

          <div class="quick-plates">
            <span class="quick-label">快速选择：</span>
            <button
              v-for="plate in quickPlates"
              :key="plate"
              class="quick-btn"
              @click="licensePlate = plate"
            >
              {{ plate }}
            </button>
          </div>

          <button
            class="btn btn-primary pickup-btn"
            :disabled="!licensePlate || isSubmitting"
            @click="handlePickup"
          >
            {{ buttonText }}
          </button>

          <div v-if="errorMessage" class="error-message">
            {{ errorMessage }}
          </div>

          <div v-if="pickupResult" class="pickup-result card" :class="pickupResult.status.toLowerCase()">
            <h3>{{ statusTitle }}</h3>
            <div class="result-grid">
              <div class="result-item">
                <span class="label">车牌号</span>
                <span class="value">{{ pickupResult.licensePlate }}</span>
              </div>
              <div class="result-item">
                <span class="label">当前层</span>
                <span class="value">第 {{ pickupResult.carrierIndex + 1 }} 层</span>
              </div>
              <div class="result-item">
                <span class="label">旋转方向</span>
                <span class="value">{{ pickupResult.direction === 'CLOCKWISE' ? '顺时针' : '逆时针' }}</span>
              </div>
              <div class="result-item">
                <span class="label">移动层数</span>
                <span class="value">{{ pickupResult.steps }} 层</span>
              </div>
              <div class="result-item">
                <span class="label">停车时长</span>
                <span class="value">{{ formatDuration(pickupResult.durationMinutes) }}</span>
              </div>
              <div class="result-item">
                <span class="label">应付金额</span>
                <span class="value amount">¥{{ pickupResult.amount.toFixed(2) }}</span>
              </div>
            </div>

            <div v-if="pickupResult.status === 'QUEUED'" class="queue-info">
              <div class="queue-spinner"></div>
              <p>排队等待中，前方还有 <strong>{{ pickupResult.queuePosition }}</strong> 位</p>
              <p class="queue-holder">当前正在调车：{{ pickupResult.currentHolder }}</p>
            </div>
          </div>
        </div>

        <div class="card info-card">
          <h3>📋 使用说明</h3>
          <ul>
            <li>输入车牌号后点击"立即取车"</li>
            <li>如遇其他车主正在调车，系统自动排队</li>
            <li>排队期间请耐心等待，轮到您时自动执行</li>
            <li>如遇紧急情况，请联系工作人员</li>
          </ul>
        </div>
      </div>

      <div class="right-panel">
        <div class="card animation-card">
          <h2>🏢 车库实时状态</h2>
          <GarageAnimation
            :total-carriers="totalCarriers"
            :ground-carrier-index="groundCarrierIndex"
            :target-carrier-index="targetCarrierIndex"
            :is-rotating="isRotating"
            :progress="rotationProgress"
            :status="garageStatus"
            :carriers="carriers"
          />
        </div>

        <div class="card status-card">
          <h3>📊 车位状态</h3>
          <div class="carrier-grid">
            <div
              v-for="(carrier, index) in carriers"
              :key="index"
              class="carrier-slot"
              :class="{
                occupied: carrier?.hasCar,
                ground: index === groundCarrierIndex,
                target: index === targetCarrierIndex
              }"
            >
              <span class="slot-number">{{ index + 1 }}</span>
              <span v-if="carrier?.hasCar" class="slot-plate">{{ carrier.licensePlate }}</span>
              <span v-else class="slot-empty">空</span>
            </div>
          </div>
        </div>
      </div>
    </main>

    <footer class="footer">
      <p>© 2024 智能立体车库管理系统 | 全自动智能垂直循环式</p>
    </footer>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import GarageAnimation from './components/GarageAnimation.vue'
import { parkingApi } from './api/parking.js'

const totalCarriers = 8
const licensePlate = ref('')
const isSubmitting = ref(false)
const isRotating = ref(false)
const groundCarrierIndex = ref(0)
const targetCarrierIndex = ref(null)
const rotationProgress = ref(0)
const garageStatus = ref('IDLE')
const pickupResult = ref(null)
const errorMessage = ref('')
const availableSpots = ref(5)

const quickPlates = ['京A12345', '沪B67890', '粤C11111']

const carriers = ref([
  { hasCar: true, licensePlate: '京A12345' },
  { hasCar: false, licensePlate: '' },
  { hasCar: true, licensePlate: '沪B67890' },
  { hasCar: false, licensePlate: '' },
  { hasCar: true, licensePlate: '粤C11111' },
  { hasCar: false, licensePlate: '' },
  { hasCar: false, licensePlate: '' },
  { hasCar: false, licensePlate: '' }
])

let rotationInterval = null
let queuePollInterval = null

const buttonText = computed(() => {
  if (isSubmitting) return '请求中...'
  if (isRotating) return '车辆传送中...'
  if (pickupResult.value?.status === 'QUEUED') return '排队等待中...'
  return '立即取车'
})

const statusTitle = computed(() => {
  if (!pickupResult.value) return ''
  const s = pickupResult.value.status
  if (s === 'QUEUED') return '⏳ 排队等待中'
  if (s === 'ROTATING') return '🔄 正在调车'
  return '取车信息'
})

function formatDuration(minutes) {
  if (!minutes) return '0分钟'
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  if (hours > 0) return `${hours}小时${mins}分钟`
  return `${mins}分钟`
}

async function handlePickup() {
  if (!licensePlate.value.trim() || isSubmitting.value) return

  errorMessage.value = ''
  pickupResult.value = null
  isSubmitting.value = true

  try {
    const result = await parkingApi.pickup(licensePlate.value.trim())

    if (result.code !== 200) {
      errorMessage.value = result.message || '取车失败'
      isSubmitting.value = false
      return
    }

    const data = result.data
    pickupResult.value = data

    if (data.status === 'QUEUED') {
      isSubmitting.value = false
      garageStatus.value = 'QUEUED'
      startQueuePolling(data.requestId)
      return
    }

    startRotationAnimation(data)

  } catch (error) {
    console.error('Pickup error:', error)
    errorMessage.value = '取车请求失败，请稍后重试'
    isSubmitting.value = false
    simulatePickup()
  }
}

function startRotationAnimation(data) {
  isSubmitting.value = false
  targetCarrierIndex.value = data.carrierIndex
  isRotating.value = true
  garageStatus.value = 'ROTATING'
  rotationProgress.value = 0

  const totalSteps = data.steps
  const stepTime = (data.estimatedTimeMs || 5000) / totalSteps / 100

  let currentStep = 0
  const startIdx = groundCarrierIndex.value
  const direction = data.direction

  if (rotationInterval) clearInterval(rotationInterval)

  rotationInterval = setInterval(() => {
    currentStep += 0.1
    rotationProgress.value = Math.min(currentStep / totalSteps, 1)

    const currentCarrierIdx = Math.round(startIdx + (direction === 'CLOCKWISE' ? 1 : -1) * currentStep)
    const normalizedIdx = ((currentCarrierIdx % totalCarriers) + totalCarriers) % totalCarriers
    groundCarrierIndex.value = normalizedIdx

    if (currentStep >= totalSteps) {
      clearInterval(rotationInterval)
      rotationInterval = null
      groundCarrierIndex.value = targetCarrierIndex.value
      rotationProgress.value = 1
      isRotating.value = false
      garageStatus.value = 'IDLE'

      completePickup(licensePlate.value.trim())
    }
  }, stepTime)
}

function startQueuePolling(requestId) {
  if (queuePollInterval) clearInterval(queuePollInterval)

  queuePollInterval = setInterval(async () => {
    try {
      const result = await parkingApi.getQueueStatus(requestId)
      if (result.code !== 200) return

      const status = result.data
      if (status.status === 'ROTATING' && pickupResult.value) {
        pickupResult.value.status = 'ROTATING'
        pickupResult.value.queuePosition = 0

        const data = pickupResult.value
        clearInterval(queuePollInterval)
        queuePollInterval = null
        startRotationAnimation(data)
      } else if (status.status === 'QUEUED') {
        if (pickupResult.value) {
          pickupResult.value.queuePosition = status.queuePosition
        }
      } else if (status.status === 'IDLE') {
        if (pickupResult.value) {
          pickupResult.value.status = 'ROTATING'
          clearInterval(queuePollInterval)
          queuePollInterval = null
          startRotationAnimation(pickupResult.value)
        }
      }
    } catch (e) {
      console.error('Queue poll error:', e)
    }
  }, 2000)
}

function simulatePickup() {
  const plate = licensePlate.value.trim()
  let carrierIdx = -1

  for (let i = 0; i < carriers.value.length; i++) {
    if (carriers.value[i].licensePlate === plate) {
      carrierIdx = i
      break
    }
  }

  if (carrierIdx === -1) {
    errorMessage.value = '未找到该车辆，请检查车牌号'
    return
  }

  const stepsClockwise = (carrierIdx - groundCarrierIndex.value + totalCarriers) % totalCarriers
  const stepsCounterClockwise = (groundCarrierIndex.value - carrierIdx + totalCarriers) % totalCarriers

  let direction, steps
  if (stepsClockwise <= stepsCounterClockwise) {
    direction = 'CLOCKWISE'
    steps = stepsClockwise
  } else {
    direction = 'COUNTER_CLOCKWISE'
    steps = stepsCounterClockwise
  }

  pickupResult.value = {
    licensePlate: plate,
    carrierIndex: carrierIdx,
    direction: direction,
    steps: steps,
    estimatedTimeMs: steps * 1500,
    durationMinutes: 185,
    amount: 25.0,
    parkTime: new Date(Date.now() - 185 * 60 * 1000),
    status: 'ROTATING'
  }

  startRotationAnimation(pickupResult.value)
}

async function completePickup(plate) {
  try {
    await parkingApi.completePickup(plate)
  } catch (e) {
    console.log('Complete pickup error:', e)
  }
}

async function loadSpotsStatus() {
  try {
    const result = await parkingApi.getAllSpots()
    if (result.code === 200 && result.data) {
      const spots = result.data
      carriers.value = spots.map(spot => ({
        hasCar: spot.isOccupied,
        licensePlate: spot.licensePlate || ''
      }))
    }
  } catch (e) {
    console.log('Using mock data for spots')
  }
}

async function loadAvailableSpots() {
  try {
    const result = await parkingApi.getAvailableSpots()
    if (result.code === 200) {
      availableSpots.value = result.data
    }
  } catch (e) {
    console.log('Using mock data for available spots')
  }
}

onMounted(() => {
  loadSpotsStatus()
  loadAvailableSpots()
})

onUnmounted(() => {
  if (rotationInterval) clearInterval(rotationInterval)
  if (queuePollInterval) clearInterval(queuePollInterval)
})
</script>

<style scoped>
.app-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 40px;
  background: rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.logo {
  display: flex;
  align-items: center;
  gap: 16px;
}

.logo-icon {
  font-size: 48px;
}

.logo-text h1 {
  font-size: 24px;
  font-weight: bold;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.logo-text p {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.6);
  margin-top: 4px;
}

.header-info {
  display: flex;
  gap: 24px;
}

.available-spots {
  display: flex;
  flex-direction: column;
  align-items: center;
  background: rgba(76, 175, 80, 0.2);
  padding: 12px 24px;
  border-radius: 12px;
  border: 1px solid rgba(76, 175, 80, 0.5);
}

.spots-number {
  font-size: 28px;
  font-weight: bold;
  color: #4CAF50;
}

.spots-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
}

.main-content {
  flex: 1;
  display: flex;
  gap: 30px;
  padding: 30px 40px;
  max-width: 1400px;
  margin: 0 auto;
  width: 100%;
}

.left-panel {
  width: 380px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.right-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.pickup-card h2 {
  font-size: 24px;
  margin-bottom: 8px;
}

.pickup-card .subtitle {
  color: rgba(255, 255, 255, 0.6);
  margin-bottom: 24px;
  font-size: 14px;
}

.input-group {
  margin-bottom: 16px;
}

.input-group label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.8);
}

.quick-plates {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
  align-items: center;
}

.quick-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
}

.quick-btn {
  padding: 6px 12px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  color: white;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.3s;
}

.quick-btn:hover {
  background: rgba(102, 126, 234, 0.3);
  border-color: #667eea;
}

.pickup-btn {
  width: 100%;
  padding: 16px;
  font-size: 18px;
}

.error-message {
  margin-top: 16px;
  padding: 12px;
  background: rgba(244, 67, 54, 0.2);
  border: 1px solid rgba(244, 67, 54, 0.5);
  border-radius: 8px;
  color: #f44336;
  font-size: 14px;
  text-align: center;
}

.pickup-result {
  margin-top: 20px;
}

.pickup-result.queued {
  background: rgba(255, 152, 0, 0.1);
  border-color: rgba(255, 152, 0, 0.3);
}

.pickup-result.rotating {
  background: rgba(76, 175, 80, 0.1);
  border-color: rgba(76, 175, 80, 0.3);
}

.pickup-result h3 {
  margin-bottom: 16px;
  font-size: 16px;
}

.pickup-result.rotating h3 {
  color: #4CAF50;
}

.pickup-result.queued h3 {
  color: #FF9800;
}

.result-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.result-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.result-item .label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
}

.result-item .value {
  font-size: 16px;
  font-weight: 600;
}

.result-item .value.amount {
  color: #FF9800;
  font-size: 20px;
}

.queue-info {
  margin-top: 20px;
  padding: 16px;
  background: rgba(255, 152, 0, 0.1);
  border-radius: 8px;
  text-align: center;
}

.queue-info p {
  margin-top: 8px;
  color: rgba(255, 255, 255, 0.8);
  font-size: 14px;
}

.queue-info .queue-holder {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
}

.queue-spinner {
  display: inline-block;
  width: 24px;
  height: 24px;
  border: 3px solid rgba(255, 152, 0, 0.3);
  border-top-color: #FF9800;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.info-card h3 {
  margin-bottom: 16px;
  font-size: 16px;
}

.info-card ul {
  list-style: none;
}

.info-card li {
  padding: 8px 0;
  color: rgba(255, 255, 255, 0.8);
  font-size: 14px;
  border-bottom: 1px dashed rgba(255, 255, 255, 0.1);
}

.info-card li:last-child {
  border-bottom: none;
}

.animation-card {
  text-align: center;
}

.animation-card h2 {
  margin-bottom: 20px;
  font-size: 20px;
}

.status-card h3 {
  margin-bottom: 16px;
  font-size: 16px;
}

.carrier-grid {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 8px;
}

.carrier-slot {
  aspect-ratio: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.1);
  border: 2px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  font-size: 12px;
  transition: all 0.3s;
}

.carrier-slot.occupied {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.3) 0%, rgba(118, 75, 162, 0.3) 100%);
  border-color: #667eea;
}

.carrier-slot.ground {
  background: rgba(76, 175, 80, 0.3);
  border-color: #4CAF50;
}

.carrier-slot.target {
  background: rgba(255, 152, 0, 0.3);
  border-color: #FF9800;
  animation: pulse-slot 1s infinite;
}

.slot-number {
  font-weight: bold;
  font-size: 14px;
}

.slot-plate {
  font-size: 10px;
  color: rgba(255, 255, 0, 0.9);
  margin-top: 2px;
}

.slot-empty {
  color: rgba(255, 255, 255, 0.4);
  font-size: 10px;
}

@keyframes pulse-slot {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.05); }
}

.footer {
  text-align: center;
  padding: 20px;
  color: rgba(255, 255, 255, 0.5);
  font-size: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}
</style>
