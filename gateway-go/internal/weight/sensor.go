package weight

import (
	"errors"
	"sync"
)

type Sensor struct {
	mu                  sync.RWMutex
	maxWeightKg         float64
	overloadDetected    bool
	obstructionDetected bool
}

func NewSensor(maxWeightKg float64) *Sensor {
	return &Sensor{
		maxWeightKg:         maxWeightKg,
		overloadDetected:    false,
		obstructionDetected: false,
	}
}

func (s *Sensor) CheckOverload(weightKg float64) (bool, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	if weightKg < 0 {
		return false, errors.New("invalid weight")
	}

	return weightKg > s.maxWeightKg, nil
}

func (s *Sensor) GetMaxWeight() float64 {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.maxWeightKg
}

func (s *Sensor) SetMaxWeight(weight float64) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.maxWeightKg = weight
}

func (s *Sensor) SetOverload(detected bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.overloadDetected = detected
}

func (s *Sensor) IsOverload() bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.overloadDetected
}

func (s *Sensor) SetObstruction(detected bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.obstructionDetected = detected
}

func (s *Sensor) IsObstructed() bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.obstructionDetected
}

type AntiJamSystem struct {
	mu           sync.Mutex
	weightSensor *Sensor
	jamDetected  bool
}

func NewAntiJamSystem(weightSensor *Sensor) *AntiJamSystem {
	return &AntiJamSystem{
		weightSensor: weightSensor,
		jamDetected:  false,
	}
}

func (a *AntiJamSystem) CheckSafeToOperate(weightKg float64) (bool, string) {
	a.mu.Lock()
	defer a.mu.Unlock()

	if a.weightSensor.IsObstructed() {
		a.jamDetected = true
		return false, "obstruction detected"
	}

	isOverload, _ := a.weightSensor.CheckOverload(weightKg)
	if isOverload {
		a.jamDetected = true
		return false, "weight overload"
	}

	a.jamDetected = false
	return true, ""
}

func (a *AntiJamSystem) IsJamDetected() bool {
	a.mu.Lock()
	defer a.mu.Unlock()
	return a.jamDetected
}

func (a *AntiJamSystem) ResetJam() {
	a.mu.Lock()
	defer a.mu.Unlock()
	a.jamDetected = false
}
