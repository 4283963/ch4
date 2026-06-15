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
  }
}

export default api
