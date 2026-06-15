<template>
  <div class="app-container">
    <header class="header">
      <div class="logo">
        <div class="logo-icon">🚗</div>
        <div class="logo-text">
          <h1>智能垂直循环式立体车库</h1>
          <p>写字楼存取车终端系统</p>
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
        <div class="tab-bar">
          <button
            class="tab-btn"
            :class="{ active: activeTab === 'pickup' }"
            @click="activeTab = 'pickup'"
          >
            🚙 取车
          </button>
          <button
            class="tab-btn"
            :class="{ active: activeTab === 'park' }"
            @click="activeTab = 'park'; resetParkForm()"
          >
            🅿️ 存车
          </button>
        </div>

        <div v-if="activeTab === 'pickup'" class="card pickup-card">
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

        <div v-else class="card park-card">
          <h2>存车服务</h2>
          <p class="subtitle">请将车辆驶入承载吊篮，然后填写信息</p>

          <div class="scale-info" :class="{ overload: parkScaleInfo?.isOverload }">
            <div class="scale-icon">⚖️</div>
            <div>
              <p class="scale-label">地磅重量检测</p>
              <p class="scale-value">
                <span :class="{ 'overload-text': parkScaleInfo?.isOverload }">
                  {{ parkScaleInfo?.weightKg?.toFixed(0) || '--' }} kg
                </span>
                <span class="scale-max"> / 最大 {{ parkScaleInfo?.maxWeightKg || 2500 }} kg</span>
              </p>
              <p v-if="parkScaleInfo?.isOverload" class="scale-warn blink-red">⚠️ 超重！禁止存车</p>
            </div>
            <button class="btn-refresh" @click="refreshScale" :disabled="isParkSubmitting">
              🔄 刷新
            </button>
          </div>

          <div class="input-group">
            <label>车牌号</label>
            <input
              v-model="parkForm.licensePlate"
              type="text"
              class="input"
              placeholder="例如：京D88888"
            />
          </div>

          <div class="input-group">
            <label>车型（选填）</label>
            <input
              v-model="parkForm.carModel"
              type="text"
              class="input"
              placeholder="例如：比亚迪汉EV"
            />
          </div>

          <div class="input-group">
            <label>车主姓名（选填）</label>
            <input
              v-model="parkForm.ownerName"
              type="text"
              class="input"
              placeholder="请输入车主姓名"
            />
          </div>

          <div class="input-group">
            <label>联系电话（选填）</label>
            <input
              v-model="parkForm.ownerPhone"
              type="text"
              class="input"
              placeholder="请输入联系电话"
            />
          </div>

          <div class="input-group">
            <label>
              调试：模拟地磅重量(kg)
              <span class="hint">（用于模拟超重场景测试）</span>
            </label>
            <input
              v-model.number="simulateWeightKg"
              type="number"
              class="input"
              placeholder="输入大于2500可模拟超重"
            />
            <button class="btn-small" @click="applySimulateWeight" style="margin-top:8px;">
              应用模拟重量
            </button>
          </div>

          <button
            class="btn btn-success park-btn"
            :disabled="!parkForm.licensePlate || isParkSubmitting || parkScaleInfo?.isOverload"
            @click="handlePark"
          >
            {{ parkButtonText }}
          </button>

          <div v-if="parkErrorMessage" class="error-message">
            {{ parkErrorMessage }}
          </div>

          <div v-if="parkSuccessInfo" class="park-success">
            <h3>✅ 存车成功</h3>
            <p>车牌号：<strong>{{ parkSuccessInfo.licensePlate }}</strong></p>
            <p>车位：第 <strong>{{ parkSuccessInfo.carrierIndex + 1 }}</strong> 层吊篮</p>
            <p>重量：<strong>{{ parkSuccessInfo.carWeightKg?.toFixed(0) || '--' }} kg</strong></p>
          </div>
        </div>

        <div class="card info-card">
          <h3>📋 使用说明</h3>
          <ul>
            <li>取车：输入车牌号后点击"立即取车"</li>
            <li>存车：先将车辆驶入吊篮，地磅检测重量后填写信息</li>
            <li>车辆限重 2500kg（2.5 吨），超重将禁止启动</li>
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

    <div v-if="overloadModal.visible" class="overload-modal-backdrop" @click="closeOverloadModal">
      <div class="overload-modal" @click.stop>
        <div class="overload-icon-big blink-red-bg">🚨</div>
        <h2 class="blink-red">车辆超重，请驶离！</h2>
        <div class="overload-weight-box">
          <p class="ow-current">当前重量</p>
          <p class="ow-value blink-red">{{ overloadModal.weightKg?.toFixed(0) }} kg</p>
          <p class="ow-max">最大限重：{{ overloadModal.maxWeightKg?.toFixed(0) }} kg</p>
          <p class="ow-over">超重 <strong>{{ (overloadModal.weightKg - overloadModal.maxWeightKg).toFixed(0) }} kg</strong></p>
        </div>
        <p class="overload-tip">
          为保护车库链条与电动机，超过限重车辆禁止入库。<br/>
          请将车辆驶离吊篮，或联系工作人员协助。
        </p>
        <button class="btn btn-primary overload-close-btn" @click="closeOverloadModal">
          我已知晓
        </button>
      </div>
    </div>

    <footer class="footer">
      <p>© 2024 智能立体车库管理系统 | 全自动智能垂直循环式</p>
    </footer>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import GarageAnimation from './components/GarageAnimation.vue'
import { parkingApi } from './api/parking.js'

const totalCarriers = 8
const activeTab = ref('pickup')
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

const parkForm = ref({
  licensePlate: '',
  carModel: '',
  ownerName: '',
  ownerPhone: '',
  carWeightKg: null
})
const simulateWeightKg = ref(null)
const isParkSubmitting = ref(false)
const parkErrorMessage = ref('')
const parkSuccessInfo = ref(null)
const parkScaleInfo = ref(null)

const overloadModal = ref({
  visible: false,
  weightKg: 0,
  maxWeightKg: 2500
})

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
let scalePollInterval = null

const buttonText = computed(() => {
  if (isSubmitting) return '请求中...'
  if (isRotating) return '车辆传送中...'
  if (pickupResult.value?.status === 'QUEUED') return '排队等待中...'
  return '立即取车'
})

const parkButtonText = computed(() => {
  if (isParkSubmitting) return '正在存车...'
  if (parkScaleInfo.value?.isOverload) return '超重禁止存车'
  return '确认存车'
})

const statusTitle = computed(() => {
  if (!pickupResult.value) return ''
  const s = pickupResult.value.status
  if (s === 'QUEUED') return '⏳ 排队等待中'
  if (s === 'ROTATING') return '🔄 正在调车'
  return '取车信息'
})

watch(() => parkScaleInfo.value?.isOverload, (newVal, oldVal) => {
  if (newVal && !oldVal && parkScaleInfo.value) {
    showOverloadModal(parkScaleInfo.value.weightKg, parkScaleInfo.value.maxWeightKg)
  }
})

function showOverloadModal(weightKg, maxWeightKg) {
  overloadModal.value = {
    visible: true,
    weightKg,
    maxWeightKg
  }
}

function closeOverloadModal() {
  overloadModal.value.visible = false
}

function formatDuration(minutes) {
  if (!minutes) return '0分钟'
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  if (hours > 0) return `${hours}小时${mins}分钟`
  return `${mins}分钟`
}

function resetParkForm() {
  parkForm.value = {
    licensePlate: '',
    carModel: '',
    ownerName: '',
    ownerPhone: '',
    carWeightKg: null
  }
  parkErrorMessage.value = ''
  parkSuccessInfo.value = null
  refreshScale()
}

async function refreshScale() {
  try {
    const result = await parkingApi.getGroundScale()
    if (result.code === 200 && result.data) {
      parkScaleInfo.value = result.data
    }
  } catch (e) {
    console.error('Scale read error:', e)
  }
}

async function applySimulateWeight() {
  if (simulateWeightKg.value == null) return
  try {
    const result = await parkingApi.setGroundScaleOverride(simulateWeightKg.value)
    if (result.code === 200) {
      await refreshScale()
    }
  } catch (e) {
    console.error('Override weight error:', e)
  }
}

async function handlePark() {
  if (!parkForm.value.licensePlate.trim() || isParkSubmitting.value) return
  if (parkScaleInfo.value?.isOverload) {
    showOverloadModal(parkScaleInfo.value.weightKg, parkScaleInfo.value.maxWeightKg)
    return
  }

  parkErrorMessage.value = ''
  parkSuccessInfo.value = null
  isParkSubmitting.value = true

  try {
    const payload = { ...parkForm.value }
    if (parkScaleInfo.value && parkScaleInfo.value.weightKg > 0) {
      payload.carWeightKg = parkScaleInfo.value.weightKg
    }

    const result = await parkingApi.park(payload)

    if (result.code === 200 && result.data) {
      parkSuccessInfo.value = result.data
      loadSpotsStatus()
      loadAvailableSpots()
    } else {
      parkErrorMessage.value = result.message || '存车失败'

      if (result.code === 413 || result.overloadWeightKg) {
        const wKg = result.overloadWeightKg || parkScaleInfo.value?.weightKg || 0
        const mKg = result.maxWeightKg || parkScaleInfo.value?.maxWeightKg || 2500
        showOverloadModal(wKg, mKg)
      }
    }
  } catch (error) {
    console.error('Park error:', error)
    if (error.response?.data?.code === 413) {
      const d = error.response.data
      parkErrorMessage.value = d.message
      showOverloadModal(
        d.overloadWeightKg || parkScaleInfo.value?.weightKg || 0,
        d.maxWeightKg || 2500
      )
    } else {
      parkErrorMessage.value = '存车请求失败，请稍后重试'
    }
  } finally {
    isParkSubmitting.value = false
  }
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
      loadSpotsStatus()
      loadAvailableSpots()
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
  refreshScale()
  if (scalePollInterval) clearInterval(scalePollInterval)
  scalePollInterval = setInterval(() => {
    if (activeTab.value === 'park') refreshScale()
  }, 3000)
})

onUnmounted(() => {
  if (rotationInterval) clearInterval(rotationInterval)
  if (queuePollInterval) clearInterval(queuePollInterval)
  if (scalePollInterval) clearInterval(scalePollInterval)
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

.tab-bar {
  display: flex;
  gap: 8px;
  margin-bottom: -4px;
}

.tab-btn {
  flex: 1;
  padding: 14px 20px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-bottom: none;
  border-radius: 12px 12px 0 0;
  color: rgba(255, 255, 255, 0.7);
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
}

.tab-btn:hover {
  background: rgba(255, 255, 255, 0.15);
  color: white;
}

.tab-btn.active {
  background: rgba(102, 126, 234, 0.25);
  border-color: rgba(102, 126, 234, 0.5);
  color: white;
  box-shadow: 0 -2px 20px rgba(102, 126, 234, 0.3);
}

.park-card h2 {
  font-size: 24px;
  margin-bottom: 8px;
}

.park-card .subtitle {
  color: rgba(255, 255, 255, 0.6);
  margin-bottom: 20px;
  font-size: 14px;
}

.scale-info {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  background: rgba(76, 175, 80, 0.1);
  border: 2px solid rgba(76, 175, 80, 0.3);
  border-radius: 12px;
  margin-bottom: 20px;
  transition: all 0.3s;
}

.scale-info.overload {
  background: rgba(244, 67, 54, 0.15);
  border-color: rgba(244, 67, 54, 0.6);
  animation: blink-border 0.8s infinite;
}

@keyframes blink-border {
  0%, 100% { border-color: rgba(244, 67, 54, 0.6); background: rgba(244, 67, 54, 0.15); }
  50% { border-color: #f44336; background: rgba(244, 67, 54, 0.3); }
}

.scale-icon {
  font-size: 36px;
  flex-shrink: 0;
}

.scale-info > div:nth-child(2) {
  flex: 1;
}

.scale-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
  margin-bottom: 4px;
}

.scale-value {
  font-size: 22px;
  font-weight: bold;
  color: #4CAF50;
}

.scale-value .overload-text {
  color: #f44336;
  animation: blink-text 0.6s infinite;
}

.scale-max {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.5);
  font-weight: normal;
}

.scale-warn {
  margin-top: 6px;
  font-size: 13px;
  font-weight: 600;
}

.blink-red {
  color: #f44336;
  animation: blink-text 0.6s infinite;
}

@keyframes blink-text {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.blink-red-bg {
  animation: blink-bg 0.6s infinite;
}

@keyframes blink-bg {
  0%, 100% { background: rgba(244, 67, 54, 0.9); }
  50% { background: rgba(255, 213, 0, 0.9); }
}

.btn-refresh {
  padding: 8px 14px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  color: white;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.3s;
  flex-shrink: 0;
}

.btn-refresh:hover:not(:disabled) {
  background: rgba(102, 126, 234, 0.3);
  border-color: #667eea;
}

.btn-refresh:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.hint {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
  font-weight: normal;
  margin-left: 4px;
}

.btn-small {
  padding: 8px 16px;
  background: rgba(255, 152, 0, 0.2);
  border: 1px solid rgba(255, 152, 0, 0.4);
  border-radius: 8px;
  color: #FF9800;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.3s;
  width: 100%;
}

.btn-small:hover {
  background: rgba(255, 152, 0, 0.3);
  border-color: #FF9800;
}

.btn-success {
  background: linear-gradient(135deg, #4CAF50 0%, #2E7D32 100%);
  color: white;
  border: none;
  border-radius: 10px;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.3s;
}

.btn-success:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(76, 175, 80, 0.4);
}

.btn-success:disabled {
  background: rgba(150, 150, 150, 0.5);
  cursor: not-allowed;
}

.park-btn {
  width: 100%;
  padding: 16px;
  font-size: 18px;
  margin-top: 8px;
}

.park-success {
  margin-top: 20px;
  padding: 20px;
  background: rgba(76, 175, 80, 0.15);
  border: 2px solid rgba(76, 175, 80, 0.5);
  border-radius: 12px;
  text-align: center;
}

.park-success h3 {
  color: #4CAF50;
  font-size: 20px;
  margin-bottom: 12px;
}

.park-success p {
  margin: 6px 0;
  color: rgba(255, 255, 255, 0.85);
  font-size: 14px;
}

.park-success strong {
  color: white;
}

.overload-modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.8);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  animation: fadeIn 0.25s;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.overload-modal {
  background: linear-gradient(160deg, #1a1a2e 0%, #16213e 100%);
  border: 3px solid #f44336;
  border-radius: 20px;
  padding: 40px 36px;
  max-width: 460px;
  width: 90%;
  text-align: center;
  animation: shake 0.5s, popup 0.3s;
  box-shadow: 0 0 60px rgba(244, 67, 54, 0.5), 0 20px 60px rgba(0, 0, 0, 0.6);
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  20% { transform: translateX(-10px); }
  40% { transform: translateX(10px); }
  60% { transform: translateX(-8px); }
  80% { transform: translateX(8px); }
}

@keyframes popup {
  from { transform: scale(0.8); }
  to { transform: scale(1); }
}

.overload-icon-big {
  width: 90px;
  height: 90px;
  line-height: 90px;
  margin: 0 auto 20px;
  font-size: 54px;
  border-radius: 50%;
  color: white;
  background: rgba(244, 67, 54, 0.9);
}

.overload-modal h2 {
  font-size: 26px;
  margin-bottom: 24px;
  font-weight: bold;
}

.overload-weight-box {
  background: rgba(244, 67, 54, 0.15);
  border: 1px solid rgba(244, 67, 54, 0.4);
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 20px;
}

.ow-current {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
  margin-bottom: 4px;
}

.ow-value {
  font-size: 42px;
  font-weight: bold;
  margin-bottom: 6px;
}

.ow-max {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.7);
  margin-bottom: 8px;
}

.ow-over {
  font-size: 16px;
  color: #FF9800;
  margin-top: 8px;
}

.overload-tip {
  color: rgba(255, 255, 255, 0.8);
  font-size: 14px;
  line-height: 1.7;
  margin-bottom: 24px;
}

.overload-close-btn {
  width: 100%;
  padding: 14px;
  font-size: 16px;
  background: linear-gradient(135deg, #f44336 0%, #c62828 100%);
  color: white;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  font-weight: bold;
  transition: all 0.3s;
}

.overload-close-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(244, 67, 54, 0.4);
}
</style>
