<template>
  <div class="garage-animation-container">
    <canvas ref="canvasRef" :width="canvasWidth" :height="canvasHeight"></canvas>
    <div class="garage-info">
      <div class="info-item">
        <span class="label">当前地面吊篮</span>
        <span class="value">#{{ groundCarrierIndex }}</span>
      </div>
      <div class="info-item" v-if="targetCarrierIndex !== null">
        <span class="label">目标吊篮</span>
        <span class="value">#{{ targetCarrierIndex }}</span>
      </div>
      <div class="info-item" v-if="isRotating">
        <span class="label">旋转进度</span>
        <span class="value">{{ Math.round(progress * 100) }}%</span>
      </div>
      <div class="info-item">
        <span class="label">状态</span>
        <span class="value status" :class="statusClass">{{ statusText }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'

const props = defineProps({
  totalCarriers: {
    type: Number,
    default: 8
  },
  groundCarrierIndex: {
    type: Number,
    default: 0
  },
  targetCarrierIndex: {
    type: Number,
    default: null
  },
  isRotating: {
    type: Boolean,
    default: false
  },
  progress: {
    type: Number,
    default: 0
  },
  status: {
    type: String,
    default: 'IDLE'
  },
  carriers: {
    type: Array,
    default: () => []
  }
})

const canvasRef = ref(null)
const canvasWidth = 600
const canvasHeight = 700

let animationId = null
let currentAngle = 0
let targetAngle = 0

const statusText = computed(() => {
  const statusMap = {
    'IDLE': '待机中',
    'ROTATING': '旋转中',
    'EMERGENCY_STOPPED': '紧急停止',
    'WEIGHT_OVERLOAD': '重量超限',
    'OBSTRUCTED': '障碍物检测'
  }
  return statusMap[props.status] || props.status
})

const statusClass = computed(() => {
  return props.status.toLowerCase().replace(/_/g, '-')
})

const degreesPerCarrier = 360 / props.totalCarriers
const groundAngle = 270

function getCarrierAngle(index) {
  return (groundAngle + index * degreesPerCarrier + currentAngle) % 360
}

function draw() {
  const canvas = canvasRef.value
  if (!canvas) return

  const ctx = canvas.getContext('2d')
  const centerX = canvasWidth / 2
  const centerY = canvasHeight / 2 - 20
  const radius = 220
  const carrierWidth = 90
  const carrierHeight = 60

  ctx.clearRect(0, 0, canvasWidth, canvasHeight)

  const gradient = ctx.createRadialGradient(centerX, centerY, 0, centerX, centerY, radius + 50)
  gradient.addColorStop(0, 'rgba(102, 126, 234, 0.1)')
  gradient.addColorStop(1, 'rgba(118, 75, 162, 0.05)')
  ctx.fillStyle = gradient
  ctx.beginPath()
  ctx.arc(centerX, centerY, radius + 50, 0, Math.PI * 2)
  ctx.fill()

  ctx.strokeStyle = 'rgba(255, 255, 255, 0.2)'
  ctx.lineWidth = 4
  ctx.beginPath()
  ctx.arc(centerX, centerY, radius, 0, Math.PI * 2)
  ctx.stroke()

  ctx.strokeStyle = 'rgba(255, 255, 255, 0.1)'
  ctx.lineWidth = 2
  ctx.setLineDash([10, 10])
  ctx.beginPath()
  ctx.arc(centerX, centerY, radius - 30, 0, Math.PI * 2)
  ctx.stroke()
  ctx.setLineDash([])

  ctx.fillStyle = 'rgba(76, 175, 80, 0.3)'
  ctx.fillRect(centerX - carrierWidth / 2 - 10, centerY + radius - carrierHeight / 2 - 5, carrierWidth + 20, carrierHeight + 50)
  ctx.strokeStyle = '#4CAF50'
  ctx.lineWidth = 3
  ctx.strokeRect(centerX - carrierWidth / 2 - 10, centerY + radius - carrierHeight / 2 - 5, carrierWidth + 20, carrierHeight + 50)

  ctx.fillStyle = '#4CAF50'
  ctx.font = 'bold 14px sans-serif'
  ctx.textAlign = 'center'
  ctx.fillText('出入口', centerX, centerY + radius + carrierHeight / 2 + 35)

  for (let i = 0; i < props.totalCarriers; i++) {
    const angle = getCarrierAngle(i) * Math.PI / 180
    const x = centerX + Math.cos(angle) * radius
    const y = centerY + Math.sin(angle) * radius

    const isGround = i === props.groundCarrierIndex
    const isTarget = i === props.targetCarrierIndex
    const hasCar = props.carriers[i]?.hasCar || false

    ctx.save()
    ctx.translate(x, y)

    const carrierGradient = ctx.createLinearGradient(-carrierWidth/2, -carrierHeight/2, -carrierWidth/2, carrierHeight/2)
    if (isGround) {
      carrierGradient.addColorStop(0, '#4CAF50')
      carrierGradient.addColorStop(1, '#45a049')
    } else if (isTarget) {
      carrierGradient.addColorStop(0, '#FF9800')
      carrierGradient.addColorStop(1, '#F57C00')
    } else if (hasCar) {
      carrierGradient.addColorStop(0, '#667eea')
      carrierGradient.addColorStop(1, '#764ba2')
    } else {
      carrierGradient.addColorStop(0, 'rgba(255, 255, 255, 0.2)')
      carrierGradient.addColorStop(1, 'rgba(255, 255, 255, 0.1)')
    }

    ctx.fillStyle = carrierGradient
    ctx.strokeStyle = isGround ? '#4CAF50' : (isTarget ? '#FF9800' : 'rgba(255, 255, 255, 0.3)')
    ctx.lineWidth = isGround || isTarget ? 3 : 1

    const cornerRadius = 8
    ctx.beginPath()
    ctx.moveTo(-carrierWidth/2 + cornerRadius, -carrierHeight/2)
    ctx.lineTo(carrierWidth/2 - cornerRadius, -carrierHeight/2)
    ctx.quadraticCurveTo(carrierWidth/2, -carrierHeight/2, carrierWidth/2, -carrierHeight/2 + cornerRadius)
    ctx.lineTo(carrierWidth/2, carrierHeight/2 - cornerRadius)
    ctx.quadraticCurveTo(carrierWidth/2, carrierHeight/2, carrierWidth/2 - cornerRadius, carrierHeight/2)
    ctx.lineTo(-carrierWidth/2 + cornerRadius, carrierHeight/2)
    ctx.quadraticCurveTo(-carrierWidth/2, carrierHeight/2, -carrierWidth/2, carrierHeight/2 - cornerRadius)
    ctx.lineTo(-carrierWidth/2, -carrierHeight/2 + cornerRadius)
    ctx.quadraticCurveTo(-carrierWidth/2, -carrierHeight/2, -carrierWidth/2 + cornerRadius, -carrierHeight/2)
    ctx.closePath()
    ctx.fill()
    ctx.stroke()

    ctx.fillStyle = 'rgba(0, 0, 0, 0.3)'
    ctx.fillRect(-carrierWidth/2 + 8, -carrierHeight/2 + 15, carrierWidth - 16, 25)

    if (hasCar) {
      ctx.fillStyle = '#e0e0e0'
      ctx.fillRect(-carrierWidth/2 + 15, -carrierHeight/2 + 18, carrierWidth - 30, 18)
      ctx.fillStyle = '#667eea'
      ctx.fillRect(-carrierWidth/2 + 18, -carrierHeight/2 + 10, carrierWidth - 36, 12)
    }

    ctx.fillStyle = 'white'
    ctx.font = 'bold 12px sans-serif'
    ctx.textAlign = 'center'
    ctx.fillText(`#${i}`, 0, carrierHeight/2 - 8)

    if (props.carriers[i]?.licensePlate) {
      ctx.fillStyle = 'rgba(255, 255, 0, 0.9)'
      ctx.font = '10px sans-serif'
      ctx.fillText(props.carriers[i].licensePlate, 0, -carrierHeight/2 + 10)
    }

    ctx.restore()

    ctx.strokeStyle = 'rgba(255, 255, 255, 0.3)'
    ctx.lineWidth = 2
    ctx.beginPath()
    ctx.moveTo(centerX, centerY)
    ctx.lineTo(x, y)
    ctx.stroke()
  }

  ctx.fillStyle = '#667eea'
  ctx.beginPath()
  ctx.arc(centerX, centerY, 20, 0, Math.PI * 2)
  ctx.fill()

  ctx.strokeStyle = 'white'
  ctx.lineWidth = 3
  ctx.stroke()

  if (props.isRotating) {
    const arrowAngle = currentAngle * Math.PI / 180
    ctx.save()
    ctx.translate(centerX, centerY)
    ctx.rotate(arrowAngle)

    ctx.fillStyle = '#FF9800'
    ctx.beginPath()
    ctx.moveTo(0, -30)
    ctx.lineTo(8, -15)
    ctx.lineTo(-8, -15)
    ctx.closePath()
    ctx.fill()

    ctx.restore()
  }

  if (props.isRotating && props.progress > 0) {
    const barWidth = 200
    const barHeight = 8
    const barX = centerX - barWidth / 2
    const barY = canvasHeight - 40

    ctx.fillStyle = 'rgba(255, 255, 255, 0.2)'
    ctx.beginPath()
    ctx.roundRect(barX, barY, barWidth, barHeight, 4)
    ctx.fill()

    const progressGradient = ctx.createLinearGradient(barX, barY, barX + barWidth, barY)
    progressGradient.addColorStop(0, '#667eea')
    progressGradient.addColorStop(1, '#764ba2')
    ctx.fillStyle = progressGradient
    ctx.beginPath()
    ctx.roundRect(barX, barY, barWidth * props.progress, barHeight, 4)
    ctx.fill()
  }
}

function animate() {
  if (props.isRotating) {
    const angleDiff = targetAngle - currentAngle
    if (Math.abs(angleDiff) > 0.1) {
      currentAngle += angleDiff * 0.05
    } else {
      currentAngle = targetAngle
    }
  }

  draw()
  animationId = requestAnimationFrame(animate)
}

function updateTargetAngle() {
  if (props.targetCarrierIndex !== null) {
    const targetDeg = (props.targetCarrierIndex * degreesPerCarrier) % 360
    targetAngle = -targetDeg
  }
}

watch(() => props.targetCarrierIndex, () => {
  updateTargetAngle()
})

watch(() => props.groundCarrierIndex, (newVal) => {
  if (!props.isRotating) {
    currentAngle = -(newVal * degreesPerCarrier)
    targetAngle = currentAngle
  }
})

onMounted(() => {
  updateTargetAngle()
  currentAngle = -(props.groundCarrierIndex * degreesPerCarrier)
  targetAngle = currentAngle
  animate()
})

onUnmounted(() => {
  if (animationId) {
    cancelAnimationFrame(animationId)
  }
})
</script>

<style scoped>
.garage-animation-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

canvas {
  border-radius: 16px;
  background: rgba(0, 0, 0, 0.2);
}

.garage-info {
  display: flex;
  gap: 30px;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  padding: 16px 32px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.info-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.info-item .label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
}

.info-item .value {
  font-size: 18px;
  font-weight: bold;
  color: white;
}

.info-item .value.status.idle {
  color: #4CAF50;
}

.info-item .value.status.rotating {
  color: #FF9800;
  animation: pulse 1s infinite;
}

.info-item .value.status.emergency-stopped,
.info-item .value.status.weight-overload,
.info-item .value.status.obstructed {
  color: #f44336;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}
</style>
