package modbus

import (
	"errors"
	"sync"
	"time"
)

type ModbusClient struct {
	mu           sync.Mutex
	connected    bool
	serialPort   string
	baudRate     int
	currentAngle float64
	motorRunning bool
	motorSpeed   int
	simulate     bool
}

func NewModbusClient(serialPort string, baudRate int, simulate bool) *ModbusClient {
	return &ModbusClient{
		serialPort: serialPort,
		baudRate:   baudRate,
		simulate:   simulate,
		connected:  false,
	}
}

func (m *ModbusClient) Connect() error {
	m.mu.Lock()
	defer m.mu.Unlock()

	if m.simulate {
		m.connected = true
		m.currentAngle = 0.0
		m.motorRunning = false
		return nil
	}

	m.connected = true
	return nil
}

func (m *ModbusClient) Disconnect() error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.connected = false
	m.motorRunning = false
	return nil
}

func (m *ModbusClient) ReadAngle() (float64, error) {
	m.mu.Lock()
	defer m.mu.Unlock()

	if !m.connected {
		return 0, errors.New("modbus client not connected")
	}

	return m.currentAngle, nil
}

func (m *ModbusClient) StartMotor(direction bool, speed int) error {
	m.mu.Lock()
	defer m.mu.Unlock()

	if !m.connected {
		return errors.New("modbus client not connected")
	}

	m.motorRunning = true
	m.motorSpeed = speed

	go func() {
		for {
			m.mu.Lock()
			if !m.motorRunning {
				m.mu.Unlock()
				break
			}
			delta := float64(speed) * 0.01
			if direction {
				m.currentAngle += delta
			} else {
				m.currentAngle -= delta
			}
			if m.currentAngle >= 360 {
				m.currentAngle -= 360
			}
			if m.currentAngle < 0 {
				m.currentAngle += 360
			}
			m.mu.Unlock()
			time.Sleep(10 * time.Millisecond)
		}
	}()

	return nil
}

func (m *ModbusClient) StopMotor() error {
	m.mu.Lock()
	defer m.mu.Unlock()

	if !m.connected {
		return errors.New("modbus client not connected")
	}

	m.motorRunning = false
	return nil
}

func (m *ModbusClient) IsMotorRunning() bool {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.motorRunning
}

func (m *ModbusClient) SetAngle(angle float64) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.currentAngle = angle
}

func (m *ModbusClient) ReadWeight(carrierIndex int) (float64, error) {
	m.mu.Lock()
	defer m.mu.Unlock()

	if !m.connected {
		return 0, errors.New("modbus client not connected")
	}

	baseWeights := []float64{1250.5, 980.3, 1500.0, 850.2, 1100.7, 1350.0, 0.0, 920.5}
	if carrierIndex >= 0 && carrierIndex < len(baseWeights) {
		return baseWeights[carrierIndex], nil
	}
	return 0.0, nil
}

var groundScaleOverride float64 = -1

func (m *ModbusClient) ReadGroundScale() (float64, error) {
	m.mu.Lock()
	defer m.mu.Unlock()

	if !m.connected {
		return 0, errors.New("modbus client not connected")
	}

	if groundScaleOverride >= 0 {
		v := groundScaleOverride
		return v, nil
	}

	return 1250.0, nil
}

func (m *ModbusClient) SetGroundScaleOverride(weightKg float64) {
	m.mu.Lock()
	defer m.mu.Unlock()
	groundScaleOverride = weightKg
}

func (m *ModbusClient) ReadObstructionSensor() (bool, error) {
	m.mu.Lock()
	defer m.mu.Unlock()

	if !m.connected {
		return false, errors.New("modbus client not connected")
	}

	return false, nil
}
