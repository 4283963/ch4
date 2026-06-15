package grpcserver

import (
	"context"
	"log"
	"net"

	"gateway-go/internal/carrier"
	"gateway-go/internal/weight"
	pb "gateway-go/proto"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

type Server struct {
	pb.UnimplementedGarageServiceServer
	carrierManager *carrier.Manager
	antiJamSystem  *weight.AntiJamSystem
	grpcServer     *grpc.Server
	listenAddr     string
}

func NewServer(listenAddr string, carrierManager *carrier.Manager, antiJam *weight.AntiJamSystem) *Server {
	return &Server{
		carrierManager: carrierManager,
		antiJamSystem:  antiJam,
		listenAddr:     listenAddr,
	}
}

func (s *Server) Start() error {
	lis, err := net.Listen("tcp", s.listenAddr)
	if err != nil {
		return err
	}

	s.grpcServer = grpc.NewServer()
	pb.RegisterGarageServiceServer(s.grpcServer, s)

	log.Printf("gRPC server listening on %s", s.listenAddr)
	go func() {
		if err := s.grpcServer.Serve(lis); err != nil {
			log.Fatalf("gRPC server failed: %v", err)
		}
	}()

	return nil
}

func (s *Server) Stop() {
	if s.grpcServer != nil {
		s.grpcServer.GracefulStop()
	}
}

func (s *Server) RotateCarrier(ctx context.Context, req *pb.RotateRequest) (*pb.RotateResponse, error) {
	log.Printf("Received RotateCarrier request: carrier_id=%d, direction=%v, steps=%d",
		req.CarrierId, req.Direction, req.Steps)

	if req.CarrierId < 0 || req.CarrierId >= carrier.TotalCarriers {
		return nil, status.Error(codes.InvalidArgument, "invalid carrier id")
	}

	weightResp, err := s.GetWeightStatus(ctx, &pb.GetWeightStatusRequest{
		CarrierIndex: req.CarrierId,
	})
	if err != nil {
		return nil, status.Error(codes.Internal, "failed to check weight")
	}

	safe, reason := s.antiJamSystem.CheckSafeToOperate(float64(weightResp.WeightKg))
	if !safe {
		return &pb.RotateResponse{
			Success: false,
			Message: "operation blocked: " + reason,
		}, status.Error(codes.FailedPrecondition, reason)
	}

	resp, err := s.carrierManager.RotateToCarrier(int(req.CarrierId), req.Direction, int(req.Steps))
	if err != nil {
		log.Printf("Rotation error: %v", err)
		return resp, status.Error(codes.Internal, err.Error())
	}

	return resp, nil
}

func (s *Server) GetCarrierStatus(ctx context.Context, req *pb.GetCarrierStatusRequest) (*pb.GetCarrierStatusResponse, error) {
	status := s.carrierManager.GetCarrierStatus()
	return status, nil
}

func (s *Server) GetWeightStatus(ctx context.Context, req *pb.GetWeightStatusRequest) (*pb.GetWeightStatusResponse, error) {
	return s.carrierManager.GetWeight(int(req.CarrierIndex))
}

func (s *Server) ReadGroundScale(ctx context.Context, req *pb.ReadGroundScaleRequest) (*pb.ReadGroundScaleResponse, error) {
	weightKg, isOverload, maxWeightKg := s.carrierManager.ReadGroundScale()
	log.Printf("ReadGroundScale: weight=%.2fkg, overload=%v, max=%.2fkg", weightKg, isOverload, maxWeightKg)
	return &pb.ReadGroundScaleResponse{
		WeightKg:    float32(weightKg),
		IsOverload:  isOverload,
		MaxWeightKg: float32(maxWeightKg),
	}, nil
}

func (s *Server) SetGroundScaleOverride(ctx context.Context, req *pb.SetGroundScaleOverrideRequest) (*pb.SetGroundScaleOverrideResponse, error) {
	s.carrierManager.GetWeight(0)
	mc := s.carrierManager.ModbusClient()
	if mc != nil {
		mc.SetGroundScaleOverride(float64(req.WeightKg))
		log.Printf("GroundScale override set to %.2fkg", req.WeightKg)
		return &pb.SetGroundScaleOverrideResponse{
			Success: true,
			Message: "ground scale override updated",
		}, nil
	}
	return &pb.SetGroundScaleOverrideResponse{
		Success: false,
		Message: "modbus client not available",
	}, nil
}

func (s *Server) EmergencyStop(ctx context.Context, req *pb.EmergencyStopRequest) (*pb.EmergencyStopResponse, error) {
	log.Printf("Emergency stop requested: %s", req.Reason)
	return s.carrierManager.EmergencyStop(req.Reason)
}

func (s *Server) StreamCarrierStatus(req *pb.StreamCarrierStatusRequest, stream pb.GarageService_StreamCarrierStatusServer) error {
	log.Printf("Client subscribed to carrier status stream")

	statusCh := s.carrierManager.SubscribeStatus()
	defer s.carrierManager.UnsubscribeStatus(statusCh)

	for update := range statusCh {
		if err := stream.Send(&update); err != nil {
			log.Printf("Stream error: %v", err)
			return err
		}
	}

	return nil
}
