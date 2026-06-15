package carrier

import (
	"errors"
	"sync"
	"time"

	"gateway-go/internal/modbus"
	pb "gateway-go/proto"
)

const (
	TotalCarriers     = 8
	DegreesPerCarrier = 45.0
	GroundAngle       = 270.0
	MaxWeightKg       = 2000.0
	StepsPerSecond    = 0.5
)

type RotationCommand struct {
	TargetIdx int
	Direction pb.RotateDirection
	Steps     int
	ResultCh  chan *RotationResult
}

type RotationResult struct {
	Success bool
	Message string
}

type Manager struct {
	mu                sync.RWMutex
	modbusClient      *modbus.ModbusClient
	carriers          []*Carrier
	groundCarrierIdx  int
	status            pb.CarrierStatus
	rotating          bool
	targetIdx         int
	startIdx          int
	startTime         time.Time
	totalSteps        int
	direction         pb.RotateDirection
	rotateSpeed       float64
	statusSubscribers []chan pb.CarrierStatusUpdate
	obstruction       bool

	commandQueue chan RotationCommand
	queueRunning bool
}

type Carrier struct {
	ID           int
	LicensePlate string
	HasCar       bool
	Angle        float64
	WeightKg     float64
}

func NewManager(modbusClient *modbus.ModbusClient) *Manager {
	carriers := make([]*Carrier, TotalCarriers)
	for i := 0; i < TotalCarriers; i++ {
		carriers[i] = &Carrier{
			ID:           i,
			LicensePlate: "",
			HasCar:       false,
			Angle:        float64(i) * DegreesPerCarrier,
			WeightKg:     0,
		}
	}

	carriers[0].LicensePlate = "京A12345"
	carriers[0].HasCar = true
	carriers[0].WeightKg = 1250.5

	carriers[2].LicensePlate = "沪B67890"
	carriers[2].HasCar = true
	carriers[2].WeightKg = 980.3

	carriers[4].LicensePlate = "粤C11111"
	carriers[4].HasCar = true
	carriers[4].WeightKg = 1500.0

	m := &Manager{
		modbusClient:     modbusClient,
		carriers:         carriers,
		groundCarrierIdx: 0,
		status:           pb.CarrierStatus_IDLE,
		rotateSpeed:      2.0,
		commandQueue:     make(chan RotationCommand, 64),
		queueRunning:     false,
	}

	go m.angleUpdateLoop()
	go m.commandProcessor()

	return m
}

func (m *Manager) commandProcessor() {
	for cmd := range m.commandQueue {
		m.mu.Lock()
		for m.rotating {
			m.mu.Unlock()
			time.Sleep(50 * time.Millisecond)
			m.mu.Lock()
		}
		m.mu.Unlock()

		result := m.executeRotation(cmd.TargetIdx, cmd.Direction, cmd.Steps)
		cmd.ResultCh <- result
	}
}

func (m *Manager) executeRotation(targetIdx int, direction pb.RotateDirection, steps int) *RotationResult {
	m.mu.Lock()

	if targetIdx < 0 || targetIdx >= TotalCarriers {
		m.mu.Unlock()
		return &RotationResult{Success: false, Message: "invalid carrier index"}
	}

	weight := m.carriers[targetIdx].WeightKg
	if weight > MaxWeightKg {
		m.status = pb.CarrierStatus_WEIGHT_OVERLOAD
		m.mu.Unlock()
		return &RotationResult{Success: false, Message: "weight overload detected"}
	}

	m.rotating = true
	m.status = pb.CarrierStatus_ROTATING
	m.targetIdx = targetIdx
	m.startIdx = m.groundCarrierIdx
	m.startTime = time.Now()
	m.totalSteps = steps
	m.direction = direction
	m.mu.Unlock()

	done := make(chan struct{})

	go func() {
		defer close(done)
		m.mu.Lock()
		clockwise := direction == pb.RotateDirection_CLOCKWISE
		stepDegrees := m.rotateSpeed
		m.mu.Unlock()

		m.modbusClient.StartMotor(clockwise, int(stepDegrees*10))

		ticker := time.NewTicker(50 * time.Millisecond)
		defer ticker.Stop()

		for range ticker.C {
			m.mu.RLock()
			if !m.rotating {
				m.mu.RUnlock()
				break
			}
			currentIdx := m.groundCarrierIdx
			m.mu.RUnlock()

			if currentIdx == targetIdx {
				m.stopRotation(false)
				break
			}
		}
	}()

	<-done

	return &RotationResult{Success: true, Message: "rotation completed"}
}

func (m *Manager) angleUpdateLoop() {
	ticker := time.NewTicker(50 * time.Millisecond)
	defer ticker.Stop()

	for range ticker.C {
		m.updateCarrierAngles()
		m.broadcastStatus()
	}
}

func (m *Manager) updateCarrierAngles() {
	m.mu.Lock()
	defer m.mu.Unlock()

	angle, _ := m.modbusClient.ReadAngle()

	for i := 0; i < TotalCarriers; i++ {
		carrierAngle := (angle + float64(i)*DegreesPerCarrier)
		for carrierAngle >= 360 {
			carrierAngle -= 360
		}
		m.carriers[i].Angle = carrierAngle
	}

	minDiff := 360.0
	groundIdx := 0
	for i := 0; i < TotalCarriers; i++ {
		diff := absAngleDiff(m.carriers[i].Angle, GroundAngle)
		if diff < minDiff {
			minDiff = diff
			groundIdx = i
		}
	}
	m.groundCarrierIdx = groundIdx
}

func absAngleDiff(a, b float64) float64 {
	diff := a - b
	if diff < 0 {
		diff = -diff
	}
	if diff > 180 {
		diff = 360 - diff
	}
	return diff
}

func (m *Manager) GetCarrierStatus() *pb.GetCarrierStatusResponse {
	m.mu.RLock()
	defer m.mu.RUnlock()

	angles := make([]float32, TotalCarriers)
	plates := make([]string, TotalCarriers)
	for i := 0; i < TotalCarriers; i++ {
		angles[i] = float32(m.carriers[i].Angle)
		plates[i] = m.carriers[i].LicensePlate
	}

	currentAngle, _ := m.modbusClient.ReadAngle()

	return &pb.GetCarrierStatusResponse{
		Status:             m.status,
		CurrentFloor:       int32(m.getFloorForCarrier(m.groundCarrierIdx)),
		GroundCarrierIndex: int32(m.groundCarrierIdx),
		CurrentAngle:       float32(currentAngle),
		CarrierAngles:      angles,
		LicensePlates:      plates,
	}
}

func (m *Manager) getFloorForCarrier(carrierIdx int) int {
	angle := m.carriers[carrierIdx].Angle
	if angle >= 247.5 && angle < 292.5 {
		return 1
	} else if (angle >= 292.5 && angle < 360) || (angle >= 0 && angle < 22.5) {
		return 2
	} else if angle >= 22.5 && angle < 67.5 {
		return 3
	} else if angle >= 67.5 && angle < 112.5 {
		return 4
	} else if angle >= 112.5 && angle < 157.5 {
		return 4
	} else if angle >= 157.5 && angle < 202.5 {
		return 3
	} else if angle >= 202.5 && angle < 247.5 {
		return 2
	}
	return 1
}

func (m *Manager) RotateToCarrier(targetIdx int, direction pb.RotateDirection, steps int) (*pb.RotateResponse, error) {
	m.mu.RLock()
	queueLen := len(m.commandQueue)
	m.mu.RUnlock()

	if queueLen >= 60 {
		return &pb.RotateResponse{
			Success: false,
			Message: "command queue full",
		}, errors.New("queue full")
	}

	resultCh := make(chan *RotationResult, 1)
	cmd := RotationCommand{
		TargetIdx: targetIdx,
		Direction: direction,
		Steps:     steps,
		ResultCh:  resultCh,
	}

	select {
	case m.commandQueue <- cmd:
		logf("Rotation command queued: target=%d, direction=%v, steps=%d, queueLen=%d",
			targetIdx, direction, steps, len(m.commandQueue))
	default:
		return &pb.RotateResponse{
			Success: false,
			Message: "command queue full, try again later",
		}, errors.New("queue full")
	}

	select {
	case result := <-resultCh:
		if result.Success {
			estimatedTime := int32(float64(steps) * 1000.0 / StepsPerSecond)
			return &pb.RotateResponse{
				Success:         true,
				Message:         result.Message,
				EstimatedTimeMs: estimatedTime,
			}, nil
		}
		return &pb.RotateResponse{
			Success: false,
			Message: result.Message,
		}, errors.New(result.Message)
	case <-time.After(120 * time.Second):
		return &pb.RotateResponse{
			Success: false,
			Message: "rotation timeout",
		}, errors.New("timeout")
	}
}

func logf(format string, args ...interface{}) {
}

func (m *Manager) stopRotation(emergency bool) {
	m.mu.Lock()
	defer m.mu.Unlock()

	m.modbusClient.StopMotor()
	m.rotating = false

	if emergency {
		m.status = pb.CarrierStatus_EMERGENCY_STOPPED
	} else {
		m.status = pb.CarrierStatus_IDLE
	}
}

func (m *Manager) EmergencyStop(reason string) (*pb.EmergencyStopResponse, error) {
	m.mu.Lock()
	defer m.mu.Unlock()

	m.modbusClient.StopMotor()
	m.rotating = false
	m.status = pb.CarrierStatus_EMERGENCY_STOPPED

	queueLen := len(m.commandQueue)
	for i := 0; i < queueLen; i++ {
		select {
		case cmd := <-m.commandQueue:
			cmd.ResultCh <- &RotationResult{Success: false, Message: "emergency stop"}
		default:
		}
	}

	return &pb.EmergencyStopResponse{
		Success: true,
		Message: "emergency stop activated: " + reason,
	}, nil
}

func (m *Manager) GetWeight(carrierIndex int) (*pb.GetWeightStatusResponse, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()

	if carrierIndex < 0 || carrierIndex >= TotalCarriers {
		return nil, errors.New("invalid carrier index")
	}

	weight := m.carriers[carrierIndex].WeightKg
	return &pb.GetWeightStatusResponse{
		WeightKg:    float32(weight),
		IsOverload:  weight > MaxWeightKg,
		MaxWeightKg: float32(MaxWeightKg),
	}, nil
}

func (m *Manager) SubscribeStatus() <-chan pb.CarrierStatusUpdate {
	m.mu.Lock()
	defer m.mu.Unlock()

	ch := make(chan pb.CarrierStatusUpdate, 100)
	m.statusSubscribers = append(m.statusSubscribers, ch)
	return ch
}

func (m *Manager) UnsubscribeStatus(ch <-chan pb.CarrierStatusUpdate) {
	m.mu.Lock()
	defer m.mu.Unlock()

	for i, subscriber := range m.statusSubscribers {
		if subscriber == ch {
			m.statusSubscribers = append(m.statusSubscribers[:i], m.statusSubscribers[i+1:]...)
			close(subscriber)
			return
		}
	}
}

func (m *Manager) broadcastStatus() {
	m.mu.RLock()
	defer m.mu.RUnlock()

	if len(m.statusSubscribers) == 0 {
		return
	}

	currentAngle, _ := m.modbusClient.ReadAngle()
	progress := float32(0)
	targetIdx := int32(0)

	if m.rotating && m.totalSteps > 0 {
		stepsDone := abs(m.groundCarrierIdx-m.startIdx) % TotalCarriers
		if stepsDone != 0 {
			progress = float32(stepsDone) / float32(m.totalSteps)
		}
		if progress > 1.0 {
			progress = 1.0
		}
		targetIdx = int32(m.targetIdx)
	}

	update := pb.CarrierStatusUpdate{
		Status:             m.status,
		GroundCarrierIndex: int32(m.groundCarrierIdx),
		CurrentAngle:       float32(currentAngle),
		Timestamp:          time.Now().UnixNano() / int64(time.Millisecond),
		Progress:           &progress,
		TargetCarrierIndex: &targetIdx,
	}

	for _, ch := range m.statusSubscribers {
		select {
		case ch <- update:
		default:
		}
	}
}

func abs(x int) int {
	if x < 0 {
		return -x
	}
	return x
}

func (m *Manager) FindCarrierByPlate(plate string) int {
	m.mu.RLock()
	defer m.mu.RUnlock()

	for i, c := range m.carriers {
		if c.LicensePlate == plate && c.HasCar {
			return i
		}
	}
	return -1
}

func (m *Manager) ParkCar(carrierIdx int, plate string, weight float64) error {
	m.mu.Lock()
	defer m.mu.Unlock()

	if carrierIdx < 0 || carrierIdx >= TotalCarriers {
		return errors.New("invalid carrier index")
	}

	if m.carriers[carrierIdx].HasCar {
		return errors.New("carrier already occupied")
	}

	m.carriers[carrierIdx].LicensePlate = plate
	m.carriers[carrierIdx].HasCar = true
	m.carriers[carrierIdx].WeightKg = weight
	return nil
}

func (m *Manager) RemoveCar(carrierIdx int) error {
	m.mu.Lock()
	defer m.mu.Unlock()

	if carrierIdx < 0 || carrierIdx >= TotalCarriers {
		return errors.New("invalid carrier index")
	}

	m.carriers[carrierIdx].LicensePlate = ""
	m.carriers[carrierIdx].HasCar = false
	m.carriers[carrierIdx].WeightKg = 0
	return nil
}
