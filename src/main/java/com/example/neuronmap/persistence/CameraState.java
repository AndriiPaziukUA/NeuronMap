package com.example.neuronmap.persistence;

public record CameraState(
        double zoom,
        double panX,
        double panY
) {
    public static CameraState defaultState() {
        return new CameraState(
                1.0,
                220.0,
                130.0
        );
    }
}
