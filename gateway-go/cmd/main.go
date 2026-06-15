package main

import (
	"flag"
	"log"
	"os"
	"os/signal"
	"syscall"

	"gateway-go/internal/carrier"
	"gateway-go/internal/grpcserver"
	"gateway-go/internal/modbus"
	"gateway-go/internal/weight"
)

var (
	grpcAddr   = flag.String("grpc", ":50051", "gRPC server address")
	serialPort = flag.String("serial", "/dev/ttyUSB0", "serial port for Modbus")
	baudRate   = flag.Int("baud", 9600, "Modbus baud rate")
	simulate   = flag.Bool("simulate", true, "simulate Modbus device")
	maxWeight  = flag.Float64("max-weight", 2500.0, "maximum weight per carrier in kg")
)

func main() {
	flag.Parse()

	log.Println("=== 全自动智能垂直循环式立体车库工控机网关 ===")
	log.Printf("gRPC 地址: %s", *grpcAddr)
	log.Printf("串口: %s (波特率: %d)", *serialPort, *baudRate)
	log.Printf("模拟模式: %v", *simulate)
	log.Printf("最大载重量: %.1f kg", *maxWeight)

	modbusClient := modbus.NewModbusClient(*serialPort, *baudRate, *simulate)
	if err := modbusClient.Connect(); err != nil {
		log.Fatalf("Failed to connect Modbus: %v", err)
	}
	defer modbusClient.Disconnect()
	log.Println("Modbus 连接成功")

	weightSensor := weight.NewSensor(*maxWeight)
	antiJamSystem := weight.NewAntiJamSystem(weightSensor)
	log.Println("重量检测与防卡阻系统初始化完成")

	carrierManager := carrier.NewManager(modbusClient)
	log.Printf("吊篮管理器初始化完成，共 %d 个吊篮", carrier.TotalCarriers)

	grpcServer := grpcserver.NewServer(*grpcAddr, carrierManager, antiJamSystem)
	if err := grpcServer.Start(); err != nil {
		log.Fatalf("Failed to start gRPC server: %v", err)
	}
	defer grpcServer.Stop()
	log.Println("gRPC 服务启动成功")

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	<-sigCh

	log.Println("正在关闭网关...")
	log.Println("网关已安全关闭")
}
