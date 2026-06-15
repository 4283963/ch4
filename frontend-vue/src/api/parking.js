import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

api.interceptors.response.use(
  response => response.data,
  error => {
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

export const parkingApi = {
  pickup(licensePlate) {
    return api.post('/parking/pickup', { licensePlate, paymentMethod: 'ONLINE' })
  },

  park(parkData) {
    return api.post('/parking/park', parkData)
  },

  completePickup(licensePlate) {
    return api.post(`/parking/complete/${licensePlate}`)
  },

  getSpot(licensePlate) {
    return api.get(`/parking/spot/${licensePlate}`)
  },

  getAllSpots() {
    return api.get('/parking/spots')
  },

  getAvailableSpots() {
    return api.get('/parking/available')
  },

  getRecords(licensePlate) {
    return api.get('/parking/records', { params: { licensePlate } })
  },

  getQueueStatus(requestId) {
    return api.get('/parking/queue-status', { params: { requestId } })
  },

  forceReleaseLock() {
    return api.post('/parking/force-release-lock')
  },

  getGroundScale() {
    return api.get('/parking/ground-scale')
  },

  setGroundScaleOverride(weightKg) {
    return api.post('/parking/ground-scale/override', { weightKg })
  }
}

export default api
